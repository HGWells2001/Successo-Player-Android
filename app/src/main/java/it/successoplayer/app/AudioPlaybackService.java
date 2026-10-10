package it.successoplayer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.PlaybackParams;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioPlaybackService extends Service {
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
    private static final String KEY_SESSION_DURATION = "session_duration_ms";
    private static final String KEY_SESSION_WAS_PLAYING = "session_was_playing";
    private static final String KEY_SESSION_SAVED_AT = "session_saved_at";

    private static final String CHANNEL_ID = "successo_playback";
    private static final int NOTIFICATION_ID = 2407;
    private static final String ACTION_TOGGLE = "it.successoplayer.app.TOGGLE";
    private static final String ACTION_PREVIOUS = "it.successoplayer.app.PREVIOUS";
    private static final String ACTION_NEXT = "it.successoplayer.app.NEXT";
    private static final String ACTION_STOP = "it.successoplayer.app.STOP";

    public final class LocalBinder extends Binder {
        public AudioPlaybackService getService() { return AudioPlaybackService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final Random random = new Random();

    private final List<Episode> queue = new ArrayList<>();
    private final Set<String> listenedIds = new HashSet<>();

    private MediaPlayer player;
    private Episode currentEpisode;
    private Listener listener;
    private SharedPreferences prefs;

    private boolean prepared;
    private boolean fallbackTried;
    private boolean shuffleEnabled;
    private boolean startedForPlayback;

    private int volumePercent = 85;
    private float speed = 1f;
    private long pendingResumeMs;
    private long lastSessionWriteAt;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private MediaSession mediaSession;
    private Bitmap mediaArtwork;

    private String lastStatus = "Pronto";
    private boolean lastBusy;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
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

        createChannel();

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        listenedIds.clear();
        listenedIds.addAll(
                prefs.getStringSet("listened_episode_ids", Collections.emptySet())
        );

        volumePercent = prefs.getInt("volume", 85);

        int speedIndex = Math.max(
                0,
                Math.min(5, prefs.getInt("speedIndex", 2))
        );
        float[] speeds = {0.75f, 0.90f, 1.00f, 1.25f, 1.50f, 2.00f};
        speed = speeds[speedIndex];

        shuffleEnabled = prefs.getBoolean("shuffle_enabled", false);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        createMediaSession();
        updateMediaSessionMetadata();
        updateMediaSessionState();

        main.post(ticker);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
                return Math.max(0L, player.getDuration());
            } catch (Exception ignored) {}
        }

        return currentEpisode != null ? Math.max(0L, currentEpisode.durationMs) : 0L;
    }

    public void setQueue(List<Episode> episodes, boolean shuffle) {
        queue.clear();
        if (episodes != null) {
            queue.addAll(episodes);
        }
        shuffleEnabled = shuffle;
        updateMediaSessionState();
    }

    public void setShuffleEnabled(boolean enabled) {
        shuffleEnabled = enabled;
        updateMediaSessionState();
    }

    public void selectEpisode(Episode episode) {
        generation.incrementAndGet();
        releasePlayer();

        currentEpisode = episode;
        prepared = false;
        fallbackTried = false;
        pendingResumeMs = 0L;

        persistSessionNow(0L, episode != null ? episode.durationMs : 0L, false);

        if (listener != null) {
            listener.onEpisode(episode);
            listener.onPosition(0L, episode != null ? episode.durationMs : 0L);
            listener.onPlaybackState(false, false);
        }

        notifyStatus("Puntata selezionata · premi ▶", false);
        updateMediaSessionMetadata();
        updateMediaSessionState();

        if (startedForPlayback) {
            updateNotification(false);
        }
    }

    public void restoreSession(Episode episode, long positionMs, boolean resumePlayback) {
        if (episode == null) return;

        generation.incrementAndGet();
        releasePlayer();

        currentEpisode = episode;
        prepared = false;
        fallbackTried = false;

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
        if (player == null || !prepared) return;

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
        // mentre RaiPlay Sound sta ancora risolvendo l'URL, il motore audio
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

        persistSessionNow(
                pendingResumeMs,
                episode.durationMs,
                true
        );

        notifyStatus("Risolvo l'audio RaiPlay Sound…", true);

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
            player = new MediaPlayer();

            player.setWakeMode(
                    getApplicationContext(),
                    PowerManager.PARTIAL_WAKE_LOCK
            );

            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
            );

            player.setOnPreparedListener(mp -> {
                if (generation.get() != token) return;

                prepared = true;

                applyVolume(1f);
                applySpeed();
                requestFocus();

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
                } catch (Exception ignored) {}

                pendingResumeMs = resumeAt;

                notifyStatus(
                        localFile
                                ? "Riproduzione da cache locale"
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

                // Facciamo uscire completamente il callback del MediaPlayer prima
                // di sostituire il player con la puntata successiva.
                main.post(() -> advanceAfterCompletion(completedEpisode));
            });

            player.setOnErrorListener((mp, what, extra) -> {
                if (!fallbackTried && currentEpisode != null) {
                    fallbackTried = true;
                    cacheFallback(currentEpisode, source, token);
                    return true;
                }

                fail("Il motore audio Android non riesce a riprodurre questa puntata.");
                return true;
            });

            if (localFile) {
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
        requestFocus();

        try {
            player.start();
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

    private void skipToNext() {
        if (currentEpisode == null || queue.isEmpty()) return;

        Episode next = shuffleEnabled
                ? randomEpisodeExcluding(currentEpisode)
                : sequentialEpisodeAfter(currentEpisode);

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
        if (completedEpisode == null || queue.isEmpty()) {
            notifyStatus("Fine della puntata", false);
            return;
        }

        Episode next = shuffleEnabled
                ? randomEpisodeExcluding(completedEpisode)
                : sequentialEpisodeAfter(completedEpisode);

        if (next == null) {
            notifyStatus("Fine del catalogo", false);
            return;
        }

        selectEpisode(next);
        prepareAndPlay();
    }

    private Episode randomEpisodeExcluding(Episode excluded) {
        if (queue.isEmpty()) return null;
        if (queue.size() == 1) return queue.get(0);

        int excludedIndex = indexOfQueue(excluded);

        int available = queue.size() - (excludedIndex >= 0 ? 1 : 0);
        if (available <= 0) return null;

        int pick = random.nextInt(available);

        if (excludedIndex >= 0 && pick >= excludedIndex) {
            pick++;
        }

        return queue.get(pick);
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
        if (player == null || !prepared) return;

        try {
            PlaybackParams params = player.getPlaybackParams();
            params.setSpeed(speed);
            params.setPitch(1f);
            player.setPlaybackParams(params);
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
            player.setVolume(volume, volume);
        } catch (Exception ignored) {}
    }

    private void requestFocus() {
        if (audioManager == null) return;

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
            audioManager.requestAudioFocus(focusRequest);
        } catch (Exception ignored) {}
    }

    private void abandonFocus() {
        if (audioManager != null && focusRequest != null) {
            try {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } catch (Exception ignored) {}
        }
    }

    public void stopPlayback() {
        long duration = getCurrentDurationMs();

        generation.incrementAndGet();
        releasePlayer();

        pendingResumeMs = 0L;

        persistSessionNow(0L, duration, false);

        abandonFocus();

        startedForPlayback = false;
        updateMediaSessionState();

        stopForeground(STOP_FOREGROUND_REMOVE);

        NotificationManager nm = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE
        );
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }

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
            try {
                player.reset();
            } catch (Exception ignored) {}

            try {
                player.release();
            } catch (Exception ignored) {}

            player = null;
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
        mediaSession = new MediaSession(this, "SuccessoPlayerMediaSession");
        mediaSession.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() {
                if (currentEpisode == null) return;
                if (player != null && prepared) {
                    playPrepared();
                } else {
                    prepareAndPlay();
                }
            }

            @Override public void onPause() {
                pause();
            }

            @Override public void onStop() {
                stopPlayback();
            }

            @Override public void onSeekTo(long pos) {
                seekTo(pos);
            }

            @Override public void onSkipToNext() {
                skipToNext();
            }

            @Override public void onSkipToPrevious() {
                skipToPrevious();
            }
        }, main);

        mediaArtwork = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.logo_successo
        );

        mediaSession.setActive(true);
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null) return;

        if (currentEpisode == null) {
            mediaSession.setMetadata(null);
            return;
        }

        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentEpisode.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Successo · Storie e voci dal Novecento")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "RaiPlay Sound")
                .putLong(
                        MediaMetadata.METADATA_KEY_DURATION,
                        Math.max(0L, getCurrentDurationMs())
                );

        if (mediaArtwork != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mediaArtwork);
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, mediaArtwork);
        }

        mediaSession.setMetadata(metadata.build());
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;

        int state;
        float playbackSpeed;

        if (currentEpisode == null) {
            state = PlaybackState.STATE_NONE;
            playbackSpeed = 0f;
        } else if (isPlaying()) {
            state = PlaybackState.STATE_PLAYING;
            playbackSpeed = speed;
        } else if (startedForPlayback && !prepared) {
            state = PlaybackState.STATE_BUFFERING;
            playbackSpeed = 0f;
        } else if (prepared || startedForPlayback) {
            state = PlaybackState.STATE_PAUSED;
            playbackSpeed = 0f;
        } else {
            state = PlaybackState.STATE_STOPPED;
            playbackSpeed = 0f;
        }

        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;

        if (!queue.isEmpty()) {
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        }

        PlaybackState.Builder builder = new PlaybackState.Builder()
                .setActions(actions)
                .setState(
                        state,
                        Math.max(0L, getCurrentPositionMs()),
                        playbackSpeed,
                        SystemClock.elapsedRealtime()
                );

        mediaSession.setPlaybackState(builder.build());
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE
        );

        if (nm == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Riproduzione audio",
                NotificationManager.IMPORTANCE_LOW
        );

        channel.setDescription(
                "Controlli di riproduzione di Successo Player"
        );
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        nm.createNotificationChannel(channel);
    }

    private void updateNotification(boolean playing) {
        if (currentEpisode == null) return;

        // Prima che sia iniziata davvero una sessione audio non serve mostrare
        // una notifica soltanto perché l'utente ha selezionato una riga.
        if (!startedForPlayback && !playing) return;

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

        PendingIntent content = PendingIntent.getActivity(
                this,
                10,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Intent previous = new Intent(
                this,
                AudioPlaybackService.class
        ).setAction(ACTION_PREVIOUS);

        PendingIntent previousPi = PendingIntent.getService(
                this,
                11,
                previous,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggle = new Intent(
                this,
                AudioPlaybackService.class
        ).setAction(ACTION_TOGGLE);

        PendingIntent togglePi = PendingIntent.getService(
                this,
                12,
                toggle,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Intent next = new Intent(
                this,
                AudioPlaybackService.class
        ).setAction(ACTION_NEXT);

        PendingIntent nextPi = PendingIntent.getService(
                this,
                13,
                next,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stop = new Intent(
                this,
                AudioPlaybackService.class
        ).setAction(ACTION_STOP);

        PendingIntent stopPi = PendingIntent.getService(
                this,
                14,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Action previousAction = new Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "Precedente",
                previousPi
        ).build();

        Notification.Action playAction = new Notification.Action.Builder(
                playing
                        ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play,
                playing ? "Pausa" : "Riproduci",
                togglePi
        ).build();

        Notification.Action nextAction = new Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "Successiva",
                nextPi
        ).build();

        Notification.Action stopAction = new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Ferma",
                stopPi
        ).build();

        updateMediaSessionMetadata();
        updateMediaSessionState();

        Notification.Builder notificationBuilder = new Notification.Builder(
                this,
                CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(currentEpisode.title)
                .setContentText(
                        playing
                                ? "Successo · in riproduzione"
                                : "Successo · in pausa"
                )
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setOngoing(startedForPlayback)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(previousAction)
                .addAction(playAction)
                .addAction(nextAction)
                .addAction(stopAction);

        if (mediaArtwork != null) {
            notificationBuilder.setLargeIcon(mediaArtwork);
        }

        if (mediaSession != null) {
            notificationBuilder.setStyle(
                    new Notification.MediaStyle()
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0, 1, 2)
            );
        }

        Notification notification = notificationBuilder.build();

        // Anche in pausa manteniamo il servizio foreground dopo che una
        // riproduzione è iniziata. Questo rende molto più robusto il ritorno
        // dall'app switcher e permette di riprendere dalla notifica.
        if (startedForPlayback) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE
            );
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private static String cleanMessage(Exception e) {
        String message = e.getMessage();

        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        persistSessionNow(
                getCurrentPositionMs(),
                getCurrentDurationMs(),
                isPlaying()
        );

        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(ticker);

        persistSessionNow(
                getCurrentPositionMs(),
                getCurrentDurationMs(),
                isPlaying()
        );

        listener = null;

        generation.incrementAndGet();
        releasePlayer();

        io.shutdownNow();

        abandonFocus();

        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {}
            mediaSession = null;
        }

        if (mediaArtwork != null) {
            try {
                mediaArtwork.recycle();
            } catch (Exception ignored) {}
            mediaArtwork = null;
        }

        super.onDestroy();
    }
}
