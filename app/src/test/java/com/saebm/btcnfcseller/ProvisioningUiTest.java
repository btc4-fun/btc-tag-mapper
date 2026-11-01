package com.saebm.btcnfcseller;
import android.app.AlertDialog;
import android.view.View;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ProvisioningUiTest {
    static final String ADDRESS = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
    @org.junit.Before public void enableNfcHardware() {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(app.getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_NFC, true);
        org.robolectric.shadows.ShadowNfcAdapter.setNfcHardwareExists(true);
        org.robolectric.Shadows.shadowOf(android.nfc.NfcAdapter.getDefaultAdapter(app)).setEnabled(true);
    }
    @Test public void successfulExplicitLookupShowsMappingAndWriteNeedsOverwriteConfirmation() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        com.saebm.btcnfcseller.core.TagProvisioning flow;
        try { java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true); flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity); }
        catch (NoSuchFieldException e) { throw new AssertionError("Missing integrated authorization", e); }
        long ticket = flow.beginLookup("00010203", ADDRESS);
        java.lang.reflect.Method response = MainActivity.class.getDeclaredMethod("lookupFinished", long.class, com.saebm.btcnfcseller.core.AddressTransport.Response.class);
        response.setAccessible(true);
        response.invoke(activity, ticket, new com.saebm.btcnfcseller.core.AddressTransport.Response(200,
            "{\"address\":\"" + ADDRESS + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}", null));
        TextView mapping = activity.getWindow().getDecorView().findViewWithTag("provisionMapping");
        assertTrue(mapping.getText().toString().contains(ADDRESS));
        assertTrue(mapping.getText().toString().contains("00010203"));
        assertTrue(mapping.getText().toString().contains("https://btc4.fun/s/26b1z00001"));
        View arm = activity.getWindow().getDecorView().findViewWithTag("writeTag");
        assertTrue(arm.isEnabled()); arm.performClick();
        AlertDialog warning = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(((TextView)warning.findViewById(android.R.id.message)).getText().toString().contains("پاک"));
        assertEquals("REVIEW", flow.state().toString());
        warning.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("WRITE_ARMED", flow.state().toString());
    }
    @Test public void backgroundCancelsLookupAndRejectsLateResponse() throws Exception {
        org.robolectric.android.controller.ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true);
        com.saebm.btcnfcseller.core.TagProvisioning flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity);
        long ticket = flow.beginLookup("00010203", ADDRESS);
        controller.pause();
        assertEquals("IDLE", flow.state().toString());
        controller.resume();
        activity.lookupFinished(ticket, new com.saebm.btcnfcseller.core.AddressTransport.Response(200,
            "{\"address\":\"" + ADDRESS + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}", null));
        assertFalse(activity.getWindow().getDecorView().findViewWithTag("writeTag").isEnabled());
    }
    @Test public void editedAddressCancelsPriorAuthority() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true);
        com.saebm.btcnfcseller.core.TagProvisioning flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity);
        long ticket = flow.beginLookup("00010203", ADDRESS);
        flow.completeLookup(ticket, ADDRESS, "26b1z00001", "https://btc4.fun/s/26b1z00001");
        ((android.widget.EditText) activity.getWindow().getDecorView().findViewWithTag("address")).setText(ADDRESS);
        assertEquals("IDLE", flow.state().toString());
    }
    @Test public void disabledControlsExplainSessionPrerequisites() {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        TextView help=activity.getWindow().getDecorView().findViewWithTag("provisionHelp");
        assertNotNull("Missing next-step and disabled-reason guidance",help);
        assertTrue(help.getText().toString().contains("فهرست روزانه"));
        assertTrue(help.getText().toString().contains("همین نشست"));
        assertTrue(help.getText().toString().contains("بررسی مستقل"));
    }
    @Test public void lockControlsAreSeparateAndWriterModeDoesNotSkipNdef() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull("Separate irreversible lock missing", activity.getWindow().getDecorView().findViewWithTag("lockTag"));
        assertFalse(activity.getWindow().getDecorView().findViewWithTag("lockTag").isEnabled());
        assertNotNull(activity.getWindow().getDecorView().findViewWithTag("verifyLock"));
        java.lang.reflect.Method flags = MainActivity.class.getDeclaredMethod("readerFlags", boolean.class); flags.setAccessible(true);
        int writer = (Integer)flags.invoke(activity, true);
        assertEquals(0, writer & android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK);
        assertTrue((writer & android.nfc.NfcAdapter.FLAG_READER_NFC_A) != 0);
    }
    @Test public void rateLimitShowsDelayAndBlocksExplicitEarlyLookup() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true);
        com.saebm.btcnfcseller.core.TagProvisioning flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity);
        long ticket = flow.beginLookup("00010203", ADDRESS);
        activity.lookupFinished(ticket, new com.saebm.btcnfcseller.core.AddressTransport.Response(429, "{\"error\":\"rate_limited\",\"retry_after\":43}", "43"));
        assertTrue(((TextView)activity.getWindow().getDecorView().findViewWithTag("provisionStatus")).getText().toString().contains("43"));
        java.lang.reflect.Method start = MainActivity.class.getDeclaredMethod("startLookup", com.saebm.btcnfcseller.core.Capture.class); start.setAccessible(true);
        start.invoke(activity, new com.saebm.btcnfcseller.core.Capture("00010203", ADDRESS, "2026-11-01T00:00:00Z"));
        assertNotEquals("LOOKUP", flow.state().toString());
    }
    @Test public void mismatchedSuccessClearsBusyDisplayAndNeverEnablesWriting() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true);
        com.saebm.btcnfcseller.core.TagProvisioning flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity);
        long ticket = flow.beginLookup("00010203", ADDRESS);
        ((TextView)activity.getWindow().getDecorView().findViewWithTag("provisionStatus")).setText("waiting");
        activity.lookupFinished(ticket, new com.saebm.btcnfcseller.core.AddressTransport.Response(200,
            "{\"address\":\"wrong\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}", null));
        assertFalse(activity.getWindow().getDecorView().findViewWithTag("writeTag").isEnabled());
        assertNotEquals("waiting", ((TextView)activity.getWindow().getDecorView().findViewWithTag("provisionStatus")).getText().toString());
    }
    @Test public void duplicateJsonKeysNeverAuthorizeWriting() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true);
        com.saebm.btcnfcseller.core.TagProvisioning flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity);
        long ticket = flow.beginLookup("00010203", ADDRESS);
        activity.lookupFinished(ticket, new com.saebm.btcnfcseller.core.AddressTransport.Response(200,
            "{\"address\":\"wrong\",\"address\":\"" + ADDRESS + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}", null));
        assertFalse(activity.getWindow().getDecorView().findViewWithTag("writeTag").isEnabled());
    }
    @Test public void editingOfflineAddressDoesNotRetireCaptureReaderGeneration() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.lang.reflect.Field generation = MainActivity.class.getDeclaredField("readerGeneration"); generation.setAccessible(true);
        long before = generation.getLong(activity);
        ((android.widget.EditText)activity.getWindow().getDecorView().findViewWithTag("address")).setText(ADDRESS);
        assertEquals(before, generation.getLong(activity));
    }
    @Test public void cancelButtonReturnsCaptureModeWithoutHidingUnknownOutcome() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true);
        com.saebm.btcnfcseller.core.TagProvisioning flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity);
        long ticket = flow.beginLookup("00010203", ADDRESS); flow.failLookup(ticket);
        activity.getWindow().getDecorView().findViewWithTag("cancelProvisioning").performClick();
        java.lang.reflect.Method capture = MainActivity.class.getDeclaredMethod("captureReaderAllowed"); capture.setAccessible(true);
        assertTrue((Boolean)capture.invoke(activity));
        assertEquals("FAILED_UNKNOWN", flow.state().toString());
    }
    @Test public void oldOverwriteDialogCannotAuthorizeNewLookup() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("provisioning"); field.setAccessible(true);
        com.saebm.btcnfcseller.core.TagProvisioning flow = (com.saebm.btcnfcseller.core.TagProvisioning)field.get(activity);
        long first = flow.beginLookup("00010203", ADDRESS);
        String body = "{\"address\":\"" + ADDRESS + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}";
        activity.lookupFinished(first, new com.saebm.btcnfcseller.core.AddressTransport.Response(200, body, null));
        activity.getWindow().getDecorView().findViewWithTag("writeTag").performClick();
        AlertDialog old = ShadowAlertDialog.getLatestAlertDialog();
        long second = flow.beginLookup("00010204", ADDRESS);
        activity.lookupFinished(second, new com.saebm.btcnfcseller.core.AddressTransport.Response(200, body, null));
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("REVIEW", flow.state().toString());
    }
    public static class LocalActivity extends MainActivity {
        int requests;
        protected com.saebm.btcnfcseller.core.AddressTransport.Response requestLookup(com.saebm.btcnfcseller.core.AddressTransport request, String address) {
            requests++;
            return new com.saebm.btcnfcseller.core.AddressTransport.Response(200, "{\"address\":\"" + address + "\",\"serial\":\"26b1z00001\",\"lookup_url\":\"https://btc4.fun/s/26b1z00001\"}", null);
        }
    }
    @Test public void explicitSavedMappingConsentDispatchesExactlyOnce() throws Exception {
        try { MainActivity.class.getDeclaredMethod("requestLookup", com.saebm.btcnfcseller.core.AddressTransport.class, String.class); }
        catch (NoSuchMethodException e) { throw new AssertionError("Missing injectable request boundary", e); }
        LocalActivity activity = Robolectric.buildActivity(LocalActivity.class).setup().get();
        try (CaptureStore store = new CaptureStore(activity)) { store.save("00010203", ADDRESS); }
        activity.getWindow().getDecorView().findViewWithTag("history").performClick();
        org.robolectric.Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
        org.robolectric.Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(0, activity.requests);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        for (int i = 0; i < 100 && !activity.getWindow().getDecorView().findViewWithTag("writeTag").isEnabled(); i++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        assertEquals(1, activity.requests);
        assertTrue(activity.getWindow().getDecorView().findViewWithTag("writeTag").isEnabled());
        assertFalse(activity.getWindow().getDecorView().findViewWithTag("lockTag").isEnabled());
        activity.onPause(); activity.onDestroy();
    }
    @Test public void discoveryKeepsReaderAliveUntilIoCompletes() throws Exception {
        java.nio.file.Path source = java.nio.file.Paths.get("src/main/java/com/saebm/btcnfcseller/MainActivity.java");
        String code = new String(java.nio.file.Files.readAllBytes(source), java.nio.charset.StandardCharsets.UTF_8);
        int queued = code.indexOf("// Retire this callback");
        int io = code.indexOf("provisioning.discovered(ticket", queued);
        assertFalse("Disabling reader before connect can invalidate live Tag", code.substring(queued, io).contains("disableReaderMode"));
    }
    @Test public void savedRecordOffersSeparateDisclosedLookupNotManualSerial() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        try (CaptureStore store = new CaptureStore(activity)) { store.save("00010203", ADDRESS); }
        activity.getWindow().getDecorView().findViewWithTag("history").performClick();
        org.robolectric.Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
        org.robolectric.Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
        AlertDialog detail = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("Explicit saved-record lookup missing", detail.getButton(AlertDialog.BUTTON_NEUTRAL));
        assertEquals("دریافت سریال از سایت", detail.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
        detail.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        AlertDialog consent = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(((TextView)consent.findViewById(android.R.id.message)).getText().toString().contains("آدرس عمومی"));
        assertTrue(((TextView)consent.findViewById(android.R.id.message)).getText().toString().contains("https://btc4.fun"));
        consent.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        View arm = activity.getWindow().getDecorView().findViewWithTag("writeTag");
        assertNotNull(arm); assertFalse(arm.isEnabled());
    }
}
