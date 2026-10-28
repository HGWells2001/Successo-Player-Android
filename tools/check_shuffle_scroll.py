#!/usr/bin/env python3
"""Regression checks: the ListView follows Shuffle selection from player callbacks."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/it/successoplayer/app"
main=(source/"MainActivity.java").read_text(encoding="utf-8")
adapter=(source/"EpisodeAdapter.java").read_text(encoding="utf-8")
player=(source/"AudioPlaybackService.java").read_text(encoding="utf-8")
assert "listener.onEpisode(episode)" in player
assert "private void scrollToShuffleSelection()" in main
assert "episodeList.post(() -> {" in main
assert "episodeList.setSelectionFromTop(index," in main
assert "expectedId.equals(selectedEpisode.id)" in main
callback=main[main.index("@Override public void onEpisode(Episode episode) {"):]
callback=callback[:callback.index("@Override public void onListenedChanged(")]
assert "if (shuffleEnabled) scrollToShuffleSelection();" in callback
auto=main[main.index("private void playEpisodeFromAutoMode(Episode episode) {"):]
auto=auto[:auto.index("private void scrollToShuffleSelection()")]
assert "scrollToShuffleSelection();" in auto
assert "revealShuffleSelectionIfHidden()" not in main
assert 'service.setQueue(' in main
assert 'shuffleEnabled ? visibleEpisodes : allEpisodes' in main
assert "adapter.setSelectedEpisodeId(selectedEpisode.id);" in main
assert "setSelectedEpisodeId(String id)" in adapter
assert "selected ? R.drawable.episode_selected_bg : R.color.panel" in adapter
assert "selected ? Typeface.BOLD : Typeface.NORMAL" in adapter
ET.parse(root/"app/src/main/res/drawable/episode_selected_bg.xml")
print("OK: shuffle track visible, scroll updated, selected row highlighted.")
