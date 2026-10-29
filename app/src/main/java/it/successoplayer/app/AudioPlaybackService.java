package it.successoplayer.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Process;

import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AudioPlaybackService extends MediaSessionService {
    public interface Listener {
        void onStatus(String text, boolean busy);
        void onPlaybackState(boolean playing, boolean prepared);
        void onPosition(long positionMs, long durationMs);
        void onEpisode(Episode episode);
        void onListenedChanged(Episode episode);
        void onPlaybackCompleted(Episode episode);
        void onError(String message);
    }

    private static final String PREFS = "successo_player";

    private static final String KEY_SESSION_EPISODE_ID = "session_episode_id";
    private static final String KEY_SESSION_POSITION = "session_position_ms";
    private static final String KEY_EPISODE_POSITION_PREFIX = "episode_resume_ms_";
    private static final String KEY_SESSION_DURATION = "session_duration_ms";
    private static final String KEY_SESSION_WAS_PLAYING = "session_was_playing";
    private static final String KEY_SESSION_SAVED_AT = "session_saved_at";
    private static final String KEY_SHUFFLE_POOL = "shuffle_pool_episode_ids";
    private static final String KEY_SHUFFLE_PLAYED = "shuffle_played_episode_ids";
    private static final String KEY_UP_NEXT = "playback_up_next_ids_json";
    private static final String KEY_PLAY_HISTORY = "playback_recent_history_v1";

    private static final String ACTION_TOGGLE = "it.successoplayer.app.TOGGLE";
    private static final String ACTION_PREVIOUS = "it.successoplayer.app.PREVIOUS";
    private static final String ACTION_NEXT = "it.successoplayer.app.NEXT";
    private static final String ACTION_STOP = "it.successoplayer.app.STOP";

    public final class LocalBinder extends Binder {
        public AudioPlaybackService getService() {
            if (Binder.getCallingUid() != Process.myUid()) {
                throw new SecurityException("Local binder restricted to this app");
            }
            return AudioPlaybackService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final ShuffleCycle shuffleCycle = new ShuffleCycle(new Random());
    private final SleepTimer sleepTimer = new SleepTimer();

    private final List<Episode> queue = new ArrayList<>();
    private final PlaybackQueue upNext = new PlaybackQueue();
    private final PlaybackHistory playHistory = new PlaybackHistory();
    private final Map<String, Episode> entireCatalog = new HashMap<>();
    private final Set<String> listenedIds = new HashSet<>();

    private Media3EpisodePlayer player;
    private Episode currentEpisode;
    private Listener listener;
    private SharedPreferences prefs;

    private boolean prepared;
    private boolean fallbackTried;
    private int networkRetryAttempts;
    private boolean shuffleEnabled;
    private String downloadedAudioUri;
    private boolean startedForPlayback;

    private int volumePercent = 85;
    private float speed = 1f;
    private long pendingResumeMs;
    private long lastSessionWriteAt;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private MediaSession mediaSession;

    private String lastStatus = "Pronto";
    private boolean lastBusy;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            // Playback is a foreground service: this keeps running with the
            // screen locked and while MainActivity is not visible.
            if (sleepTimer.isDue(SystemClock.elapsedRealtime())) {
                expireSleepTimer();
            }
            if (player != null && prepared) {
                try {
                    int position = player.getCurrentPosition();
                    int duration = player.getDuration();

                    pendingResumeMs = position;
                    notifyPosition(position, duration);
                    maybeMarkListened(position, duration);
                    persistSessionThrottled(position, duration, isPlaying());
                    updateMediaSessionState();
                } catch (Exception ignored) {}
            }

            main.postDelayed(this, 500L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        listenedIds.clear();
        listenedIds.addAll(
                prefs.getStringSet("listened_episode_ids", Collections.emptySet())
        );

        volumePercent = prefs.getInt("volume", 85);

        // Always start at normal playback speed, ignoring obsolete speedIndex values.
        speed = 1f;

        shuffleEnabled = prefs.getBoolean("shuffle_enabled", false);
        restoreUpNext();
        restorePlayHistory();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // A stable ExoPlayer is essential: the session survives episode changes.
        player = new Media3EpisodePlayer(getApplicationContext());
        createMediaSession();
        updateMediaSessionMetadata();
        updateMediaSessionState();

        main.post(ticker);
    }

    @Override
    public IBinder onBind(Intent intent) {
        IBinder controllerBinder = super.onBind(intent);
        return intent != null && intent.getAction() == null ? binder : controllerBinder;
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Required to handle media-button actions sent to MediaSessionService.
        super.onStartCommand(intent, flags, startId);
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            togglePlayPause();
        } else if (intent != null && ACTION_PREVIOUS.equals(intent.getAction())) {
            skipToPrevious();
        } else if (intent != null && ACTION_NEXT.equals(intent.getAction())) {
            skipToNext();
        } else if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopPlayback();
        }

        // Se Android termina il servizio mentre non è in foreground, la sessione
        // viene comunque salvata e sarà ripristinata dalla Activity al rientro.
        return START_NOT_STICKY;
    }

    public void setListener(Listener listener) {
        this.listener = listener;

        if (listener == null) return;

        if (currentEpisode != null) {
            listener.onEpisode(currentEpisode);
        }

        listener.onStatus(lastStatus, lastBusy);
        listener.onPlaybackState(isPlaying(), prepared);
        listener.onPosition(getCurrentPositionMs(), getCurrentDurationMs());
    }

    public void clearListener(Listener expected) {
        if (listener == expected) {
            listener = null;
        }
    }

    public Episode getCurrentEpisode() {
        return currentEpisode;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public boolean isPlaying() {
        try {
            return player != null && prepared && player.isPlaying();
        } catch (Exception ignored) {
            return false;
        }
    }

    public long getCurrentPositionMs() {
        if (player != null && prepared) {
            try {
                return Math.max(0L, player.getCurrentPosition());
            } catch (Exception ignored) {}
        }

        return Math.max(0L, pendingResumeMs);
    }

    public long getCurrentDurationMs() {
        if (player != null && prepared) {
            try {
                long actual = player.getDuration();
                if (actual > 0L) return actual;
            } catch (Exception ignored) {}
        }

        return currentEpisode != null ? Math.max(0L, currentEpisode.durationMs) : 0L;
    }

    /** Explicit queued episodes can be outside the currently filtered Shuffle pool. */
    public void setFullCatalog(List<Episode> episodes) {
        entireCatalog.clear();
        if (episodes == null) return;
        for (Episode ep : episodes) {
            if (ep != null && ep.id != null) entireCatalog.put(ep.id, ep);
        }
    }

    public List<String> getUpNextIds() {
        return upNext.snapshot();
    }

    public boolean enqueueUpNext(Episode episode, boolean playNext) {
        if (episode == null || episode.id == null) return false;
        entireCatalog.put(episode.id, episode);
        boolean added = upNext.enqueue(episode.id, playNext);
        if (added) persistUpNext();
        return added;
    }

    public boolean moveUpNext(int index, int direction) {
        boolean moved = upNext.move(index, direction);
        if (moved) persistUpNext();
        return moved;
    }

    public boolean removeFromUpNext(int index) {
        boolean removed = upNext.removeAt(index);
        if (removed) persistUpNext();
        return removed;
    }

    public boolean removeFromUpNextById(String id) {
        boolean removed = upNext.removeId(id);
        if (removed) persistUpNext();
        return removed;
    }

    public void clearUpNext() {
        upNext.clear();
        persistUpNext();
    }

    private void persistUpNext() {
        JSONArray ids = new JSONArray();
        for (String id : upNext.snapshot()) ids.put(id);
        prefs.edit().putString(KEY_UP_NEXT, ids.toString()).apply();
    }

    private void restoreUpNext() {
        List<String> restored = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString(KEY_UP_NEXT, "[]"));
            for (int i = 0; i < saved.length() && i < PlaybackQueue.MAX_ITEMS; ++i) {
                String id = saved.optString(i, "");
                if (!id.isEmpty()) restored.add(id);
            }
        } catch (Exception ignored) {}
        upNext.restore(restored);
    }

    private Episode takeQueuedEpisode() {
        while (upNext.size() > 0) {
            String id = upNext.takeNext();
            persistUpNext();
            Episode ep = entireCatalog.get(id);
            if (ep != null) return ep;
            // A removed or renamed catalog entry must not block playback.
        }
        return null;
    }

    /** History includes only episodes that actually started playing. */
    public List<PlaybackHistory.Entry> getRecentHistory() {
        return playHistory.snapshot();
    }

    public boolean removeFromPlayHistory(String episodeId) {
        boolean removed = playHistory.remove(episodeId);
        if (removed) persistPlayHistory();
        return removed;
    }

    public void clearPlayHistory() {
        playHistory.clear();
        persistPlayHistory();
    }

    private void recordPlayHistory() {
        if (currentEpisode == null || currentEpisode.id == null) return;
        if (playHistory.record(currentEpisode.id, System.currentTimeMillis())) {
            persistPlayHistory();
        }
    }

    private void persistPlayHistory() {
        JSONArray saved = new JSONArray();
        for (PlaybackHistory.Entry entry : playHistory.snapshot()) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", entry.episodeId);
                object.put("at", entry.playedAtMs);
                saved.put(object);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(KEY_PLAY_HISTORY, saved.toString()).apply();
    }

    private void restorePlayHistory() {
        List<PlaybackHistory.Entry> recovered = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString(KEY_PLAY_HISTORY, "[]"));
            for (int i = 0; i < saved.length() && i < PlaybackHistory.MAX_ITEMS; i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                long at = item.optLong("at", 0L);
                if (!id.isEmpty() && at > 0L) {
                    recovered.add(new PlaybackHistory.Entry(id, at));
                }
            }
        } catch (Exception ignored) {
            // Corrupt history must not interfere with playback or other prefs.
        }
        playHistory.restore(recovered);
    }

    public void setQueue(List<Episode> episodes, boolean shuffle) {
        queue.clear();
        if (episodes != null) queue.addAll(episodes);
        shuffleEnabled = shuffle;
        if (shuffleEnabled) {
            List<String> ids = new ArrayList<>();
            for (Episode episode : queue) {
                if (episode != null && episode.id != null) ids.add(episode.id);
            }
            if (shuffleCycle.setPool(ids)) {
                // Reuse progress after an app restart when filters/pool match.
                if (shuffleCycle.getPoolIds().equals(
                        prefs.getStringSet(KEY_SHUFFLE_POOL, Collections.emptySet()))) {
                    shuffleCycle.restorePlayed(
                            prefs.getStringSet(KEY_SHUFFLE_PLAYED, Collections.emptySet()));
                }
                saveShuffleProgress();
            }
        }
        updateMediaSessionState();
    }

    private void saveShuffleProgress() {
        prefs.edit()
                .putStringSet(KEY_SHUFFLE_POOL, shuffleCycle.getPoolIds())
                .putStringSet(KEY_SHUFFLE_PLAYED, shuffleCycle.getPlayedIds())
                .apply();
    }

    /**
     * Called by the UI's initial Shuffle click and by background auto-advance.
     * A single service owns the cycle, including media-button requests.
     */
    public Episode pickNextShuffleEpisode(Episode excluded) {
        String id = shuffleCycle.next(excluded == null ? null : excluded.id);
        if (id == null) return null;
        saveShuffleProgress();
        for (Episode episode : queue) {
            if (episode != null && id.equals(episode.id)) return episode;
        }
        return null;
    }

    /** Configure a time-based sleep timer, irrespective of Shuffle or Next Up. */
    public void startSleepTimer(int minutes) {
        sleepTimer.startMinutes(minutes, SystemClock.elapsedRealtime());
        notifyStatus("Timer sonno attivo: " + minutes + " minuti", false);
    }

    /** At episode completion, do not advance to Shuffle or queued episodes. */
    public boolean sleepAfterCurrentEpisode() {
        if (currentEpisode == null || currentEpisode.id == null) return false;
        boolean ok = sleepTimer.finishAtEnd(currentEpisode.id);
        if (ok) notifyStatus("Timer sonno: arresto alla fine della puntata", false);
        return ok;
    }

    public void cancelSleepTimer() {
        sleepTimer.cancel();
        notifyStatus("Timer sonno disattivato", false);
    }

    public boolean isSleepTimerActive() {
        return sleepTimer.isActive();
    }

    public boolean isSleepTimerUntilEpisodeEnd() {
        return sleepTimer.isEndOfEpisode();
    }

    public long getSleepTimerRemainingMs() {
        return sleepTimer.remainingMs(SystemClock.elapsedRealtime());
    }

    private void expireSleepTimer() {
        if (!sleepTimer.isDue(SystemClock.elapsedRealtime())) return;
        sleepTimer.cancel();
        if (player != null && prepared) {
            // Pause also while buffering: audio must not restart after the timer expires.
            // Pause preserves the listening bookmark for a future return.
            pause();
        } else if (currentEpisode != null && !prepared) {
            // Cancel asynchronous preparation, so it cannot start playback
            // once the timer has already expired.
            generation.incrementAndGet();
            releasePlayer();
            persistSessionNow(getCurrentPositionMs(), getCurrentDurationMs(), false);
            notifyPlayback();
            updateMediaSessionState();
            if (startedForPlayback) updateNotification(false);
        }
        notifyStatus("Timer sonno terminato · riproduzione in pausa", false);
    }

    public void setShuffleEnabled(boolean enabled) {
        shuffleEnabled = enabled;
        updateMediaSessionState();
    }

    /**
     * Recupera il segnalibro dell'episodio, migrando la vecchia posizione
     * globale soltanto per l'episodio a cui effettivamente apparteneva.
     */
    public long getSavedEpisodePosition(Episode episode) {
        if (episode == null || episode.id == null || prefs == null) return 0L;
        String key = KEY_EPISODE_POSITION_PREFIX + episode.id;
        long fallback = episode.id.equals(
                prefs.getString(KEY_SESSION_EPISODE_ID, ""))
                ? prefs.getLong(KEY_SESSION_POSITION, 0L) : 0L;
        long position = prefs.getLong(key, fallback);
        long duration = episode.durationMs;
        if (duration > 0L && position >= Math.max(0L, duration - 1500L)) return 0L;
        return Math.max(0L, duration > 0L ? Math.min(position, duration) : position);
    }

    public void selectEpisode(Episode episode) {
        // Salva PRIMA la posizione dell'episodio che stiamo lasciando.
        // Il servizio mantiene un segnalibro indipendente per ogni puntata.
        if (currentEpisode != null) {
            persistSessionNow(getCurrentPositionMs(), getCurrentDurationMs(), false);
        }
        final long nextPosition = getSavedEpisodePosition(episode);
        generation.incrementAndGet();
        releasePlayer();

        currentEpisode = episode;
        downloadedAudioUri = null;
        // "At end of episode" belongs only to the episode selected when set.
        sleepTimer.cancelIfDifferentEpisode(episode == null ? null : episode.id);
        if (shuffleEnabled && episode != null
                && shuffleCycle.markPlayed(episode.id)) saveShuffleProgress();
        prepared = false;
        fallbackTried = false;
        networkRetryAttempts = 0;
        pendingResumeMs = nextPosition;

        persistSessionNow(nextPosition, episode != null ? episode.durationMs : 0L, false);

        if (listener != null) {
            listener.onEpisode(episode);
            listener.onPosition(nextPosition, episode != null ? episode.durationMs : 0L);
            listener.onPlaybackState(false, false);
        }

        notifyStatus("Puntata selezionata · premi ▶", false);
        updateMediaSessionMetadata();
        updateMediaSessionState();

        if (startedForPlayback) {
            updateNotification(false);
        }
    }

    /**
     * A selected completed download is played directly, without resolving
     * its streaming URL. Subsequent ordinary selections return to streaming.
     */
    public void selectDownloadedEpisode(Episode episode, android.net.Uri uri) {
        if (episode == null || uri == null || !"content".equals(uri.getScheme())) return;
        selectEpisode(episode);
        downloadedAudioUri = uri.toString();
    }

    public void restoreSession(Episode episode, long positionMs, boolean resumePlayback) {
        if (episode == null) return;

        generation.incrementAndGet();
        releasePlayer();

        currentEpisode = episode;
        downloadedAudioUri = null;
        prepared = false;
        fallbackTried = false;
        networkRetryAttempts = 0;

        long max = episode.durationMs > 0 ? episode.durationMs : Long.MAX_VALUE;
        pendingResumeMs = Math.max(0L, Math.min(positionMs, max));

        if (listener != null) {
            listener.onEpisode(episode);
            listener.onPosition(pendingResumeMs, episode.durationMs);
            listener.onPlaybackState(false, false);
        }

        notifyStatus(
                resumePlayback
                        ? "Ripristino la riproduzione…"
                        : "Sessione ripristinata · premi ▶",
                resumePlayback
        );

        persistSessionNow(pendingResumeMs, episode.durationMs, resumePlayback);
        updateMediaSessionMetadata();
        updateMediaSessionState();

        if (resumePlayback) {
            prepareAndPlay();
        } else if (startedForPlayback) {
            updateNotification(false);
        }
    }

    public void togglePlayPause() {
        if (currentEpisode == null) return;

        if (player != null && prepared) {
            if (player.isPlaying()) {
                pause();
            } else {
                playPrepared();
            }
        } else {
            prepareAndPlay();
        }
    }

    public void pause() {
        if (player == null || !prepared) {
            // An incoming call/pause during URL resolution or reconnection
            // must cancel all pending callbacks: never auto-start afterwards.
            if (currentEpisode != null && startedForPlayback) {
                generation.incrementAndGet();
                releasePlayer();
                persistSessionNow(pendingResumeMs, getCurrentDurationMs(), false);
                notifyPlayback();
                updateMediaSessionState();
                updateNotification(false);
            }
            return;
        }

        try {
            player.pause();
        } catch (Exception ignored) {}

        pendingResumeMs = getCurrentPositionMs();

        persistSessionNow(
                pendingResumeMs,
                getCurrentDurationMs(),
                false
        );

        notifyPlayback();
        updateMediaSessionState();
        updateNotification(false);
    }

    public void seekTo(long ms) {
        long duration = getCurrentDurationMs();
        long target = Math.max(
                0L,
                duration > 0L ? Math.min(ms, duration) : ms
        );

        pendingResumeMs = target;

        if (player != null && prepared) {
            try {
                player.seekTo((int) target);
            } catch (Exception ignored) {}
        }

        notifyPosition(target, duration);

        persistSessionNow(
                target,
                duration,
                isPlaying()
        );
        updateMediaSessionState();
    }

    public void seekBy(long deltaMs) {
        seekTo(getCurrentPositionMs() + deltaMs);
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.5f, Math.min(2.0f, speed));
        applySpeed();
    }

    public void setVolumePercent(int value) {
        volumePercent = Math.max(0, Math.min(100, value));
        applyVolume(1f);
    }

    private void ensureStartedForPlayback() {
        if (startedForPlayback) return;

        startedForPlayback = true;

        try {
            startService(new Intent(getApplicationContext(), AudioPlaybackService.class));
        } catch (Exception ignored) {}

        // Promuoviamo subito il servizio a foreground: se l'utente cambia app
        // mentre la sorgente sta ancora risolvendo l'URL, il motore audio
        // non viene lasciato appeso a un'Activity ormai in background.
        updateNotification(false);
    }

    private void prepareAndPlay() {
        final Episode episode = currentEpisode;
        if (episode == null) return;

        ensureStartedForPlayback();

        final int token = generation.incrementAndGet();

        prepared = false;
        fallbackTried = false;
        networkRetryAttempts = 0;

        persistSessionNow(
                pendingResumeMs,
                episode.durationMs,
                true
        );

        if (downloadedAudioUri != null) {
            final String local = downloadedAudioUri;
            main.post(() -> {
                if (generation.get() == token && currentEpisode == episode) {
                    openSource(local, true, token);
                }
            });
            return;
        }

        notifyStatus("Risolvo l'audio…", true);

        io.execute(() -> {
            try {
                String resolved = RaiClient.resolveAudioUrl(episode);

                main.post(() -> {
                    if (generation.get() != token || currentEpisode != episode) {
                        return;
                    }

                    openSource(resolved, false, token);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (generation.get() != token || currentEpisode != episode) {
                        return;
                    }

                    fail("Impossibile aprire l'audio: " + cleanMessage(e));
                });
            }
        });
    }

    private void openSource(String source, boolean localFile, int token) {
        releasePlayer();

        try {
            player.setEpisodeInfo(currentEpisode);
            player.setOnRouteInterruptedListener(mp -> {
                if (generation.get() != token || player != mp) return;
                // ExoPlayer pauses on a noisy output route change.
                pendingResumeMs = getCurrentPositionMs();
                persistSessionNow(pendingResumeMs, getCurrentDurationMs(), false);
                notifyStatus("Uscita audio cambiata · riproduzione in pausa", false);
                notifyPlayback();
                updateMediaSessionState();
                updateNotification(false);
            });

            player.setOnPreparedListener(mp -> {
                if (generation.get() != token) return;

                prepared = true;

                applyVolume(1f);
                if (!requestFocus()) {
                    notifyStatus("Audio occupato da una chiamata o altra app · premi ▶ per riprovare", false);
                    notifyPlayback();
                    updateMediaSessionState();
                    updateNotification(false);
                    return;
                }

                long duration = 0L;
                try {
                    duration = mp.getDuration();
                } catch (Exception ignored) {}

                long resumeAt = Math.max(
                        0L,
                        duration > 0L
                                ? Math.min(pendingResumeMs, Math.max(0L, duration - 1000L))
                                : pendingResumeMs
                );

                if (resumeAt > 0L) {
                    try {
                        mp.seekTo((int) resumeAt);
                    } catch (Exception ignored) {}
                }

                try {
                    mp.start();
                    recordPlayHistory();
                    applySpeed();
                } catch (Exception ignored) {}

                pendingResumeMs = resumeAt;

                notifyStatus(
                        localFile
                                ? "Riproduzione offline"
                                : "Riproduzione streaming",
                        false
                );

                notifyPlayback();
                updateMediaSessionMetadata();
                updateMediaSessionState();
                updateNotification(true);

                try {
                    int position = mp.getCurrentPosition();
                    int actualDuration = mp.getDuration();

                    pendingResumeMs = position;
                    notifyPosition(position, actualDuration);
                    persistSessionNow(position, actualDuration, true);
                } catch (Exception ignored) {}
            });

            player.setOnCompletionListener(mp -> {
                Episode completedEpisode = currentEpisode;

                long duration = getCurrentDurationMs();
                pendingResumeMs = duration;

                markCurrentListened();

                persistSessionNow(duration, duration, false);
                notifyPlayback();
                updateMediaSessionState();
                updateNotification(false);

                if (listener != null && completedEpisode != null) {
                    listener.onPlaybackCompleted(completedEpisode);
                }

                if (sleepTimer.consumeEpisodeCompletion(
                        completedEpisode == null ? null : completedEpisode.id)) {
                    // Keep the last position and mark the episode listened.
                    // Do not consume Next Up or trigger Shuffle auto-advance.
                    notifyStatus("Timer sonno · fine puntata raggiunta", false);
                } else {
                    // Exit the player completion callback before switching sources.
                    main.post(() -> advanceAfterCompletion(completedEpisode));
                }
            });

            player.setOnErrorListener((mp, what, extra) -> {
                if (generation.get() != token || player != mp) return true;
                if (!localFile && AudioRecoveryPolicy.isNetworkError(what)) {
                    long at = getCurrentPositionMs();
                    pendingResumeMs = Math.max(0L, at);
                    persistSessionNow(pendingResumeMs, getCurrentDurationMs(), false);
                    long delay = AudioRecoveryPolicy.delayMs(networkRetryAttempts++);
                    if (delay >= 0L) {
                        prepared = false;
                        notifyStatus("Rete instabile · riconnessione dalla posizione salvata…", true);
                        main.postDelayed(() -> {
                            if (generation.get() == token && currentEpisode != null
                                    && player == mp) {
                                openSource(source, false, token);
                            }
                        }, delay);
                    } else {
                        fail("Connessione interrotta. Posizione salvata: premi ▶ per riprovare.");
                    }
                    return true;
                }
                if (!localFile && !fallbackTried && currentEpisode != null) {
                    fallbackTried = true;
                    cacheFallback(currentEpisode, source, token);
                    return true;
                }
                fail("Errore decodifica o file non riproducibile.");
                return true;
            });

            if (localFile && !source.startsWith("content://")) {
                player.setDataSource(source);
            } else {
                player.setDataSource(
                        getApplicationContext(),
                        android.net.Uri.parse(source)
                );
            }

            notifyStatus(
                    localFile
                            ? "Apro la copia locale…"
                            : "Preparo la riproduzione…",
                    true
            );

            player.prepareAsync();

        } catch (Exception e) {
            if (!localFile && !fallbackTried && currentEpisode != null) {
                fallbackTried = true;
                cacheFallback(currentEpisode, source, token);
            } else {
                fail("Errore player: " + cleanMessage(e));
            }
        }
    }

    private void cacheFallback(Episode episode, String resolvedUrl, int token) {
        notifyStatus(
                "Compatibilità Android: preparo una copia audio locale…",
                true
        );

        io.execute(() -> {
            try {
                File file = RaiClient.downloadToInternalCache(
                        getApplicationContext(),
                        episode,
                        resolvedUrl
                );

                main.post(() -> {
                    if (generation.get() == token && currentEpisode == episode) {
                        openSource(file.getAbsolutePath(), true, token);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (generation.get() != token || currentEpisode != episode) {
                        return;
                    }

                    fail("Fallback locale fallito: " + cleanMessage(e));
                });
            }
        });
    }

    private void playPrepared() {
        if (player == null || !prepared) return;

        ensureStartedForPlayback();
        if (!requestFocus()) {
            notifyStatus("Audio temporaneamente occupato · riprova dopo la chiamata", false);
            return;
        }

        try {
            player.start();
            applySpeed();
            recordPlayHistory();
        } catch (Exception ignored) {}

        pendingResumeMs = getCurrentPositionMs();

        persistSessionNow(
                pendingResumeMs,
                getCurrentDurationMs(),
                true
        );

        notifyPlayback();
        updateMediaSessionState();
        updateNotification(true);
    }

    public void skipToNext() {
        if (currentEpisode == null && upNext.size() == 0) return;

        // Manual Up Next overrides Shuffle and ordinary sequential playback.
        Episode next = takeQueuedEpisode();
        if (next == null && currentEpisode != null && !queue.isEmpty()) {
            next = shuffleEnabled
                    ? pickNextShuffleEpisode(currentEpisode)
                    : sequentialEpisodeAfter(currentEpisode);
        }

        if (next == null) {
            notifyStatus("Fine del catalogo", false);
            updateMediaSessionState();
            return;
        }

        selectEpisode(next);
        prepareAndPlay();
    }

    private void skipToPrevious() {
        if (currentEpisode == null || queue.isEmpty()) return;

        // Comportamento tipico dei player: dopo alcuni secondi il tasto
        // precedente riporta all'inizio della puntata corrente.
        if (getCurrentPositionMs() > 5000L) {
            seekTo(0L);
            return;
        }

        int index = indexOfQueue(currentEpisode);
        if (index <= 0) {
            seekTo(0L);
            return;
        }

        Episode previous = queue.get(index - 1);
        selectEpisode(previous);
        prepareAndPlay();
    }

    private void advanceAfterCompletion(Episode completedEpisode) {
        if (completedEpisode == null) {
            notifyStatus("Fine della puntata", false);
            return;
        }

        // Consume user-defined order before falling back to Shuffle/catalog.
        Episode next = takeQueuedEpisode();
        if (next == null && !queue.isEmpty()) {
            next = shuffleEnabled
                    ? pickNextShuffleEpisode(completedEpisode)
                    : sequentialEpisodeAfter(completedEpisode);
        }

        if (next == null) {
            notifyStatus("Fine del catalogo", false);
            return;
        }

        selectEpisode(next);
        prepareAndPlay();
    }

    private Episode sequentialEpisodeAfter(Episode current) {
        int index = indexOfQueue(current);
        if (index < 0) return null;

        int next = index + 1;
        return next < queue.size() ? queue.get(next) : null;
    }

    private int indexOfQueue(Episode episode) {
        if (episode == null || episode.id == null) return -1;

        for (int i = 0; i < queue.size(); i++) {
            Episode candidate = queue.get(i);

            if (candidate != null
                    && candidate.id != null
                    && candidate.id.equals(episode.id)) {
                return i;
            }
        }

        return -1;
    }

    private void applySpeed() {
        // ExoPlayer applies speed without restarting an intentionally paused item.
        if (player == null || !prepared) return;
        try {
            player.setSpeed(speed);
        } catch (Exception ignored) {}
    }

    private void applyVolume(float focusMultiplier) {
        if (player == null) return;

        double perceptual = Math.sqrt(volumePercent / 100.0);
        float volume = (float) Math.max(
                0,
                Math.min(1, perceptual * focusMultiplier)
        );

        try {
            player.setVolume(volume);
        } catch (Exception ignored) {}
    }

    private boolean requestFocus() {
        if (audioManager == null) return false;

        if (focusRequest == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            focusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN
            )
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pause();
                        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            applyVolume(0.25f);
                        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                            applyVolume(1f);
                        }
                    })
                    .build();
        }

        try {
            return audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void abandonFocus() {
        if (audioManager != null && focusRequest != null) {
            try {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Chiusura completa del player: conserva la posizione ma non puo'
     * conservare lo stato "in riproduzione" dopo l'uscita.
     */
    public void shutdownForAppExit() {
        sleepTimer.cancel();
        final long position = getCurrentPositionMs();
        final long duration = getCurrentDurationMs();
        generation.incrementAndGet();
        persistSessionNow(position, duration, false);
        releasePlayer();
        pendingResumeMs = position;
        startedForPlayback = false;
        abandonFocus();

        // Clearing Media3 media items removes the system notification.
        if (listener != null) listener.onPlaybackState(false, false);
        stopSelf();
    }

    public void stopPlayback() {
        sleepTimer.cancel();
        long duration = getCurrentDurationMs();

        generation.incrementAndGet();
        releasePlayer();

        pendingResumeMs = 0L;

        persistSessionNow(0L, duration, false);

        abandonFocus();

        startedForPlayback = false;
        updateMediaSessionState();

        if (listener != null) {
            listener.onPlaybackState(false, false);
            listener.onPosition(
                    0L,
                    currentEpisode != null ? currentEpisode.durationMs : 0L
            );
        }

        notifyStatus("Riproduzione fermata", false);

        stopSelf();
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            player.clearCallbacks();
            player.reset();
        }
    }

    private void maybeMarkListened(long positionMs, long durationMs) {
        if (currentEpisode == null || durationMs <= 0L) return;

        long remaining = Math.max(0L, durationMs - positionMs);

        boolean reachedThreshold =
                positionMs >= Math.round(durationMs * 0.95)
                        || remaining <= 30000L;

        if (reachedThreshold) {
            markCurrentListened();
        }
    }

    private void markCurrentListened() {
        Episode episode = currentEpisode;

        if (episode == null
                || episode.id == null
                || episode.id.trim().isEmpty()) {
            return;
        }

        if (listenedIds.contains(episode.id)) return;

        listenedIds.add(episode.id);

        prefs.edit()
                .putStringSet(
                        "listened_episode_ids",
                        new HashSet<>(listenedIds)
                )
                .apply();

        if (listener != null) {
            listener.onListenedChanged(episode);
        }
    }

    private void notifyStatus(String text, boolean busy) {
        lastStatus = text == null ? "" : text;
        lastBusy = busy;

        if (listener != null) {
            listener.onStatus(lastStatus, lastBusy);
        }
    }

    private void notifyPlayback() {
        if (listener != null) {
            listener.onPlaybackState(isPlaying(), prepared);
        }
    }

    private void notifyPosition(long position, long duration) {
        if (listener != null) {
            listener.onPosition(position, duration);
        }
    }

    private void fail(String text) {
        prepared = false;

        persistSessionNow(
                getCurrentPositionMs(),
                getCurrentDurationMs(),
                false
        );

        notifyStatus(text, false);

        if (listener != null) {
            listener.onError(text);
        }

        updateMediaSessionState();
        updateNotification(false);
    }

    private void persistSessionThrottled(
            long position,
            long duration,
            boolean playing
    ) {
        long now = System.currentTimeMillis();

        if (now - lastSessionWriteAt < 2000L) return;

        lastSessionWriteAt = now;
        persistSessionNow(position, duration, playing);
    }

    private void persistSessionNow(
            long position,
            long duration,
            boolean playing
    ) {
        if (prefs == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (currentEpisode != null && currentEpisode.id != null) {
            editor.putString(KEY_SESSION_EPISODE_ID, currentEpisode.id);
            String key = KEY_EPISODE_POSITION_PREFIX + currentEpisode.id;
            // Una puntata terminata riparte dall'inizio al prossimo ascolto.
            if (duration > 0L && position >= Math.max(0L, duration - 1500L)) {
                editor.remove(key);
            } else {
                editor.putLong(key, Math.max(0L, position));
            }
        } else {
            editor.remove(KEY_SESSION_EPISODE_ID);
        }

        editor.putLong(KEY_SESSION_POSITION, Math.max(0L, position));
        editor.putLong(KEY_SESSION_DURATION, Math.max(0L, duration));
        editor.putBoolean(KEY_SESSION_WAS_PLAYING, playing);
        editor.putLong(KEY_SESSION_SAVED_AT, System.currentTimeMillis());
        editor.apply();
    }

    private void createMediaSession() {
        // External Media3 controls reuse the app's existing queue and bookmark logic.
        Player controls = new ForwardingPlayer(player.getExoPlayer()) {
            @Override public Commands getAvailableCommands() {
                return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build();
            }

            @Override public void play() {
                if (currentEpisode == null) return;
                if (prepared) playPrepared();
                else prepareAndPlay();
            }

            @Override public void pause() { AudioPlaybackService.this.pause(); }
            @Override public void stop() { stopPlayback(); }
            @Override public void seekTo(long positionMs) {
                AudioPlaybackService.this.seekTo(positionMs);
            }
            @Override public void seekToNext() { skipToNext(); }
            @Override public void seekToNextMediaItem() { skipToNext(); }
            @Override public void seekToPrevious() { skipToPrevious(); }
            @Override public void seekToPreviousMediaItem() { skipToPrevious(); }
        };
        PendingIntent open = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mediaSession = new MediaSession.Builder(this, controls)
                .setSessionActivity(open)
                .build();
    }

    private void updateMediaSessionMetadata() {
        // Metadata now come from the current Media3 MediaItem.
    }

    private void updateMediaSessionState() {
        // MediaSessionService observes ExoPlayer, without manual platform states.
    }

    private void updateNotification(boolean playing) {
        // MediaSessionService owns the sole playback notification.
    }

    private static String cleanMessage(Exception e) {
        String message = e.getMessage();

        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // "Rimuovi dalle recenti" equivale a un vero Esci.
        shutdownForAppExit();
        PlaybackExit.stopAll(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        sleepTimer.cancel();
        main.removeCallbacks(ticker);

        // Se il servizio viene distrutto, nessuna riproduzione puo' continuare.
        persistSessionNow(
                getCurrentPositionMs(),
                getCurrentDurationMs(),
                false
        );

        listener = null;

        generation.incrementAndGet();
        releasePlayer();

        io.shutdownNow();

        abandonFocus();

        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}
