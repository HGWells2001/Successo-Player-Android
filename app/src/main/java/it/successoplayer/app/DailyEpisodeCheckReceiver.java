package it.successoplayer.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

public final class DailyEpisodeCheckReceiver extends BroadcastReceiver {
    public static final String ACTION_CHECK = "it.successoplayer.app.ACTION_DAILY_EPISODE_CHECK";

    private static final String CHANNEL_ID = "successo_new_episodes";
    private static final int NOTIFICATION_ID = 2501;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            EpisodeCheckScheduler.schedule(context);
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context app = context.getApplicationContext();

        Thread worker = new Thread(() -> {
            try {
                checkForNewEpisodes(app);
            } catch (Exception ignored) {
                // Network or RaiPlay Sound temporarily unavailable: retry at the next daily check.
            } finally {
                EpisodeCheckScheduler.schedule(app);
                pendingResult.finish();
            }
        }, "successo-episode-check");
        worker.start();
    }

    private static void checkForNewEpisodes(Context context) throws Exception {
        RaiClient.Catalog catalog = RaiClient.loadCatalog();
        List<Episode> episodes = catalog.episodes;
        if (episodes == null || episodes.isEmpty()) return;

        Episode latest = episodes.get(0);
        SharedPreferences prefs = EpisodeWatchState.prefs(context);
        String previousId = EpisodeWatchState.lastId(context);
        String previousDate = EpisodeWatchState.lastDate(context);

        // First check after installation: establish a baseline, do not announce old episodes.
        if (previousId == null || previousId.isEmpty()) {
            EpisodeWatchState.markLatestSeen(context, latest);
            return;
        }

        if (previousId.equals(latest.id)) return;

        int newCount = countNewEpisodes(episodes, previousId, previousDate);
        if (newCount <= 0) {
            // The catalogue may have been reordered or pruned. Move the baseline without a false alert.
            EpisodeWatchState.markLatestSeen(context, latest);
            return;
        }

        if (showNotification(context, latest, newCount)) {
            EpisodeWatchState.markLatestSeen(context, latest);
        }
    }

    private static int countNewEpisodes(List<Episode> episodes, String previousId, String previousDate) {
        for (int i = 0; i < episodes.size(); i++) {
            if (previousId.equals(episodes.get(i).id)) {
                return i;
            }
        }

        Episode latest = episodes.get(0);
        String latestDate = latest.dateIso == null ? "" : latest.dateIso;
        if (previousDate == null || previousDate.isEmpty()) return 1;
        return latestDate.compareTo(previousDate) > 0 ? 1 : 0;
    }

    private static boolean showNotification(Context context, Episode latest, int newCount) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nuove puntate di Successo",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Avvisa quando RaiPlay Sound pubblica una nuova puntata di Successo");
        nm.createNotificationChannel(channel);

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                2502,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = newCount == 1
                ? "Nuova puntata di Successo"
                : newCount + " nuove puntate di Successo";

        String date = latest.dateDisplay == null ? "" : latest.dateDisplay.trim();
        String episodeTitle = latest.title == null || latest.title.trim().isEmpty()
                ? "Nuova puntata disponibile"
                : latest.title.trim();
        String summary = date.isEmpty() ? episodeTitle : episodeTitle + " · " + date;

        String bigText;
        if (newCount == 1) {
            bigText = episodeTitle + (date.isEmpty() ? "" : "\n" + date) + "\nTocca per aprire Successo Player.";
        } else {
            bigText = "Sono disponibili " + newCount + " nuove puntate.\n"
                    + "Ultima: " + episodeTitle
                    + (date.isEmpty() ? "" : "\n" + date)
                    + "\nTocca per aprire Successo Player.";
        }

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(new Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .build();

        nm.notify(NOTIFICATION_ID, notification);
        return true;
    }
}
