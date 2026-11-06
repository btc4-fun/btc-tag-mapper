package com.saebm.btcnfcseller.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class TagProvisioningTest {
    private static final String ADDRESS = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
    @Test public void lookupBindsExactSavedMappingAndNeverArmsOnItsOwn() throws Exception {
        TagProvisioning flow = new TagProvisioning();
        java.lang.reflect.Method begin;
        try { begin = flow.getClass().getMethod("beginLookup", String.class, String.class); }
        catch (NoSuchMethodException e) { throw new AssertionError("Missing explicit lookup admission", e); }
        long ticket = (Long) begin.invoke(flow, "00010203", ADDRESS);
        flow.getClass().getMethod("completeLookup", long.class, String.class, String.class, String.class)
            .invoke(flow, ticket, ADDRESS, "26b1z00001", "https://btc4.fun/s/26b1z00001");
        assertEquals("REVIEW", flow.state().toString());
    }
    @Test public void explicitWriteRequiresConsentThenMatchingFreshTag() throws Exception {
        TagProvisioning flow = new TagProvisioning();
        long ticket = flow.beginLookup("00010203", ADDRESS);
        flow.completeLookup(ticket, ADDRESS, "26b1z00001", "https://btc4.fun/s/26b1z00001");
        try { flow.getClass().getMethod("armWrite").invoke(flow); }
        catch (NoSuchMethodException e) { throw new AssertionError("No explicit write consent", e); }
        assertEquals("WRITE_ARMED", flow.state().toString());
    }
    @Test public void tagWriteTransportIsARequiredBoundary() throws Exception {
        try { Class.forName("com.saebm.btcnfcseller.core.TagProvisioning$Port"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Missing real tag I/O boundary", e); }
    }
    static class TagPort implements TagProvisioning.Port {
        String id = "00010203"; boolean writable = true, lockable = true, mismatch, lost;
        int size = 1024, writes, locks, connects; byte[] content = new byte[0];
        Runnable beforeWrite = () -> {};
        public String uid() { return id; }
        public void connect() { connects++; }
        public boolean writable() { return writable; }
        public boolean canLock() { return lockable; }
        public int capacity() { return size; }
        public byte[] message(String url) { return url.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        public void write(byte[] bytes) { writes++; content = bytes; if (lost) throw new IllegalStateException("lost"); }
        public byte[] read() { return mismatch ? new byte[0] : content; }
        public boolean lock() { locks++; writable = false; return true; }
        public void close() {}
    }
    private TagProvisioning reviewed() {
        TagProvisioning flow = new TagProvisioning();
        long ticket = flow.beginLookup("00010203", ADDRESS);
        assertTrue(flow.completeLookup(ticket, ADDRESS, "26b1z00001", "https://btc4.fun/s/26b1z00001"));
        return flow;
    }
    @Test public void writesOnceAndReadsBackWithoutLocking() {
        TagProvisioning flow = reviewed(); TagPort tag = new TagPort();
        long ticket = flow.armWrite(); flow.discovered(ticket, tag);
        assertEquals(1, tag.writes); assertEquals(0, tag.locks);
        assertEquals("WRITTEN_UNLOCKED", flow.state().toString());
        flow.discovered(ticket, tag); assertEquals(1, tag.writes);
    }
    @Test public void cancelDuringPreflightPreventsUnstartedWrite() throws Exception {
        TagProvisioning flow = reviewed(); long ticket = flow.armWrite();
        TagPort tag = new TagPort() {
            public boolean canLock() {
                try { flow.getClass().getMethod("cancel").invoke(flow); }
                catch (ReflectiveOperationException e) { throw new AssertionError("Missing cancel fence", e); }
                return true;
            }
        };
        flow.discovered(ticket, tag);
        assertEquals(0, tag.writes);
        assertEquals("FAILED_UNKNOWN", flow.state().toString());
    }
    @Test public void lockRequiresSeparateConsentAndTwoFreshDiscoveries() throws Exception {
        TagProvisioning flow = reviewed(); TagPort first = new TagPort();
        flow.discovered(flow.armWrite(), first);
        long lockTicket;
        try { lockTicket = (Long) flow.getClass().getMethod("armLock").invoke(flow); }
        catch (NoSuchMethodException e) { throw new AssertionError("Missing irreversible lock consent", e); }
        TagPort second = new TagPort(); second.content = first.content;
        flow.discovered(lockTicket, second);
        assertEquals(1, second.locks); assertEquals(0, second.writes);
        assertEquals("LOCK_AWAITING_VERIFICATION", flow.state().toString());
        flow.discovered(lockTicket, second);
        assertEquals("LOCK_AWAITING_VERIFICATION", flow.state().toString());
        long verifyTicket = (Long) flow.getClass().getMethod("armVerify").invoke(flow);
        TagPort third = new TagPort(); third.content = first.content; third.writable = false;
        flow.discovered(verifyTicket, third);
        assertEquals("LOCKED_VERIFIED", flow.state().toString());
    }
    @Test public void unsafeTagsNeverWriteOrLock() {
        for (int scenario = 0; scenario < 4; scenario++) {
            TagProvisioning flow = reviewed(); TagPort tag = new TagPort();
            if (scenario == 0) tag.id = "00010204";
            if (scenario == 1) tag.size = 1;
            if (scenario == 2) tag.writable = false;
            flow.discovered(flow.armWrite(), scenario == 3 ? null : tag);
            assertEquals(0, tag.writes); assertEquals(0, tag.locks);
            assertEquals("FAILED_UNKNOWN", flow.state().toString());
        }
    }
    @Test public void readbackMismatchOrTagLossNeverAuthorizesLock() {
        for (boolean loss : new boolean[]{false, true}) {
            TagProvisioning flow = reviewed(); TagPort tag = new TagPort(); tag.lost = loss; tag.mismatch = !loss;
            flow.discovered(flow.armWrite(), tag);
            assertEquals("FAILED_UNKNOWN", flow.state().toString());
            assertThrows(IllegalStateException.class, flow::armLock); assertEquals(0, tag.locks);
        }
    }
    @Test public void noConsentAndCancelledCallbacksHaveNoEffects() {
        TagProvisioning flow = reviewed(); TagPort tag = new TagPort();
        flow.discovered(1, tag); assertEquals(0, tag.connects);
        assertThrows(IllegalStateException.class, flow::armLock);
        long ticket = flow.armWrite(); flow.cancel(); flow.discovered(ticket, tag);
        assertEquals(0, tag.connects); assertEquals(0, tag.writes);
        long lookup = flow.beginLookup("00010203", ADDRESS); flow.cancel();
        assertFalse(flow.completeLookup(lookup, ADDRESS, "26b1z00001", "https://btc4.fun/s/26b1z00001"));
    }
    @Test public void shortAndLegacySerialShapesAreBothAccepted() {
        for (String serial : new String[]{"6a6dk0", "6a6dkz", "26b1z00001"}) {
            TagProvisioning flow = new TagProvisioning();
            long ticket = flow.beginLookup("00010203", ADDRESS);
            assertTrue(serial, flow.completeLookup(ticket, ADDRESS, serial, "https://btc4.fun/s/" + serial));
        }
        for (String serial : new String[]{"6a6d", "6A6dk0"}) {
            TagProvisioning flow = new TagProvisioning();
            long ticket = flow.beginLookup("00010203", ADDRESS);
            assertFalse(serial, flow.completeLookup(ticket, ADDRESS, serial, "https://btc4.fun/s/" + serial));
        }
    }
    @Test public void invalidServerMappingFailsClosed() {
        String[][] cases = {{"other", "26b1z00001", "https://btc4.fun/s/26b1z00001"},
            {ADDRESS, "../bad", "https://btc4.fun/s/../bad"},
            {ADDRESS, "26b1z00001", "https://evil.invalid/s/26b1z00001"},
            {ADDRESS, "26b1z00001", "https://btc4.fun/s/26b1z00001?x=1"},
            {ADDRESS, "26b1z00001", "http://btc4.fun/s/26b1z00001"}};
        for (String[] values : cases) {
            TagProvisioning flow = new TagProvisioning(); long ticket = flow.beginLookup("00010203", ADDRESS);
            assertFalse(flow.completeLookup(ticket, values[0], values[1], values[2]));
            assertThrows(IllegalStateException.class, flow::armWrite);
        }
    }
    @Test public void lockRejectsDifferentContentUidOrCapability() {
        for (int scenario = 0; scenario < 3; scenario++) {
            TagProvisioning flow = reviewed(); TagPort written = new TagPort(); flow.discovered(flow.armWrite(), written);
            TagPort fresh = new TagPort(); fresh.content = written.content;
            if (scenario == 0) fresh.id = "00010204";
            if (scenario == 1) fresh.mismatch = true;
            if (scenario == 2) fresh.lockable = false;
            flow.discovered(flow.armLock(), fresh);
            assertEquals(0, fresh.locks); assertEquals("FAILED_UNKNOWN", flow.state().toString());
        }
    }
    @Test public void cancellationDuringStartedWriteKeepsUnknownAfterLateSuccess() {
        TagProvisioning flow = reviewed();
        TagPort tag = new TagPort() { public void write(byte[] bytes) { super.write(bytes); flow.cancel(); } };
        flow.discovered(flow.armWrite(), tag);
        assertEquals(1, tag.writes); assertEquals("FAILED_UNKNOWN", flow.state().toString());
        assertThrows(IllegalStateException.class, flow::armLock);
    }
    @Test public void failedOrCancelledIoRemainsUnknownAcrossRepeatedCancellation() {
        TagProvisioning flow = reviewed(); TagPort tag = new TagPort(); tag.lost = true;
        flow.discovered(flow.armWrite(), tag); flow.cancel(); flow.cancel();
        assertEquals("FAILED_UNKNOWN", flow.state().toString());
    }
    @Test public void provisioningStartsWithoutAnyWriteAuthority() throws Exception {
        Class<?> type;
        try { type = Class.forName("com.saebm.btcnfcseller.core.TagProvisioning"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Missing consent state machine", e); }
        Object flow = type.getConstructor().newInstance();
        assertEquals("IDLE", type.getMethod("state").invoke(flow).toString());
    }
}
