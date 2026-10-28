package it.successoplayer.app;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Weekday programme window in Europe/Rome, including summer/winter time. */
final class LiveProgramStatus {
    static final ZoneId ROME = ZoneId.of("Europe/Rome");
    static final LocalTime START = LocalTime.of(5, 35);
    static final LocalTime END = LocalTime.of(6, 0);

    static final class Snapshot {
        final boolean inWindow;
        final ZonedDateTime nextStart;
        final long secondsRemaining;
        Snapshot(boolean inWindow, ZonedDateTime nextStart, long secondsRemaining) {
            this.inWindow = inWindow;
            this.nextStart = nextStart;
            this.secondsRemaining = Math.max(0L, secondsRemaining);
        }
    }
    private LiveProgramStatus() {}

    static Snapshot at(ZonedDateTime now) {
        ZonedDateTime romeNow = now.withZoneSameInstant(ROME);
        LocalDate date = romeNow.toLocalDate();
        ZonedDateTime beginning = date.atTime(START).atZone(ROME);
        ZonedDateTime ending = date.atTime(END).atZone(ROME);
        if (weekday(date.getDayOfWeek()) && !romeNow.isBefore(beginning)
                && romeNow.isBefore(ending)) {
            return new Snapshot(true, null, secondsUntil(romeNow, ending));
        }
        ZonedDateTime next = beginning;
        while (!next.isAfter(romeNow) || !weekday(next.getDayOfWeek())) {
            next = next.toLocalDate().plusDays(1).atTime(START).atZone(ROME);
        }
        return new Snapshot(false, next, secondsUntil(romeNow, next));
    }

    private static boolean weekday(DayOfWeek day) {
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private static long secondsUntil(ZonedDateTime now, ZonedDateTime target) {
        long millis = Duration.between(now, target).toMillis();
        return Math.max(0L, (millis + 999L) / 1000L);
    }

    static String countdown(long seconds) {
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        String clock = String.format(Locale.ITALY, "%02d:%02d:%02d", hours, minutes, secs);
        return days > 0L ? days + " g " + clock : clock;
    }

    static String summary(Snapshot s) {
        if (s.inWindow) return "🔴 Fascia in corso · termina tra " + countdown(s.secondsRemaining);
        String day;
        switch (s.nextStart.getDayOfWeek()) {
            case MONDAY: day = "lun"; break;
            case TUESDAY: day = "mar"; break;
            case WEDNESDAY: day = "mer"; break;
            case THURSDAY: day = "gio"; break;
            case FRIDAY: day = "ven"; break;
            default: throw new IllegalStateException("Next window is not a weekday");
        }
        return "Prossima diretta " + day + " 05:35 · tra " + countdown(s.secondsRemaining);
    }
}
