package it.successoplayer.app;

public final class AudioRecoveryPolicyTest {
    private static void eq(long a, long b) {
        if (a != b) throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) {
        eq(2000L,AudioRecoveryPolicy.delayMs(0));
        eq(5000L,AudioRecoveryPolicy.delayMs(1));
        eq(10000L,AudioRecoveryPolicy.delayMs(2));
        eq(-1L,AudioRecoveryPolicy.delayMs(3));
        eq(-1L,AudioRecoveryPolicy.delayMs(100));
        if (!AudioRecoveryPolicy.isNetworkError(2001)
                || !AudioRecoveryPolicy.isNetworkError(2002)
                || AudioRecoveryPolicy.isNetworkError(2005)
                || AudioRecoveryPolicy.isNetworkError(4003)) {
            throw new AssertionError("Only recover network failures");
        }
        System.out.println("OK: bounded retry policy");
    }
}
