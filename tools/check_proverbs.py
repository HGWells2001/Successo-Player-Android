#!/usr/bin/env python3
"""Valida il corpus di proverbi italiani incluso nell'APK."""
import re
import unicodedata
from pathlib import Path

root = Path(__file__).resolve().parents[1]
file = root / "app/src/main/assets/proverbi_it.txt"
main = root / "app/src/main/java/it/successoplayer/app/MainActivity.java"
strings = [s.strip() for s in file.read_text(encoding="utf-8").splitlines()]
assert len(strings) >= 2000, f"Archivio troppo piccolo: {len(strings)}"
assert not any(not s for s in strings), "Riga vuota nell'archivio"
assert not any(len(s) > 160 for s in strings), "Proverbio troppo lungo"
assert "ProverbOfDay.forDate(this, today.dateKey)" in main.read_text(encoding="utf-8")

def normalized(s):
    s = unicodedata.normalize("NFD", s.casefold())
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", s)

keys = [normalized(s) for s in strings]
assert len(set(keys)) == len(keys), "Trovati proverbi duplicati"
print(f"OK: {len(strings)} proverbi distinti, offline, pronti per il ciclo quotidiano.")
