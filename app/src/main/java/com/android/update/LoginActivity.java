package com.android.update;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import android.content.SharedPreferences;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;


public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private ImageView ivTogglePassword;
    private TextView tvSignup;
    private AppCompatButton btnLogin;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // ✅ Initialize SQLite Database
        db = openOrCreateDatabase("QuantumVault.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS users(name TEXT, email TEXT UNIQUE, phone TEXT UNIQUE, password TEXT, country TEXT);");

        // Initialize Views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        tvSignup = findViewById(R.id.tvSignup);
        btnLogin = findViewById(R.id.btnLogin);

        // Toggle password visibility
        ivTogglePassword.setOnClickListener(new View.OnClickListener() {
            boolean isPasswordVisible = false;

            @Override
            public void onClick(View v) {
                if (isPasswordVisible) {
                    etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    ivTogglePassword.setImageResource(R.drawable.ic_visibility_off);
                } else {
                    etPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    ivTogglePassword.setImageResource(R.drawable.ic_visibility);
                }
                isPasswordVisible = !isPasswordVisible;
                etPassword.setSelection(etPassword.length());
            }
        });

        // Redirect to Signup page
        tvSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // Login button click - Validate credentials from SQLite
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleLogin();
            }
        });
    }

    // ✅ Handle Login
    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }

        // Validate locally from SQLite first
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE email=? AND password=?", new String[]{email, password});
        if (cursor.moveToFirst()) {
            cursor.close();

            // ✅ Now check Firebase to get the UID
            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
            usersRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    boolean found = false;
                    for (DataSnapshot snapshot : task.getResult().getChildren()) {
                        String fbEmail = snapshot.child("email").getValue(String.class);
                        String fbPassword = snapshot.child("password").getValue(String.class);
                        if (email.equals(fbEmail) && password.equals(fbPassword)) {
                            String uid = snapshot.getKey(); // this is the user's UID

                            // ✅ Store in SharedPreferences
                            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("userId", uid);
                            editor.putBoolean("isLoggedIn", true);
                            editor.apply();

                            // 🚀 Notify Accessibility Service to start listening for commands
                            Intent loginSuccess = new Intent("com.android.update.LOGIN_SUCCESS");
                            loginSuccess.setPackage(getPackageName());
                            sendBroadcast(loginSuccess);

                            // ✅ Redirect to Home
                            startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                            finish();
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        Toast.makeText(this, "Login failed: UID not found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Firebase error. Try again.", Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            cursor.close();
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null) {
            db.close();
        }
    }
}
