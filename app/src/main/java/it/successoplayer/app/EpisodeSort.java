package it.successoplayer.app;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sort the visible catalog without mutating the underlying playback catalog. */
final class EpisodeSort {
    static final int NEWEST = 0;
    static final int OLDEST = 1;
    static final int LONGEST = 2;
    static final int PROGRESS = 3;
    static final String[] LABELS = {
            "Più recenti", "Meno recenti", "Durata maggiore", "Più avanzate"
    };

    interface ProgressProvider {
        int percent(Episode episode);
    }

    private EpisodeSort() {}

    static int validMode(int mode) {
        return mode >= NEWEST && mode <= PROGRESS ? mode : NEWEST;
    }

    static String label(int mode) {
        return LABELS[validMode(mode)];
    }

    static void sort(List<Episode> episodes, int mode, ProgressProvider progress) {
        if (episodes == null || episodes.size() < 2) return;
        final int ordering = validMode(mode);
        final Map<String, Integer> percentCache = new HashMap<>();
        if (ordering == PROGRESS && progress != null) {
            for (Episode ep : episodes) {
                percentCache.put(ep.id, Math.max(0, Math.min(100, progress.percent(ep))));
            }
        }
        final Comparator<Episode> recent = (a, b) -> date(b).compareTo(date(a));
        switch (ordering) {
            case OLDEST:
                Collections.sort(episodes, (a, b) -> date(a).compareTo(date(b)));
                break;
            case LONGEST:
                Collections.sort(episodes, (a, b) -> {
                    int duration = Long.compare(b.durationMs, a.durationMs);
                    return duration != 0 ? duration : recent.compare(a, b);
                });
                break;
            case PROGRESS:
                Collections.sort(episodes, (a, b) -> {
                    int completed = Integer.compare(percentCache.get(b.id), percentCache.get(a.id));
                    return completed != 0 ? completed : recent.compare(a, b);
                });
                break;
            default:
                Collections.sort(episodes, recent);
        }
    }

    private static String date(Episode ep) {
        return ep.dateIso == null ? "" : ep.dateIso;
    }
}
