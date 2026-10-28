package it.successoplayer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the public, published GitHub Release without an account or token.
 * Never installs an APK silently. The Android package installer verifies the
 * permanent signing certificate when the user installs an update.
 */
public final class UpdateChecker {
    private static final String API =
            "https://api.github.com/repos/HGWells2001/Successo-Player-Android/releases/latest";
    private static final String TRUSTED_DOWNLOAD =
            "https://github.com/HGWells2001/Successo-Player-Android/releases/download/";
    private static final AtomicBoolean CHECK_IN_PROGRESS = new AtomicBoolean(false);
    private static final Pattern TAG =
            Pattern.compile("^v([0-9]+(?:\\.[0-9]+)*)(?:-build([0-9]+))?$");

    private UpdateChecker() {}

    public static void check(Activity activity, SharedPreferences prefs, boolean manual) {
        if (activity == null || prefs == null) return;
        if (!CHECK_IN_PROGRESS.compareAndSet(false, true)) {
            if (manual) Toast.makeText(activity, "Controllo già in corso.", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "SuccessoPlayer-Android");
                if (connection.getResponseCode() != 200) {
                    throw new IllegalStateException("GitHub HTTP " + connection.getResponseCode());
                }

                StringBuilder json = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) json.append(line);
                }
                JSONObject release = new JSONObject(json.toString());
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return;
                String tag = release.optString("tag_name");
                Matcher matcher = TAG.matcher(tag);
                if (!matcher.matches()) throw new IllegalStateException("Tag non valido");

                PackageInfo pkg = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0);
                String installedVersion = pkg.versionName == null ? "0" : pkg.versionName;
                long installedCode = Build.VERSION.SDK_INT >= 28
                        ? pkg.getLongVersionCode() : pkg.versionCode;
                long remoteCode = matcher.group(2) == null
                        ? 0L : Long.parseLong(matcher.group(2));
                int compared = compareVersion(matcher.group(1), installedVersion);
                boolean newer = compared > 0 || (compared == 0 && remoteCode > installedCode);

                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = "";
                if (assets != null) {
                    for (int i = 0; i < assets.length(); ++i) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a == null) continue;
                        String name = a.optString("name");
                        String url = a.optString("browser_download_url");
                        if (name.equals("SuccessoPlayer_Android_latest.apk")
                                && url.startsWith(TRUSTED_DOWNLOAD)) {
                            apkUrl = url;
                            break;
                        }
                    }
                }
                // A Release without a verified APK is not an update offer.
                final String downloadUrl = apkUrl;
                onUi(activity, () -> {
                    if (newer && !downloadUrl.isEmpty()) {
                        new AlertDialog.Builder(activity)
                                .setTitle("Aggiornamento disponibile")
                                .setMessage("Successo Player " + matcher.group(1)
                                        + " (build " + remoteCode + ") è disponibile su GitHub."
                                        + "\\n\\nVuoi scaricare l'APK firmato?")
                                .setPositiveButton("Scarica APK", (d, w) -> {
                                    try {
                                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                                Uri.parse(downloadUrl)));
                                    } catch (Exception error) {
                                        Toast.makeText(activity,
                                                "Impossibile aprire il link GitHub.",
                                                Toast.LENGTH_LONG).show();
                                    }
                                })
                                .setNegativeButton("Più tardi", null)
                                .show();
                    } else if (manual) {
                        Toast.makeText(activity,
                                newer ? "Release senza APK disponibile."
                                        : "Hai già la versione più recente.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                if (manual) onUi(activity, () ->
                        Toast.makeText(activity,
                                "Controllo aggiornamenti non riuscito: verifica Internet o GitHub.",
                                Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
                CHECK_IN_PROGRESS.set(false);
            }
        }, "Successo-Release-Check").start();
    }

    private static void onUi(Activity activity, Runnable task) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) task.run();
        });
    }

    /** Numeric dotted versions: 4.11 > 4.9 (unlike lexical strings). */
    static int compareVersion(String remote, String installed) {
        String[] r = remote.split("\\.");
        String[] l = installed.split("\\.");
        for (int i = 0; i < Math.max(r.length, l.length); ++i) {
            long a = i < r.length ? parsePart(r[i]) : 0L;
            long b = i < l.length ? parsePart(l[i]) : 0L;
            if (a != b) return Long.compare(a, b);
        }
        return 0;
    }

    private static long parsePart(String input) {
        try { return Long.parseLong(input); } catch (NumberFormatException e) { return 0L; }
    }
}
