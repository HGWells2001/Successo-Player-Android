package it.successoplayer.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Sintonizzazione su Rai Radio 1 nei giorni feriali, orario di Roma. */
public final class LiveRadioScheduler {
    static final String PREFS = "successo_player";
    static final String PREF_ENABLED = "live_radio_auto_enabled";
    static final String ACTION_START = "it.successoplayer.app.RADIO_START_0535";
    static final String ACTION_STOP = "it.successoplayer.app.RADIO_STOP_0600";
    static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalTime START = LocalTime.of(5, 35);
    private static final LocalTime END = LocalTime.of(6, 0);

    private LiveRadioScheduler() {}

    public static boolean enabled(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getBoolean(PREF_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, enabled).apply();
        if (enabled) {
            schedule(context);
        } else {
            cancel(context);
            context.stopService(new Intent(context, LiveRadioService.class));
        }
    }

    public static boolean canSchedulePrecisely(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return false;
        return Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms();
    }

    public static boolean inBroadcastWindow() {
        ZonedDateTime now = ZonedDateTime.now(ROME);
        DayOfWeek day = now.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                && !now.toLocalTime().isBefore(START)
                && now.toLocalTime().isBefore(END);
    }

    private static ZonedDateTime nextOccurrence(LocalTime hour, ZonedDateTime now) {
        ZonedDateTime candidate = now.toLocalDate().atTime(hour).atZone(ROME);
        while (!candidate.isAfter(now)
                || candidate.getDayOfWeek() == DayOfWeek.SATURDAY
                || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.toLocalDate().plusDays(1).atTime(hour).atZone(ROME);
        }
        return candidate;
    }

    private static PendingIntent pending(Context context, String action, int requestCode) {
        Intent broadcast = new Intent(context, LiveRadioReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, broadcast,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleOne(Context context, String action, int requestCode,
                                    LocalTime at, ZonedDateTime now) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        PendingIntent pi = pending(context, action, requestCode);
        long millis = nextOccurrence(at, now).toInstant().toEpochMilli();
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                if (canSchedulePrecisely(context)) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi);
                } else {
                    // Permesso non concesso: approssimazione, non garantisce le 05:35.
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi);
                }
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, millis, pi);
            }
        } catch (SecurityException denied) {
            if (Build.VERSION.SDK_INT >= 23) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi);
            }
        }
    }

    public static void schedule(Context context) {
        if (!enabled(context)) {
            cancel(context);
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(ROME);
        scheduleOne(context, ACTION_START, 5305, START, now);
        scheduleOne(context, ACTION_STOP, 6000, END, now);
    }

    public static void cancel(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        alarm.cancel(pending(context, ACTION_START, 5305));
        alarm.cancel(pending(context, ACTION_STOP, 6000));
    }
}
