package it.successoplayer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Manages an APK with Android DownloadManager, never via browser percent state.
 * Only STATUS_SUCCESSFUL plus matching SHA-256 can trigger the installer.
 * Download continues in background and is picked up when the Activity reopens.
 */
final class ApkUpdateDownloader {
    private static final String KEY_ID = "apk_update_download_id";
    private static final String KEY_SHA = "apk_update_sha256";
    private static final String KEY_SIZE = "apk_update_expected_size";
    private static final String KEY_CODE = "apk_update_version_code";
    private static final String KEY_READY = "apk_update_verified";
    private static final String MIME = "application/vnd.android.package-archive";
    private static final String GITHUB_PREFIX =
            "https://github.com/HGWells2001/Successo-Player-Android/releases/download/";

    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final DownloadManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private boolean verifying;
    private boolean prompted;
    private int lastReportedPercent = -1;
    private final Runnable poller = this::poll;

    ApkUpdateDownloader(MainActivity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
        manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    static boolean hasPending(SharedPreferences prefs, long remoteCode) {
        return prefs.getLong(KEY_ID, -1L) >= 0L
                && prefs.getLong(KEY_CODE, 0L) >= remoteCode;
    }

    void onStart() {
        active = true;
        prompted = false;
        if (downloadId() < 0L) return;
        if (UpdateChecker.currentVersionCode(activity) >= prefs.getLong(KEY_CODE, 0L)) {
            removePreviousDownload();
            return;
        }
        if (prefs.getBoolean(KEY_READY, false)) {
            activity.showUpdateDownloadStatus("Aggiornamento scaricato e verificato.");
            promptInstaller();
        } else {
            handler.removeCallbacks(poller);
            handler.post(poller);
        }
    }

    void onStop() {
        active = false;
        handler.removeCallbacks(poller);
    }

    void start(String url, String sha, long expectedSize, long versionCode) {
        if (manager == null || url == null || !url.startsWith(GITHUB_PREFIX)
                || sha == null || !sha.matches("[0-9a-fA-F]{64}")
                || expectedSize <= 0L || versionCode <= 0L) {
            Toast.makeText(activity, "Aggiornamento non verificabile.", Toast.LENGTH_LONG).show();
            return;
        }
        if (hasPending(prefs, versionCode)) {
            if (prefs.getBoolean(KEY_READY, false)) promptInstaller();
            else {
                Toast.makeText(activity, "Il download è già in corso.", Toast.LENGTH_SHORT).show();
                handler.post(poller);
            }
            return;
        }

        // Remove stale APK and DownloadManager entry, never touch episode downloads.
        removePreviousDownload();
        String filename = "SuccessoPlayer-update-" + versionCode + ".apk";
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir != null) {
            File previous = new File(dir, filename);
            if (previous.exists() && !previous.delete()) {
                Toast.makeText(activity, "Impossibile sostituire il precedente APK.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Successo Player · aggiornamento")
                    .setDescription("Scaricamento dell'APK ufficiale firmato")
                    .setMimeType(MIME)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, filename);
            long id = manager.enqueue(request);
            prefs.edit()
                    .putLong(KEY_ID, id)
                    .putString(KEY_SHA, sha.toLowerCase(Locale.ROOT))
                    .putLong(KEY_SIZE, expectedSize)
                    .putLong(KEY_CODE, versionCode)
                    .putBoolean(KEY_READY, false)
                    .apply();
            lastReportedPercent = -1;
            activity.showUpdateDownloadStatus("Download aggiornamento avviato...");
            handler.removeCallbacks(poller);
            if (active) handler.post(poller);
        } catch (Exception e) {
            activity.showUpdateDownloadStatus("Impossibile iniziare il download.");
            Toast.makeText(activity, "Download non avviato: riprova.", Toast.LENGTH_LONG).show();
        }
    }

    private long downloadId() {
        return prefs.getLong(KEY_ID, -1L);
    }

