package com.saebm.btcnfcseller;

import static org.junit.Assert.*;
import android.app.Activity;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainActivityTest {
    @Test public void nfcToggleRefreshesWhileResumedAndStopsOnPause() {
        org.robolectric.shadows.ShadowNfcAdapter.setNfcHardwareExists(true);
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().getPackageManager())
            .setSystemFeature(android.content.pm.PackageManager.FEATURE_NFC, true);
        org.robolectric.android.controller.ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        android.nfc.NfcAdapter adapter = android.nfc.NfcAdapter.getDefaultAdapter(activity);
        org.robolectric.shadows.ShadowNfcAdapter shadow = org.robolectric.Shadows.shadowOf(adapter);
        shadow.setEnabled(true);
        activity.sendBroadcast(new android.content.Intent(android.nfc.NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(shadow.isInReaderMode());
        shadow.setEnabled(false);
        activity.sendBroadcast(new android.content.Intent(android.nfc.NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(shadow.isInReaderMode());
        assertTrue(((TextView) activity.getWindow().getDecorView().findViewWithTag("nfcStatus")).getText().toString().contains("خاموش"));
        controller.pause();
        shadow.setEnabled(true);
        activity.sendBroadcast(new android.content.Intent(android.nfc.NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(shadow.isInReaderMode());
    }

    @Test public void cancelledExportRemovesOwnedSnapshotButNotOtherFiles() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        java.io.File keep = new java.io.File(activity.getCacheDir(), "unrelated.txt");
        java.nio.file.Files.write(keep.toPath(), new byte[]{1});
        com.saebm.btcnfcseller.core.Capture capture = new com.saebm.btcnfcseller.core.Capture(
            "00010203", "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345", "2026-10-31T10:00:00Z");
        Object part = com.saebm.btcnfcseller.core.CsvExporter.export(java.util.Collections.singletonList(capture), "2026-10-31").get(0);
        java.lang.reflect.Method export = MainActivity.class.getDeclaredMethod("exportPart", part.getClass());
        export.setAccessible(true);
        export.invoke(activity, part);
        assertEquals(1, activity.getCacheDir().listFiles((dir, name) -> name.startsWith("capture-export-")).length);
        activity.onActivityResult(201, Activity.RESULT_CANCELED, null);
        assertEquals(0, activity.getCacheDir().listFiles((dir, name) -> name.startsWith("capture-export-")).length);
        assertTrue(keep.exists());
    }

    @Test public void entryAndScannerWindowsPreventSystemScreenshots() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue((activity.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0);
        Class<? extends Activity> scanner = Class.forName("com.saebm.btcnfcseller.SecureCaptureActivity").asSubclass(Activity.class);
        Activity capture = Robolectric.buildActivity(scanner).create().get();
        assertTrue((capture.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0);
    }

    @Test public void appliesSystemInsetsForAndroidEdgeToEdge() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        android.view.View content = activity.getWindow().getDecorView().findViewWithTag("root");
        android.view.WindowInsets insets = android.view.WindowInsets.class.getConstructor(android.graphics.Rect.class)
            .newInstance(new android.graphics.Rect(0, 40, 0, 60));
        content.dispatchApplyWindowInsets(insets);
        assertTrue("Status bar must not cover controls", content.getPaddingTop() >= 40);
        assertTrue("Navigation bar must not cover controls", content.getPaddingBottom() >= 60);
    }

    @Test public void invalidManualInputIsNeverPersistedInSavedState() {
        org.robolectric.android.controller.ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        android.widget.EditText input = controller.get().getWindow().getDecorView().findViewWithTag("address");
        input.setText("SYNTHETIC_NOT_A_PUBLIC_ADDRESS");
        android.os.Bundle state = new android.os.Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        assertEquals("", state.getString("address"));
        MainActivity restored = Robolectric.buildActivity(MainActivity.class).create(state).start().restoreInstanceState(state).resume().get();
        assertEquals("", ((TextView) restored.getWindow().getDecorView().findViewWithTag("address")).getText().toString());
    }

    @Test public void dailyHistoryOpensDetailsAndExportsViaDocumentPicker() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        try (CaptureStore store = new CaptureStore(activity)) {
            store.save("00010203", "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345");
        }
        android.view.View history = activity.getWindow().getDecorView().findViewWithTag("history");
        assertNotNull("Daily history missing", history);
        history.performClick();
        android.app.AlertDialog days = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        org.robolectric.Shadows.shadowOf(days).clickOnItem(0);
        android.app.AlertDialog records = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(1, records.getListView().getAdapter().getCount());
        records.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.app.AlertDialog parts = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        org.robolectric.Shadows.shadowOf(parts).clickOnItem(0);
        android.content.Intent picker = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, picker.getAction());
        assertEquals("text/csv", picker.getType());
        assertTrue(picker.getStringExtra(android.content.Intent.EXTRA_TITLE).endsWith(".csv"));
        android.net.Uri target = android.net.Uri.parse("content://synthetic-export/capture.csv");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        org.robolectric.Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(target, bytes);
        activity.onActivityResult(201, Activity.RESULT_OK, new android.content.Intent().setData(target));
        String exported = bytes.toString("UTF-8");
        assertTrue(exported.startsWith("uid,address,captured_at\n00010203,bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345,"));
        assertTrue(exported.endsWith("Z\n"));
        assertTrue(((TextView) activity.getWindow().getDecorView().findViewWithTag("result")).getText().toString().contains("ورود به سرور انجام نشده"));
    }

    @Test public void scannerRequestsCameraAndDeniedPermissionIsClear() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        android.view.View scan = activity.getWindow().getDecorView().findViewWithTag("scan");
        assertNotNull("Public QR scanner missing", scan);
        scan.performClick();
        org.robolectric.shadows.ShadowActivity.PermissionsRequest request = org.robolectric.Shadows.shadowOf(activity).getLastRequestedPermission();
        assertArrayEquals(new String[]{android.Manifest.permission.CAMERA}, request.requestedPermissions);
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
        TextView result = activity.getWindow().getDecorView().findViewWithTag("result");
        assertTrue(result.getText().toString().contains("مجوز دوربین"));
    }

    @Test public void reviewShowsFullSnapshotAndRequiresConfirmation() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.acceptTag(new byte[]{0, 1, 2, 3});
        android.widget.EditText input = activity.getWindow().getDecorView().findViewWithTag("address");
        input.setText("bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345");
        activity.getWindow().getDecorView().findViewWithTag("review").performClick();
        android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("Explicit confirmation dialog missing", dialog);
        TextView reviewedUid = dialog.findViewById(R.id.review_uid);
        TextView reviewedAddress = dialog.findViewById(R.id.review_address);
        assertEquals("00010203", reviewedUid.getText().toString());
        assertEquals(input.getText().toString(), reviewedAddress.getText().toString());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        TextView result = activity.getWindow().getDecorView().findViewWithTag("result");
        assertEquals("ثبت شد؛ فقط در حافظهٔ این دستگاه.", result.getText().toString());
        activity.acceptTag(new byte[]{0, 1, 2, 3});
        input.setText("bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345");
        activity.getWindow().getDecorView().findViewWithTag("review").performClick();
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("قبلاً ثبت شده؛ رکوردی تغییر نکرد.", result.getText().toString());
    }

    @Test public void tagReadRequiresExplicitReviewAndSurvivesRotation() throws Exception {
        org.robolectric.android.controller.ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        try { MainActivity.class.getDeclaredMethod("acceptTag", byte[].class).invoke(activity, (Object) new byte[]{0, 4, (byte) 0x80, (byte) 0xFF}); }
        catch (NoSuchMethodException e) { throw new AssertionError("Tag capture UI missing"); }
        TextView uid = activity.getWindow().getDecorView().findViewWithTag("uid");
        assertEquals("000480FF", uid.getText().toString());
        assertEquals(android.view.View.LAYOUT_DIRECTION_LTR, uid.getLayoutDirection());
        android.widget.EditText address = activity.getWindow().getDecorView().findViewWithTag("address");
        address.setText("bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345");
        android.os.Bundle state = new android.os.Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        MainActivity restored = Robolectric.buildActivity(MainActivity.class).create(state).start().restoreInstanceState(state).resume().visible().get();
        assertEquals("000480FF", ((TextView) restored.getWindow().getDecorView().findViewWithTag("uid")).getText().toString());
        assertEquals(address.getText().toString(), ((TextView) restored.getWindow().getDecorView().findViewWithTag("address")).getText().toString());
    }

    @Test public void startsOfflineWithExplicitSafetyAndNfcStatus() throws Exception {
        Class<?> type;
        try { type = Class.forName("com.saebm.btcnfcseller.MainActivity"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Seller activity is missing"); }
        Activity activity = Robolectric.buildActivity(type.asSubclass(Activity.class)).setup().get();
        TextView warning = activity.getWindow().getDecorView().findViewWithTag("warning");
        assertNotNull(warning);
        assertTrue(warning.getText().toString().contains("اصالت"));
        TextView status = activity.getWindow().getDecorView().findViewWithTag("nfcStatus");
        assertFalse(status.getText().toString().isEmpty());
        assertNotNull(activity.getWindow().getDecorView().findViewWithTag("review"));
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL,
            activity.getWindow().getDecorView().findViewWithTag("root").getLayoutDirection());
    }
}
