package it.successoplayer.app;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for the bounded, persistent recent-playback order. */
public final class PlaybackHistoryTest {
    private static void ok(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        PlaybackHistory history = new PlaybackHistory();
        ok(history.size() == 0, "initially empty");
        ok(!history.record(null, 100L), "reject null");
        ok(!history.record("", 100L), "reject blank id");
        ok(!history.record("A", 0L), "reject invalid time");
        ok(history.record("A", 100L), "first play");
        ok(history.record("B", 200L), "second play");
        ok(history.record("A", 300L), "replay moves to top");
        ok(history.size() == 2, "do not duplicate episode");
        List<PlaybackHistory.Entry> recent = history.snapshot();
        ok("A".equals(recent.get(0).episodeId) && recent.get(0).playedAtMs == 300L,
                "most recent and its timestamp");
        ok("B".equals(recent.get(1).episodeId), "other episode second");
        recent.clear();
        ok(history.size() == 2, "snapshot must not mutate history");

        ok(history.remove("B"), "remove existing");
        ok(!history.remove("UNKNOWN"), "cannot remove absent item");
        ok(!history.contains("B") && history.contains("A"), "removed id gone");

        List<PlaybackHistory.Entry> saved = new ArrayList<>();
        saved.add(new PlaybackHistory.Entry("X", 500L));
        saved.add(new PlaybackHistory.Entry("X", 300L));
        saved.add(null);
        saved.add(new PlaybackHistory.Entry("", 60L));
        saved.add(new PlaybackHistory.Entry("Y", 400L));
        saved.add(new PlaybackHistory.Entry("Z", 0L));
        history.restore(saved);
        ok(history.size() == 2 && "X".equals(history.snapshot().get(0).episodeId),
                "restore skips duplicates and invalid records");
        ok("Y".equals(history.snapshot().get(1).episodeId),
                "restore preserves saved order");

        for (int i = 0; i < 60; i++) {
            history.record("EP_" + i, 1000L + i);
        }
        ok(history.size() == PlaybackHistory.MAX_ITEMS, "cap at 50");
        ok("EP_59".equals(history.snapshot().get(0).episodeId),
                "newest history entry at top");
        ok(!history.contains("EP_0") && history.contains("EP_59"),
                "oldest history entry is evicted");

        List<PlaybackHistory.Entry> restored = history.snapshot();
        PlaybackHistory reopened = new PlaybackHistory();
        reopened.restore(restored);
        ok(reopened.size() == PlaybackHistory.MAX_ITEMS, "full restoration");
        ok("EP_59".equals(reopened.snapshot().get(0).episodeId),
                "order restored after application restart");
        reopened.clear();
        ok(reopened.size() == 0, "clear only removes history records");
        System.out.println("OK: recent playback, no duplicates, max 50, restore, remove and clear.");
    }
}
