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
import com.saebm.btcnfcseller.core.TagProvisioning;
import com.saebm.btcnfcseller.core.AddressTransport;
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
    private Button writeTag, lockTag, verifyLock;
    private final TagProvisioning provisioning = new TagProvisioning();
    private TextView provisionMapping, provisionStatus, provisionHelp;
    private AddressTransport lookup;
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean foreground;
    private long writerTicket;
    private long readerGeneration;
    // Main-thread ownership includes queued work, before the core enters BUSY.
    private boolean tagIoPending;
    private boolean readerRefreshPending;
    private AlertDialog provisionDialog;
    private long retryAt;
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
        if (state != null) { pendingExport = state.getString("pendingExport"); retryAt = state.getLong("retryAt", 0); }
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
        address.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (provisioning.state() != TagProvisioning.State.IDLE) cancelProvisioning();
            }
            public void afterTextChanged(android.text.Editable s) { }
        });
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
        writeTag = new Button(this);
        writeTag.setTag("writeTag"); writeTag.setText("تأیید بازنویسی تگ"); writeTag.setEnabled(false);
        root.addView(writeTag);
        provisionMapping = text("", "provisionMapping");
        provisionMapping.setTextDirection(View.TEXT_DIRECTION_LTR);
        provisionMapping.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        provisionMapping.setTextIsSelectable(true);
        provisionStatus = text("", "provisionStatus");
        provisionHelp = text("", "provisionHelp");
        lockTag = new Button(this); lockTag.setTag("lockTag"); lockTag.setText("قفل دائمی همین تگ"); lockTag.setEnabled(false); root.addView(lockTag);
        verifyLock = new Button(this); verifyLock.setTag("verifyLock"); verifyLock.setText("بررسی قفل با خواندن دوباره"); verifyLock.setEnabled(false); root.addView(verifyLock);
        Button cancel = new Button(this); cancel.setTag("cancelProvisioning"); cancel.setText("لغو عملیات تگ"); root.addView(cancel);
        cancel.setOnClickListener(v -> cancelProvisioning());
        lockTag.setOnClickListener(v -> {
            long consent = provisioning.generation();
            provisionDialog = new AlertDialog.Builder(this).setTitle("قفل دائمی و برگشت‌ناپذیر")
                .setMessage("فقط همین تگ و پیوند بررسی‌شده قفل می‌شود. پس از قفل، تغییر یا پاک‌کردن محتوا ممکن نیست. تگ را دور کنید؛ سپس تأیید کنید و دوباره نزدیک کنید.")
                .setNegativeButton("انصراف", (d, w) -> cancelProvisioning())
                .setPositiveButton("قفل دائمی را تأیید می‌کنم", (d, w) -> {
                    if (!foreground || consent != provisioning.generation() || provisioning.state() != TagProvisioning.State.WRITTEN_UNLOCKED) return;
                    writerTicket = provisioning.armLock(); renderProvisioning(); refreshNfc();
                }).setOnCancelListener(d -> cancelProvisioning()).show();
        });
        verifyLock.setOnClickListener(v -> {
            if (!foreground || provisioning.state() != TagProvisioning.State.LOCK_AWAITING_VERIFICATION) return;
            writerTicket = provisioning.armVerify(); renderProvisioning(); refreshNfc();
        });
        writeTag.setOnClickListener(v -> {
            long consent = provisioning.generation();
            provisionDialog = new AlertDialog.Builder(this).setTitle("بازنویسی همین تگ")
                .setMessage("محتوای قبلی تگ پاک می‌شود. شناسه، آدرس و سریال را با پاکت تطبیق دهید. فقط پیوند نمایش‌داده‌شده نوشته می‌شود؛ قفل خودکار نیست.")
                .setNegativeButton("انصراف", (d, w) -> cancelProvisioning())
                .setPositiveButton("تأیید بازنویسی", (d, w) -> {
                    if (!foreground || consent != provisioning.generation() || provisioning.state() != TagProvisioning.State.REVIEW) return;
                    writerTicket = provisioning.armWrite(); renderProvisioning(); refreshNfc();
                }).setOnCancelListener(d -> cancelProvisioning()).show();
        });
        text("روزها بر اساس زمان هماهنگ جهانی هستند. صدور فایل به معنی ورود اطلاعات به سرور نیست. برای حفظ نسخهٔ خروجی، همهٔ بخش‌های شماره‌دار را ذخیره کنید.", "exportWarning");
        Button history = new Button(this);
        history.setTag("history");
        history.setText("فهرست روزانه و صدور فایل");
        history.setOnClickListener(v -> showDays());
        root.addView(history);
        renderProvisioning();
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
                    .setNeutralButton("دریافت سریال از سایت", (d, w) -> discloseLookup(capture))
                    .setPositiveButton("بازگشت", (d, w) -> showDay(day)).show();
            }).setPositiveButton("صدور فایل‌های این روز", (dialog, which) -> showParts(captures, day))
                .setNegativeButton("بستن", null).show();
        } catch (RuntimeException e) { result.setText("خواندن رکوردها انجام نشد."); }
    }

    private void discloseLookup(Capture capture) {
        cancelProvisioning();
        provisionDialog = new AlertDialog.Builder(this).setTitle("ارسال اختیاری به سایت")
            .setMessage("فقط آدرس عمومی ذخیره‌شده برای یافتن سریال به سایت فرستاده می‌شود. ثبت و خروجی همچنان آفلاین هستند.\n\nhttps://btc4.fun")
            .setNegativeButton("انصراف", (d, w) -> cancelProvisioning())
            .setPositiveButton("ارسال آدرس عمومی", (d, w) -> startLookup(capture))
            .setOnCancelListener(d -> cancelProvisioning()).show();
    }

    private void startLookup(Capture capture) {
        if (!foreground) return;
        if (android.os.SystemClock.elapsedRealtime() < retryAt) {
            provisionStatus.setText("زمان انتظار سایت هنوز تمام نشده است؛ درخواست ارسال نشد."); return;
        }
        cancelProvisioning();
        long ticket = provisioning.beginLookup(capture.uid, capture.address);
        AddressTransport request = new AddressTransport(); lookup = request;
        renderProvisioning();
        io.execute(() -> {
            AddressTransport.Response response;
            try { response = requestLookup(request, capture.address); }
            catch (Exception e) { response = new AddressTransport.Response(503, "", null); }
            AddressTransport.Response completed = response;
            runOnUiThread(() -> lookupFinished(ticket, completed));
        });
    }

    protected AddressTransport.Response requestLookup(AddressTransport request, String savedAddress) throws Exception {
        return request.lookup(savedAddress);
    }

    void lookupFinished(long ticket, AddressTransport.Response response) {
        if (!foreground) return;
        if (response.status == 200) {
            try {
                java.util.Map<String, String> body = parseMapping(response.body);
                if (!provisioning.completeLookup(ticket, body.get("address"), body.get("serial"), body.get("lookup_url"))) { renderProvisioning(); return; }
            } catch (org.json.JSONException e) {
                if (!provisioning.completeLookup(ticket, "", "", "")) { renderProvisioning(); return; }
            }
        } else {
            if (!provisioning.failLookup(ticket)) return;
            renderProvisioning();
            if (response.status == 429) {
                long seconds = 60;
                try {
                    Object value = new org.json.JSONObject(response.body).get("retry_after");
                    if (!(value instanceof Integer) || (Integer)value < 1 || (Integer)value > 86400
                        || !String.valueOf(value).equals(response.retryAfter)) throw new org.json.JSONException("Invalid delay");
                    seconds = (Integer)value;
                } catch (org.json.JSONException ignored) { }
                retryAt = android.os.SystemClock.elapsedRealtime() + seconds * 1000;
                provisionStatus.setText(String.format(java.util.Locale.ROOT,
                    "تعداد درخواست‌ها زیاد است. دست‌کم این تعداد ثانیه صبر کنید؛ تلاش دوباره خودکار نیست.\n\n%d", seconds));
                return;
            }
            provisionStatus.setText(response.status == 404 ? "این آدرس در سایت ثبت نشده است؛ نوشتن مجاز نیست."
                : response.status == 400 ? "آدرس پذیرفته نشد؛ نوشتن مجاز نیست."
                : response.status == 429 ? "تعداد درخواست‌ها زیاد است؛ بعداً با انتخاب خودتان تلاش کنید."
                : "سایت در دسترس نیست یا پاسخ معتبر نیست؛ هیچ مجوز نوشتنی صادر نشد.");
            return;
        }
        renderProvisioning();
    }

    private static java.util.Map<String, String> parseMapping(String json) throws org.json.JSONException {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        try (android.util.JsonReader reader = new android.util.JsonReader(new java.io.StringReader(json))) {
            reader.setLenient(false); reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ((!key.equals("address") && !key.equals("serial") && !key.equals("lookup_url"))
                    || values.containsKey(key) || reader.peek() != android.util.JsonToken.STRING)
                    throw new java.io.IOException("Invalid mapping");
                values.put(key, reader.nextString());
            }
            reader.endObject();
            if (values.size() != 3 || reader.peek() != android.util.JsonToken.END_DOCUMENT) throw new java.io.IOException("Invalid mapping");
            return values;
        } catch (java.io.IOException | IllegalStateException e) { throw new org.json.JSONException("Invalid mapping"); }
    }

    private void cancelProvisioning() {
        invalidateProvisioning();
        if (foreground) refreshNfc();
    }

    private void invalidateProvisioning() {
        if (lookup != null) { lookup.cancel(); lookup = null; }
        provisioning.cancel(); readerGeneration++;
        if (provisionDialog != null) provisionDialog.dismiss();
        if (writeTag != null && provisionMapping != null) renderProvisioning();
    }

    private void renderProvisioning() {
        TagProvisioning.State state = provisioning.state();
        writeTag.setEnabled(state == TagProvisioning.State.REVIEW);
        lockTag.setEnabled(state == TagProvisioning.State.WRITTEN_UNLOCKED);
        verifyLock.setEnabled(state == TagProvisioning.State.LOCK_AWAITING_VERIFICATION);
        String nextStep;
        switch (state) {
            case IDLE: nextStep = "ابتدا رکورد ذخیره‌شده را از فهرست روزانه باز کنید و دریافت سریال را تأیید کنید."; break;
            case LOOKUP: nextStep = "منتظر پاسخ معتبر سایت بمانید؛ هنوز نوشتن و قفل مجاز نیست."; break;
            case REVIEW: nextStep = "شناسه و پیوند را تطبیق دهید؛ سپس نوشتن را جداگانه تأیید کنید. قفل تا نوشتن و بازخوانی موفق غیرفعال است."; break;
            case WRITE_ARMED: nextStep = "همان تگِ قالب‌بندی‌شده، قابل نوشتن و دارای فضای کافی را دوباره نزدیک کنید. قفل هنوز مجاز نیست."; break;
            case WRITTEN_UNLOCKED: nextStep = "نوشتن و بازخوانی موفق بود. در صورت نیاز، قفل دائمی را جداگانه تأیید کنید. بررسی قفل تا اقدام قفل غیرفعال است."; break;
            case LOCK_ARMED: nextStep = "قفل دائمی را تأیید کرده‌اید؛ همان تگ را دور و دوباره نزدیک کنید. بررسی قفل هنوز آماده نیست."; break;
            case LOCK_AWAITING_VERIFICATION: nextStep = "فرمان قفل کافی نیست. تگ را دور کنید، بررسی قفل را بزنید و دوباره نزدیک کنید. این بررسی فقط می‌خواند."; break;
            case VERIFY_ARMED: nextStep = "برای بررسی فقط‌خواندنی، همان تگ را دوباره نزدیک کنید. تگ یا محتوای نامنطبق تأیید نمی‌شود."; break;
            case LOCKED_VERIFIED: nextStep = "این مسیر تمام شد؛ دکمه‌های نوشتن و قفل غیرفعال‌اند. قفل، اصالت یا موجودی را ثابت نمی‌کند."; break;
            case BUSY: nextStep = "تا پایان عملیات دکمه‌ها غیرفعال‌اند. عملیات سخت‌افزاری شروع‌شده قابل برگشت نیست."; break;
            default: nextStep = "وضعیت نامعلوم مجوز قفل یا تأیید نمی‌دهد. هیچ تلاش دوباره‌ای خودکار نیست؛ وضعیت واقعی تگ را با ابزار مناسب بررسی کنید.";
        }
        provisionHelp.setText(String.format(java.util.Locale.ROOT, "%s\n\n%s", nextStep, "قفل فقط پس از نوشتن موفق در همین نشست و بررسی قفل فقط پس از اقدام قفل فعال می‌شود. بررسی مستقل یا قفل تگ قبلی در این برنامه وجود ندارد. خروج، لغو یا رفتن به پس‌زمینه مجوز این نشست را از بین می‌برد."));
        provisionMapping.setText(provisioning.url() == null ? "" : provisioning.uid() + "\n" + provisioning.address()
            + "\n" + provisioning.serial() + "\n" + provisioning.url());
        provisionStatus.setText(state == TagProvisioning.State.LOOKUP ? "در حال دریافت سریال؛ فقط یک درخواست."
            : state == TagProvisioning.State.REVIEW ? "سریال را با پاکت تطبیق دهید؛ هنوز چیزی نوشته نشده است."
            : state == TagProvisioning.State.WRITE_ARMED ? "همین تگ را دور کنید و دوباره نزدیک کنید تا پیوند نوشته شود."
            : state == TagProvisioning.State.WRITTEN_UNLOCKED ? "پیوند نوشته و دوباره خوانده شد؛ تگ هنوز قفل نشده است."
            : state == TagProvisioning.State.LOCK_ARMED ? "فقط همان تگ را دوباره نزدیک کنید؛ قفل دائمی در انتظار است."
            : state == TagProvisioning.State.LOCK_AWAITING_VERIFICATION ? "فرمان قفل پذیرفته شد اما هنوز تأیید نشده است. تگ را دور کنید و دکمهٔ بررسی قفل را بزنید."
            : state == TagProvisioning.State.VERIFY_ARMED ? "تگ را دوباره نزدیک کنید تا قفل بررسی شود."
            : state == TagProvisioning.State.LOCKED_VERIFIED ? "پیوند و غیرقابل‌نوشتن بودن تگ با خواندن تازه تأیید شد."
            : state == TagProvisioning.State.BUSY ? "عملیات تگ در حال اجراست؛ عملیات شروع‌شده قابل برگشت نیست."
            : state == TagProvisioning.State.FAILED_UNKNOWN ? "عملیات ناموفق یا وضعیت تگ نامشخص است. عملیات شروع‌شده قابل برگشت نیست."
            : "");
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
        foreground = true;
        if (!observingNfc) {
            android.content.IntentFilter filter = new android.content.IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= 33)
                registerReceiver(nfcChanges, filter, android.content.Context.RECEIVER_EXPORTED);
            else registerReceiver(nfcChanges, filter);
            observingNfc = true;
        }
        refreshNfc();
    }

    private boolean captureReaderAllowed() {
        return provisioning.state() == TagProvisioning.State.IDLE || provisioning.state() == TagProvisioning.State.FAILED_UNKNOWN
            || provisioning.state() == TagProvisioning.State.LOCKED_VERIFIED;
    }

    static int readerFlags(boolean writer) {
        int flags = NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B;
        return writer ? flags | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V : flags | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
    }

    protected TagProvisioning.Port openTagPort(android.nfc.Tag tag) {
        return new NdefPort(tag);
    }

    private void refreshNfc() {
        adapter = NfcAdapter.getDefaultAdapter(this);
        nfcStatus.setText(adapter == null ? "این دستگاه سخت‌افزار خواندن تگ ندارد."
            : adapter.isEnabled() ? "تگ را نزدیک گوشی نگه دارید." : "خواندن تگ خاموش است؛ آن را در تنظیمات روشن کنید.");
        boolean enabled = adapter != null && adapter.isEnabled();
        if (!enabled) invalidateProvisioning();
        if (!foreground) return;
        if (tagIoPending) {
            readerRefreshPending = true;
            return;
        }
        if (enabled) {
            long scan = ++readerGeneration;
            TagProvisioning.State state = provisioning.state();
            boolean writer = state == TagProvisioning.State.WRITE_ARMED || state == TagProvisioning.State.LOCK_ARMED || state == TagProvisioning.State.VERIFY_ARMED;
            long ticket = writerTicket;
            adapter.disableReaderMode(this);
            adapter.enableReaderMode(this, tag -> runOnUiThread(() -> {
                if (!foreground || scan != readerGeneration) return;
                if (!writer) { if (captureReaderAllowed()) acceptTag(tag.getId()); return; }
                // Retire this callback before queuing I/O. No duplicate discovery can queue another write.
                readerGeneration++;
                tagIoPending = true;
                io.execute(() -> {
                    try { provisioning.discovered(ticket, openTagPort(tag)); }
                    finally {
                        runOnUiThread(() -> {
                            tagIoPending = false;
                            boolean refresh = readerRefreshPending;
                            readerRefreshPending = false;
                            if (foreground) {
                                renderProvisioning();
                                if (refresh) refreshNfc();
                                else if (adapter != null) adapter.disableReaderMode(this);
                            }
                        });
                    }
                });
            }), readerFlags(writer), null);
        } else {
            if (adapter != null) adapter.disableReaderMode(this);
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
        foreground = false;
        cancelProvisioning();
        if (provisionDialog != null) provisionDialog.dismiss();
        if (observingNfc) {
            unregisterReceiver(nfcChanges);
            observingNfc = false;
        }
        if (adapter != null) adapter.disableReaderMode(this);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("retryAt", retryAt);
        state.putString("uid", uid.getText().toString());
        String draft = address.getText().toString();
        state.putString("address", BitcoinAddress.isValid(draft) ? draft : "");
        state.putString("pendingExport", pendingExport);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        foreground = false;
        cancelProvisioning(); io.shutdownNow();
        if (provisionDialog != null) provisionDialog.dismiss();
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
