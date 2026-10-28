package it.successoplayer.app;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class LiveProgramStatusTest {
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static LiveProgramStatus.Snapshot at(String iso) {
        return LiveProgramStatus.at(ZonedDateTime.parse(iso).withZoneSameInstant(ROME));
    }
    private static void eq(Object exp, Object actual) {
        if (!exp.equals(actual)) throw new AssertionError(exp + " != " + actual);
    }
    private static void yes(boolean val) {
        if (!val) throw new AssertionError("Expected true");
    }
    public static void main(String[] args) {
        var pre = at("2026-10-26T05:34:59+01:00");
        yes(!pre.inWindow);
        eq(1L, pre.secondsRemaining);
        var start = at("2026-10-26T05:35:00+01:00");
        yes(start.inWindow);
        eq(1500L, start.secondsRemaining);
        yes(LiveProgramStatus.summary(start).contains("termina tra 00:25:00"));
        var end = at("2026-10-26T06:00:00+01:00");
        yes(!end.inWindow);
        eq("TUESDAY", end.nextStart.getDayOfWeek().name());
        var friday = at("2026-10-30T06:00:00+01:00");
        eq("MONDAY", friday.nextStart.getDayOfWeek().name());
        eq("2026-11-02T05:35+01:00[Europe/Rome]", friday.nextStart.toString());
        var fall = at("2026-10-24T05:40:00+02:00");
        eq("2026-10-26T05:35+01:00[Europe/Rome]", fall.nextStart.toString());
        eq(176100L, fall.secondsRemaining);
        var spring = at("2027-03-27T05:40:00+01:00");
        eq("2027-03-29T05:35+02:00[Europe/Rome]", spring.nextStart.toString());
        eq(168900L, spring.secondsRemaining);
        eq("2 g 03:04:05", LiveProgramStatus.countdown(2*86400+3*3600+4*60+5));
        eq("Connessione: disattivata", LiveRadioConnection.label(false,true,true,"playing",1000,2000));
        eq("Connessione: inattiva · avvio programmato",LiveRadioConnection.label(true,false,true,"playing",1000,2000));
        eq("Connessione: in ascolto",LiveRadioConnection.label(true,true,true,"playing",1000,2000));
        eq("Connessione: collegamento…",LiveRadioConnection.label(true,true,true,"connecting",1000,2000));
        eq("Connessione: riconnessione…",LiveRadioConnection.label(true,true,true,"retrying",1000,2000));
        eq("Connessione: audio in pausa",LiveRadioConnection.label(true,true,true,"paused",1000,2000));
        eq("Connessione: non attiva",LiveRadioConnection.label(true,true,true,"playing",1000,37000));
        eq("Connessione: non riuscita · verifica la rete",LiveRadioConnection.label(true,true,true,"error",1000,37000));
        eq("Connessione: non attiva",LiveRadioConnection.label(true,true,true,"error",1000,2_000_000));
        eq("⚠ Avvio non esatto · controlla permesso sveglie",LiveRadioConnection.label(true,false,false,"playing",1000,2000));
        System.out.println("OK: time window, weekends, DST, countdown and connection heartbeat");
    }
}
