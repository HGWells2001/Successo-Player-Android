package it.successoplayer.app;

import android.content.Context;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RaiClient {
    public static final String BASE = "https://www.raiplaysound.it";
    public static final String PROGRAM = "successostorieevocidalnovecento";
    public static final String PROGRAM_JSON = BASE + "/programmi/" + PROGRAM + ".json";
    public static final String PROGRAM_WEB = BASE + "/programmi/" + PROGRAM;

    private static final Pattern CDN_PATTERN = Pattern.compile("ostr(\\d+)/(.+?mp\\d)(?=$|[/?#])", Pattern.CASE_INSENSITIVE);
    private static final int TIMEOUT = 30000;

    private RaiClient() {}

    public static final class Catalog {
        public final String title;
        public final List<Episode> episodes;
        Catalog(String title, List<Episode> episodes) {
            this.title = title;
            this.episodes = episodes;
        }
    }

    public static Catalog loadCatalog() throws Exception {
        JSONObject root = getJson(PROGRAM_JSON);
        String programTitle = root.optJSONObject("podcast_info") != null
                ? root.optJSONObject("podcast_info").optString("title", "Successo")
                : "Successo";

        JSONObject block = root.optJSONObject("block");
        if (block == null) throw new IllegalStateException("Catalogo RaiPlay Sound non riconosciuto.");

        List<JSONObject> raw = new ArrayList<>();
        String type = block.optString("content_type", "");
        JSONArray cards = block.optJSONArray("cards");

        if ("playlist".equalsIgnoreCase(type) && cards != null) {
            for (int i = 0; i < cards.length(); i++) {
                JSONObject p = cards.optJSONObject(i);
                if (p == null) continue;
                String path = p.optString("path_id", "");
                if (path.isEmpty()) continue;
                JSONObject playlist = getJson(absoluteUrl(path));
                JSONObject pb = playlist.optJSONObject("block");
                JSONArray eps = pb != null ? pb.optJSONArray("cards") : null;
                if (eps != null) {
                    for (int j = 0; j < eps.length(); j++) {
                        JSONObject ep = eps.optJSONObject(j);
                        if (ep != null) raw.add(ep);
                    }
                }
                Thread.sleep(90);
            }
        } else if (cards != null) {
            for (int i = 0; i < cards.length(); i++) {
                JSONObject ep = cards.optJSONObject(i);
                if (ep != null) raw.add(ep);
            }
        }

        List<Episode> result = new ArrayList<>();
        for (JSONObject ep : raw) {
            Episode parsed = parseEpisode(ep);
            if (parsed != null) result.add(parsed);
        }

        Collections.sort(result, new Comparator<Episode>() {
            @Override public int compare(Episode a, Episode b) {
                int d = b.dateIso.compareTo(a.dateIso);
                if (d != 0) return d;
                return Integer.compare(b.episodeNumber, a.episodeNumber);
            }
        });
        return new Catalog(programTitle, result);
    }

    private static Episode parseEpisode(JSONObject ep) {
        String id = ep.optString("uniquename", "");
        if (id.isEmpty()) return null;
        String title = ep.optString("episode_title", "");
        if (title.isEmpty()) title = ep.optString("title", "Puntata");
        String description = htmlToText(ep.optString("description", ""));
        String web = absoluteUrl(ep.optString("weblink", ""));

        JSONObject audio = ep.optJSONObject("audio");
        JSONObject downloadable = ep.optJSONObject("downloadable_audio");
        String audioUrl = audio != null ? absoluteUrl(audio.optString("url", "")) : "";
        String downloadUrl = downloadable != null ? absoluteUrl(downloadable.optString("url", "")) : "";
        long durationMs = audio != null ? parseDurationMs(audio.optString("duration", "")) : 0L;
        if (durationMs <= 0) durationMs = parseDurationMs(ep.optString("duration_small_format", ""));

        JSONObject track = ep.optJSONObject("track_info");
        String date = track != null ? track.optString("date", "") : "";
        int number = 0;
        if (track != null) {
            try { number = Integer.parseInt(track.optString("episode_number", "0")); } catch (Exception ignored) {}
        }
        String display = formatDate(date);
        return new Episode(id, title, description, date, display, web, audioUrl, downloadUrl, durationMs, number);
    }

    public static String resolveAudioUrl(Episode ep) throws Exception {
        String candidate = !empty(ep.downloadUrl) ? ep.downloadUrl : ep.audioUrl;
        if (empty(candidate)) throw new IllegalStateException("Nessun URL audio disponibile per questa puntata.");

        String redirected = followRedirects(candidate);
        String resolved = redirected;
        Matcher m = CDN_PATTERN.matcher(redirected);
        if (!redirected.matches("^https://creativemedia\\d*-rai-it\\.akamaized\\.net/.*") && m.find()) {
            String number = m.group(1);
            String file = m.group(2);
            resolved = "https://creativemedia" + number + "-rai-it.akamaized.net/" + file;
        }

        String mp3 = resolved.replaceFirst("(?i)mp4(?=($|[?#]))", "mp3");
        if (!mp3.equals(resolved) && testUrl(mp3)) resolved = mp3;
        if (testUrl(resolved)) return resolved;
        if (testUrl(redirected)) return redirected;
        throw new IllegalStateException("Il server Rai ha restituito un flusso audio non raggiungibile.");
    }

    public static File downloadToInternalCache(Context context, Episode ep, String resolvedUrl) throws Exception {
        File dir = new File(context.getCacheDir(), "audio");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Impossibile creare la cache audio.");
        String ext = extensionFor(resolvedUrl, "");
        File out = new File(dir, safeName(ep.id) + ext);
        if (out.isFile() && out.length() > 65536) return out;
        File tmp = new File(out.getAbsolutePath() + ".part");
        if (tmp.exists()) tmp.delete();

        HttpURLConnection c = open(resolvedUrl, "GET", true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IllegalStateException("Download cache fallito: HTTP " + code);
        }
        String type = c.getContentType() != null ? c.getContentType() : "";
        String finalUrl = c.getURL().toString();
        ext = extensionFor(finalUrl, type);
        out = new File(dir, safeName(ep.id) + ext);
        tmp = new File(out.getAbsolutePath() + ".part");

        try (InputStream in = new BufferedInputStream(c.getInputStream());
             BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(tmp))) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) fos.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
        if (tmp.length() < 65536) {
            tmp.delete();
            throw new IllegalStateException("Il file audio ricevuto è incompleto.");
        }
        if (out.exists()) out.delete();
        if (!tmp.renameTo(out)) throw new IllegalStateException("Impossibile finalizzare la cache audio.");
        return out;
    }

    public static String extensionFor(String url, String mime) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.contains("mpeg") || m.contains("mp3") || u.matches(".*\\.mp3(?:$|[?#]).*") || u.matches(".*mp3(?:$|[?#]).*")) return ".mp3";
        if (m.contains("mp4") || m.contains("m4a") || u.matches(".*\\.(mp4|m4a)(?:$|[?#]).*")) return ".m4a";
        if (m.contains("aac") || u.matches(".*\\.aac(?:$|[?#]).*")) return ".aac";
        return ".mp3";
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = open(url, "GET", true);
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new IllegalStateException("RaiPlay Sound: HTTP " + code);
        }
        StringBuilder sb = new StringBuilder();
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) >= 0) sb.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            c.disconnect();
        }
        return new JSONObject(sb.toString());
    }

    private static String followRedirects(String url) throws Exception {
        HttpURLConnection c = open(url, "GET", true);
        c.setRequestProperty("Range", "bytes=0-0");
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) {
            c.disconnect();
            throw new IllegalStateException("Audio Rai non raggiungibile: HTTP " + code);
        }
        String finalUrl = c.getURL().toString();
        c.disconnect();
        return finalUrl;
    }

    private static boolean testUrl(String url) {
        try {
            HttpURLConnection c = open(url, "HEAD", true);
            int code = c.getResponseCode();
            c.disconnect();
            if (code >= 200 && code < 400) return true;
        } catch (Exception ignored) {}
        try {
            HttpURLConnection c = open(url, "GET", true);
            c.setRequestProperty("Range", "bytes=0-0");
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 400;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static HttpURLConnection open(String url, String method, boolean redirects) throws Exception {
        URL u = URI.create(absoluteUrl(url)).toURL();
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(TIMEOUT);
        c.setReadTimeout(TIMEOUT);
        c.setInstanceFollowRedirects(redirects);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SuccessoPlayer/1.0");
        c.setRequestProperty("Accept", "*/*");
        return c;
    }

    public static String absoluteUrl(String u) {
        if (u == null) return "";
        u = u.trim().replace("&amp;", "&");
        if (u.isEmpty()) return "";
        if (u.startsWith("http://") || u.startsWith("https://")) return u;
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("/")) return BASE + u;
        return BASE + "/" + u;
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    public static String safeName(String s) {
        String v = s == null ? "audio" : s.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
        if (v.length() > 120) v = v.substring(0, 120).trim();
        return v.isEmpty() ? "audio" : v;
    }

    private static String htmlToText(String s) {
        if (s == null || s.isEmpty()) return "";
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private static long parseDurationMs(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try {
            String[] p = s.trim().split(":");
            long sec;
            if (p.length == 3) sec = Long.parseLong(p[0]) * 3600 + Long.parseLong(p[1]) * 60 + Long.parseLong(p[2]);
            else if (p.length == 2) sec = Long.parseLong(p[0]) * 60 + Long.parseLong(p[1]);
            else return 0;
            return sec * 1000;
        } catch (Exception e) { return 0; }
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).parse(iso.substring(0, 10));
            return new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(d);
        } catch (Exception e) { return iso; }
    }
}
