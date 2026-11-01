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
}
