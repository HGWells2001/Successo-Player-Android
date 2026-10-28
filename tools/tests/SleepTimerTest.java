package it.successoplayer.app;

public final class SleepTimerTest {
    private static void ok(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SleepTimer timer = new SleepTimer();
        ok(!timer.isActive(), "disabled initially");
        ok(!timer.isDue(123L), "disabled does not expire");
        timer.startMinutes(15, 42_000L);
        ok(timer.isActive(), "countdown active");
        ok(!timer.isEndOfEpisode(), "not episode end");
        ok(timer.remainingMs(42_000L) == 900_000L, "correct duration");
        ok(timer.remainingMs(42_000L + 899_999L) == 1L, "before deadline");
        ok(!timer.isDue(42_000L + 899_999L), "not due early");
        ok(timer.isDue(42_000L + 900_000L), "due on time");
        ok(timer.remainingMs(42_000L + 999_999L) == 0L, "no negative time");
        ok(!timer.cancelIfDifferentEpisode("some-other-id"), "timed timer survives episode change");
        ok(timer.isDue(42_000L + 900_000L), "timer still due");
        timer.cancel();
        ok(!timer.isActive() && !timer.isDue(Long.MAX_VALUE), "cancel clears all");

        ok(timer.finishAtEnd("EP-01"), "episode-end accepted");
        ok(timer.isActive() && timer.isEndOfEpisode(), "end-of-episode mode");
        ok(!timer.isDue(Long.MAX_VALUE), "end timer never expires by clock");
        ok(!timer.consumeEpisodeCompletion("EP-02"), "other episode cannot finish timer");
        ok(timer.isActive(), "other completion does not clear end mode");
        ok(timer.consumeEpisodeCompletion("EP-01"), "correct episode completes");
        ok(!timer.isActive(), "one shot consumed");
        ok(!timer.consumeEpisodeCompletion("EP-01"), "cannot consume twice");

        ok(timer.finishAtEnd("EP-03"), "rearm end timer");
        ok(!timer.cancelIfDifferentEpisode("EP-03"), "same episode retains end timer");
        ok(timer.cancelIfDifferentEpisode("EP-04"), "new episode clears old target");
        ok(!timer.isActive(), "cancelled after selection change");
        ok(!timer.finishAtEnd(""), "empty episode rejected");
        ok(!timer.finishAtEnd(null), "null episode rejected");
        timer.startMinutes(60, 7_000L);
        timer.startMinutes(30, 8_000L);
        ok(timer.remainingMs(8_000L) == 1_800_000L, "new timer replaces old");
        for (int n : new int[]{0, -2, 181}) {
            boolean rejected = false;
            try { timer.startMinutes(n, 0L); } catch (IllegalArgumentException e) {
                rejected = true;
            }
            ok(rejected, "reject invalid duration " + n);
        }
        ok(timer.isActive(), "invalid choice does not remove current timer");
        System.out.println("OK: timed and episode-end sleep timer, replacement, cancellation and monotonic deadline.");
    }
}
