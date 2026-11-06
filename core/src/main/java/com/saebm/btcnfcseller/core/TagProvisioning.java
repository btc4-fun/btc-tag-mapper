package com.saebm.btcnfcseller.core;

/** Volatile, per-foreground-session authorization; never persisted. */
public final class TagProvisioning {
    public interface Port extends AutoCloseable {
        String uid();
        void connect() throws Exception;
        boolean writable() throws Exception;
        boolean canLock() throws Exception;
        int capacity() throws Exception;
        byte[] message(String url) throws Exception;
        void write(byte[] message) throws Exception;
        byte[] read() throws Exception;
        boolean lock() throws Exception;
        void close() throws Exception;
    }
    public void discovered(long ticket, Port port) {
        String expectedUrl;
        State operation;
        synchronized (this) {
            if (ticket != generation || (state != State.WRITE_ARMED && state != State.LOCK_ARMED && state != State.VERIFY_ARMED)) return;
            operation = state;
            state = State.BUSY;
            expectedUrl = url;
        }
        try (Port tag = port) {
            if (tag == null || !uid().equals(tag.uid())) throw new IllegalStateException("Wrong or unsupported tag");
            tag.connect();
            byte[] message = tag.message(expectedUrl);
            State completed;
            if (operation == State.WRITE_ARMED) {
                if (!tag.writable() || tag.capacity() < message.length) throw new IllegalStateException("Not writable or too small");
                tag.canLock();
                if (!admitEffect(ticket)) return;
                tag.write(message);
                if (!java.util.Arrays.equals(message, tag.read())) throw new IllegalStateException("Readback mismatch");
                completed = State.WRITTEN_UNLOCKED;
            } else {
                if (!java.util.Arrays.equals(message, tag.read())) throw new IllegalStateException("Fresh content mismatch");
                if (operation == State.LOCK_ARMED) {
                    if (!tag.writable() || !tag.canLock()) throw new IllegalStateException("Cannot lock");
                    if (!admitEffect(ticket)) return;
                    if (!tag.lock()) throw new IllegalStateException("Lock rejected");
                    completed = State.LOCK_AWAITING_VERIFICATION;
                } else {
                    if (tag.writable()) throw new IllegalStateException("Still writable");
                    completed = State.LOCKED_VERIFIED;
                }
            }
            synchronized (this) { if (ticket == generation) state = completed; }
        } catch (Exception e) {
            synchronized (this) { if (ticket == generation) state = State.FAILED_UNKNOWN; }
        }
    }
    // Linearization point: after admission the platform call is in flight, not undoable.
    private synchronized boolean admitEffect(long ticket) { return ticket == generation && state == State.BUSY; }
    public synchronized void cancel() {
        boolean uncertain = state == State.FAILED_UNKNOWN || state == State.BUSY || state == State.WRITTEN_UNLOCKED || state == State.LOCK_ARMED || state == State.LOCK_AWAITING_VERIFICATION || state == State.VERIFY_ARMED;
        generation++;
        state = uncertain ? State.FAILED_UNKNOWN : State.IDLE;
        uid = address = serial = url = null;
    }
    public enum State { IDLE, LOOKUP, REVIEW, WRITE_ARMED, BUSY, WRITTEN_UNLOCKED, LOCK_ARMED, LOCK_AWAITING_VERIFICATION, VERIFY_ARMED, LOCKED_VERIFIED, FAILED_UNKNOWN }
    public synchronized long armWrite() {
        if (state != State.REVIEW) throw new IllegalStateException("Review required");
        state = State.WRITE_ARMED;
        return ++generation;
    }
    public synchronized long armLock() {
        if (state != State.WRITTEN_UNLOCKED) throw new IllegalStateException("Verified write required");
        state = State.LOCK_ARMED;
        return ++generation;
    }
    public synchronized long armVerify() {
        if (state != State.LOCK_AWAITING_VERIFICATION) throw new IllegalStateException("Lock attempt required");
        state = State.VERIFY_ARMED;
        return ++generation;
    }
    private State state = State.IDLE;
    private long generation;
    private String uid, address, serial, url;
    public synchronized State state() { return state; }
    public synchronized long generation() { return generation; }
    public synchronized String uid() { return uid; }
    public synchronized String address() { return address; }
    public synchronized String serial() { return serial; }
    public synchronized String url() { return url; }
    public synchronized long beginLookup(String savedUid, String savedAddress) {
        generation++;
        state = State.IDLE;
        uid = address = serial = url = null;
        if (!Uid.isValid(savedUid) || !BitcoinAddress.isValid(savedAddress))
            throw new IllegalArgumentException("Invalid saved mapping");
        uid = savedUid; address = savedAddress; state = State.LOOKUP;
        return generation;
    }
    public synchronized boolean failLookup(long ticket) {
        if (ticket != generation || state != State.LOOKUP) return false;
        state = State.FAILED_UNKNOWN; return true;
    }
    public synchronized boolean completeLookup(long ticket, String returnedAddress, String returnedSerial, String returnedUrl) {
        if (ticket != generation || state != State.LOOKUP) return false;
        // Exact backend SERIAL_PATTERN, not a claim of issuance.
        String pattern = "(?=[A-Za-z0-9][A-Za-z0-9_\\-]{0,63}$)[A-Za-z0-9_\\-]*"
            + "(?:"
            + "[0-9]{2}[0-9ab][0-9a-u][0-9a-mz](?:[0-5][0-9][0-5][0-9]|[0-9a-zA-X]{2})[0-9a-z]+"
            + "|"
            + "[0-9a-z][0-9ab][0-9a-u][0-9a-mz][0-9a-z][0-9a-z]+"
            + ")";
        if (!address.equals(returnedAddress) || returnedSerial == null || !returnedSerial.matches(pattern)
                || !("https://btc4.fun/s/" + returnedSerial).equals(returnedUrl)) {
            state = State.FAILED_UNKNOWN;
            return false;
        }
        serial = returnedSerial; url = returnedUrl; state = State.REVIEW;
        return true;
    }
}
