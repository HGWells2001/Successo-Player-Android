package it.successoplayer.app;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** DownloadManager-backed library, including compatible downloads from older builds. */
final class OfflineEpisodeLibrary {
    static final class Item {
        final Episode episode;
        final long downloadId;
        final long bytes;

        Item(Episode episode, long downloadId, long bytes) {
            this.episode = episode;
            this.downloadId = downloadId;
            this.bytes = bytes;
        }

        String label() {
            return episode.title + "  ·  " +
                    String.format(Locale.ITALY, "%.1f MB", bytes / 1048576.0);
        }
    }

    private OfflineEpisodeLibrary() {}

    static List<Item> scan(Context context, List<Episode> catalog) {
        List<Item> result = new ArrayList<>();
        if (catalog == null || catalog.isEmpty()) return result;
        DownloadManager manager = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return result;

        Map<String, List<Episode>> byTitle = new HashMap<>();
        for (Episode ep : catalog) {
            if (ep == null || ep.title == null) continue;
            List<Episode> matches = byTitle.get(ep.title);
            if (matches == null) {
                matches = new ArrayList<>();
                byTitle.put(ep.title, matches);
            }
            matches.add(ep);
        }

        Set<String> seen = new HashSet<>();
        try (Cursor cursor = manager.query(new DownloadManager.Query())) {
            if (cursor == null) return result;
            int status = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
            int title = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE);
            int location = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI);
            int idCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
            int size = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
            while (cursor.moveToNext()) {
                if (cursor.getInt(status) != DownloadManager.STATUS_SUCCESSFUL) continue;
                List<Episode> matches = byTitle.get(cursor.getString(title));
                if (matches == null) continue;
                long downloadId = cursor.getLong(idCol);
                if (manager.getUriForDownloadedFile(downloadId) == null) continue;
                Episode ep = identifyEpisode(matches, cursor.getString(location));
                if (ep == null || ep.id == null || !seen.add(ep.id)) continue;
                result.add(new Item(ep, downloadId,
                        Math.max(0L, cursor.getLong(size))));
            }
        } catch (Exception ignored) {
            // Some Android versions may hide old records: user can open folder.
        }

        result.sort((a, b) -> {
            String x = a.episode.dateIso == null ? "" : a.episode.dateIso;
            String y = b.episode.dateIso == null ? "" : b.episode.dateIso;
            return y.compareTo(x);
        });
        return result;
    }

    static Episode identifyEpisode(List<Episode> matches, String localUri) {
        if (matches == null || matches.isEmpty()) return null;
        String name = localUri == null ? "" :
                Uri.decode(Uri.parse(localUri).getLastPathSegment());
        if (name == null) name = "";
        for (Episode ep : matches) {
            String date = ep.dateIso == null ? "" : ep.dateIso;
            String filename = RaiClient.safeName(
                    (date.isEmpty() ? "" : date + " - ") + ep.title);
            if (name.equalsIgnoreCase(filename + ".mp3")
                    || name.equalsIgnoreCase(filename + ".m4a")
                    || name.equalsIgnoreCase(filename + ".aac")) return ep;
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    static Uri uriFor(Context context, Item item) {
        DownloadManager dm = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
        return dm == null || item == null ? null :
                dm.getUriForDownloadedFile(item.downloadId);
    }

    static boolean delete(Context context, Item item) {
        if (item == null) return false;
        DownloadManager dm = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
        try { return dm != null && dm.remove(item.downloadId) == 1; }
        catch (Exception ignored) { return false; }
    }
}
