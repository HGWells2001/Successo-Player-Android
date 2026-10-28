package it.successoplayer.app;

/** Persisted heartbeat state, not a guess based on the scheduled window. */
final class LiveRadioConnection {
    static final String KEY_STATE = "live_connection_state_v1";
    static final String KEY_UPDATED_AT = "live_connection_updated_at_v1";
    static final String STOPPED = "stopped";
    static final String CONNECTING = "connecting";
    static final String PLAYING = "playing";
    static final String RETRYING = "retrying";
    static final String PAUSED = "paused";
    static final String ERROR = "error";
    private static final long HEARTBEAT_MAX_AGE_MS = 35_000L;

    private LiveRadioConnection() {}

    static String label(boolean enabled, boolean inWindow, boolean preciseAlarm,
                        String state, long updatedAtMs, long nowMs) {
        if (!enabled) return "Connessione: disattivata";
        if (!inWindow) {
            return preciseAlarm ? "Connessione: inattiva · avvio programmato"
                    : "⚠ Avvio non esatto · controlla permesso sveglie";
        }
        long age = nowMs - updatedAtMs;
        boolean recent = updatedAtMs > 0 && age >= 0 && age <= HEARTBEAT_MAX_AGE_MS;
        if (recent) {
            if (PLAYING.equals(state)) return "Connessione: in ascolto";
            if (CONNECTING.equals(state)) return "Connessione: collegamento…";
            if (RETRYING.equals(state)) return "Connessione: riconnessione…";
            if (PAUSED.equals(state)) return "Connessione: audio in pausa";
        }
        if (ERROR.equals(state) && updatedAtMs > 0 && age >= 0 && age <= 30L * 60_000L) {
            return "Connessione: non riuscita · verifica la rete";
        }
        return "Connessione: non attiva";
    }
}
