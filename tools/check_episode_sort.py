#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/it/successoplayer/app"
with tempfile.TemporaryDirectory() as temp:
    subprocess.run(["javac","-encoding","UTF-8","-d",temp,
        str(java/"Episode.java"),
        str(java/"EpisodeSort.java"),
        str(root/"tools/tests/EpisodeSortTest.java")],check=True)
    subprocess.run(["java","-cp",temp,"it.successoplayer.app.EpisodeSortTest"],check=True)
print("OK: sort catalog without changing original playback list.")
