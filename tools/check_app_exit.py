#!/usr/bin/env python3
"""Static regression checks for explicit exit and task-removal cleanup."""
from pathlib import Path
base = Path(__file__).resolve().parents[1]
source = base / "app/src/main/java/it/successoplayer/app"
activity = (source / "MainActivity.java").read_text(encoding="utf-8")
audio = (source / "AudioPlaybackService.java").read_text(encoding="utf-8")
radio = (source / "LiveRadioService.java").read_text(encoding="utf-8")
shutdown = (source / "PlaybackExit.java").read_text(encoding="utf-8")
xml = (base / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
manifest = (base / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

assert '@+id/exitButton' in xml
assert 'exitButton.setOnClickListener(v -> closeAppCompletely())' in activity
assert 'public void onBackPressed()' in activity
assert 'finishAndRemoveTask()' in activity
assert 'if (isFinishing())' in activity
assert 'service.shutdownForAppExit()' in activity
assert 'PlaybackExit.stopAll(this)' in activity
assert 'shutdownForAppExit()' in audio and 'persistSessionNow(position, duration, false)' in audio
assert 'mediaSession.setActive(false)' in audio
assert 'public void onTaskRemoved(Intent rootIntent)' in audio
assert 'public void onTaskRemoved(Intent rootIntent)' in radio
assert 'PlaybackExit.stopAll(this)' in audio and 'PlaybackExit.stopAll(this)' in radio
assert 'context.stopService(new Intent(context, AudioPlaybackService.class))' in shutdown
assert 'context.stopService(new Intent(context, LiveRadioService.class))' in shutdown
assert 'manager.cancelAll()' in shutdown
assert manifest.count('android:stopWithTask="false"') >= 2, 'Needed for onTaskRemoved callbacks'
assert 'service.restoreSession(\n                restoreEpisode,\n                resumePosition,\n                false' in activity
print("OK: completa uscita, chiusura servizi/notifiche, restore senza autoplay.")
