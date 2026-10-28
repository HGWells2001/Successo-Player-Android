#!/usr/bin/env python3
"""Static Android integration and JVM tests for the v4.24 Media3 episode engine."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/it/successoplayer/app"
svc=(src/"AudioPlaybackService.java").read_text(encoding="utf-8")
bridge=(src/"Media3EpisodePlayer.java").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

assert "implementation 'androidx.media3:media3-exoplayer:1.11.1'" in gradle
assert "MediaPlayer" not in svc
assert "private Media3EpisodePlayer player;" in svc
assert "new Media3EpisodePlayer(getApplicationContext())" in svc
assert "new ExoPlayer.Builder(context)" in bridge
assert ".setHandleAudioBecomingNoisy(true)" in bridge
assert ".setWakeMode(C.WAKE_MODE_LOCAL)" in bridge
assert ".setAudioAttributes(speech, false)" in bridge, "Do not manage audio focus twice"
assert "PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY" in bridge
assert "player.setOnRouteInterruptedListener" in svc
assert "persistSessionNow(pendingResumeMs, getCurrentDurationMs(), false)" in svc
assert "AudioRecoveryPolicy.delayMs(networkRetryAttempts++)" in svc
assert "generation.get() == token" in svc
assert "if (!requestFocus())" in svc
assert "== AudioManager.AUDIOFOCUS_REQUEST_GRANTED" in svc
assert "prepared = false;" in svc
assert "private void createMediaSession()" in svc
assert "new MediaSession.Builder(this, controls)" in svc
assert "extends MediaSessionService" in svc
assert "player.clearCallbacks();" in svc
assert "KEY_UP_NEXT" in svc and "KEY_EPISODE_POSITION_PREFIX" in svc
assert "private final SleepTimer sleepTimer" in svc
assert 'android.permission.WAKE_LOCK' in manifest
assert 'android.permission.REQUEST_INSTALL_PACKAGES' in manifest
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,
        str(src/"AudioRecoveryPolicy.java"),
        str(root/"tools/tests/AudioRecoveryPolicyTest.java")],check=True)
    subprocess.run(["java","-cp",tmp,"it.successoplayer.app.AudioRecoveryPolicyTest"],check=True)
print("OK: Media3 ExoPlayer, noisy route handling, audio focus, bounded retries and session preservation")
