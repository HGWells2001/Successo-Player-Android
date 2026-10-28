#!/usr/bin/env python3
"""Ensure live ToggleButton appearance matches neighboring controls without losing behavior."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
layout = root / "app/src/main/res/layout/activity_main.xml"
draw = root / "app/src/main/res/drawable/live_toggle_bg.xml"
palette = root / "app/src/main/res/color/live_toggle_text.xml"
ns = "{http://schemas.android.com/apk/res/android}"
tree = ET.parse(layout)
toggle = next((x for x in tree.iter() if x.attrib.get(ns + "id") == "@+id/liveAutoSwitch"), None)
assert toggle is not None and toggle.tag == "ToggleButton"
assert toggle.attrib.get(ns + "layout_height") == "48dp"
assert toggle.attrib.get(ns + "layout_width") == "0dp"
assert toggle.attrib.get(ns + "layout_weight") == "1"
# Tutti e tre i pulsanti devono essere fratelli nella stessa riga,
# senza una colonna di testo che ne altera il centraggio verticale.
row = next(parent for parent in tree.iter() if toggle in list(parent))
for button_id in ("@+id/downloadButton", "@+id/openDownloadsButton"):
    button = next(child for child in row if child.attrib.get(ns + "id") == button_id)
    assert button.attrib.get(ns + "layout_height") == "48dp"
    assert button.attrib.get(ns + "layout_width") == "48dp"
# Nothing lives beneath the button: programme and connection are inside it.
ids = {x.attrib.get(ns + "id") for x in tree.iter()}
assert "@+id/liveScheduleHint" not in ids
assert "@+id/liveConnectionHint" not in ids
assert set(x.attrib.get(ns + "id") for x in row if x.attrib.get(ns + "id")) == {
    "@+id/downloadButton", "@+id/openDownloadsButton", "@+id/liveAutoSwitch"
}
assert row.attrib.get(ns + "gravity") == "center_vertical"
assert toggle.attrib.get(ns + "textOn") == "● Diretta programma"
assert toggle.attrib.get(ns + "textOff") == "○ Diretta programma"
assert toggle.attrib.get(ns + "background") == "@drawable/live_toggle_bg"
assert toggle.attrib.get(ns + "textColor") == "@color/live_toggle_text"
assert toggle.attrib.get(ns + "textAllCaps") == "false"
assert toggle.attrib.get(ns + "maxLines") == "3"
assert toggle.attrib.get(ns + "autoSizeTextType") == "uniform"
assert toggle.attrib.get(ns + "autoSizeMinTextSize") == "8sp"
assert toggle.attrib.get(ns + "autoSizeMaxTextSize") == "10sp"
assert toggle.attrib.get(ns + "textSize") == "10sp"
assert toggle.attrib.get(ns + "includeFontPadding") == "false"
assert "private ToggleButton liveAutoSwitch;" in activity
assert "liveAutoSwitch.setOnCheckedChangeListener" in activity
assert "LiveRadioScheduler.setEnabled(this, enabled);" in activity
assert "syncingLiveSwitch = true;" in activity and "syncingLiveSwitch = false;" in activity
bg = ET.parse(draw).getroot()
items = list(bg)
assert items[0].attrib[ns + "state_checked"] == "true"
assert items[0].attrib[ns + "drawable"] == "@drawable/accent_button_bg"
assert items[1].attrib[ns + "drawable"] == "@drawable/button_bg"
color = list(ET.parse(palette).getroot())
assert color[0].attrib[ns + "state_checked"] == "true"
assert color[0].attrib[ns + "color"] == "@color/headerText"
assert color[1].attrib[ns + "color"] == "@color/textPrimary"
print("OK: live info inside unchanged 48dp button, no extra UI below.")
