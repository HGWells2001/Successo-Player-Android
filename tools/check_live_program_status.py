#!/usr/bin/env python3
"""Regression: precise weekday schedule and real stream status on Android."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/it/successoplayer/app"
main=(src/"MainActivity.java").read_text(encoding="utf-8")
service=(src/"LiveRadioService.java").read_text(encoding="utf-8")
scheduler=(src/"LiveRadioScheduler.java").read_text(encoding="utf-8")
xml=ET.parse(root/"app/src/main/res/layout/activity_main.xml")
ns="{http://schemas.android.com/apk/res/android}"
ids={el.attrib.get(ns+"id"):el for el in xml.iter()}
assert "@+id/liveScheduleHint" not in ids and "@+id/liveConnectionHint" not in ids
assert ids["@+id/liveAutoSwitch"].attrib[ns+"layout_height"]=="48dp"
assert ids["@+id/liveAutoSwitch"].attrib[ns+"maxLines"]=="3"
assert "liveAutoSwitch.setTextOn(compactText);" in main
assert "liveAutoSwitch.setTextOff(compactText);" in main
assert "liveAutoSwitch.setText(compactText);" in main
assert "liveAutoSwitch.setContentDescription(" in main
assert "LiveButtonText.compose(enabled, schedule, connectionText)" in main
assert "liveScheduleHint" not in main and "liveConnectionHint" not in main
assert "LiveProgramStatus.at(" in main and "LiveProgramStatus.summary(schedule)" in main
assert "LiveRadioConnection.label(" in main
assert "liveStatusHandler.postDelayed(this, 1000L)" in main
assert "liveStatusHandler.removeCallbacks(liveStatusTick)" in main
assert "liveStatusHandler.removeCallbacksAndMessages(null)" in main
for state in ("PLAYING","CONNECTING","RETRYING","PAUSED","ERROR"):
    assert "LiveRadioConnection."+state in service
assert "publishConnection(connectionState)" in service
assert "LiveProgramStatus.at(ZonedDateTime.now(ROME)).inWindow" in scheduler
assert "LiveRadioScheduler.setEnabled(this, enabled);" in main
assert "@+id/moreMenuButton" in ids
with tempfile.TemporaryDirectory() as tmp:
    source=[src/"LiveProgramStatus.java",src/"LiveRadioConnection.java",
            src/"LiveButtonText.java",root/"tools/tests/LiveProgramStatusTest.java",
            root/"tools/tests/LiveButtonTextTest.java"]
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,*map(str,source)],check=True)
    subprocess.run(["java","-cp",tmp,"it.successoplayer.app.LiveProgramStatusTest"],check=True)
    subprocess.run(["java","-cp",tmp,"it.successoplayer.app.LiveButtonTextTest"],check=True)
print("OK: live schedule, compact three-line switch, and connection regression")
