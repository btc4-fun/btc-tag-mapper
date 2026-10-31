package com.saebm.btcnfcseller;

import static org.junit.Assert.*;
import org.junit.Test;

public class PublicQrTest {
    @Test public void acceptsOnlyCanonicalPublicAddressAndNeverEchoesPrivatePayload() throws Exception {
        Class<?> type;
        try { type = Class.forName("com.saebm.btcnfcseller.PublicQr"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Public QR gate missing"); }
        java.lang.reflect.Method method = type.getDeclaredMethod("address", String.class);
        String valid = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
        assertEquals(valid, method.invoke(null, valid));
        for (String payload : new String[]{"abandon abandon abandon", "private-secret", "bitcoin:" + valid, " " + valid,
                "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", valid.toUpperCase(), ""}) {
            assertNull(method.invoke(null, payload));
        }
    }
}
