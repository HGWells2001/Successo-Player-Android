#!/usr/bin/env python3
"""v4.11 regression: per-episode resume and signed GitHub releases."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
base=root/"app/src/main/java/it/successoplayer/app"
svc=(base/"AudioPlaybackService.java").read_text(encoding="utf-8")
main=(base/"MainActivity.java").read_text(encoding="utf-8")
up=(base/"UpdateChecker.java").read_text(encoding="utf-8")
workflow=(root/".github/workflows/build-apk.yml").read_text(encoding="utf-8")
xml=root/"app/src/main/res/layout/activity_main.xml"
assert 'KEY_EPISODE_POSITION_PREFIX' in svc
assert 'public long getSavedEpisodePosition(Episode episode)' in svc
select=svc.split("public void selectEpisode(Episode episode)",1)[1].split("public void restoreSession(",1)[0]
assert select.index("persistSessionNow(getCurrentPositionMs()") < select.index("releasePlayer();")
assert 'final long nextPosition = getSavedEpisodePosition(episode);' in select
assert 'pendingResumeMs = nextPosition;' in select
assert 'listener.onPosition(nextPosition,' in select
assert 'editor.putLong(key, Math.max(0L, position));' in svc
assert 'editor.remove(key);' in svc
assert 'service.restoreSession(\n                restoreEpisode,\n                resumePosition,\n                false' in main
assert 'UpdateChecker.check(this, prefs, false);' in main
assert 'UpdateChecker.check(this, prefs, true)' in main
tree=ET.parse(xml)
android='{http://schemas.android.com/apk/res/android}'
assert any(x.attrib.get(android+'id')=='@+id/moreMenuButton' for x in tree.iter())
assert '@+id/updateButton' not in xml.read_text(encoding='utf-8')
assert 'case MENU_UPDATE:' in main
assert 'https://api.github.com/repos/HGWells2001/Successo-Player-Android/releases/latest' in up
assert 'SuccessoPlayer_Android_latest.apk' in up
assert 'compareVersion' in up and 'remoteCode > installedCode' in up
assert 'new Thread(' in up and 'setConnectTimeout(' in up and 'setReadTimeout(' in up
assert 'setPositiveButton("Scarica APK"' in up
assert 'beginApkUpdateDownload(' in up
downloader=(base/"ApkUpdateDownloader.java").read_text(encoding="utf-8")
assert 'Intent.ACTION_VIEW' in downloader
assert 'DownloadManager.STATUS_SUCCESSFUL' in downloader
assert 'permissions:\n      contents: write' in workflow
assert 'gh release create "$tag"' in workflow
assert 'gh release view "$tag"' in workflow
assert '--latest' in workflow
assert 'release/SuccessoPlayer_Android_latest.apk' in workflow
assert workflow.index('Firma e verifica certificato permanente') < workflow.index('Pubblica automaticamente su GitHub Releases')
assert 'gh release upload' not in workflow
print("OK: per-episode resume; checked release and user-approved direct APK link.")
