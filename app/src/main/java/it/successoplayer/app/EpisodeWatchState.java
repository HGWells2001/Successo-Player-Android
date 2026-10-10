package it.successoplayer.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

public final class EpisodeWatchState {
    private static final String PREFS = "successo_episode_watch";
    private static final String KEY_LAST_ID = "last_episode_id";
    private static final String KEY_LAST_DATE = "last_episode_date";
    private static final String KEY_LAST_TITLE = "last_episode_title";

    private EpisodeWatchState() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void markLatestSeen(Context context, List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return;
        markLatestSeen(context, episodes.get(0));
    }

    public static void markLatestSeen(Context context, Episode episode) {
        if (episode == null || episode.id == null || episode.id.isEmpty()) return;
        prefs(context).edit()
                .putString(KEY_LAST_ID, episode.id)
                .putString(KEY_LAST_DATE, safe(episode.dateIso))
                .putString(KEY_LAST_TITLE, safe(episode.title))
                .apply();
    }

    public static String lastId(Context context) {
        return prefs(context).getString(KEY_LAST_ID, "");
    }

    public static String lastDate(Context context) {
        return prefs(context).getString(KEY_LAST_DATE, "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
