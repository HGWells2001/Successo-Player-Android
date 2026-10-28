package it.successoplayer.app;

import java.text.Normalizer;
import java.util.Locale;

/** Accent-insensitive, order-independent title and description search. */
final class EpisodeSearch {
    private EpisodeSearch() {}

    static boolean matches(Episode episode, String query) {
        if (episode == null) return false;
        String words = normalized(query);
        if (words.isEmpty()) return true;

        String text = normalized(safe(episode.title) + " "
                + safe(episode.description) + " "
                + safe(episode.dateDisplay) + " "
                + safe(episode.dateIso) + " "
                + episode.episodeNumber);
        for (String token : words.split("\\s+")) {
            if (!token.isEmpty() && !text.contains(token)) return false;
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalized(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
