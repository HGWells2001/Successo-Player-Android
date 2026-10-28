#!/usr/bin/env python3
"""Regression of offline episode library and integration with Android playback."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
base=root/"app/src/main/java/it/successoplayer/app"
activity=(base/"MainActivity.java").read_text(encoding="utf-8")
service=(base/"AudioPlaybackService.java").read_text(encoding="utf-8")
library=(base/"OfflineEpisodeLibrary.java").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
xml=ET.parse(root/"app/src/main/res/layout/activity_main.xml")
ns="{http://schemas.android.com/apk/res/android}"
button=next(e for e in xml.iter() if e.attrib.get(ns+"id")=="@+id/openDownloadsButton")
assert button.attrib.get(ns+"layout_width")=="48dp"
assert button.attrib.get(ns+"contentDescription")=="Apri la libreria delle puntate scaricate"
assert "openDownloadsButton.setOnClickListener(v -> openEpisodeDownloads());" in activity
for pattern in ["showOfflineLibrary(","showOfflineEpisodeActions(","playOfflineEpisode(",
                "openEpisodeDownloadsFolder()","OfflineEpisodeLibrary.scan(this, snapshot)",
                "OfflineEpisodeLibrary.delete(this, item)",
                "service.selectDownloadedEpisode(item.episode, uri)"]:
    assert pattern in activity, pattern
assert 'setPositiveButton("Elimina"' in activity
assert "if (!bound || service == null)" in activity
assert "public void selectDownloadedEpisode(" in service
assert "downloadedAudioUri = uri.toString();" in service
assert "if (downloadedAudioUri != null)" in service
assert "openSource(local, true, token);" in service
assert 'source.startsWith("content://")' in service
assert "if (!localFile && !fallbackTried" in service
for pattern in ["DownloadManager.STATUS_SUCCESSFUL","DownloadManager.COLUMN_LOCAL_URI",
                "DownloadManager.COLUMN_TITLE","manager.getUriForDownloadedFile(downloadId)",
                "dm.remove(item.downloadId) == 1","identifyEpisode("]:
    assert pattern in library, pattern
assert "android.permission.MANAGE_EXTERNAL_STORAGE" not in manifest
assert "android.permission.READ_MEDIA_AUDIO" not in manifest
print("OK: integrated offline library, offline URI playback, deletion, legacy download history.")
