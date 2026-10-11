package android.os;

// Test-only replacement for the Android clock, following android.util.Log's test stub.
public class SystemClock {
    public static long elapsedRealtime() {
        return System.currentTimeMillis();
    }
}
