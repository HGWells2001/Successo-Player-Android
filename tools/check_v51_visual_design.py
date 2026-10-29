#!/usr/bin/env python3
"""Verify 5.1 cards, compact mini-player, both color themes and update compatibility."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
ns = "{http://schemas.android.com/apk/res/android}"
layout = ET.parse(res / "layout/activity_main.xml").getroot()
row = ET.parse(res / "layout/episode_row.xml").getroot()
main_ids = {x.attrib.get(ns+"id"): x for x in layout.iter() if x.attrib.get(ns+"id")}
row_ids = {x.attrib.get(ns+"id"): x for x in row.iter() if x.attrib.get(ns+"id")}
assert main_ids["@+id/episodeList"].attrib[ns+"layout_weight"] == "1"
assert main_ids["@+id/episodeList"].attrib[ns+"dividerHeight"] == "6dp"
assert main_ids["@+id/miniPlayer"] is list(layout)[-1]
assert main_ids["@+id/miniPlayer"].attrib[ns+"background"] == "@drawable/mini_player_bg"
assert main_ids["@+id/miniPlayButton"].attrib["style"] == "@style/MiniPlayButton"
assert main_ids["@+id/miniExpandButton"].attrib[ns+"layout_width"] == "44dp"
assert main_ids["@+id/miniArtwork"].attrib[ns+"layout_width"] == "30dp"
assert "@+id/speedSpinner" not in main_ids
assert row.attrib[ns+"background"] == "@drawable/episode_card_bg"
assert row_ids["@+id/rowTitle"].attrib[ns+"textSize"] == "16sp"
assert row_ids["@+id/rowFavorite"].attrib[ns+"layout_width"] == "48dp"
assert row_ids["@+id/rowActiveMark"].attrib[ns+"visibility"] == "invisible"
assert row_ids["@+id/rowProgress"].attrib[ns+"max"] == "1000"
for name in ("values", "values-night"):
    colors = ET.parse(res / name / "colors.xml").getroot()
    names = {x.attrib["name"] for x in colors}
    assert {"brandAccent","miniTextMuted","miniProgressTrack","onAccent","headerButtonSurface"}.issubset(names)
    styles = ET.parse(res / name / "styles.xml").getroot()
    assert any(x.attrib.get("name") == "MiniPlayButton" for x in styles)
for name in ("episode_card_bg","episode_selected_bg","row_active_mark_bg","mini_player_bg","mini_art_bg","mini_play_bg"):
    ET.parse(res / "drawable" / (name + ".xml"))
adapter = (root / "app/src/main/java/it/successoplayer/app/EpisodeAdapter.java").read_text(encoding="utf-8")
assert "h.activeMark.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);" in adapter
assert "R.drawable.episode_card_bg" in adapter
activity = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
assert "R.drawable.shuffle_active_bg : R.drawable.button_bg" in activity
assert "speedSpinner" not in activity
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
import re
version_code = re.search(r"versionCode\s+(\d+)", gradle)
assert version_code is not None and int(version_code.group(1)) >= 51000
assert "versionName '5.1'" in gradle
workflow = (root / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
assert "code = 51000 + int(os.environ['GITHUB_RUN_NUMBER'])" in workflow
assert "Pubblica automaticamente su GitHub Releases" in workflow
print("OK: modern episode cards, night/day theme, persistent mini-player and OTA version.")
