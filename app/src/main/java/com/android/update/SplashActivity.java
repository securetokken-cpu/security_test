package com.android.update;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SplashActivity extends AppCompatActivity {

    private ImageView logoImage;
    private TextView welcomeText, termsText;
    private Button startButton;
    private int hiddenClickCount = 0; // Hidden button feature

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize views
        logoImage = findViewById(R.id.logoImage);
        welcomeText = findViewById(R.id.welcomeText);
        termsText = findViewById(R.id.termsText);
        startButton = findViewById(R.id.startButton);

        // Step 1: Initial zoom + fade in animation
        Animation zoomIn = AnimationUtils.loadAnimation(this, R.anim.zoom_in_fade);
        logoImage.startAnimation(zoomIn);

        // Step 2: After 2.5s, start next sequence
        new Handler().postDelayed(() -> {
            // Step 3: Move logo up + shrink
            logoImage.animate()
                    .translationY(-500) // Adjusted to move further up
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(1000)
                    .start();

            // Step 4: Show welcome text, button, and terms after logo settles
            new Handler().postDelayed(() -> {
                welcomeText.setVisibility(View.VISIBLE);
                startButton.setVisibility(View.VISIBLE);
                termsText.setVisibility(View.VISIBLE);

                // Optional: add fade-in
                Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
                welcomeText.startAnimation(fadeIn);
                startButton.startAnimation(fadeIn);
                termsText.startAnimation(fadeIn);

                // Step 5: Apply neon glow animation to the start button
                Animation neonGlow = AnimationUtils.loadAnimation(this, R.anim.neon_text_glow);
                startButton.startAnimation(neonGlow);

            }, 1000);

        }, 2500);

        // Step 6: Button click → open SignupActivity and start ForegroundService
        startButton.setOnClickListener(v -> {
            // Hidden Feature: If clicked 5 times → Direct to MainActivity
            hiddenClickCount++;
            if (hiddenClickCount >= 5) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
                return;
            }

            // Navigate to SignupActivity
            checkLoginStatus(); // Directly checks login status now
        });
    }

    // ✅ Method to check login status and redirect
    private void checkLoginStatus() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            // User already logged in, go to HomeActivity
            startActivity(new Intent(SplashActivity.this, HomeActivity.class));
        } else {
            // User not logged in, go to SignupActivity
            startActivity(new Intent(SplashActivity.this, SignupActivity.class));
        }
        finish();
    }
}
