#!/usr/bin/env python3
"""Regression test for recent playback history and its mobile UI integration."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
java = root / "app/src/main/java/it/successoplayer/app"
service = (java / "AudioPlaybackService.java").read_text(encoding="utf-8")
main = (java / "MainActivity.java").read_text(encoding="utf-8")
layout = ET.parse(root / "app/src/main/res/layout/activity_main.xml")
ns = "{http://schemas.android.com/apk/res/android}"

button = next(x for x in layout.iter()
              if x.attrib.get(ns+"id") == "@+id/moreMenuButton")
assert button.tag == "Button"
assert button.attrib[ns+"text"] == "⋮"
assert button.attrib[ns+"layout_width"] == "48dp"
assert button.attrib[ns+"layout_height"] == "48dp"
assert "case MENU_HISTORY:" in main
assert "showPlaybackHistory();" in main
assert "@+id/historyButton" not in {x.attrib.get(ns+"id") for x in layout.iter()}
assert "private void showPlaybackHistory()" in main
assert "private void showHistoryEntryActions(String episodeId)" in main
assert "service.getRecentHistory()" in main
assert "service.getSavedEpisodePosition(ep)" in main
assert "playEpisodeFromAutoMode(ep);" in main
assert "service.enqueueUpNext(ep, false)" in main
assert "service.removeFromPlayHistory(episodeId)" in main
assert "service.clearPlayHistory()" in main
assert 'new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)' in main
assert 'setTitle("Cancellare la cronologia?")' in main

assert 'KEY_PLAY_HISTORY = "playback_recent_history_v1"' in service
assert "private final PlaybackHistory playHistory = new PlaybackHistory();" in service
assert "restorePlayHistory();" in service
assert "private void persistPlayHistory()" in service
assert "private void restorePlayHistory()" in service
assert "public List<PlaybackHistory.Entry> getRecentHistory()" in service
assert "public void clearPlayHistory()" in service
assert "public boolean removeFromPlayHistory(String episodeId)" in service
assert "playHistory.record(currentEpisode.id, System.currentTimeMillis())" in service
assert "prefs.edit().putString(KEY_PLAY_HISTORY, saved.toString()).apply();" in service
actual_play = service.split("private void playPrepared() {", 1)[1].split("public void skipToNext()",1)[0]
assert actual_play.index("player.start();") < actual_play.index("recordPlayHistory();")
selected = service.split("public void selectEpisode(Episode episode) {",1)[1].split("public void selectDownloadedEpisode",1)[0]
assert "recordPlayHistory();" not in selected, "select must not count as a play"
restore = service.split("public void restoreSession(",1)[1].split("private void ",1)[0]
assert "recordPlayHistory();" not in restore

with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp,
                    str(java / "PlaybackHistory.java"),
                    str(root / "tools/tests/PlaybackHistoryTest.java")], check=True)
    subprocess.run(["java", "-cp", tmp, "it.successoplayer.app.PlaybackHistoryTest"], check=True)

print("OK: history records real playback, preserves bookmarks and offers resume/queue/clear.")
