package com.saebm.btcnfcseller;

import android.os.Bundle;
import android.view.WindowManager;
import com.journeyapps.barcodescanner.CaptureActivity;

/** Protect transient camera frames from screenshots and recent-task snapshots. */
public final class SecureCaptureActivity extends CaptureActivity {
    @Override public void onCreate(Bundle state) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(state);
    }
}
