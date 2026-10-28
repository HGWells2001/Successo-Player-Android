#!/usr/bin/env python3
"""Regression checks: queued episodes take priority over shuffle and normal order."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
java = root / "app/src/main/java/it/successoplayer/app"
service = (java / "AudioPlaybackService.java").read_text(encoding="utf-8")
activity = (java / "MainActivity.java").read_text(encoding="utf-8")
tree = ET.parse(root / "app/src/main/res/layout/activity_main.xml")
ns = "{http://schemas.android.com/apk/res/android}"
button = next(n for n in tree.iter() if n.attrib.get(ns+"id") == "@+id/moreMenuButton")
assert button.attrib[ns+"text"] == "⋮"
assert button.attrib[ns+"layout_height"] == "48dp"
assert "case MENU_QUEUE:" in activity
assert "showUpNextQueue();" in activity
assert "@+id/upNextButton" not in {x.attrib.get(ns+"id") for x in tree.iter()}
assert "episodeList.setOnItemLongClickListener(" in activity
assert "service.enqueueUpNext(chosen, which == 1)" in activity
assert "service.moveUpNext(index, -1)" in activity
assert "service.moveUpNext(index, 1)" in activity
assert "service.removeFromUpNext(index)" in activity
assert "service.clearUpNext()" in activity
assert "service.skipToNext();" in activity
assert "service.setFullCatalog(allEpisodes);" in activity
assert "refreshMoreMenuState();" in activity
assert 'KEY_UP_NEXT = "playback_up_next_ids_json"' in service
assert "restoreUpNext();" in service
assert "prefs.edit().putString(KEY_UP_NEXT, ids.toString()).apply();" in service
assert "public boolean enqueueUpNext(Episode episode, boolean playNext)" in service
assert "public List<String> getUpNextIds()" in service
assert "private Episode takeQueuedEpisode()" in service
assert "public void setFullCatalog(List<Episode> episodes)" in service
skip = service.split("public void skipToNext() {", 1)[1].split("private void skipToPrevious()", 1)[0]
complete = service.split("private void advanceAfterCompletion(Episode completedEpisode) {", 1)[1].split("private Episode sequentialEpisodeAfter", 1)[0]
for segment in (skip, complete):
    assert segment.index("Episode next = takeQueuedEpisode();") < segment.index("pickNextShuffleEpisode(")
    assert "selectEpisode(next);" in segment
    assert "prepareAndPlay();" in segment
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp,
                    str(java / "PlaybackQueue.java"),
                    str(root / "tools/tests/PlaybackQueueTest.java")], check=True)
    subprocess.run(["java", "-cp", tmp, "it.successoplayer.app.PlaybackQueueTest"], check=True)
print("OK: Next Up queue, UI actions, priority, persistence and ordering.")
