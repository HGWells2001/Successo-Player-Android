package it.successoplayer.app;
public final class EpisodeProgressTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(EpisodeProgress.percent(0, 0)==0,"unknown duration");
        check(EpisodeProgress.percent(0, 1000)==0,"unstarted");
        check(EpisodeProgress.percent(250, 1000)==25,"quarter");
        check(EpisodeProgress.percent(500, 1000)==50,"half");
        check(EpisodeProgress.percent(2500, 1000)==100,"overshoot capped");
        check(EpisodeProgress.percent(-100, 1000)==0,"negative position");
        check(EpisodeProgress.percent(100, -500)==0,"invalid duration");
        check(EpisodeProgress.thousandths(250, 1000)==250,"mini thousandths");
        check(EpisodeProgress.thousandths(1200, 1000)==1000,"mini capped");
        check(EpisodeProgress.thousandths(0, 0)==0,"no division by zero");
        System.out.println("OK: per-episode progress clamping and mini-player scale.");
    }
}
