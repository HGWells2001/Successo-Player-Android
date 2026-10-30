package it.successoplayer.app;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EpisodeSortTest {
    private static Episode ep(String id, String date, long duration) {
        return new Episode(id, id, "", date, date, "", "", "", duration, 0);
    }
    private static void check(boolean good, String error) {
        if (!good) throw new AssertionError(error);
    }
    private static String ids(List<Episode> episodes) {
        StringBuilder s = new StringBuilder();
        for (Episode e : episodes) s.append(e.id);
        return s.toString();
    }
    public static void main(String[] args) {
        List<Episode> entries = new ArrayList<>(Arrays.asList(
            ep("A", "2025-11-10", 1000L),
            ep("B", "2026-10-20", 3000L),
            ep("C", "2026-01-08", 2000L)
        ));
        EpisodeSort.sort(entries, EpisodeSort.NEWEST, e -> 0);
        check(ids(entries).equals("BCA"), "newest");
        EpisodeSort.sort(entries, EpisodeSort.OLDEST, e -> 0);
        check(ids(entries).equals("ACB"), "oldest");
        EpisodeSort.sort(entries, EpisodeSort.LONGEST, e -> 0);
        check(ids(entries).equals("BCA"), "duration");
        EpisodeSort.sort(entries, EpisodeSort.PROGRESS, e -> e.id.equals("C") ? 95 :
                (e.id.equals("A") ? 20 : 0));
        check(ids(entries).equals("CAB"), "progress");
        check(EpisodeSort.validMode(-9) == EpisodeSort.NEWEST, "invalid pref");
        System.out.println("OK: date, duration and progress ordering.");
    }
}
