#!/usr/bin/env python3
"""Sleep timer must live in playback service and never consume Up Next on completion."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
base = root / "app/src/main/java/it/successoplayer/app"
activity = (base / "MainActivity.java").read_text(encoding="utf-8")
service = (base / "AudioPlaybackService.java").read_text(encoding="utf-8")
source = (base / "SleepTimer.java").read_text(encoding="utf-8")
layout = ET.parse(root / "app/src/main/res/layout/activity_main.xml")
ns = "{http://schemas.android.com/apk/res/android}"
ids = {e.attrib.get(ns + "id"): e for e in layout.iter() if e.attrib.get(ns + "id")}
button = ids["@+id/moreMenuButton"]
assert button.attrib[ns + "layout_height"] == "48dp"
assert button.attrib[ns + "text"] == "⋮"
assert button.attrib[ns + "minWidth"] == "48dp"
assert "@+id/sleepTimerButton" not in ids
assert "case MENU_SLEEP:" in activity
assert "showSleepTimerOptions();" in activity
assert "private void showSleepTimerOptions()" in activity
assert "private void refreshSleepTimerButton()" in activity
assert '"Alla fine della puntata"' in activity
assert '"Disattiva timer"' in activity
assert "service.startSleepTimer(minutes[index]);" in activity
assert "service.sleepAfterCurrentEpisode()" in activity
assert "service.cancelSleepTimer()" in activity
assert "sleepTimerUiHandler.removeCallbacks(sleepTimerUiTick);" in activity
assert "sleepTimerUiHandler.post(sleepTimerUiTick);" in activity
assert "sleepTimerUiHandler.postDelayed(this, 15_000L);" in activity
assert 'sleepLabel += " · fine puntata";' in activity
assert '"☾ Timer sonno"' in activity
assert 'sleepLabel += " · " + Math.max' in activity
assert "refreshMoreMenuState();" in activity
assert "shuffleEnabled ? visibleEpisodes : allEpisodes" in activity

assert "new SleepTimer()" in service
assert "sleepTimer.startMinutes(minutes, SystemClock.elapsedRealtime());" in service
assert "sleepTimer.finishAtEnd(currentEpisode.id)" in service
assert "sleepTimer.isDue(SystemClock.elapsedRealtime())" in service
assert "expireSleepTimer();" in service
assert "sleepTimer.cancelIfDifferentEpisode(" in service
assert "if (sleepTimer.consumeEpisodeCompletion(" in service
assert "sleepTimer.cancel();" in service
completion = service.split("player.setOnCompletionListener(mp -> {", 1)[1].split("player.setOnErrorListener(", 1)[0]
assert completion.index("sleepTimer.consumeEpisodeCompletion(") < completion.index("advanceAfterCompletion(completedEpisode)")
assert "notifyStatus(" in completion
expire = service.split("private void expireSleepTimer() {", 1)[1].split("public void setShuffleEnabled(", 1)[0]
assert "pause();" in expire, "must preserve listening bookmarks"
assert "generation.incrementAndGet();" in expire, "must cancel pending asynchronous playback"
assert "stopPlayback();" not in expire, "do not reset bookmark"
assert "SystemClock.elapsedRealtime()" in service
assert "System.currentTimeMillis" not in source

with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp,
                    str(base / "SleepTimer.java"),
                    str(root / "tools/tests/SleepTimerTest.java")], check=True)
    subprocess.run(["java", "-cp", tmp, "it.successoplayer.app.SleepTimerTest"], check=True)
print("OK: sleep timer integrates with service, UI and auto-advance safely.")
