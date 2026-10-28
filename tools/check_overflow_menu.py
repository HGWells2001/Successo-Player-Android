#!/usr/bin/env python3
"""Only one header dropdown launches Search, Queue, Sleep, History and Updates."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
layout=ET.parse(root/"app/src/main/res/layout/activity_main.xml")
ns="{http://schemas.android.com/apk/res/android}"
ids={n.attrib.get(ns+"id"):n for n in layout.iter() if n.attrib.get(ns+"id")}
header=list(layout.getroot())[0]
top_buttons=[child for child in list(header) if child.tag=="Button"]
assert len(top_buttons)==1, f"Expected one top-right button, got {len(top_buttons)}"
button=ids["@+id/moreMenuButton"]
assert button is top_buttons[0] and button is list(header)[-1]
assert button.attrib[ns+"text"]=="⋮"
assert button.attrib[ns+"layout_width"]=="48dp"
assert button.attrib[ns+"layout_height"]=="48dp"
assert "ricerca, coda, timer, cronologia e aggiornamenti" in button.attrib[ns+"contentDescription"]
for legacy in ("searchButton","updateButton","upNextButton","sleepTimerButton","historyButton"):
    assert "@+id/"+legacy not in ids, legacy+" old control remains"
    assert "R.id."+legacy not in main, legacy+" old Java binding remains"

assert "import android.widget.PopupMenu;" in main
assert "new PopupMenu(this, moreMenuButton)" in main
assert "menu.setGravity(Gravity.END);" in main
assert "moreMenuButton.setOnClickListener(v -> showMoreMenu());" in main
assert "menu.setOnMenuItemClickListener(item -> {" in main
assert "menu.show();" in main
menu_body=main.split("private void showMoreMenu() {",1)[1].split("private void refreshMoreMenuState()",1)[0]
expected=[
    ("MENU_SEARCH","toggleSearchPanel();"),
    ("MENU_QUEUE","showUpNextQueue();"),
    ("MENU_SLEEP","showSleepTimerOptions();"),
    ("MENU_HISTORY","showPlaybackHistory();"),
    ("MENU_UPDATE","UpdateChecker.check(this, prefs, true);"),
]
assert menu_body.count("menu.getMenu().add(0, MENU_")==5
for i,(key,target) in enumerate(expected):
    assert f"menu.getMenu().add(0, {key}, {i}," in menu_body
    section=menu_body.split("case "+key+":",1)[1].split("return true;",1)[0]
    assert target in section,(key,target)
assert 'getUpNextIds().size()' in menu_body
assert 'service.isSleepTimerActive()' in menu_body
assert "searchPanel.getVisibility() == View.VISIBLE" in menu_body
assert "searchPanel.getVisibility() == View.VISIBLE" in main
assert 'searchInput.setText("");' in main
assert 'searchHandler.postDelayed(filterSearchTask, 180L);' in main
assert 'if (!EpisodeSearch.matches(e, searchQuery)) continue;' in main
assert "service.enqueueUpNext(chosen, which == 1)" in main
assert "service.startSleepTimer(minutes[index]);" in main
assert "service.getRecentHistory()" in main
assert "UpdateChecker.check(this, prefs, false);" in main
assert "private void refreshMoreMenuState()" in main
assert "moreMenuButton.setContentDescription(description);" in main
assert '@+id/searchPanel' in ids and '@+id/clearSearchButton' in ids
print("OK: one top-right overflow menu, five actions, compact layout and preserved functionality.")
