package com.saebm.btcnfcseller;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import com.saebm.btcnfcseller.core.Uid;
import android.app.AlertDialog;
import com.saebm.btcnfcseller.core.BitcoinAddress;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.saebm.btcnfcseller.core.Capture;
import com.saebm.btcnfcseller.core.CsvExporter;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView nfcStatus;
    private TextView uid;
    private EditText address;
    private NfcAdapter adapter;
    private TextView result;
    private CaptureStore store;
    private AlertDialog confirmation;
    private String pendingExport;
    private boolean observingNfc;
    private final android.content.BroadcastReceiver nfcChanges = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) {
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(intent.getAction())) refreshNfc();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        store = new CaptureStore(this);
        if (state != null) pendingExport = state.getString("pendingExport");
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setTag("root");
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(24, 24, 24, 24);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(24 + insets.getSystemWindowInsetLeft(), 24 + insets.getSystemWindowInsetTop(),
                24 + insets.getSystemWindowInsetRight(), 24 + insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);
        text("ثبت آفلاین فروشنده", "title");
        text("شناسهٔ تگ مدرک اصالت یا ضدکپی نیست. فقط آدرس عمومی را وارد کنید؛ کلمات بازیابی، کلید خصوصی یا تصویر داخل پاکت را وارد نکنید.", "warning");
        nfcStatus = text("", "nfcStatus");
        text("شناسهٔ تگ — نه سریال چاپی کیف‌پول", "uidLabel");
        uid = text(state == null ? "" : state.getString("uid", ""), "uid");
        uid.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        uid.setTextDirection(View.TEXT_DIRECTION_LTR);
        uid.setTextIsSelectable(true);
        address = new EditText(this);
        address.setTag("address");
        address.setId(R.id.address_input);
        address.setSaveEnabled(false);

        address.setHint("آدرس عمومی بیت‌کوین");
        address.setSingleLine(true);
        address.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        address.setTextDirection(View.TEXT_DIRECTION_LTR);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        address.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (state != null) address.setText(state.getString("address", ""));
        root.addView(address);
        Button scan = new Button(this);
        scan.setTag("scan");
        scan.setText("اسکن کد آدرس عمومی");
        scan.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 200);
            else startScanner();
        });
        root.addView(scan);
        Button review = new Button(this);
        review.setTag("review");
        review.setText("بررسی و تأیید اتصال");
        review.setOnClickListener(v -> review());
        root.addView(review);
        result = text("", "result");
        text("روزها بر اساس زمان هماهنگ جهانی هستند. صدور فایل به معنی ورود اطلاعات به سرور نیست. برای حفظ نسخهٔ خروجی، همهٔ بخش‌های شماره‌دار را ذخیره کنید.", "exportWarning");
        Button history = new Button(this);
        history.setTag("history");
        history.setText("فهرست روزانه و صدور فایل");
        history.setOnClickListener(v -> showDays());
        root.addView(history);
    }

    private void showDays() {
        try {
            List<String> days = store.days();
            if (days.isEmpty()) { result.setText("هنوز رکوردی ثبت نشده است."); return; }
            new AlertDialog.Builder(this).setTitle("روز ثبت — زمان هماهنگ جهانی")
                .setItems(days.toArray(new String[0]), (dialog, index) -> showDay(days.get(index)))
                .setNegativeButton("بستن", null).show();
        } catch (RuntimeException e) { result.setText("خواندن فهرست از حافظهٔ دستگاه انجام نشد."); }
    }

    private void showDay(String day) {
        try {
            List<Capture> captures = store.forDay(day);
            String[] labels = new String[captures.size()];
            for (int i = 0; i < captures.size(); i++) labels[i] = captures.get(i).uid;
            new AlertDialog.Builder(this).setTitle(day).setItems(labels, (dialog, index) -> {
                Capture capture = captures.get(index);
                LinearLayout details = new LinearLayout(this);
                details.setOrientation(LinearLayout.VERTICAL);
                addLtr(details, capture.uid, R.id.detail_uid);
                addLtr(details, capture.address, R.id.detail_address);
                addLtr(details, capture.capturedAt, R.id.detail_timestamp);
                new AlertDialog.Builder(this).setTitle("جزئیات رکورد ذخیره‌شده").setView(details)
                    .setPositiveButton("بازگشت", (d, w) -> showDay(day)).show();
            }).setPositiveButton("صدور فایل‌های این روز", (dialog, which) -> showParts(captures, day))
                .setNegativeButton("بستن", null).show();
        } catch (RuntimeException e) { result.setText("خواندن رکوردها انجام نشد."); }
    }

    private void showParts(List<Capture> captures, String day) {
        try {
            List<CsvExporter.Part> parts = CsvExporter.export(captures, day);
            if (parts.isEmpty()) { result.setText("این روز رکوردی ندارد."); return; }
            String[] names = new String[parts.size()];
            for (int i = 0; i < parts.size(); i++) names[i] = parts.get(i).filename;
            new AlertDialog.Builder(this).setTitle("هر بخش را جدا ذخیره کنید؛ همهٔ بخش‌ها لازم‌اند")
                .setItems(names, (dialog, index) -> exportPart(parts.get(index)))
                .setNegativeButton("بستن", null).show();
        } catch (RuntimeException e) { result.setText("ساخت فایل خروجی انجام نشد؛ هیچ رکوردی حذف نشده است."); }
    }

    private void exportPart(CsvExporter.Part part) {
        try {
            clearPendingExport();
            File snapshot = File.createTempFile("capture-export-", ".csv", getCacheDir());
            pendingExport = snapshot.getName();
            Files.write(snapshot.toPath(), part.bytes);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv").putExtra(Intent.EXTRA_TITLE, part.filename);
            startActivityForResult(intent, 201);
        } catch (IOException | RuntimeException e) {
            clearPendingExport();
            result.setText("آماده‌سازی خروجی یا انتخاب فایل ممکن نشد؛ دوباره تلاش کنید.");
        }
    }

    private void clearPendingExport() {
        if (pendingExport != null && pendingExport.matches("capture-export-[A-Za-z0-9-]+\\.csv")) {
            new File(getCacheDir(), pendingExport).delete();
        }
        pendingExport = null;
    }

    private void startScanner() {
        new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setCaptureActivity(SecureCaptureActivity.class)
            .setPrompt("فقط کد آدرس عمومی روی پاکت را اسکن کنید")
            .setBarcodeImageEnabled(false).setBeepEnabled(false).setOrientationLocked(false)
            .initiateScan();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 200) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startScanner();
            else result.setText("مجوز دوربین رد شد؛ آدرس عمومی را دستی وارد یا بچسبانید. برای اسکن، مجوز دوربین را در تنظیمات اپ فعال کنید.");
        }
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request == 201) {
            if (code != RESULT_OK || data == null || data.getData() == null) {
                clearPendingExport();
                result.setText("صدور فایل لغو شد؛ رکوردهای اصلی محفوظ‌اند.");
                return;
            }
            try {
                if (pendingExport == null || !pendingExport.matches("capture-export-[A-Za-z0-9-]+\\.csv"))
                    throw new IOException("No export snapshot");
                File snapshot = new File(getCacheDir(), pendingExport);
                try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (output == null) throw new IOException("No output stream");
                    Files.copy(snapshot.toPath(), output);
                }
                Files.deleteIfExists(snapshot.toPath());
                pendingExport = null;
                result.setText("این بخش ذخیره شد؛ برای بخش‌های دیگر دوباره فهرست روز را باز کنید. ورود به سرور انجام نشده است.");
            } catch (IOException | RuntimeException e) {
                clearPendingExport();
                result.setText("صدور کامل نشد؛ فایل مقصد ممکن است ناقص باشد. دوباره صادر کنید؛ رکوردهای اصلی محفوظ‌اند.");
            }
            return;
        }
        IntentResult scanned = IntentIntegrator.parseActivityResult(request, code, data);
        if (scanned != null) {
            if (scanned.getContents() == null) {
                result.setText("اسکن لغو شد یا دوربین در دسترس نیست؛ ورود دستی همچنان ممکن است.");
                return;
            }
            String publicAddress = PublicQr.address(scanned.getContents());
            if (publicAddress == null) {
                address.setText("");
                result.setText("کد اسکن‌شده آدرس عمومی معتبر و مجاز نیست؛ محتوای آن ذخیره نشد.");
            } else {
                address.setText(publicAddress);
                result.setText("آدرس خوانده شد؛ برای ثبت، شناسه و آدرس را تأیید کنید.");
            }
        }
    }

    private void review() {
        String selectedUid = uid.getText().toString();
        String selectedAddress = address.getText().toString();
        if (!Uid.isValid(selectedUid) || !BitcoinAddress.isValid(selectedAddress)) {
            result.setText("تگ و آدرس عمومی معتبر لازم است؛ آدرس شبکهٔ اصلی با جمع کنترلی درست وارد کنید.");
            return;
        }
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(24, 24, 24, 24);
        addLtr(details, selectedUid, R.id.review_uid);
        addLtr(details, selectedAddress, R.id.review_address);
        confirmation = new AlertDialog.Builder(this).setTitle("شناسه و آدرس کامل را تطبیق دهید")
            .setView(details).setNegativeButton("انصراف", null)
            .setPositiveButton("تأیید و ثبت", (dialog, which) -> {
                try {
                    CaptureStore.Result saved = store.save(selectedUid, selectedAddress);
                    switch (saved) {
                        case SAVED:
                            result.setText("ثبت شد؛ فقط در حافظهٔ این دستگاه.");
                            uid.setText(""); address.setText(""); break;
                        case DUPLICATE: result.setText("قبلاً ثبت شده؛ رکوردی تغییر نکرد."); break;
                        case UID_CONFLICT: result.setText("این تگ قبلاً به آدرس دیگری متصل شده؛ بازنویسی انجام نشد."); break;
                        case ADDRESS_CONFLICT: result.setText("این آدرس قبلاً به تگ دیگری متصل شده؛ بازنویسی انجام نشد."); break;
                    }
                } catch (RuntimeException e) {
                    result.setText("ذخیره‌سازی انجام نشد؛ فضای خالی دستگاه را بررسی و دوباره تلاش کنید.");
                }
            }).create();
        confirmation.show();
    }

    private void addLtr(LinearLayout container, String value, int id) {
        TextView view = new TextView(this);
        view.setId(id);
        view.setText(value);
        view.setTextSize(18);
        view.setTextIsSelectable(true);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        view.setTextDirection(View.TEXT_DIRECTION_LTR);
        view.setPadding(8, 16, 8, 16);
        container.addView(view);
    }

    @Override public void onResume() {
        super.onResume();
        if (!observingNfc) {
            android.content.IntentFilter filter = new android.content.IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= 33)
                registerReceiver(nfcChanges, filter, android.content.Context.RECEIVER_EXPORTED);
            else registerReceiver(nfcChanges, filter);
            observingNfc = true;
        }
        refreshNfc();
    }

    private void refreshNfc() {
        adapter = NfcAdapter.getDefaultAdapter(this);
        nfcStatus.setText(adapter == null ? "این دستگاه سخت‌افزار خواندن تگ ندارد."
            : adapter.isEnabled() ? "تگ را نزدیک گوشی نگه دارید." : "خواندن تگ خاموش است؛ آن را در تنظیمات روشن کنید.");
        if (adapter != null && adapter.isEnabled()) {
            adapter.enableReaderMode(this, tag -> {
                byte[] id = tag.getId();
                runOnUiThread(() -> acceptTag(id));
            }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        } else if (adapter != null) {
            adapter.disableReaderMode(this);
        }
    }

    void acceptTag(byte[] bytes) {
        if (confirmation != null && confirmation.isShowing()) return;
        try {
            uid.setText(Uid.fromBytes(bytes));
            nfcStatus.setText("شناسه خوانده شد؛ هنوز هیچ رکوردی ثبت نشده است.");
        } catch (IllegalArgumentException e) {
            uid.setText("");
            nfcStatus.setText("شناسهٔ تگ نامعتبر است؛ تگ چهار، هفت یا ده‌بایتی لازم است.");
        }
    }

    @Override protected void onPause() {
        if (observingNfc) {
            unregisterReceiver(nfcChanges);
            observingNfc = false;
        }
        if (adapter != null) adapter.disableReaderMode(this);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("uid", uid.getText().toString());
        String draft = address.getText().toString();
        state.putString("address", BitcoinAddress.isValid(draft) ? draft : "");
        state.putString("pendingExport", pendingExport);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (confirmation != null) confirmation.dismiss();
        store.close();
        super.onDestroy();
    }

    private TextView text(String value, String tag) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(18);
        view.setTag(tag);
        view.setPadding(0, 12, 0, 12);
        root.addView(view);
        return view;
    }
}
