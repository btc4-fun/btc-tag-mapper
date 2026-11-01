package com.saebm.btcnfcseller;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import com.saebm.btcnfcseller.core.TagProvisioning;
import com.saebm.btcnfcseller.core.Uid;

/** No formatting fallback; every read uses the live tag, never cached NDEF. */
final class NdefPort implements TagProvisioning.Port {
    private final Tag tag;
    private final Ndef ndef;
    NdefPort(Tag tag) { this.tag = tag; this.ndef = Ndef.get(tag); }
    static byte[] uriMessage(String url) { return new NdefMessage(new NdefRecord[]{NdefRecord.createUri(url)}).toByteArray(); }
    public String uid() { return Uid.fromBytes(tag.getId()); }
    public void connect() throws Exception {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) throw new IllegalStateException("I/O on UI thread");
        if (ndef == null) throw new java.io.IOException("Already formatted NDEF required");
        ndef.connect();
    }
    public boolean writable() { return ndef.isWritable(); }
    public boolean canLock() { return ndef.canMakeReadOnly(); }
    public int capacity() { return ndef.getMaxSize(); }
    public byte[] message(String url) { return uriMessage(url); }
    public void write(byte[] bytes) throws Exception { ndef.writeNdefMessage(new NdefMessage(bytes)); }
    public byte[] read() throws Exception {
        NdefMessage message = ndef.getNdefMessage();
        return message == null ? new byte[0] : message.toByteArray();
    }
    public boolean lock() throws Exception { return ndef.makeReadOnly(); }
    public void close() throws Exception { if (ndef != null) ndef.close(); }
}
