#!/usr/bin/env python3
"""Regression test for removal of toolbar buttons (without losing app exit/catalog)."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/it/successoplayer/app/MainActivity.java").read_text(encoding="utf-8")
xml = (root / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
for word in ("refreshButton", "exitButton"):
    assert word not in activity, "Riferimento Java residuo: " + word
    assert word not in xml, "Pulsante XML residuo: " + word
assert "loadCatalog(false);" in activity, "Il catalogo deve caricarsi all'avvio"
assert "public void onBackPressed()" in activity
assert "closeAppCompletely();" in activity
assert "PlaybackExit.stopAll(this);" in activity
for must_keep in ("liveAutoSwitch", "favoritesFilterButton", "unlistenedFilterButton", "playButton"):
    assert must_keep in activity, "Controllo essenziale perso: " + must_keep
print("OK: pulsanti superflui rimossi, catalogo e chiusura funzionanti.")
