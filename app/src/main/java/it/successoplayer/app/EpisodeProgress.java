package it.successoplayer.app;

/** Small, deterministic progress calculation shared by list and mini-player. */
final class EpisodeProgress {
    private EpisodeProgress() {}

    static int percent(long positionMs, long durationMs) {
        if (durationMs <= 0L) return 0;
        double fraction = Math.max(0.0, Math.min(1.0,
                Math.max(0L, positionMs) / (double) durationMs));
        return (int) Math.round(fraction * 100.0);
    }

    static int thousandths(long positionMs, long durationMs) {
        if (durationMs <= 0L) return 0;
        double fraction = Math.max(0.0, Math.min(1.0,
                Math.max(0L, positionMs) / (double) durationMs));
        return (int) Math.round(fraction * 1000.0);
    }
}
