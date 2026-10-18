#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/"app/src/main/java/it/successoplayer/app"
xml=(root/"app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
activity=(base/"MainActivity.java").read_text(encoding="utf-8")
scheduler=(base/"LiveRadioScheduler.java").read_text(encoding="utf-8")
service=(base/"LiveRadioService.java").read_text(encoding="utf-8")
assert not any(x in activity+service+xml for x in ["liveListenButton","liveStopButton","ACTION_START_MANUAL","Rai Radio 1 ora"])
for item in ('@+id/downloadButton','@+id/openDownloadsButton'):
    frag=xml[xml.index(item):][:430]
    assert 'android:layout_width="48dp"' in frag and 'android:layout_height="48dp"' in frag
assert 'android:text="⬇"' in xml and 'android:text="📂"' in xml
assert 'android:text="Diretta programma"' in xml
assert xml.count('@+id/liveAutoSwitch')==1
assert 'openDownloadsButton.setOnClickListener(v -> openEpisodeDownloads());' in activity
assert 'downloadButton.setOnClickListener(v -> startDownload(selectedEpisode));' in activity
assert 'LiveRadioScheduler.setEnabled(this, enabled);' in activity
assert 'if (inBroadcastWindow()) {' in scheduler
assert 'setAction(LiveRadioService.ACTION_START_AUTO)' in scheduler
assert 'context.startForegroundService(play)' in scheduler
assert 'context.stopService(new Intent(context, LiveRadioService.class))' in scheduler
assert 'putBoolean(LiveRadioScheduler.PREF_ENABLED, false)' in service
assert 'LiveRadioScheduler.cancel(this)' in service
print("OK: compact buttons, toggle stops/resumes live within schedule.")
