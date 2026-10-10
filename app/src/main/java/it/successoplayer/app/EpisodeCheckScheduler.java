package it.successoplayer.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.TimeZone;

public final class EpisodeCheckScheduler {
    private static final int REQUEST_CODE = 2501;
    private static final int CHECK_HOUR = 8;
    private static final int CHECK_MINUTE = 15;
    private static final TimeZone ROME = TimeZone.getTimeZone("Europe/Rome");

    private EpisodeCheckScheduler() {}

    public static void schedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar now = Calendar.getInstance(ROME);
        Calendar next = Calendar.getInstance(ROME);
        next.set(Calendar.HOUR_OF_DAY, CHECK_HOUR);
        next.set(Calendar.MINUTE, CHECK_MINUTE);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);

        Intent intent = new Intent(app, DailyEpisodeCheckReceiver.class)
                .setAction(DailyEpisodeCheckReceiver.ACTION_CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(
                app,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
    }
}
