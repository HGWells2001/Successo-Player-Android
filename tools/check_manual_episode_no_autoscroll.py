#!/usr/bin/env python3
"""Manual catalog selection must not jump, including with Shuffle enabled."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
click=main.split("episodeList.setOnItemClickListener((parent, view, position, id) -> {",1)[1].split("        });",1)[0]
callback=main.split("@Override public void onEpisode(Episode episode) {",1)[1].split("@Override public void onListenedChanged(",1)[0]
shuffle=main.split("private void scrollToShuffleSelection() {",1)[1].split("private int indexOfVisible(",1)[0]
assert "selectedEpisode = adapter.getEpisode(position);" in click
assert 'recentTapEpisodeId = selectedEpisode.id == null ? "" : selectedEpisode.id;' in click
assert "recentTapExpiresAtMs = android.os.SystemClock.elapsedRealtime() + 10_000L;" in click
assert "service.selectEpisode(selectedEpisode);" in click
assert "service.togglePlayPause();" in click
assert "boolean originatedFromTap = episode.id != null" in callback
assert "episode.id.equals(recentTapEpisodeId)" in callback
assert "recentTapEpisodeId = \"\";" in callback
assert "if (shuffleEnabled && !originatedFromTap)" in callback
assert "scrollToShuffleSelection();" in callback
assert "episodeList.setSelection" not in callback, "onEpisode must not unconditionally scroll"
assert "setSelectionFromTop(index," in shuffle, "automatic shuffle scrolling must remain"
row=(root/"app/src/main/java/it/successoplayer/app/EpisodeAdapter.java").read_text(encoding="utf-8")
assert "list.performItemClick(v, livePosition, getItemId(livePosition));" in row
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
assert "versionName '5.3.2'" in gradle
print("OK: manual episode click preserves scroll, auto Shuffle follows selection.")
