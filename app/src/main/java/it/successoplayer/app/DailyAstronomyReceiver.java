package it.successoplayer.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public final class DailyAstronomyReceiver extends BroadcastReceiver {
    public static final String ACTION_NOTIFY = "it.successoplayer.app.ACTION_DAILY_ASTRONOMY";

    private static final String CHANNEL_ID = "saxa_rubra_astronomy";
    private static final int NOTIFICATION_ID = 2401;
    private static final TimeZone ROME = TimeZone.getTimeZone("Europe/Rome");

    // Centro di Produzione RAI Saxa Rubra, Roma.
    private static final double LATITUDE = 41.97954;
    private static final double LONGITUDE = 12.49511;

    private static final double SYNODIC_MONTH = 29.530588853;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AstronomyScheduler.schedule(context);
            return;
        }

        try {
            showAstronomyNotification(context);
        } finally {
            // One-shot alarm: reschedule every day so DST changes in Rome are handled correctly.
            AstronomyScheduler.schedule(context);
        }
    }

    private static void showAstronomyNotification(Context context) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Calendar today = Calendar.getInstance(ROME);
        double sunrise = solarTime(today, true);
        double sunset = solarTime(today, false);
        MoonInfo moon = moonInfo(today);

        String sunLine = "Alba " + formatHour(sunrise) + " · Tramonto " + formatHour(sunset);
        String moonLine = moon.phase + " · illuminazione " + moon.illumination + "%";
        String bigText = "Saxa Rubra\n" + sunLine + "\n" + moonLine;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Alba, tramonto e Luna",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Riepilogo astronomico giornaliero per Saxa Rubra");
        nm.createNotificationChannel(channel);

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                2402,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Saxa Rubra · oggi")
                .setContentText(sunLine + " · " + moon.phase)
                .setStyle(new Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .build();

        nm.notify(NOTIFICATION_ID, notification);
    }

    private static double solarTime(Calendar date, boolean sunrise) {
        int dayOfYear = date.get(Calendar.DAY_OF_YEAR);
        double longitudeHour = LONGITUDE / 15.0;
        double approximate = dayOfYear + (((sunrise ? 6.0 : 18.0) - longitudeHour) / 24.0);

        double meanAnomaly = (0.9856 * approximate) - 3.289;
        double trueLongitude = normalizeDegrees(
                meanAnomaly
                        + (1.916 * sinDeg(meanAnomaly))
                        + (0.020 * sinDeg(2.0 * meanAnomaly))
                        + 282.634
        );

        double rightAscension = normalizeDegrees(
                Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(trueLongitude))))
        );
        double longitudeQuadrant = Math.floor(trueLongitude / 90.0) * 90.0;
        double raQuadrant = Math.floor(rightAscension / 90.0) * 90.0;
        rightAscension = (rightAscension + longitudeQuadrant - raQuadrant) / 15.0;

        double sinDeclination = 0.39782 * sinDeg(trueLongitude);
        double cosDeclination = Math.cos(Math.asin(sinDeclination));

        double zenith = 90.833; // official sunrise/sunset incl. atmospheric refraction
        double cosHourAngle = (
                cosDeg(zenith) - (sinDeclination * sinDeg(LATITUDE))
        ) / (cosDeclination * cosDeg(LATITUDE));

        // Saxa Rubra never reaches polar-day/night conditions, but keep the calculation safe.
        cosHourAngle = Math.max(-1.0, Math.min(1.0, cosHourAngle));

        double hourAngle = Math.toDegrees(Math.acos(cosHourAngle));
        if (sunrise) hourAngle = 360.0 - hourAngle;
        hourAngle /= 15.0;

        double localMeanTime = hourAngle + rightAscension - (0.06571 * approximate) - 6.622;
        double utcHours = normalizeHours(localMeanTime - longitudeHour);

        Calendar localNoon = Calendar.getInstance(ROME);
        localNoon.clear();
        localNoon.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), 12, 0, 0);
        double offsetHours = ROME.getOffset(localNoon.getTimeInMillis()) / 3600000.0;

        return normalizeHours(utcHours + offsetHours);
    }

    private static MoonInfo moonInfo(Calendar localDate) {
        Calendar sample = Calendar.getInstance(ROME);
        sample.clear();
        sample.set(
                localDate.get(Calendar.YEAR),
                localDate.get(Calendar.MONTH),
                localDate.get(Calendar.DAY_OF_MONTH),
                12, 0, 0
        );

        GregorianCalendar epoch = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ITALY);
        epoch.clear();
        epoch.set(2000, Calendar.JANUARY, 6, 18, 14, 0); // known new moon reference

        double days = (sample.getTimeInMillis() - epoch.getTimeInMillis()) / 86400000.0;
        double age = days % SYNODIC_MONTH;
        if (age < 0) age += SYNODIC_MONTH;

        String phase;
        if (age < 1.84566 || age >= 27.68493) phase = "Luna nuova";
        else if (age < 5.53699) phase = "Falce crescente";
        else if (age < 9.22831) phase = "Primo quarto";
        else if (age < 12.91963) phase = "Gibbosa crescente";
        else if (age < 16.61096) phase = "Luna piena";
        else if (age < 20.30228) phase = "Gibbosa calante";
        else if (age < 23.99361) phase = "Ultimo quarto";
        else phase = "Falce calante";

        int illumination = (int) Math.round((1.0 - Math.cos((2.0 * Math.PI * age) / SYNODIC_MONTH)) * 50.0);
        return new MoonInfo(phase, Math.max(0, Math.min(100, illumination)));
    }

    private static String formatHour(double value) {
        int totalMinutes = (int) Math.round(normalizeHours(value) * 60.0);
        totalMinutes %= 24 * 60;
        if (totalMinutes < 0) totalMinutes += 24 * 60;
        int hour = totalMinutes / 60;
        int minute = totalMinutes % 60;
        return String.format(Locale.ITALY, "%02d:%02d", hour, minute);
    }

    private static double sinDeg(double value) { return Math.sin(Math.toRadians(value)); }
    private static double cosDeg(double value) { return Math.cos(Math.toRadians(value)); }

    private static double normalizeDegrees(double value) {
        double r = value % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    private static double normalizeHours(double value) {
        double r = value % 24.0;
        return r < 0 ? r + 24.0 : r;
    }

    private static final class MoonInfo {
        final String phase;
        final int illumination;

        MoonInfo(String phase, int illumination) {
            this.phase = phase;
            this.illumination = illumination;
        }
    }
}
