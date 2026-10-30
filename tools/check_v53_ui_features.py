#!/usr/bin/env python3
"""Six concrete v5.3 UI upgrades, excluding a new continue-listening feature."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
ns = "{http://schemas.android.com/apk/res/android}"
main = ET.parse(res / "layout/activity_main.xml").getroot()
row = ET.parse(res / "layout/episode_row.xml").getroot()
views = {v.attrib.get(ns+"id"): v for v in main.iter() if v.attrib.get(ns+"id")}
row_views = {v.attrib.get(ns+"id"): v for v in row.iter() if v.attrib.get(ns+"id")}
activity = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
adapter = (root / "app/src/main/java/it/successoplayer/app/EpisodeAdapter.java").read_text(encoding="utf-8")
offline = (root / "app/src/main/java/it/successoplayer/app/OfflineEpisodeLibrary.java").read_text(encoding="utf-8")
assert views["@+id/miniProgress"].tag == "SeekBar"
assert views["@+id/miniProgress"].attrib[ns+"layout_height"] == "40dp"
assert "miniProgress.setOnSeekBarChangeListener(" in activity
assert "service.seekTo(target);" in activity
assert "miniSeeking = true" not in activity or "miniSeeking = miniDurationMs > 0L" in activity
assert "if (miniProgress != null && !miniSeeking)" in activity
assert "miniPlayButton.animate()" in activity

controls = {
    "previousButton": "ic_previous_art",
    "rewindButton": "ic_rewind30_art",
    "playButton": "ic_play_art",
    "forwardButton": "ic_forward30_art",
    "nextButton": "ic_next_art",
    "miniPlayButton": "ic_play_art",
    "miniNextButton": "ic_next_art",
    "miniExpandButton": "ic_chevron_up_art"
}
for control, icon in controls.items():
    view = views["@+id/" + control]
    assert view.tag == "ImageButton", (control, view.tag)
    assert view.attrib[ns+"src"] == "@drawable/" + icon
    assert int(view.attrib[ns+"layout_width"].rstrip("dp")) >= 44
    assert view.attrib.get(ns+"contentDescription")
    drawable = ET.parse(res / "drawable" / (icon + ".xml")).getroot()
    assert drawable.tag == "vector"
ET.parse(res / "drawable/ic_pause_art.xml")
assert views["@+id/rootLayout"] is main
assert "adaptControlsToScreen()" in activity
assert "config.screenWidthDp < 380" in activity
assert "config.fontScale >= 1.25f" in activity
assert "config.screenHeightDp < 450" in activity

assert row_views["@+id/rowInfo"].tag == "ImageButton"
assert row_views["@+id/rowOfflineIndicator"].tag == "ImageView"
assert "favoriteHandler.showDetails(e);" in adapter
assert "setOfflineState(Set<String> completedIds, String downloadingId)" in adapter
assert "refreshOfflineLibraryState()" in activity
assert "OfflineEpisodeLibrary.scan(getApplicationContext(), snapshot)" in activity
assert "DownloadManager.STATUS_SUCCESSFUL" in offline
assert 'putString("active_download_episode_id", downloadingEpisodeId)' in activity
for icon in ("ic_offline_ready_art", "ic_downloading_art", "ic_download_error_art"):
    ET.parse(res / "drawable" / (icon + ".xml"))
assert "showEpisodeDetails(Episode episode)" in activity
assert "episode.description.trim()" in activity
assert "setTextIsSelectable(true)" in activity
assert 'setNeutralButton("Scarica"' in activity
assert "MENU_SORT = 1006" in activity
assert "EpisodeSort.sort(visibleEpisodes, sortMode," in activity
assert "showCatalogSortOptions()" in activity
assert 'prefs.edit().putInt("catalog_sort_mode", sortMode).apply();' in activity
assert "service.setFullCatalog(allEpisodes);" in activity
assert "ui_continue_listening_card" not in activity
print("OK: v5.3 scrubbing, offline states, episode details, sorting, vector controls, responsiveness.")
