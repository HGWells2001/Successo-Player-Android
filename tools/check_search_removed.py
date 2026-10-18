#!/usr/bin/env python3
"""Controlla la rimozione della ricerca, preservando gli altri filtri."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
layout = (root / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
for term in ("searchBox", "TextWatcher", "savedSearch", '"search_query"', "q.isEmpty()"):
    assert term not in activity, f"Ricerca ancora presente: {term}"
assert "searchBox" not in layout and "Cerca titolo" not in layout
assert '.remove("ui_search_query")' in activity
assert "if (favoritesOnly && !isFavorite(e)) continue;" in activity
assert "if (unlistenedOnly && isListened(e)) continue;" in activity
assert '@+id/favoritesFilterButton' in layout and '@+id/unlistenedFilterButton' in layout
print("OK: ricerca rimossa, filtri Preferite e Non ascoltate conservati.")
