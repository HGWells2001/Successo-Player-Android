#!/usr/bin/env python3
"""Verifica il pulsante Apri download e la destinazione DownloadManager."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
layout = (root / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")

assert "openRaiButton" not in activity + layout
assert "Apri su RaiPlay" not in layout
assert '@+id/openDownloadsButton' in layout
assert 'android:src="@drawable/ic_library_art"' in layout
assert 'android:text="📂"' not in layout
assert 'android:contentDescription="Apri la libreria delle puntate scaricate"' in layout
assert "openDownloadsButton.setOnClickListener(v -> openEpisodeDownloads());" in activity
assert "OfflineEpisodeLibrary.scan(this, snapshot)" in activity
assert "openEpisodeDownloadsFolder()" in activity
assert 'EPISODE_DOWNLOAD_FOLDER = "Successo - Storie e voci dal Novecento"' in activity
assert 'DocumentsContract.buildDocumentUri(' in activity
assert 'DocumentsContract.Document.MIME_TYPE_DIR' in activity
assert 'DocumentsContract.EXTRA_INITIAL_URI' in activity
assert 'DownloadManager.ACTION_VIEW_DOWNLOADS' in activity
assert re.search(
    r'setDestinationInExternalPublicDir\(Environment\.DIRECTORY_DOWNLOADS,\s*'
    r'EPISODE_DOWNLOAD_FOLDER\s*\+\s*"/"\s*\+\s*filename\)',
    activity
), "I download devono continuare nella stessa cartella."
assert "downloadButton.setOnClickListener(v -> startDownload(selectedEpisode));" in activity
print("OK: pulsante Download, percorso e compatibilita' file manager.")
