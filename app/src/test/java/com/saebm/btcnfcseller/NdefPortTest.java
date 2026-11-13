package com.saebm.btcnfcseller;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NdefPortTest {
    @Test public void usesExactlyOneStandardUriRecordWithNoApplicationRecord() throws Exception {
        Class<?> type;
        try { type = Class.forName("com.saebm.btcnfcseller.NdefPort"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Missing Android NDEF transport", e); }
        byte[] bytes = (byte[]) type.getDeclaredMethod("uriMessage", String.class).invoke(null, "https://btc4.fun/s/26b1z00001");
        android.nfc.NdefMessage message = new android.nfc.NdefMessage(bytes);
        assertEquals(1, message.getRecords().length);
        assertEquals(android.nfc.NdefRecord.TNF_WELL_KNOWN, message.getRecords()[0].getTnf());
        assertArrayEquals(android.nfc.NdefRecord.RTD_URI, message.getRecords()[0].getType());
        assertEquals("https://btc4.fun/s/26b1z00001", message.getRecords()[0].toUri().toString());
    }

    /**
     * Pins the exact on-tag bytes against the NFC Forum URI RTD and Apple's documented
     * background-tag-reading requirement: one record, TNF well-known (0x01), type "U" (0x55),
     * and a URI identifier code of 0x04 for "https://". Apple reads the first URI record only,
     * so a second record or a non-"U" type would be ignored on iPhone.
     */
    @Test public void writesTheExactNfcForumUriRecordAppleBackgroundReadingRequires() throws Exception {
        byte[] bytes = (byte[]) NdefPort.class.getDeclaredMethod("uriMessage", String.class)
            .invoke(null, "https://btc4.fun/s/26b1z00001");
        // MB=1 ME=1 CF=0 SR=1 IL=0 TNF=0x01 -> 0xD1; type length 0x01; payload length 0x16 = 22.
        assertEquals((byte) 0xD1, bytes[0]);
        assertEquals((byte) 0x01, bytes[1]);
        assertEquals((byte) 0x16, bytes[2]);
        // Type "U" (0x55) is the NFC Forum URI RTD that Apple's background reading looks for.
        assertEquals((byte) 0x55, bytes[3]);
        // NFC Forum URI identifier code 0x04 abbreviates "https://".
        assertEquals((byte) 0x04, bytes[4]);
        assertEquals("btc4.fun/s/26b1z00001",
            new String(bytes, 5, bytes.length - 5, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(26, bytes.length);
    }

    /** The abbreviated prefix must rebuild the exact public URL the backend contract returns. */
    @Test public void abbreviatedPrefixRebuildsTheExactBackendUrl() throws Exception {
        for (String serial : new String[]{"26b1z00001", "6a6dk0"}) {
            String url = "https://btc4.fun/s/" + serial;
            byte[] bytes = (byte[]) NdefPort.class.getDeclaredMethod("uriMessage", String.class).invoke(null, url);
            android.nfc.NdefRecord record = new android.nfc.NdefMessage(bytes).getRecords()[0];
            assertEquals(url, record.toUri().toString());
            assertEquals(android.nfc.NdefRecord.TNF_WELL_KNOWN, record.getTnf());
            assertArrayEquals(android.nfc.NdefRecord.RTD_URI, record.getType());
        }
    }

    /** A short serial keeps the message far below the smallest common NTAG213 payload (137 bytes). */
    @Test public void messageFitsTheSmallestCommonNdefTag() throws Exception {
        byte[] bytes = (byte[]) NdefPort.class.getDeclaredMethod("uriMessage", String.class)
            .invoke(null, "https://btc4.fun/s/26b1z00001");
        assertTrue("NDEF message must fit an NTAG213 (137 usable bytes)", bytes.length <= 137);
    }
}
