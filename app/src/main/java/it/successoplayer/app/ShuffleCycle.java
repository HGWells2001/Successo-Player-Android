package it.successoplayer.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Shuffle without replacement. The pool contains the ids allowed by current
 * filters. A new cycle begins only after all eligible items have been visited.
 * No Android dependencies so the algorithm can be tested on the JVM.
 */
final class ShuffleCycle {
    private final Random random;
    private final List<String> pool = new ArrayList<>();
    private final Set<String> played = new HashSet<>();

    ShuffleCycle(Random random) {
        this.random = random;
    }

    /** Returns true when the eligible set has changed, not just its ordering. */
    boolean setPool(List<String> candidateIds) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        if (candidateIds != null) {
            for (String id : candidateIds) {
                if (id != null && !id.isEmpty()) distinct.add(id);
            }
        }
        boolean changed = !new HashSet<>(pool).equals(distinct);
        pool.clear();
        pool.addAll(distinct);
        if (changed) played.clear();
        return changed;
    }

    Set<String> getPoolIds() {
        return new HashSet<>(pool);
    }

    Set<String> getPlayedIds() {
        return new HashSet<>(played);
    }

    void restorePlayed(Set<String> saved) {
        played.clear();
        if (saved == null) return;
        for (String id : saved) {
            if (pool.contains(id)) played.add(id);
        }
    }

    boolean markPlayed(String id) {
        if (id == null || !pool.contains(id)) return false;
        return played.add(id);
    }

    String next(String currentId) {
        if (pool.isEmpty()) return null;
        List<String> available = new ArrayList<>();
        for (String id : pool) {
            if (!played.contains(id)) available.add(id);
        }
        if (available.isEmpty()) {
            // Every eligible episode has been used once: start a new round.
            played.clear();
            available.addAll(pool);
        }
        // Avoid playing the same episode twice consecutively at cycle edges.
        // A singleton pool necessarily repeats its only eligible episode.
        if (available.size() > 1) available.remove(currentId);
        if (available.isEmpty()) return null;
        String chosen = available.get(random.nextInt(available.size()));
        played.add(chosen);
        return chosen;
    }
}
