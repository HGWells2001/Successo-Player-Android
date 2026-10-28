package it.successoplayer.app;

import java.time.ZonedDateTime;

/** UI formatting checked without Android dependencies. */
public final class LiveButtonTextTest {
    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(
                "Expected: [" + expected + "] but got: [" + actual + "]");
    }

    private static void assertThreeLines(String text) {
        String[] lines = text.split("\n", -1);
        if (lines.length != 3) throw new AssertionError("Expected exactly 3 lines: " + text);
        if (lines[0].length() > 25 || lines[1].length() > 28 || lines[2].length() > 25) {
            throw new AssertionError("Text too wide for compact switch: " + text);
        }
    }

    public static void main(String[] args) {
        LiveProgramStatus.Snapshot live =
                LiveProgramStatus.at(ZonedDateTime.parse("2026-10-28T05:47:25+01:00"));
        equal("● Diretta programma\n🔴 Fine tra 00:12:35\n✓ In ascolto",
                LiveButtonText.compose(true, live, "Connessione: in ascolto"));
        equal("○ Diretta programma\n🔴 Fine tra 00:12:35\nDisattivata",
                LiveButtonText.compose(false, live, "Connessione: disattivata"));

        LiveProgramStatus.Snapshot tomorrow =
                LiveProgramStatus.at(ZonedDateTime.parse("2026-10-28T21:00:00+01:00"));
        equal("● Diretta programma\nInizio tra 08:35:00\nAvvio programmato",
                LiveButtonText.compose(true, tomorrow,
                        "Connessione: inattiva · avvio programmato"));

        LiveProgramStatus.Snapshot weekend =
                LiveProgramStatus.at(ZonedDateTime.parse("2026-10-30T06:00:00+01:00"));
        assertThreeLines(LiveButtonText.compose(true, weekend,
                "⚠ Avvio non esatto · controlla permesso sveglie"));
        assertThreeLines(LiveButtonText.compose(true, live, "Connessione: in ascolto"));
        assertThreeLines(LiveButtonText.compose(true, tomorrow,
                "Connessione: inattiva · avvio programmato"));
        equal("Collegamento…", LiveButtonText.compactConnection("Connessione: collegamento…"));
        equal("Riconnessione…", LiveButtonText.compactConnection("Connessione: riconnessione…"));
        equal("Audio in pausa", LiveButtonText.compactConnection("Connessione: audio in pausa"));
        equal("⚠ Errore connessione",
                LiveButtonText.compactConnection("Connessione: non riuscita · verifica la rete"));
        equal("Non collegata", LiveButtonText.compactConnection("Connessione: non attiva"));
        System.out.println("OK: three compact lines, active/inactive, countdown and connection statuses");
    }
}
