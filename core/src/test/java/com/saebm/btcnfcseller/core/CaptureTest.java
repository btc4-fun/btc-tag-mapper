package com.saebm.btcnfcseller.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureTest {
    @Test public void requiresRealTimestampSecondsZoneAndAtMostMicroseconds() {
        for (String t : new String[]{null, "", "2026-10-31", "2026-10-31T00:00Z", "2026-10-31T00:00:00",
                "2026-10-31T00:00:00.1234567Z", "2026-02-29T00:00:00Z", "2026-10-31T24:00:00Z",
                "2026-10-31T23:59:60Z", "2026-10-31T00:00:00+24:00", "2026-10-31T00:00:00+00:60",
                "2026-10-31T00:00:00z", "2026-10-31T00:00:00Z\n", "0000-01-01T00:00:00Z"})
            assertThrows(String.valueOf(t), IllegalArgumentException.class, () -> new Capture("0012ABFF", BitcoinAddressTest.A, t));
        for (String t : new String[]{"2024-02-29T00:00:00Z", "2026-10-31T00:00:00.1-23:59",
                "2026-10-31T00:00:00+23:59", "2026-10-31T00:00:00.123456-00:00"})
            assertEquals(t, new Capture("0012ABFF", BitcoinAddressTest.A, t).capturedAt);
    }
    @Test public void rejectsInvalidBindingValues() {
        assertThrows(IllegalArgumentException.class, () -> new Capture("0012abff", BitcoinAddressTest.A, "2026-10-31T00:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> new Capture("0012ABFF", "bitcoin:"+BitcoinAddressTest.A, "2026-10-31T00:00:00Z"));
    }
    @Test public void preservesCaptureValuesExactly() {
        Capture c = new Capture("0012ABFF", BitcoinAddressTest.A, "2026-10-31T23:59:59.123456+03:30");
        assertEquals("0012ABFF", c.uid);
        assertEquals(BitcoinAddressTest.A, c.address);
        assertEquals("2026-10-31T23:59:59.123456+03:30", c.capturedAt);
    }
}
