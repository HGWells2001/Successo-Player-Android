package it.successoplayer.app;

/** Three short lines fitted into the existing 48dp live ToggleButton. */
final class LiveButtonText {
    private LiveButtonText() {}

    static String compose(boolean enabled, LiveProgramStatus.Snapshot schedule,
                          String fullConnectionLabel) {
        String title = (enabled ? "● " : "○ ") + "Diretta programma";
        String countdown = (schedule.inWindow ? "🔴 Fine tra " : "Inizio tra ")
                + LiveProgramStatus.countdown(schedule.secondsRemaining);
        return title + "\n" + countdown + "\n" + compactConnection(fullConnectionLabel);
    }

    static String compactConnection(String full) {
        if ("Connessione: in ascolto".equals(full)) return "✓ In ascolto";
        if ("Connessione: collegamento…".equals(full)) return "Collegamento…";
        if ("Connessione: riconnessione…".equals(full)) return "Riconnessione…";
        if ("Connessione: audio in pausa".equals(full)) return "Audio in pausa";
        if ("Connessione: non riuscita · verifica la rete".equals(full)) {
            return "⚠ Errore connessione";
        }
        if ("Connessione: inattiva · avvio programmato".equals(full)) {
            return "Avvio programmato";
        }
        if ("Connessione: disattivata".equals(full)) return "Disattivata";
        if ("⚠ Avvio non esatto · controlla permesso sveglie".equals(full)) {
            return "⚠ Sveglia non esatta";
        }
        return "Non collegata";
    }
}
