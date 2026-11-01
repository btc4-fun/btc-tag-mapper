package com.saebm.btcnfcseller;

import android.app.AlertDialog;
import android.app.Application;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Looper;
import android.widget.TextView;
import com.saebm.btcnfcseller.core.TagProvisioning;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowNfcAdapter;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real activity/reader callbacks with synthetic tags; no radio or network I/O. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ProvisioningReaderTest {
    private static final String ADDRESS = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
    public static class TagActivity extends MainActivity {
        TagProvisioning.Port tagPort;
        protected TagProvisioning.Port openTagPort(Tag tag) { return tagPort; }
    }
    private ActivityController<TagActivity> controller;
    private MainActivity activity;
    private ShadowNfcAdapter radio;
    private TagProvisioning flow;

    @Before public void setUp() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app.getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC, true);
        ShadowNfcAdapter.setNfcHardwareExists(true);
        radio = shadowOf(NfcAdapter.getDefaultAdapter(app));
        radio.setEnabled(true);
        controller = Robolectric.buildActivity(TagActivity.class).setup();
        activity = controller.get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning");
        field.setAccessible(true);
        flow = (TagProvisioning)field.get(activity);
    }

    @After public void tearDown() { controller.pause().stop().destroy(); }

    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private TextView view(String tag) { return activity.getWindow().getDecorView().findViewWithTag(tag); }
    private void dispatch(int lastByte) throws Exception {
        Tag tag = (Tag)Tag.class.getMethod("createMockTag", byte[].class, int[].class, Bundle[].class)
            .invoke(null, new byte[]{0, 1, 2, (byte)lastByte}, new int[0], new Bundle[0]);
        radio.dispatchTagDiscovered(tag);
        idle();
    }
    private AlertDialog disclosure() {
        try (CaptureStore store = new CaptureStore(activity)) { store.save("00010203", ADDRESS); }
        view("history").performClick();
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idle();
        return ShadowAlertDialog.getLatestAlertDialog();
    }
    private void assertCaptureOnly() throws Exception {
        assertFalse(view("writeTag").isEnabled());
        assertFalse(view("lockTag").isEnabled());
        assertFalse(view("verifyLock").isEnabled());
        assertNull(flow.url());
        dispatch(4);
        assertEquals("00010204", view("uid").getText().toString());
        assertTrue(flow.state() == TagProvisioning.State.IDLE || flow.state() == TagProvisioning.State.FAILED_UNKNOWN);
    }

    private void radioEnabled(boolean enabled) {
        radio.setEnabled(enabled);
        activity.sendBroadcast(new android.content.Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
        idle();
    }
    private void reviewMapping() {
        long ticket = flow.beginLookup("00010203", ADDRESS);
        activity.lookupFinished(ticket, new com.saebm.btcnfcseller.core.AddressTransport.Response(200,
            "{\"address\":\"" + ADDRESS + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}", null));
    }
    private static class MemoryTag implements TagProvisioning.Port {
        byte[] bytes;
        int locks;
        public String uid() { return "00010203"; }
        public void connect() { }
        public boolean writable() { return true; }
        public boolean canLock() { return true; }
        public int capacity() { return 1024; }
        public byte[] message(String url) { return url.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        public void write(byte[] message) { bytes = message; }
        public byte[] read() { return bytes; }
        public boolean lock() { locks++; return true; }
        public void close() { }
    }
    private MemoryTag writtenMapping() throws Exception {
        reviewMapping();
        MemoryTag tag = new MemoryTag();
        flow.discovered(flow.armWrite(), tag);
        java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("renderProvisioning");
        render.setAccessible(true); render.invoke(activity);
        return tag;
    }

    private void finishIo() throws Exception { executor().submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);idle(); }
    @Test public void completeConfirmedWriteLockFreshVerifyAndCancelSession() throws Exception {
        MemoryTag tag=new MemoryTag() {
            public boolean writable(){return locks==0;}
        };
        controller.get().tagPort=tag;
        armWrite();
        assertFalse(view("lockTag").isEnabled());assertFalse(view("verifyLock").isEnabled());
        dispatch(3);finishIo();
        assertEquals(TagProvisioning.State.WRITTEN_UNLOCKED,flow.state());
        assertTrue(view("lockTag").isEnabled());assertFalse(view("verifyLock").isEnabled());
        assertTrue(view("provisionHelp").getText().toString().contains("جداگانه"));
        view("lockTag").performClick();
        assertEquals(0,tag.locks);assertEquals(TagProvisioning.State.WRITTEN_UNLOCKED,flow.state());
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();
        NfcAdapter.ReaderCallback old=callback();
        dispatch(3);finishIo();
        assertEquals(1,tag.locks);assertEquals(TagProvisioning.State.LOCK_AWAITING_VERIFICATION,flow.state());
        assertFalse(view("lockTag").isEnabled());assertTrue(view("verifyLock").isEnabled());
        view("verifyLock").performClick();idle();
        assertEquals(TagProvisioning.State.VERIFY_ARMED,flow.state());
        Tag stale=(Tag)Tag.class.getMethod("createMockTag",byte[].class,int[].class,Bundle[].class).invoke(null,new byte[]{0,1,2,3},new int[0],new Bundle[0]);
        old.onTagDiscovered(stale);finishIo();
        assertEquals(TagProvisioning.State.VERIFY_ARMED,flow.state());
        dispatch(3);finishIo();
        assertEquals(TagProvisioning.State.LOCKED_VERIFIED,flow.state());assertEquals(1,tag.locks);
        assertFalse(view("verifyLock").isEnabled());
        view("cancelProvisioning").performClick();idle();controller.pause().resume();
        assertCaptureOnly();assertThrows(IllegalStateException.class,flow::armLock);assertThrows(IllegalStateException.class,flow::armVerify);
    }
    @Test public void wrongContentBeforeLockNeverLocksOrVerifies() throws Exception {
        MemoryTag tag=writtenMapping();tag.bytes=new byte[]{1};controller.get().tagPort=tag;
        view("lockTag").performClick();ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();
        dispatch(3);finishIo();assertEquals(0,tag.locks);assertEquals(TagProvisioning.State.FAILED_UNKNOWN,flow.state());
        assertFalse(view("verifyLock").isEnabled());assertFalse(view("lockTag").isEnabled());
    }
    @Test public void radioOffRevokesLockAuthorityAndOnOnlyCaptures() throws Exception {
        MemoryTag tag = writtenMapping();
        view("lockTag").performClick();
        AlertDialog consent = ShadowAlertDialog.getLatestAlertDialog();
        consent.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        assertEquals(TagProvisioning.State.LOCK_ARMED, flow.state());
        long oldTicket = flow.generation();
        radioEnabled(false);
        assertEquals(TagProvisioning.State.FAILED_UNKNOWN, flow.state());
        assertFalse(radio.isInReaderMode());
        radioEnabled(true);
        consent.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        flow.discovered(oldTicket, tag);
        assertEquals(0, tag.locks);
        assertCaptureOnly();
    }

    private NfcAdapter.ReaderCallback callback() throws Exception {
        java.lang.reflect.Field field = ShadowNfcAdapter.class.getDeclaredField("readerCallback");
        field.setAccessible(true); return (NfcAdapter.ReaderCallback)field.get(radio);
    }
    private java.util.concurrent.ExecutorService executor() throws Exception {
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("io");
        field.setAccessible(true); return (java.util.concurrent.ExecutorService)field.get(activity);
    }
    private void armWrite() {
        reviewMapping();
        view("writeTag").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
    }
    @Test public void cancelDefersReaderRestartUntilQueuedTagIoHasFinished() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Future<?> blocker = executor().submit(() -> {
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        MemoryTag tag = new MemoryTag();
        controller.get().tagPort = tag;
        try {
            armWrite();
            NfcAdapter.ReaderCallback installed = callback();
            dispatch(3);
            view("cancelProvisioning").performClick(); idle();
            assertSame("Do not restart reader while a tag operation is queued or active", installed, callback());
            release.countDown(); blocker.get(5, java.util.concurrent.TimeUnit.SECONDS);
            executor().submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS); idle();
            assertNull("Cancelled queued write must have no effect", tag.bytes);
            assertEquals(0, tag.locks);
            assertCaptureOnly();
        } finally { release.countDown(); }
    }

    @Test public void cancellationDuringAdmittedWritePreservesUnknownAndDefersReaderRestart() throws Exception {
        try { MainActivity.class.getDeclaredMethod("openTagPort", Tag.class); }
        catch (NoSuchMethodException e) { throw new AssertionError("Missing injectable real tag I/O boundary", e); }
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        MemoryTag tag = new MemoryTag() {
            public void write(byte[] message) {
                entered.countDown();
                try { assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                super.write(message);
            }
        };
        controller.get().tagPort = tag;
        try {
            armWrite();
            NfcAdapter.ReaderCallback installed = callback();
            dispatch(3);
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(TagProvisioning.State.BUSY, flow.state());
            view("cancelProvisioning").performClick(); idle();
            assertEquals(TagProvisioning.State.FAILED_UNKNOWN, flow.state());
            assertSame(installed, callback());
            radioEnabled(false); radioEnabled(true);
            assertSame("Radio bounce must not restart an active connection", installed, callback());
            release.countDown();
            executor().submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS); idle();
            assertNotNull("An admitted hardware effect is not undone", tag.bytes);
            assertEquals(TagProvisioning.State.FAILED_UNKNOWN, flow.state());
            assertCaptureOnly();
        } finally { release.countDown(); }
    }

    @Test public void disclosureBackRestoresActualCaptureCallback() throws Exception {
        dispatch(3); disclosure().cancel(); idle(); assertCaptureOnly();
    }
    @Test public void overwriteNegativeRestoresActualCaptureCallback() throws Exception {
        dispatch(3); reviewMapping(); view("writeTag").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle(); assertCaptureOnly();
    }
    @Test public void overwriteBackRestoresActualCaptureCallback() throws Exception {
        dispatch(3); reviewMapping(); view("writeTag").performClick();
        ShadowAlertDialog.getLatestAlertDialog().cancel(); idle(); assertCaptureOnly();
    }
    @Test public void lockNegativeRestoresCaptureWithoutHidingUnknown() throws Exception {
        dispatch(3); writtenMapping(); view("lockTag").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle(); assertEquals(TagProvisioning.State.FAILED_UNKNOWN, flow.state()); assertCaptureOnly();
    }
    @Test public void lockBackRestoresCaptureWithoutHidingUnknown() throws Exception {
        dispatch(3); writtenMapping(); view("lockTag").performClick();
        ShadowAlertDialog.getLatestAlertDialog().cancel(); idle();
        assertEquals(TagProvisioning.State.FAILED_UNKNOWN, flow.state()); assertCaptureOnly();
    }
    @Test public void radioOffInvalidatesPendingLookupAndLateResponse() throws Exception {
        long ticket = flow.beginLookup("00010203", ADDRESS);
        radioEnabled(false); radioEnabled(true);
        assertFalse(flow.completeLookup(ticket, ADDRESS, "26b1z00001", "https://btc4.fun/s/26b1z00001"));
        assertCaptureOnly();
    }
    @Test public void radioOffDismissesReviewAndRejectsOldConsent() throws Exception {
        reviewMapping(); view("writeTag").performClick();
        AlertDialog old = ShadowAlertDialog.getLatestAlertDialog();
        radioEnabled(false); assertFalse(old.isShowing()); radioEnabled(true);
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); assertCaptureOnly();
    }
    @Test public void radioOffInvalidatesWriteArming() throws Exception {
        armWrite(); radioEnabled(false); radioEnabled(true); assertCaptureOnly();
    }
    @Test public void pauseDoesNotReinstallReaderOnCancellation() throws Exception {
        disclosure(); controller.pause();
        assertFalse(radio.isInReaderMode());
        view("cancelProvisioning").performClick(); idle();
        assertFalse(radio.isInReaderMode());
        controller.resume(); assertCaptureOnly();
    }

    @Test public void radioOffInvalidatesAwaitingLockVerification() throws Exception {
        MemoryTag tag = writtenMapping();
        flow.discovered(flow.armLock(), tag);
        assertEquals(TagProvisioning.State.LOCK_AWAITING_VERIFICATION, flow.state());
        radioEnabled(false); radioEnabled(true);
        assertEquals(TagProvisioning.State.FAILED_UNKNOWN, flow.state()); assertCaptureOnly();
    }
    @Test public void radioOffInvalidatesArmedVerification() throws Exception {
        MemoryTag tag = writtenMapping();
        flow.discovered(flow.armLock(), tag); flow.armVerify();
        radioEnabled(false); radioEnabled(true);
        assertEquals(TagProvisioning.State.FAILED_UNKNOWN, flow.state()); assertCaptureOnly();
    }
    @Test public void obsoleteCaptureCallbackCannotPopulateNewReader() throws Exception {
        NfcAdapter.ReaderCallback old = callback();
        disclosure().cancel(); idle();
        Tag tag = (Tag)Tag.class.getMethod("createMockTag", byte[].class, int[].class, Bundle[].class)
            .invoke(null, new byte[]{0, 1, 2, 9}, new int[0], new Bundle[0]);
        old.onTagDiscovered(tag); idle();
        assertEquals("", view("uid").getText().toString());
        assertCaptureOnly();
    }

    @Test public void disclosureNegativeRestoresActualCaptureCallback() throws Exception {
        dispatch(3);
        assertEquals("00010203", view("uid").getText().toString());
        disclosure().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertCaptureOnly();
    }
}
