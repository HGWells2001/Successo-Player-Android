package it.successoplayer.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class ShuffleCycleTest {
    private static void check(boolean okay, String failure) {
        if (!okay) throw new AssertionError(failure);
    }

    public static void main(String[] args) {
        ShuffleCycle cycle = new ShuffleCycle(new Random(13));
        check(cycle.setPool(Arrays.asList("A", "B", "C", "D", "B")), "initial pool");
        check(cycle.getPoolIds().size() == 4, "deduplicated pool");
        cycle.markPlayed("A");
        String previous = "A";
        Set<String> seen = new HashSet<>();
        seen.add("A");
        for (int i = 0; i < 3; ++i) {
            String id = cycle.next(previous);
            check(id != null && !seen.contains(id), "duplicate before full round");
            seen.add(id);
            previous = id;
        }
        check(seen.size() == 4, "all tracks used");
        String nextRound = cycle.next(previous);
        check(nextRound != null && !previous.equals(nextRound), "immediate repeat at cycle boundary");
        Set<String> saved = cycle.getPlayedIds();
        ShuffleCycle reopened = new ShuffleCycle(new Random(12));
        check(reopened.setPool(Arrays.asList("D", "B", "C", "A")), "new object");
        reopened.restorePlayed(saved);
        check(reopened.getPlayedIds().equals(saved), "persistence restored");
        check(!reopened.setPool(Arrays.asList("A", "B", "C", "D")), "reorder must preserve progress");
        check(reopened.getPlayedIds().equals(saved), "reorder should not wipe history");

        check(reopened.setPool(Arrays.asList("B", "D")), "filter changes pool");
        check(reopened.getPlayedIds().isEmpty(), "new filter starts a fresh cycle");
        String one = reopened.next(null);
        String two = reopened.next(one);
        check(one != null && two != null && !one.equals(two), "filtered cycle covers both");
        check(new HashSet<>(Arrays.asList("B", "D")).contains(one), "filter pool first");
        check(new HashSet<>(Arrays.asList("B", "D")).contains(two), "filter pool second");
        check(!two.equals(reopened.next(two)), "filtered cycle avoids boundary duplicate");

        check(reopened.setPool(Collections.emptyList()), "empty filter");
        check(reopened.next(null) == null, "empty pool must not play");
        check(reopened.setPool(Collections.singletonList("ONLY")), "single filter");
        check("ONLY".equals(reopened.next(null)), "singleton first");
        check("ONLY".equals(reopened.next("ONLY")), "singleton repeats only after complete cycle");
        check(!reopened.markPlayed("NOT_IN_POOL"), "out of pool must not count");

        ShuffleCycle full = new ShuffleCycle(new Random(19));
        full.setPool(Arrays.asList("A", "B", "C"));
        Set<String> played = new HashSet<>();
        for (int i = 0; i < 3; ++i) played.add(full.next(null));
        check(played.size() == 3, "full coverage");
        System.out.println("OK: no repeats, filters, cycles, stable pool, persistence, empty/singleton.");
    }
}
