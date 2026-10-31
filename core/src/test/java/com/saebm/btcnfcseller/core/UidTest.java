package com.saebm.btcnfcseller.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class UidTest {
    @Test public void validatesOnlyCanonicalUppercaseHex() {
        for (String v : new String[]{"0012ABFF", "0001020304ABFF", "00010203040506070809"}) assertTrue(Uid.isValid(v));
        for (String v : new String[]{null,"", "0012abff", "0012ABFG", "00:12:AB:FF", " 0012ABFF", "0012ABFF\n", "0012AB", "۰۰۱۲ABFF"}) assertFalse(Uid.isValid(v));
    }
    @Test public void rejectsUnsupportedByteLengths() {
        for (int n : new int[]{0,1,3,5,6,8,9,11,100})
            assertThrows(IllegalArgumentException.class, () -> Uid.fromBytes(new byte[n]));
        assertThrows(IllegalArgumentException.class, () -> Uid.fromBytes(null));
    }
    @Test public void preservesByteOrderLeadingZerosAndUppercase() {
        assertEquals("00017F80", Uid.fromBytes(new byte[]{0, 1, 127, (byte)128}));
        assertEquals("0001020304ABFF", Uid.fromBytes(new byte[]{0, 1, 2, 3, 4, (byte)171, (byte)255}));
        assertEquals("00010203040506070809", Uid.fromBytes(new byte[]{0,1,2,3,4,5,6,7,8,9}));
    }
}
