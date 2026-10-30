package it.successoplayer.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.content.res.ColorStateList;
import java.util.Set;
import java.util.HashSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class EpisodeAdapter extends BaseAdapter {
    public interface FavoriteHandler {
        boolean isFavorite(Episode episode);
        boolean isListened(Episode episode);
        long savedPositionMs(Episode episode);
        void toggleFavorite(Episode episode);
        void showDetails(Episode episode);
    }

    private final LayoutInflater inflater;
    private final FavoriteHandler favoriteHandler;
    private final List<Episode> items = new ArrayList<>();
    private String selectedEpisodeId = "";
    private String currentProgressId = "";
    private long currentPositionMs;
    private long currentDurationMs;
    private int currentPercent = -1;
    private final Set<String> offlineEpisodeIds = new HashSet<>();
    private String downloadingEpisodeId = "";

    public EpisodeAdapter(Context context, FavoriteHandler favoriteHandler) {
        inflater = LayoutInflater.from(context);
        this.favoriteHandler = favoriteHandler;
    }

    public void setItems(List<Episode> episodes) {
        items.clear();
        if (episodes != null) items.addAll(episodes);
        notifyDataSetChanged();
    }

    /** Evidenziamo la traccia anche dopo il riciclo delle righe. */
    public void setSelectedEpisodeId(String id) {
        String next = id == null ? "" : id;
        if (next.equals(selectedEpisodeId)) return;
        selectedEpisodeId = next;
        notifyDataSetChanged();
    }

    /** Refresh only when the displayed percent or current episode changes. */
    public void setCurrentPlayback(String id, long positionMs, long durationMs) {
        String nextId = id == null ? "" : id;
        int percent = EpisodeProgress.percent(positionMs, durationMs);
        if (nextId.equals(currentProgressId) && percent == currentPercent) return;
        currentProgressId = nextId;
        currentPositionMs = Math.max(0L, positionMs);
        currentDurationMs = Math.max(0L, durationMs);
        currentPercent = percent;
        notifyDataSetChanged();
    }

    /** Offline markers are driven by actual successful DownloadManager records. */
    public void setOfflineState(Set<String> completedIds, String downloadingId) {
        Set<String> next = completedIds == null
                ? new HashSet<>() : new HashSet<>(completedIds);
        String current = downloadingId == null ? "" : downloadingId;
        if (offlineEpisodeIds.equals(next) && downloadingEpisodeId.equals(current)) return;
        offlineEpisodeIds.clear();
        offlineEpisodeIds.addAll(next);
        downloadingEpisodeId = current;
        notifyDataSetChanged();
    }

    public Episode getEpisode(int position) { return items.get(position); }
    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.episode_row, parent, false);
            h = new ViewHolder();
            h.activeMark = convertView.findViewById(R.id.rowActiveMark);
            h.title = convertView.findViewById(R.id.rowTitle);
            h.meta = convertView.findViewById(R.id.rowMeta);
            h.favorite = convertView.findViewById(R.id.rowFavorite);
            h.offline = convertView.findViewById(R.id.rowOfflineIndicator);
            h.info = convertView.findViewById(R.id.rowInfo);
            h.progressContainer = convertView.findViewById(R.id.rowProgressContainer);
            h.progress = convertView.findViewById(R.id.rowProgress);
            h.progressLabel = convertView.findViewById(R.id.rowProgressLabel);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        Episode e = items.get(position);
        boolean selected = e.id != null && e.id.equals(selectedEpisodeId);
        convertView.setBackgroundResource(
                selected ? R.drawable.episode_selected_bg : R.drawable.episode_card_bg);
        h.activeMark.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        h.title.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        h.title.setText(e.title);

        String meta = e.dateDisplay;
        if (e.durationMs > 0) {
            meta += "  ·  " + MainActivity.formatTime(e.durationMs);
        }
        if (favoriteHandler != null && favoriteHandler.isListened(e)) {
            meta += "  ·  ✓ Ascoltata";
        }
        h.meta.setText(meta);

        long savedMs = favoriteHandler == null ? 0L
                : favoriteHandler.savedPositionMs(e);
        long duration = e.durationMs;
        if (e.id != null && e.id.equals(currentProgressId)) {
            savedMs = currentPositionMs;
            if (currentDurationMs > 0L) duration = currentDurationMs;
        }
        boolean listened = favoriteHandler != null && favoriteHandler.isListened(e);
        int percent = EpisodeProgress.percent(savedMs, duration);
        if (listened && savedMs <= 0L) percent = 100;
        h.progressContainer.setVisibility(View.VISIBLE);
        h.progress.setProgress(percent * 10);
        if (listened && savedMs <= 0L) {
            h.progressLabel.setText("✓ Ascoltata");
        } else if (savedMs > 0L) {
            h.progressLabel.setText(percent + "% · " + MainActivity.formatTime(savedMs));
        } else {
            h.progressLabel.setText("Da ascoltare");
        }

        boolean favorite = favoriteHandler != null && favoriteHandler.isFavorite(e);
        h.favorite.setText(favorite ? "★" : "☆");
        h.favorite.setContentDescription(
                favorite ? "Rimuovi dai preferiti" : "Aggiungi ai preferiti"
        );

        h.favorite.setOnClickListener(v -> {
            if (favoriteHandler != null) {
                favoriteHandler.toggleFavorite(e);
            }
        });

        boolean completed = e.id != null && offlineEpisodeIds.contains(e.id);
        boolean downloading = !completed && e.id != null
                && e.id.equals(downloadingEpisodeId);
        h.offline.setVisibility(completed || downloading ? View.VISIBLE : View.GONE);
        if (completed) {
            h.offline.setImageResource(R.drawable.ic_offline_ready_art);
            h.offline.setContentDescription("Disponibile offline");
        } else if (downloading) {
            h.offline.setImageResource(R.drawable.ic_downloading_art);
            h.offline.setContentDescription("Download in corso");
        }
        h.info.setContentDescription("Dettagli: " + e.title);
        h.info.setOnClickListener(v -> {
            if (favoriteHandler != null) favoriteHandler.showDetails(e);
        });

        return convertView;
    }

    private static final class ViewHolder {
        View activeMark;
        TextView title;
        TextView meta;
        TextView favorite;
        ImageView offline;
        ImageButton info;
        LinearLayout progressContainer;
        ProgressBar progress;
        TextView progressLabel;
    }
}
