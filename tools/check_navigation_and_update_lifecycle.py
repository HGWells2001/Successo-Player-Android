#!/usr/bin/env python3
"""v4.21: Back navigation and update prompts must not disrupt audio/background use."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/java/it/successoplayer/app"
main = (source / "MainActivity.java").read_text(encoding="utf-8")
checker = (source / "UpdateChecker.java").read_text(encoding="utf-8")
layout = (root / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")

back = main.split("public void onBackPressed()", 1)[1].split("@Override", 1)[0]
assert "searchPanel != null && searchPanel.getVisibility() == View.VISIBLE" in back
assert back.index("toggleSearchPanel();") < back.index("closeAppCompletely();")
assert "return;" in back.split("toggleSearchPanel();", 1)[1].split("closeAppCompletely();", 1)[0]
search = main.split("private void toggleSearchPanel()", 1)[1].split("private void loadCatalog(", 1)[0]
assert 'searchInput.setText("");' in search
assert "searchPanel.setVisibility(View.GONE);" in search
assert "refreshMoreMenuState();" in search
assert 'description += ". Ricerca aperta";' in main
assert "return activityStarted && !destroyed && !isFinishing();" in main
ui = checker.split("private static void onUi(", 1)[1].split("static int compareVersion(", 1)[0]
assert "activity.isFinishing() || activity.isDestroyed()" in ui
assert "!((MainActivity) activity).canShowUpdatePrompt()" in ui
assert "task.run();" in ui
assert "refreshMoreMenuState();\n            refreshMoreMenuState();" not in main
menu = main.split("private void showMoreMenu()", 1)[1].split("private void refreshMoreMenuState()", 1)[0]
assert menu.count("menu.getMenu().add(0, MENU_") == 5
assert '@+id/moreMenuButton' in layout
for removed in ("@+id/searchButton", "@+id/updateButton", "@+id/queueButton", "@+id/sleepTimerButton", "@+id/historyButton"):
    assert removed not in layout
assert 'UpdateChecker.check(this, prefs, false);' in main
assert 'UpdateChecker.check(this, prefs, true);' in main
print("OK: Back closes search first, update prompts foreground-only, five menu actions.")
