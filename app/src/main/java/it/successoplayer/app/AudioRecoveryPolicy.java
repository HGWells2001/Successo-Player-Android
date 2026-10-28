package it.successoplayer.app;

/** Bounded recovery of streaming network failures. No autoplay after user pause. */
final class AudioRecoveryPolicy {
    private AudioRecoveryPolicy() {}
    static boolean isNetworkError(int code) {
        return code == 2000  // Media3 IO_UNSPECIFIED
                || code == 2001 // NETWORK_CONNECTION_FAILED
                || code == 2002; // NETWORK_CONNECTION_TIMEOUT
    }
    static long delayMs(int previousAttempts) {
        switch (previousAttempts) {
            case 0: return 2000L;
            case 1: return 5000L;
            case 2: return 10000L;
            default: return -1L; // report error; manual Play can retry
        }
    }
}
