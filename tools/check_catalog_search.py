#!/usr/bin/env python3
"""Regression tests for compact catalog search and its interaction with Shuffle."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/it/successoplayer/app"
main=(java/"MainActivity.java").read_text(encoding="utf-8")
service=(java/"AudioPlaybackService.java").read_text(encoding="utf-8")
layout=ET.parse(root/"app/src/main/res/layout/activity_main.xml")
ns="{http://schemas.android.com/apk/res/android}"
ids={x.attrib.get(ns+"id"):x for x in layout.iter() if x.attrib.get(ns+"id")}
header=list(layout.getroot())[0]
moreButton=ids["@+id/moreMenuButton"]
assert moreButton in list(header) and moreButton.tag=="Button"
assert moreButton.attrib[ns+"layout_width"]=="48dp"
assert moreButton.attrib[ns+"layout_height"]=="48dp"
assert "menu" in moreButton.attrib[ns+"contentDescription"].lower()
assert moreButton is list(header)[-1]
assert "@+id/searchButton" not in ids
assert "@+id/updateButton" not in ids
panel=ids["@+id/searchPanel"]
assert panel.attrib[ns+"visibility"]=="gone"
assert panel in list(layout.getroot())
assert ids["@+id/searchInput"].tag=="EditText"
assert ids["@+id/searchInput"].attrib[ns+"maxLength"]=="100"
assert ids["@+id/clearSearchButton"].attrib[ns+"contentDescription"]=="Cancella ricerca"
assert 'searchInput.addTextChangedListener(new TextWatcher()' in main
assert "case MENU_SEARCH:" in main
assert "toggleSearchPanel();" in main
assert 'moreMenuButton.setOnClickListener(v -> showMoreMenu());' in main
assert 'clearSearchButton.setOnClickListener(v -> searchInput.setText(""));' in main
assert 'searchPanel.setVisibility(View.GONE);' in main
assert 'private void toggleSearchPanel()' in main
assert 'searchInput.setText("");' in main
assert 'searchHandler.postDelayed(filterSearchTask, 180L);' in main
assert 'searchHandler.removeCallbacksAndMessages(null);' in main
assert 'searchQuery = prefs.getString("ui_search_query", "");' in main
assert '.putString("ui_search_query", searchQuery)' in main
assert 'if (!EpisodeSearch.matches(e, searchQuery)) continue;' in main
assert 'if (!searchQuery.isEmpty()) {' in main
assert 'adapter.setItems(visibleEpisodes);' in main
assert 'shuffleEnabled ? visibleEpisodes : allEpisodes' in main
assert 'service.setQueue(' in main
assert 'private void refreshMoreMenuState()' in main
assert 'private void showOfflineLibrary(' in main
assert 'UpdateChecker.check(this, prefs, false);' in main
assert 'private static final String KEY_UP_NEXT' in service

with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,
                    str(java/"Episode.java"),
                    str(java/"EpisodeSearch.java"),
                    str(root/"tools/tests/EpisodeSearchTest.java")],check=True)
    subprocess.run(["java","-cp",tmp,"it.successoplayer.app.EpisodeSearchTest"],check=True)
print("OK: compact search, combined filters, Shuffle pool, state persistence and JVM search.")
