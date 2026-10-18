package it.successoplayer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public final class LiveRadioService extends Service {
    public static final String ACTION_START_AUTO = "it.successoplayer.app.LIVE_AUTO";
    public static final String ACTION_STOP = "it.successoplayer.app.LIVE_STOP";
    private static final String STREAM = "https://icestreaming.rai.it/1.mp3";
    private static final String CHANNEL = "successo_radio1_live";
    private static final int NOTIFICATION_ID = 2601;
    private static final int MAX_RETRIES = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean playingAuto;
    private boolean active;
    private int attempts;
    private final Runnable retry = this::prepareStream;
    private final Runnable checkWindow = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (playingAuto && !LiveRadioScheduler.inBroadcastWindow()) {
                shutdown();
            } else {
                handler.postDelayed(this, 15000L);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Diretta automatica Rai Radio 1", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Ascolto dal lunedi' al venerdi' alle 05:35");
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            // La notifica spegne anche l'interruttore e i prossimi allarmi.
            getSharedPreferences(LiveRadioScheduler.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(LiveRadioScheduler.PREF_ENABLED, false).apply();
            LiveRadioScheduler.cancel(this);
            shutdown();
            return START_NOT_STICKY;
        }
        if (!ACTION_START_AUTO.equals(action)) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (!LiveRadioScheduler.enabled(this)
                || !LiveRadioScheduler.inBroadcastWindow()) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;
        playingAuto = true;
        active = true;
        attempts = 0;

        // startForeground subito: Android richiede una notifica tempestiva.
        startForeground(NOTIFICATION_ID, createNotification("Connessione alla diretta…"));
        if (!requestFocus()) {
            shutdown();
            return START_NOT_STICKY;
        }
        handler.post(checkWindow);
        prepareStream();
        return START_NOT_STICKY;
    }

    private Notification createNotification(String subtitle) {
        PendingIntent open = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, LiveRadioService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Rai Radio 1 · Diretta")
                .setContentText(subtitle)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "Ferma diretta", stop).build())
                .build();
    }

    private boolean requestFocus() {
        if (audioManager == null) return false;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS) shutdown();
                        else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && player != null) {
                            try { player.pause(); } catch (Exception ignored) {}
                        } else if (change == AudioManager.AUDIOFOCUS_GAIN && player != null) {
                            try { player.start(); } catch (Exception ignored) {}
                        }
                    })
                    .build();
            return audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return false;
    }

    private void prepareStream() {
        if (!active) return;
        if (playingAuto && !LiveRadioScheduler.inBroadcastWindow()) {
            shutdown();
            return;
        }
        releasePlayer();
        attempts++;
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            next.setOnPreparedListener(mp -> {
                if (!active || mp != player) return;
                if (playingAuto && !LiveRadioScheduler.inBroadcastWindow()) {
                    shutdown();
                    return;
                }
                try {
                    mp.start();
                    NotificationManager manager = (NotificationManager)
                            getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID,
                                createNotification("🔴 Successo · Storie e voci dal Novecento"));
                    }
                } catch (RuntimeException e) {
                    onStreamError();
                }
            });
            next.setOnErrorListener((mp, what, extra) -> {
                if (mp == player) onStreamError();
                return true;
            });
            next.setOnCompletionListener(mp -> {
                if (mp == player) onStreamError();
            });
            next.setDataSource(STREAM);
            next.prepareAsync();
        } catch (Exception error) {
            onStreamError();
        }
    }

    private void onStreamError() {
        if (!active) return;
        releasePlayer();
        if (attempts < MAX_RETRIES && (!playingAuto || LiveRadioScheduler.inBroadcastWindow())) {
            handler.removeCallbacks(retry);
            handler.postDelayed(retry, 10000L);
        } else {
            shutdown();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void shutdown() {
        active = false;
        handler.removeCallbacks(retry);
        handler.removeCallbacks(checkWindow);
        releasePlayer();
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Anche la diretta deve sparire quando l'app viene rimossa.
        shutdown();
        PlaybackExit.stopAll(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        active = false;
        handler.removeCallbacks(retry);
        handler.removeCallbacks(checkWindow);
        releasePlayer();
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
