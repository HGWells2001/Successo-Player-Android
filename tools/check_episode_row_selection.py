#!/usr/bin/env python3
"""Regression: list-row taps and long presses must survive Info and Favorite buttons."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
layout = ET.parse(root/"app/src/main/res/layout/episode_row.xml").getroot()
activity = (root/"app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
adapter = (root/"app/src/main/java/it/successoplayer/app/EpisodeAdapter.java").read_text(encoding="utf-8")
a = "{http://schemas.android.com/apk/res/android}"
children = {child.attrib.get(a+"id"):child for child in layout.iter()}
assert layout.attrib[a+"descendantFocusability"] == "blocksDescendants"
assert children["@+id/rowInfo"].tag == "ImageButton"
assert children["@+id/rowInfo"].attrib[a+"focusable"] == "false"
assert children["@+id/rowFavorite"].attrib[a+"focusable"] == "false"
assert children["@+id/rowFavorite"].attrib[a+"clickable"] == "true"
assert 'favoriteHandler.showDetails(e)' in adapter
assert 'favoriteHandler.toggleFavorite(e)' in adapter

# ListView may skip OnItemClick with action buttons in a row.
# Forward clicks through live position, never stale recycled adapter index.
assert "convertView.setOnClickListener(v -> {" in adapter
assert "int livePosition = list.getPositionForView(v);" in adapter
assert "list.performItemClick(v, livePosition, getItemId(livePosition));" in adapter
assert "convertView.setOnLongClickListener(v -> {" in adapter
assert "listener.onItemLongClick(" in adapter
assert 'list.getOnItemLongClickListener();' in adapter
assert 'episodeList.setOnItemClickListener((parent, view, position, id) -> {' in activity
assert 'selectedEpisode = adapter.getEpisode(position);' in activity
assert 'service.selectEpisode(selectedEpisode);' in activity
assert 'service.togglePlayPause();' in activity
assert 'episodeList.setOnItemLongClickListener((parent, view, position, id) -> {' in activity
assert 'service.enqueueUpNext(chosen, which == 1)' in activity

gradle = (root/"app/build.gradle").read_text(encoding="utf-8")
assert "versionName '5.3.2'" in gradle
print("OK: row clicks select episodes, Info/Favorites isolated, long press preserves queue.")
