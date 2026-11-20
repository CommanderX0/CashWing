package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {
    private EditText editPhone, editPin;
    private Button btnLogin, btnRegister;

    @Override protected void onCreate(Bundle savedInstanceState) {
        boolean darkMode = getSharedPreferences("cashwing_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Anim.zoomIn(this, findViewById(R.id.imgLogo));
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        editPhone = findViewById(R.id.editPhone);
        editPin = findViewById(R.id.editPin);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        Button btnBiometric = findViewById(R.id.btnBiometric);
        btnBiometric.setOnClickListener(v -> unlockBiometric());
        btnLogin.setOnClickListener(v -> { Anim.pulse(this, v); loginUser(); });
        btnRegister.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Anim.open(this, new Intent(this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String phone = PhoneUtil.toE164(editPhone.getText().toString());
        String pin = editPin.getText().toString().trim();
        if (phone.isEmpty()) { editPhone.setError("Enter a valid Egyptian mobile number"); return; }
        if (!pin.matches("\\d{6}")) { editPin.setError("PIN must be 6 digits"); return; }

        setBusy(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(PhoneCredential.emailForPhone(phone), pin)
            .addOnCompleteListener(this, task -> {
                if (!task.isSuccessful() || FirebaseAuth.getInstance().getCurrentUser() == null) {
                    setBusy(false);
                    Toast.makeText(this, authError(task.getException()), Toast.LENGTH_LONG).show();
                    return;
                }
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(profile -> {
                        String storedPhone = profile.getString("phoneE164");
                        if (!profile.exists() || !phone.equals(storedPhone)) {
                            FirebaseAuth.getInstance().signOut();
                            setBusy(false);
                            Toast.makeText(this, "Account profile was not found. Please contact support.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            // This local copy is only for transfer confirmation/biometric convenience.
                            // The real cross-device login is Firebase Email/Password above.
                            DevicePinManager.save(this, uid, pin);
                        } catch (Exception e) {
                            setBusy(false);
                            Toast.makeText(this, "Could not prepare device security: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                        openHome(PhoneUtil.local(phone));
                    })
                    .addOnFailureListener(e -> {
                        FirebaseAuth.getInstance().signOut();
                        setBusy(false);
                        Toast.makeText(this, "Could not load account: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    });
            });
    }

    private void setBusy(boolean busy) {
        btnLogin.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
        btnLogin.setText(busy ? "Signing in…" : "Login");
    }

    private String authError(Exception e) {
        String m = e == null ? "Authentication failed" : e.getLocalizedMessage();
        if (m == null) return "Authentication failed";
        String lower = m.toLowerCase();
        if (lower.contains("no user record") || lower.contains("user-not-found") || lower.contains("invalid credential") || lower.contains("wrong password") || lower.contains("invalid login credentials"))
            return "Phone number or PIN is incorrect, or the account does not exist.";
        if (lower.contains("network")) return "Check your internet connection and try again.";
        return m;
    }

    private void unlockBiometric() {
        String phone = PhoneUtil.toE164(editPhone.getText().toString());
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();
        String currentEmail = FirebaseAuth.getInstance().getCurrentUser() == null ? null : FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (uid == null || phone.isEmpty() || !PhoneCredential.emailForPhone(phone).equals(currentEmail) || !DevicePinManager.isConfigured(this, uid)) {
            Toast.makeText(this, "Log in with your phone and PIN on this device first", Toast.LENGTH_LONG).show();
            return;
        }
        BiometricManager manager = BiometricManager.from(this);
        int can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric unlock is not available on this device", Toast.LENGTH_LONG).show();
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { openHome(PhoneUtil.local(phone)); }
            @Override public void onAuthenticationError(int code, CharSequence message) { Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock CashWing").setSubtitle("Confirm your device identity")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
        prompt.authenticate(info);
    }

    private void openHome(String phone) {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra(HomeActivity.KEY_PHONE, phone);
        Anim.openClear(this, intent);
    }
}
