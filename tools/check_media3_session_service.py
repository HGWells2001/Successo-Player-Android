#!/usr/bin/env python3
"""MediaSessionService, native notification and external-transport regression."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/it/successoplayer/app"
svc=(src/"AudioPlaybackService.java").read_text(encoding="utf-8")
engine=(src/"Media3EpisodePlayer.java").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
xml=ET.parse(root/"app/src/main/AndroidManifest.xml")
ns="{http://schemas.android.com/apk/res/android}"
matches=[e for e in xml.iter("service") if e.attrib.get(ns+"name")==".AudioPlaybackService"]
assert len(matches)==1
service=matches[0]
assert service.attrib[ns+"exported"]=="true"
assert service.attrib[ns+"foregroundServiceType"]=="mediaPlayback"
assert service.attrib[ns+"stopWithTask"]=="false"
assert any(a.attrib.get(ns+"name")=="androidx.media3.session.MediaSessionService"
           for a in service.iter("action"))
assert "implementation 'androidx.media3:media3-exoplayer:1.11.1'" in gradle
assert "implementation 'androidx.media3:media3-session:1.11.1'" in gradle
assert "extends MediaSessionService" in svc
assert "MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo)" in svc
assert "super.onBind(intent);" in svc
assert "Binder.getCallingUid() != Process.myUid()" in svc
assert "super.onStartCommand(intent, flags, startId);" in svc
assert svc.count("new Media3EpisodePlayer(getApplicationContext())")==1
assert "new ForwardingPlayer(player.getExoPlayer())" in svc
assert "new MediaSession.Builder(this, controls)" in svc
for action in ("play()", "pause()", "stop()", "seekTo(long positionMs)",
               "seekToNext()", "seekToPrevious()", "seekToNextMediaItem()",
               "seekToPreviousMediaItem()"):
    assert "public void "+action in svc,action
assert "skipToNext();" in svc and "skipToPrevious();" in svc
assert "player.setEpisodeInfo(currentEpisode);" in svc
method=svc.split("private void releasePlayer()",1)[1].split("private void maybeMarkListened",1)[0]
assert "player.clearCallbacks();" in method and "player.reset();" in method
assert "player.release();" not in method
assert "mediaSession.release();" in svc and "player.release();" in svc
assert "new Notification.Builder" not in svc
assert "android.media.session.MediaSession" not in svc
assert "android.media.session.PlaybackState" not in svc
assert "setMediaMetadata(meta)" in engine and "setMediaId(episodeId)" in engine
assert "setArtworkUri" in engine
assert "public void onTaskRemoved(Intent rootIntent)" in svc
assert "PlaybackExit.stopAll(this)" in svc
print("OK: stable ExoPlayer, MediaSessionService, external commands, native notification")
