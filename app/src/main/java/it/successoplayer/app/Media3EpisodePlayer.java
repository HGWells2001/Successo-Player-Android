package it.successoplayer.app;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

/**
 * Media3 bridge for the original service. The public episode/queue/session APIs
 * remain unchanged. All methods are called on Android's main looper.
 *
 * ExoPlayer owns decoder, buffering, device routing and wake locks; the existing
 * AudioPlaybackService owns audio focus so there is exactly one focus manager.
 */
final class Media3EpisodePlayer {
    interface PreparedListener { void onPrepared(Media3EpisodePlayer player); }
    interface CompletedListener { void onCompleted(Media3EpisodePlayer player); }
    interface ErrorListener { boolean onError(Media3EpisodePlayer player, int code, int extra); }
    interface RouteInterruptedListener { void onRouteInterrupted(Media3EpisodePlayer player); }

    private final ExoPlayer exo;
    private PreparedListener preparedListener;
    private CompletedListener completedListener;
    private ErrorListener errorListener;
    private RouteInterruptedListener routeInterruptedListener;
    private boolean prepared;
    private boolean completed;
    private boolean released;
    private String episodeId = "";
    private String episodeTitle = "";
    private final String artworkUri;

    Media3EpisodePlayer(Context context) {
        artworkUri = "android.resource://" + context.getPackageName() + "/"
                + R.drawable.logo_successo;
        AudioAttributes speech = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();
        exo = new ExoPlayer.Builder(context)
                .setAudioAttributes(speech, false)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        exo.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (released) return;
                if (state == Player.STATE_READY && !prepared) {
                    prepared = true;
                    if (preparedListener != null) preparedListener.onPrepared(Media3EpisodePlayer.this);
                } else if (state == Player.STATE_ENDED && !completed) {
                    completed = true;
                    if (completedListener != null) completedListener.onCompleted(Media3EpisodePlayer.this);
                }
            }
            @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (!released && !playWhenReady
                        && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                        && routeInterruptedListener != null) {
                    routeInterruptedListener.onRouteInterrupted(Media3EpisodePlayer.this);
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (!released && errorListener != null) {
                    errorListener.onError(Media3EpisodePlayer.this, error.errorCode, 0);
                }
            }
        });
    }

    ExoPlayer getExoPlayer() { return exo; }

    void setEpisodeInfo(Episode episode) {
        episodeId = episode == null || episode.id == null ? "" : episode.id;
        episodeTitle = episode == null || episode.title == null ? "" : episode.title;
    }

    void clearCallbacks() {
        preparedListener = null;
        completedListener = null;
        errorListener = null;
        routeInterruptedListener = null;
    }

    private MediaItem item(Uri uri) {
        MediaMetadata meta = new MediaMetadata.Builder()
                .setTitle(episodeTitle)
                .setArtist("Successo · Storie e voci dal Novecento")
                .setAlbumTitle("Successo · Storie e voci dal Novecento")
                .setArtworkUri(Uri.parse(artworkUri))
                .build();
        return new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(episodeId)
                .setMediaMetadata(meta)
                .build();
    }

    void setOnPreparedListener(PreparedListener callback) { preparedListener = callback; }
    void setOnCompletionListener(CompletedListener callback) { completedListener = callback; }
    void setOnErrorListener(ErrorListener callback) { errorListener = callback; }
    void setOnRouteInterruptedListener(RouteInterruptedListener callback) {
        routeInterruptedListener = callback;
    }

    void setDataSource(String source) {
        // Local internal fallback uses absolute paths, not file:// URLs.
        Uri uri = source.startsWith("/") ? Uri.fromFile(new File(source)) : Uri.parse(source);
        exo.setMediaItem(item(uri));
    }
    void setDataSource(Context context, Uri uri) {
        exo.setMediaItem(item(uri));
    }

    void prepareAsync() { exo.prepare(); }
    void start() { exo.play(); }
    void pause() { exo.pause(); }
    boolean isPlaying() { return exo.isPlaying(); }
    int getCurrentPosition() { return clamp(exo.getCurrentPosition()); }
    int getDuration() {
        long d = exo.getDuration();
        return d == C.TIME_UNSET ? 0 : clamp(d);
    }
    void seekTo(int positionMs) { exo.seekTo(Math.max(0L, (long) positionMs)); }
    void setSpeed(float speed) {
        exo.setPlaybackParameters(new PlaybackParameters(speed, 1f));
    }
    void setVolume(float volume) { exo.setVolume(volume); }

    void reset() {
        if (released) return;
        exo.stop();
        exo.clearMediaItems();
        prepared = false;
        completed = false;
    }
    void release() {
        if (released) return;
        released = true;
        exo.release();
    }

    private static int clamp(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }
}