    private void poll() {
        if (!active || verifying || downloadId() < 0L) return;
        long id = downloadId();
        try (Cursor cursor = manager.query(
                new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) {
                fail("Download non trovato. Riprova.");
                return;
            }
            int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (state == DownloadManager.STATUS_SUCCESSFUL) {
                verifyDownloadedApk(id);
                return;
            }
            if (state == DownloadManager.STATUS_FAILED) {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                fail("Download fallito (errore Android " + reason + "). Puoi riprovare.");
                return;
            }
            long received = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (total > 0L) {
                int percent = (int) Math.min(100L, Math.max(0L, received * 100L / total));
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent;
                    activity.showUpdateDownloadStatus(percent == 100
                            ? "Download ricevuto: verifica completamento Android..."
                            : "Download aggiornamento: " + percent + "%");
                }
            } else if (lastReportedPercent < 0) {
                activity.showUpdateDownloadStatus("Download aggiornamento in corso...");
                lastReportedPercent = 0;
            }
            handler.postDelayed(poller, 900L);
        } catch (Exception e) {
            fail("Download interrotto. Riprova.");
        }
    }

    private void verifyDownloadedApk(long id) {
        if (verifying) return;
        verifying = true;
        activity.showUpdateDownloadStatus("Download completato. Verifica integrità APK...");
        final String expectedSha = prefs.getString(KEY_SHA, "");
        final long expectedSize = prefs.getLong(KEY_SIZE, -1L);
        new Thread(() -> {
            String problem = null;
            try (ParcelFileDescriptor fd = manager.openDownloadedFile(id);
                 FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                long bytes = 0L;
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    if (bytes == 0L && (read < 2 || buffer[0] != 'P' || buffer[1] != 'K')) {
                        throw new SecurityException("Non è un file APK");
                    }
                    bytes += read;
                    if (bytes > 200_000_000L) throw new SecurityException("APK troppo grande");
                    digest.update(buffer, 0, read);
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : digest.digest()) {
                    hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
                }
                if (bytes != expectedSize || !hex.toString().equalsIgnoreCase(expectedSha)) {
                    throw new SecurityException("SHA-256 o dimensione non corrispondente");
                }
            } catch (Exception e) {
                problem = "APK incompleto o non valido: verifica fallita. Riprova.";
            }
            final String error = problem;
            handler.post(() -> {
                verifying = false;
                if (downloadId() != id) return;
                if (error != null) {
                    fail(error);
                    return;
                }
                prefs.edit().putBoolean(KEY_READY, true).apply();
                activity.showUpdateDownloadStatus("APK verificato. Pronto per l'installazione.");
                if (active) promptInstaller();
            });
        }, "Successo-Verify-APK").start();
    }

    private void promptInstaller() {
        if (!active || prompted || !prefs.getBoolean(KEY_READY, false)) return;
        prompted = true;
        new AlertDialog.Builder(activity)
                .setTitle("Aggiornamento pronto")
                .setMessage("L'APK è stato scaricato completamente e verificato "
                        + "con il codice SHA-256 pubblicato su GitHub. "
                        + "Vuoi aprire l'installazione Android?")
                .setPositiveButton("Installa", (dialog, which) -> openInstaller())
                .setNegativeButton("Più tardi", null)
                .show();
    }

    private void openInstaller() {
        try {
            if (Build.VERSION.SDK_INT >= 26
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity,
                        "Consenti l'installazione da Successo Player, poi torna nell'app.",
                        Toast.LENGTH_LONG).show();
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                return;
            }
            Uri uri = manager.getUriForDownloadedFile(downloadId());
            if (uri == null || !"content".equals(uri.getScheme())) {
                throw new IllegalStateException("APK non disponibile nel gestore download");
            }
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, MIME);
            install.setClipData(ClipData.newRawUri("APK Successo Player", uri));
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(install);
        } catch (Exception e) {
            Toast.makeText(activity,
                    "Installazione non avviata. Apri il file APK nei Download.",
                    Toast.LENGTH_LONG).show();
            try {
                activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
            } catch (Exception ignored) {}
        }
    }

    private void fail(String reason) {
        removePreviousDownload();
        activity.showUpdateDownloadStatus(reason);
        if (active) Toast.makeText(activity, reason, Toast.LENGTH_LONG).show();
    }

    private void removePreviousDownload() {
        long id = downloadId();
        if (id >= 0L && manager != null) {
            try { manager.remove(id); } catch (Exception ignored) {}
        }
        prefs.edit()
                .remove(KEY_ID).remove(KEY_SHA).remove(KEY_SIZE)
                .remove(KEY_CODE).remove(KEY_READY).apply();
    }
}
