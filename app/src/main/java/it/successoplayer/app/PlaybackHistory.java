package it.successoplayer.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Last 50 distinct episodes that actually started playing. The list is newest
 * first; choosing an episode without starting audio does not add an entry.
 */
final class PlaybackHistory {
    static final int MAX_ITEMS = 50;

    static final class Entry {
        final String episodeId;
        final long playedAtMs;

        Entry(String episodeId, long playedAtMs) {
            this.episodeId = episodeId;
            this.playedAtMs = playedAtMs;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    int size() {
        return entries.size();
    }

    boolean record(String episodeId, long timeMs) {
        if (episodeId == null || episodeId.trim().isEmpty() || timeMs <= 0L) {
            return false;
        }
        remove(episodeId);
        entries.add(0, new Entry(episodeId, timeMs));
        if (entries.size() > MAX_ITEMS) entries.remove(entries.size() - 1);
        return true;
    }

    void restore(List<Entry> saved) {
        entries.clear();
        if (saved == null) return;
        for (Entry candidate : saved) {
            if (candidate == null || candidate.episodeId == null
                    || candidate.episodeId.trim().isEmpty()
                    || candidate.playedAtMs <= 0L || contains(candidate.episodeId)) {
                continue;
            }
            entries.add(new Entry(candidate.episodeId, candidate.playedAtMs));
            if (entries.size() == MAX_ITEMS) break;
        }
    }

    boolean remove(String episodeId) {
        if (episodeId == null) return false;
        for (int i = 0; i < entries.size(); i++) {
            if (episodeId.equals(entries.get(i).episodeId)) {
                entries.remove(i);
                return true;
            }
        }
        return false;
    }

    boolean contains(String episodeId) {
        if (episodeId == null) return false;
        for (Entry entry : entries) {
            if (episodeId.equals(entry.episodeId)) return true;
        }
        return false;
    }

    void clear() {
        entries.clear();
    }
}
