#!/usr/bin/env python3
"""Compile and execute plain-Java shuffle tests; check Activity/Service integration."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
base = root / "app/src/main/java/it/successoplayer/app"
activity = (base / "MainActivity.java").read_text(encoding="utf-8")
service = (base / "AudioPlaybackService.java").read_text(encoding="utf-8")
assert 'shuffleEnabled ? visibleEpisodes : allEpisodes' in activity
assert "service.pickNextShuffleEpisode(selectedEpisode)" in activity
assert "shuffleEnabled ? visibleEpisodes : allEpisodes, shuffleEnabled" in activity
assert 'revealShuffleSelectionIfHidden' not in activity
assert "playEpisodeFromAutoMode(randomEpisode);" in activity
assert "if (shuffleEnabled) scrollToShuffleSelection();" in activity
assert 'shuffleCycle.setPool(ids)' in service
assert 'shuffleCycle.restorePlayed(' in service
assert 'KEY_SHUFFLE_PLAYED' in service and 'KEY_SHUFFLE_POOL' in service
assert 'shuffleCycle.markPlayed(episode.id)' in service
assert 'pickNextShuffleEpisode(currentEpisode)' in service
assert 'pickNextShuffleEpisode(completedEpisode)' in service
assert 'randomEpisodeExcluding(' not in service
with tempfile.TemporaryDirectory() as tmp:
    sources = [
        base / "ShuffleCycle.java",
        root / "tools/tests/ShuffleCycleTest.java",
    ]
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp] + [str(x) for x in sources], check=True)
    subprocess.run(["java", "-cp", tmp, "it.successoplayer.app.ShuffleCycleTest"], check=True)
print("OK: Shuffle no repeats and filter integration.")
