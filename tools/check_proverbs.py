#!/usr/bin/env python3
"""Controlla numero e unicità dei proverbi inclusi nell'app Android."""
import json
import re
import unicodedata
from pathlib import Path

java = Path(__file__).resolve().parents[1] / "app/src/main/java/it/successoplayer/app/ProverbOfDay.java"
source = java.read_text(encoding="utf-8")
body = source.split("private static final String[] SAYINGS = {", 1)[1].split("};", 1)[0]
values = [json.loads(line.strip().rstrip(",")) for line in body.splitlines() if line.strip()]

def key(value):
    text = unicodedata.normalize("NFD", value.casefold())
    return re.sub(r"[^a-z0-9]", "", "".join(c for c in text if not unicodedata.combining(c)))

assert len(values) == 730, f"Attesi 730 proverbi; trovati {len(values)}"
assert len({key(value) for value in values}) == 730, "Proverbi duplicati"
assert all(value.strip() and value == value.strip() for value in values), "Proverbio vuoto o con spazi"
print("OK: 730 proverbi distinti; ciclo quotidiano di 730 giorni.")
