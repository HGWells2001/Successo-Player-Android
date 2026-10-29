#!/usr/bin/env python3
"""Guard the bespoke vector icons, 48dp touch targets, and playback wiring."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
ns = "{http://schemas.android.com/apk/res/android}"
layout = ET.parse(res / "layout/activity_main.xml")
views = {n.attrib.get(ns+"id"): n for n in layout.iter() if n.attrib.get(ns+"id")}
icons = {
    "downloadButton": "ic_download_art",
    "openDownloadsButton": "ic_library_art",
    "shuffleButton": "ic_shuffle_art",
}
for control, icon in icons.items():
    button = views["@+id/" + control]
    assert button.tag == "ImageButton", (control, button.tag)
    assert button.attrib[ns+"layout_width"] == "48dp"
    assert button.attrib[ns+"layout_height"] == "48dp"
    assert button.attrib[ns+"src"] == "@drawable/" + icon
    assert button.attrib[ns+"scaleType"] == "centerInside"
    assert button.attrib[ns+"contentDescription"]
    drawable = ET.parse(res / "drawable" / (icon + ".xml")).getroot()
    assert drawable.tag == "vector" and len(list(drawable)) >= 2
assert all(t not in (res / "layout/activity_main.xml").read_text(encoding="utf-8") for t in ['android:text="⬇"', 'android:text="📂"', 'android:text="🔀"'])
source = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
assert "private ImageButton shuffleButton, downloadButton, openDownloadsButton;" in source
assert "downloadButton.setOnClickListener(v -> startDownload(selectedEpisode));" in source
assert "openDownloadsButton.setOnClickListener(v -> openEpisodeDownloads());" in source
assert "shuffleButton.setOnClickListener(v -> {" in source
assert "shuffleButton.setActivated(shuffleEnabled);" in source
assert "shuffleButton.setImageTintList(ColorStateList.valueOf(" in source
assert "shuffleButton.setText(" not in source
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
assert "versionName '5.2'" in gradle
print("OK: custom vector action icons, accessible sizing, active Shuffle and update version.")
