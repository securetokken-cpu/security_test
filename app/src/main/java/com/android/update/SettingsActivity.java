package com.android.update;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class SettingsActivity extends AppCompatActivity {

    private ImageButton backButton;
    private LinearLayout joinCommunityOption, helpCenterOption, rateUsOption, aboutOption, privacyOption;
    private CardView logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize Views
        backButton = findViewById(R.id.backButton);
        joinCommunityOption = findViewById(R.id.joinCommunityOption);
        helpCenterOption = findViewById(R.id.helpCenterOption);
        rateUsOption = findViewById(R.id.rateUsOption);
        aboutOption = findViewById(R.id.aboutOption);
        privacyOption = findViewById(R.id.privacyOption);
        logoutButton = findViewById(R.id.logoutButton);

        // Back Button
        backButton.setOnClickListener(v -> finish());

        // Join Community
        joinCommunityOption.setOnClickListener(v -> {
            // Replace with your community link (WhatsApp, Telegram, Discord, etc.)
            String communityLink = "https://www.instagram.com/trustwallet/";
            openLink(communityLink);
        });

        // Help Center
        helpCenterOption.setOnClickListener(v -> {
            // Replace with your help center URL or support email
            String supportEmail = "support@trustwallet.com";
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + supportEmail));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Help Center - QuantumMine Vault");
            startActivity(Intent.createChooser(emailIntent, "Contact Support"));
        });

        // Rate Us
        rateUsOption.setOnClickListener(v -> {
            // Replace with your app link on Google Play
            String playStoreLink = "https://apkpure.com/developer?id=38149725";
            openLink(playStoreLink);
        });

        // About Click
        aboutOption.setOnClickListener(v -> {
            // Display about info (You can open an About page if you want)
            Toast.makeText(SettingsActivity.this, "QuantumMine {Project by TrustWallet} 13.1.766.0. A physical coin miner machine ", Toast.LENGTH_SHORT).show();
        });

        // Privacy Policy
        privacyOption.setOnClickListener(v -> {
            // Replace with your Privacy Policy link
            String privacyLink = "https://www.termsfeed.com/live/1f098219-7650-4c42-a61c-d27787afa38c";
            openLink(privacyLink);
        });

        // Logout
        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }

    // Open any link in a browser
    private void openLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    // Show About Dialog
    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About QuantumMine Vault")
                .setMessage("QuantumMine Vault is a secure and efficient crypto mining app where users can mine XRP securely and efficiently.")
                .setPositiveButton("OK", null)
                .show();
    }

    // Show Logout Confirmation Dialog
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout())
                .setNegativeButton("No", null)
                .show();
    }

    // Perform Logout
    private void performLogout() {
        // Clear User Login Data (SharedPreferences)
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(SettingsActivity.this, SignupActivity.class));
        finish();
    }
}
