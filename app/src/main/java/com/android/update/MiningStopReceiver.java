package com.android.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

public class MiningStopReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Stop mining by updating SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("isMining", false).apply();

        // Show a Toast notification
        Toast.makeText(context, "Mining session ended. Tap to restart mining.", Toast.LENGTH_SHORT).show();
    }
}
