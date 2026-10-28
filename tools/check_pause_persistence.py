#!/usr/bin/env python3
"""Regression checks for the pause / close / reopen behavior."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / "app/src/main/java/it/successoplayer/app"
activity = (root / "MainActivity.java").read_text(encoding="utf-8")
service = (root / "AudioPlaybackService.java").read_text(encoding="utf-8")

# If a stopped process is recreated, restoring a position must NOT invoke PLAY.
assert re.search(
    r"service\.restoreSession\(\s*restoreEpisode,\s*resumePosition,\s*false\s*\)",
    activity,
), "A cold app start must restore the selected episode paused."
assert "SESSION_AUTOPLAY_MAX_AGE_MS" not in activity
assert "pendingAutoPlay && selectedEpisode != null" in activity, (
    "User-initiated play while binding must still work."
)

# Media3 must set speed without ever starting an intentionally paused player.
speed_method = service.split("private void applySpeed()", 1)[1].split(
    "private void applyVolume(", 1
)[0]
assert "player.setSpeed(speed);" in speed_method
assert "player.start();" not in speed_method
assert "if (player == null || !prepared) return;" in speed_method

prepared = service.split("player.setOnPreparedListener(", 1)[1].split(
    "player.setOnCompletionListener(", 1
)[0]
assert prepared.index("mp.start();") < prepared.index("applySpeed();")
resume = service.split("private void playPrepared()", 1)[1].split(
    "private void skipToNext()", 1
)[0]
assert resume.index("player.start();") < resume.index("applySpeed();")
assert "generation.incrementAndGet();" in service.split("public void pause()", 1)[1].split("public void seekTo(", 1)[0]
assert "persistSessionNow(\n                pendingResumeMs,\n                getCurrentDurationMs(),\n                false" in service, (
    "The pause command must persist paused state."
)
print("OK: reopening a paused player cannot auto-start via restore or speed.")
