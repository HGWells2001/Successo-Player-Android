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
assert toggle.attrib.get(ns + "textOn") == "● Diretta programma"
assert toggle.attrib.get(ns + "textOff") == "○ Diretta programma"
assert toggle.attrib.get(ns + "background") == "@drawable/live_toggle_bg"
assert toggle.attrib.get(ns + "textColor") == "@color/live_toggle_text"
assert toggle.attrib.get(ns + "textAllCaps") == "false"
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
print("OK: states, colors, form factor and behavior wired.")
