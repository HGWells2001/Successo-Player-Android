#!/usr/bin/env python3
"""Legacy UI migration regression: retired searchBox is gone; compact search is wired."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
layout = (root / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")

# The former always-visible search widget must stay retired.
for term in ("searchBox", "savedSearch", '"search_query"', "q.isEmpty()"):
    assert term not in activity, f"Legacy search still present: {term}"
assert "searchBox" not in layout
# A compact, optional, persisted search replaces it.
assert "moreMenuButton" in activity and '@+id/moreMenuButton' in layout
assert "MENU_SEARCH" in activity and "toggleSearchPanel();" in activity
assert "@+id/searchButton" not in layout
assert "searchPanel" in activity and '@+id/searchPanel' in layout
assert "TextWatcher" in activity
assert '.putString("ui_search_query", searchQuery)' in activity
assert 'EpisodeSearch.matches(e, searchQuery)' in activity
# Original user-selected filters must retain their meaning.
assert "if (favoritesOnly && !isFavorite(e)) continue;" in activity
assert "if (unlistenedOnly && isListened(e)) continue;" in activity
assert '@+id/favoritesFilterButton' in layout
assert '@+id/unlistenedFilterButton' in layout
print("OK: legacy search retired; compact search and existing filters are retained.")
