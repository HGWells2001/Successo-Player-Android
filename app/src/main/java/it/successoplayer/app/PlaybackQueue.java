package it.successoplayer.app;

import java.util.ArrayList;
import java.util.List;

/**
 * FIFO of explicit user-selected episode IDs. No Android dependencies.
 * The current episode is not altered by changing this list.
 */
final class PlaybackQueue {
    static final int MAX_ITEMS = 100;
    private final ArrayList<String> ids = new ArrayList<>();

    int size() { return ids.size(); }

    List<String> snapshot() { return new ArrayList<>(ids); }

    boolean enqueue(String id, boolean playNext) {
        if (id == null || id.trim().isEmpty()
                || ids.size() >= MAX_ITEMS || ids.contains(id)) return false;
        if (playNext) ids.add(0, id);
        else ids.add(id);
        return true;
    }

    void restore(List<String> saved) {
        ids.clear();
        if (saved == null) return;
        for (String id : saved) enqueue(id, false);
    }

    String takeNext() {
        return ids.isEmpty() ? null : ids.remove(0);
    }

    boolean removeAt(int index) {
        if (index < 0 || index >= ids.size()) return false;
        ids.remove(index);
        return true;
    }

    boolean removeId(String id) {
        return id != null && ids.remove(id);
    }

    boolean move(int from, int direction) {
        int to = from + direction;
        if ((direction != -1 && direction != 1)
                || from < 0 || from >= ids.size() || to < 0 || to >= ids.size()) return false;
        String moved = ids.remove(from);
        ids.add(to, moved);
        return true;
    }

    void clear() { ids.clear(); }
}
