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
assert 'android:src="@drawable/ic_download_art"' in xml
assert 'android:src="@drawable/ic_library_art"' in xml
assert 'android:text="⬇"' not in xml and 'android:text="📂"' not in xml
assert '<ToggleButton' in xml
assert 'android:textOn="● Diretta programma"' in xml
assert 'android:textOff="○ Diretta programma"' in xml
assert 'android:background="@drawable/live_toggle_bg"' in xml
assert 'android:textColor="@color/live_toggle_text"' in xml
assert 'private ToggleButton liveAutoSwitch;' in activity
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
print("OK: compact buttons, styled ToggleButton stops/resumes live within schedule.")
