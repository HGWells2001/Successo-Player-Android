#!/usr/bin/env python3
"""v5.0 UI contract: pinned mini-player, readable rows, persistent progress, organized menu."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/it/successoplayer/app"
main=(java/"MainActivity.java").read_text(encoding="utf-8")
adapter=(java/"EpisodeAdapter.java").read_text(encoding="utf-8")
audio_service=(java/"AudioPlaybackService.java").read_text(encoding="utf-8")
layout=ET.parse(root/"app/src/main/res/layout/activity_main.xml")
row=ET.parse(root/"app/src/main/res/layout/episode_row.xml")
ns="{http://schemas.android.com/apk/res/android}"
ids={x.attrib.get(ns+"id"):x for x in layout.iter() if x.attrib.get(ns+"id")}
rows={x.attrib.get(ns+"id"):x for x in row.iter() if x.attrib.get(ns+"id")}
children=list(layout.getroot())
assert ids["@+id/episodeList"] in children
assert "@+id/speedSpinner" not in ids, "the playback speed dropdown must stay removed"
assert "speedSpinner" not in main and "selectedSpeed()" not in main
assert "speedIndex" not in main, "the removed preference must not be written by the activity"
assert "speedIndex" not in audio_service.split("public void setSpeed(", 1)[0], "old speed prefs must not be restored"
assert "service.setSpeed(1f);" in main, "existing services must return to normal speed"
assert ids["@+id/playbackDetailsPanel"] in children
assert ids["@+id/miniPlayer"] in children
assert children.index(ids["@+id/episodeList"]) < children.index(ids["@+id/playbackDetailsPanel"]) < children.index(ids["@+id/miniPlayer"])
assert children[-1] == ids["@+id/miniPlayer"], "mini-player must remain below scrolling list"
assert ids["@+id/episodeList"].attrib[ns+"layout_weight"]=="1"
assert ids["@+id/playbackDetailsPanel"].attrib[ns+"visibility"]=="gone"
for id in ["miniTitle","miniSubtitle","miniPlayButton","miniNextButton","miniExpandButton","miniProgress"]:
    assert "@+id/"+id in ids,id
assert ids["@+id/miniPlayButton"].attrib[ns+"contentDescription"]=="Riproduci o metti in pausa"
assert ids["@+id/miniExpandButton"].attrib[ns+"layout_width"]=="44dp"
assert ids["@+id/miniProgress"].attrib[ns+"max"]=="1000"
assert "miniPlayButton.setOnClickListener(v -> playButton.performClick());" in main
assert "miniNextButton.setOnClickListener(v -> nextButton.performClick());" in main
assert "miniExpandButton.setOnClickListener(v -> setPlayerExpanded(!playerExpanded));" in main
assert 'playerExpanded = prefs.getBoolean("ui_player_expanded", false);' in main
assert '.putBoolean("ui_player_expanded", expanded)' in main
assert "private void updateMiniProgress(long positionMs, long durationMs)" in main
assert "updateMiniProgress(positionMs, knownDurationMs);" in main
assert "miniPlayButton.setText(playing ? " in main
assert "adapter.setCurrentPlayback(selectedEpisode.id, miniPositionMs, miniDurationMs)" in main

for id in ["rowProgressContainer","rowProgress","rowProgressLabel"]:
    assert "@+id/"+id in rows,id
assert rows["@+id/rowProgress"].attrib[ns+"max"]=="1000"
assert rows["@+id/rowTitle"].attrib[ns+"textSize"]=="16sp"
assert "long savedPositionMs(Episode episode);" in adapter
assert "savedPositionMs(e)" in adapter
assert "setCurrentPlayback(String id, long positionMs, long durationMs)" in adapter
assert "EpisodeProgress.percent(savedMs, duration)" in adapter
assert "h.progress.setProgress(percent * 10)" in adapter
assert 'h.progressLabel.setText("Da ascoltare");' in adapter
assert "if (listened && savedMs <= 0L) percent = 100;" in adapter
assert "getLong(" in main and '"episode_resume_ms_" + episode.id' in main

menu=main.split("private void showMoreMenu() {",1)[1].split("private void refreshMoreMenuState()",1)[0]
assert "setGroupDividerEnabled(true)" in menu
for group,id in [(1,"MENU_SEARCH"),(1,"MENU_HISTORY"),(2,"MENU_QUEUE"),(2,"MENU_SLEEP"),(3,"MENU_UPDATE")]:
    assert f"menu.getMenu().add({group}, {id}," in menu
assert 'case MENU_UPDATE:' in menu and 'case MENU_SLEEP:' in menu
assert "UpdateChecker.check(this, prefs, false);" in main

with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,
         str(java/"EpisodeProgress.java"),
         str(root/"tools/tests/EpisodeProgressTest.java")],check=True)
    subprocess.run(["java","-cp",tmp,"it.successoplayer.app.EpisodeProgressTest"],check=True)
print("OK: compact persistent mini-player, per-episode progress, grouped overflow and regression.")
