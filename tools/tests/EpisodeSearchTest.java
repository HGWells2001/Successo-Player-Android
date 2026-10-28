package it.successoplayer.app;

/** JVM tests for accent-insensitive and multiword episode search. */
public final class EpisodeSearchTest {
    private static void check(boolean expected, String message) {
        if (!expected) throw new AssertionError(message);
    }
    private static Episode ep(String title, String description, String date, int number) {
        return new Episode("id-" + number, title, description, date, date,
                "", "", "", 0L, number);
    }
    public static void main(String[] args) {
        Episode first = ep("La città che scomparve", "Un viaggio nella Firenze del Novecento",
                "1966-11-04", 120);
        Episode second = ep("Voci del futuro", "Macchine del tempo e tecnologie",
                "1975-04-01", 121);
        check(EpisodeSearch.matches(first, ""), "empty matches any");
        check(EpisodeSearch.matches(first, "  "), "whitespace matches any");
        check(EpisodeSearch.matches(first, "CITTA"), "ignore accents and case");
        check(EpisodeSearch.matches(first, "firenze novecento"), "multiple words");
        check(EpisodeSearch.matches(first, "Novecento Firenze"), "order-independent");
        check(EpisodeSearch.matches(first, "1966"), "ISO date");
        check(EpisodeSearch.matches(first, "120"), "episode number");
        check(!EpisodeSearch.matches(first, "citta milano"), "all tokens required");
        check(!EpisodeSearch.matches(first, "futuro"), "nonmatching record");
        check(EpisodeSearch.matches(second, "FUTURO"), "second title");
        check(EpisodeSearch.matches(second, "macchine tempo"), "description words");
        check(!EpisodeSearch.matches(second, "1977"), "incorrect year");
        check(!EpisodeSearch.matches(null, "test"), "null episode excluded");
        System.out.println("OK: query title, description, number, date, accent and token order.");
    }
}
