#!/usr/bin/env python3
"""Static regression checks for a verifiable DownloadManager APK update path."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/it/successoplayer/app"
main=(source/"MainActivity.java").read_text(encoding="utf-8")
checker=(source/"UpdateChecker.java").read_text(encoding="utf-8")
downloader=(source/"ApkUpdateDownloader.java").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
layout=ET.parse(root/"app/src/main/res/layout/activity_main.xml")
ns="{http://schemas.android.com/apk/res/android}"
status=next(x for x in layout.iter() if x.attrib.get(ns+"id")=="@+id/updateDownloadHint")
assert status.attrib.get(ns+"visibility")=="gone"
assert "apkUpdateDownloader.onStart();" in main
assert "apkUpdateDownloader.onStop();" in main
assert "beginApkUpdateDownload(" in main
assert "new ApkUpdateDownloader(this, prefs)" in main
assert "loadReleaseChecksum(checksumUrl, versionedName)" in checker
assert "SuccessoPlayer_Android_latest.apk" in checker
assert "SHA256.txt" in checker
assert "ApkUpdateDownloader.hasPending(prefs, remoteCode)" in checker
assert "((MainActivity) activity).beginApkUpdateDownload(" in checker
assert "Intent.ACTION_VIEW" not in checker
assert "android.permission.REQUEST_INSTALL_PACKAGES" in manifest
assert "DownloadManager.Request" in downloader
assert "DownloadManager.STATUS_SUCCESSFUL" in downloader
assert "DownloadManager.STATUS_FAILED" in downloader
assert "DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR" in downloader
assert "DownloadManager.COLUMN_TOTAL_SIZE_BYTES" in downloader
assert "Download ricevuto: verifica completamento Android..." in downloader
assert "manager.openDownloadedFile(id)" in downloader
assert 'MessageDigest.getInstance("SHA-256")' in downloader
assert "bytes != expectedSize" in downloader
assert "hex.toString().equalsIgnoreCase(expectedSha)" in downloader
assert "prefs.edit().putBoolean(KEY_READY, true).apply()" in downloader
assert 'setPositiveButton("Installa"' in downloader
assert "getUriForDownloadedFile(downloadId())" in downloader
assert "Intent.FLAG_GRANT_READ_URI_PERMISSION" in downloader
assert "canRequestPackageInstalls()" in downloader
assert "Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES" in downloader
assert "Intent.ACTION_VIEW" in downloader
assert "handler.postDelayed(poller, 900L)" in downloader
assert "hasPending(SharedPreferences prefs, long remoteCode)" in downloader
assert "showPendingOnDemand()" in downloader
assert "prompted = false;" in downloader
assert "showPendingApkUpdate()" in checker
assert "DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED" in downloader
print("OK: APK is installed only after DownloadManager success and SHA-256 verification.")
