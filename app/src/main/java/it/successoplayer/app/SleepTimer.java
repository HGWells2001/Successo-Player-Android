package it.successoplayer.app;

/**
 * Sleep timer policy. Millisecond countdowns use Android elapsedRealtime at
 * call sites, not wall time, so clock/timezone changes do not move the timer.
 * The timer is tied to the playback service's lifetime, not the Activity.
 */
final class SleepTimer {
    private long deadlineElapsedMs;
    private String finishEpisodeId;

    void startMinutes(int minutes, long nowElapsedMs) {
        if (minutes < 1 || minutes > 180) {
            throw new IllegalArgumentException("Minutes must be 1..180");
        }
        deadlineElapsedMs = nowElapsedMs + minutes * 60_000L;
        finishEpisodeId = null;
    }

    boolean finishAtEnd(String episodeId) {
        if (episodeId == null || episodeId.trim().isEmpty()) return false;
        deadlineElapsedMs = 0L;
        finishEpisodeId = episodeId;
        return true;
    }

    boolean isActive() {
        return deadlineElapsedMs > 0L || finishEpisodeId != null;
    }

    boolean isEndOfEpisode() {
        return finishEpisodeId != null;
    }

    long remainingMs(long nowElapsedMs) {
        if (deadlineElapsedMs == 0L) return 0L;
        return Math.max(0L, deadlineElapsedMs - nowElapsedMs);
    }

    boolean isDue(long nowElapsedMs) {
        return deadlineElapsedMs > 0L && nowElapsedMs >= deadlineElapsedMs;
    }

    boolean consumeEpisodeCompletion(String episodeId) {
        if (finishEpisodeId == null || !finishEpisodeId.equals(episodeId)) return false;
        cancel();
        return true;
    }

    boolean cancelIfDifferentEpisode(String episodeId) {
        if (finishEpisodeId == null || finishEpisodeId.equals(episodeId)) return false;
        cancel();
        return true;
    }

    void cancel() {
        deadlineElapsedMs = 0L;
        finishEpisodeId = null;
    }
}
