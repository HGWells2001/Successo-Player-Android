package it.successoplayer.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class EpisodeAdapter extends BaseAdapter {
    public interface FavoriteHandler {
        boolean isFavorite(Episode episode);
        boolean isListened(Episode episode);
        void toggleFavorite(Episode episode);
    }

    private final LayoutInflater inflater;
    private final FavoriteHandler favoriteHandler;
    private final List<Episode> items = new ArrayList<>();

    public EpisodeAdapter(Context context, FavoriteHandler favoriteHandler) {
        inflater = LayoutInflater.from(context);
        this.favoriteHandler = favoriteHandler;
    }

    public void setItems(List<Episode> episodes) {
        items.clear();
        if (episodes != null) items.addAll(episodes);
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
            h.title = convertView.findViewById(R.id.rowTitle);
            h.meta = convertView.findViewById(R.id.rowMeta);
            h.favorite = convertView.findViewById(R.id.rowFavorite);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        Episode e = items.get(position);

        h.title.setText(e.title);

        String meta = e.dateDisplay;
        if (e.durationMs > 0) {
            meta += "  ·  " + MainActivity.formatTime(e.durationMs);
        }
        if (favoriteHandler != null && favoriteHandler.isListened(e)) {
            meta += "  ·  ✓ Ascoltata";
        }
        h.meta.setText(meta);

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

        return convertView;
    }

    private static final class ViewHolder {
        TextView title;
        TextView meta;
        TextView favorite;
    }
}
