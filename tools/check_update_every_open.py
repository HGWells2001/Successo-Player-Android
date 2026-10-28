#!/usr/bin/env python3
"""Regression: visible update button and an uncached check on every foreground entry."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/it/successoplayer/app"
main=(java/"MainActivity.java").read_text(encoding="utf-8")
checker=(java/"UpdateChecker.java").read_text(encoding="utf-8")
xml=ET.parse(root/"app/src/main/res/layout/activity_main.xml")
ns="{http://schemas.android.com/apk/res/android}"
button=next(x for x in xml.iter() if x.attrib.get(ns+"id")=="@+id/moreMenuButton")
assert button.tag=="Button"
assert button.attrib.get(ns+"text")=="⋮"
assert button.attrib.get(ns+"layout_width")=="48dp"
assert button.attrib.get(ns+"layout_height")=="48dp"
assert "aggiornamenti" in button.attrib.get(ns+"contentDescription")
header=list(xml.getroot())[0]
assert button in list(header), "Overflow menu must sit inside the title header"
assert header.attrib.get(ns+"orientation")=="horizontal"
assert header.attrib.get(ns+"gravity")=="center_vertical"
assert button is list(header)[-1], "Overflow must be at the top right"
assert "case MENU_UPDATE:" in main
assert "UpdateChecker.check(this, prefs, true);" in main
assert "@+id/updateButton" not in {x.attrib.get(ns+"id") for x in xml.iter()}
create=main.split("protected void onCreate(",1)[1].split("protected void onStart(",1)[0]
start=main.split("protected void onStart(",1)[1].split("private void mainRebindPlaybackService(",1)[0]
assert "UpdateChecker.check(this, prefs, false);" in start
assert "UpdateChecker.check(this, prefs, false);" not in create
assert start.index("activityStarted = true;") < start.index("UpdateChecker.check(this, prefs, false);")
assert "INTERVAL_MS" not in checker
assert "PREF_LAST_CHECK" not in checker
assert "last_release_check_ms" not in checker
assert "CHECK_IN_PROGRESS.compareAndSet(false, true)" in checker
assert "CHECK_IN_PROGRESS.set(false)" in checker
assert "connection.setUseCaches(false)" in checker
assert '"Cache-Control", "no-cache"' in checker
assert "RaiPlay Sound" not in (root/"app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
assert "RaiPlay Sound" not in main
assert "RaiPlay Sound" not in (java/"AudioPlaybackService.java").read_text(encoding="utf-8")
assert "RaiPlay Sound" not in (java/"DailyEpisodeCheckReceiver.java").read_text(encoding="utf-8")
assert "RaiPlay Sound" not in (java/"RaiClient.java").read_text(encoding="utf-8")
assert 'programSubtitle.setText(catalog.title);' in main
assert "https://api.github.com/repos/HGWells2001/Successo-Player-Android/releases/latest" in checker
assert 'setPositiveButton("Scarica APK"' in checker
print("OK: compact header update icon, no external branding in app UI, checks on every access.")
