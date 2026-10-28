package it.successoplayer.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PlaybackQueueTest {
    static void ok(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    static void same(PlaybackQueue q, String... ids) {
        ok(q.snapshot().equals(Arrays.asList(ids)),
                "Expected " + Arrays.toString(ids) + " got " + q.snapshot());
    }
    public static void main(String[] args) {
        PlaybackQueue q = new PlaybackQueue();
        ok(q.enqueue("A", false), "append A");
        ok(q.enqueue("B", false), "append B");
        ok(q.enqueue("C", true), "prepend C");
        same(q, "C", "A", "B");
        ok(!q.enqueue("A", false), "duplicate rejected");
        ok(!q.enqueue(null, true), "null rejected");
        ok(!q.enqueue("", false), "empty rejected");
        ok(q.move(2, -1), "move up");
        same(q, "C", "B", "A");
        ok(q.move(1, 1), "move down");
        same(q, "C", "A", "B");
        ok(!q.move(0, -1), "move before start");
        ok(!q.move(2, 1), "move after end");
        ok("C".equals(q.takeNext()), "FIFO priority");
        ok(q.removeId("A"), "remove by id");
        same(q, "B");
        ok(q.removeAt(0), "remove by index");
        ok(q.takeNext() == null, "empty");
        q.restore(Arrays.asList("Y", "Y", "Z", "", "X"));
        same(q, "Y", "Z", "X");
        List<String> snapshot = q.snapshot();
        snapshot.clear();
        same(q, "Y", "Z", "X");
        q.clear();
        same(q);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 120; i++) many.add("ITEM_" + i);
        q.restore(many);
        ok(q.size() == PlaybackQueue.MAX_ITEMS, "max queue");
        ok(!q.enqueue("OVERFLOW", false), "max enforce");
        System.out.println("OK: queue ordering, moves, dedup, snapshot, persistence and cap.");
    }
}
