package it.successoplayer.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.TimeZone;

public final class AstronomyScheduler {
    private static final int REQUEST_CODE = 2401;
    private static final int NOTIFICATION_HOUR = 7;
    private static final int NOTIFICATION_MINUTE = 0;
    private static final TimeZone ROME = TimeZone.getTimeZone("Europe/Rome");

    private AstronomyScheduler() {}

    public static void cancel(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = PendingIntent.getBroadcast(
                app,
                REQUEST_CODE,
                new Intent(app, DailyAstronomyReceiver.class).setAction(DailyAstronomyReceiver.ACTION_NOTIFY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(pi);
        pi.cancel();
    }

    public static void schedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar now = Calendar.getInstance(ROME);
        Calendar next = Calendar.getInstance(ROME);
        next.set(Calendar.HOUR_OF_DAY, NOTIFICATION_HOUR);
        next.set(Calendar.MINUTE, NOTIFICATION_MINUTE);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);

        PendingIntent pi = PendingIntent.getBroadcast(
                app,
                REQUEST_CODE,
                new Intent(app, DailyAstronomyReceiver.class).setAction(DailyAstronomyReceiver.ACTION_NOTIFY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
    }
}
