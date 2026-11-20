package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private EditText editName, editPhone, editPin;
    private Button btnCreate;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        editName = findViewById(R.id.editName);
        editPhone = findViewById(R.id.editPhone);
        editPin = findViewById(R.id.editPin);
        btnCreate = findViewById(R.id.btnCreate);
        btnCreate.setOnClickListener(v -> { Anim.pulse(this, v); createAccount(); });
    }

    private void createAccount() {
        String name = editName.getText().toString().trim();
        String phone = PhoneUtil.toE164(editPhone.getText().toString());
        String pin = editPin.getText().toString().trim();
        if (name.length() < 2 || name.length() > 40) { editName.setError("Name must be 2 to 40 characters"); return; }
        if (phone.isEmpty()) { editPhone.setError("Enter a valid Egyptian mobile number (01xxxxxxxxx)"); return; }
        if (!pin.matches("\\d{6}")) { editPin.setError("PIN must be 6 digits"); return; }

        btnCreate.setEnabled(false);
        btnCreate.setText("Creating account…");
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(PhoneCredential.emailForPhone(phone), pin)
            .addOnCompleteListener(this, task -> {
                if (!task.isSuccessful() || FirebaseAuth.getInstance().getCurrentUser() == null) {
                    restoreButton();
                    String message = task.getException() == null ? "Could not create account" : task.getException().getLocalizedMessage();
                    if (message != null && message.toLowerCase().contains("already in use")) message = "This phone number already has an account. Please log in.";
                    Toast.makeText(this, message == null ? "Could not create account" : message, Toast.LENGTH_LONG).show();
                    return;
                }
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                String phoneKey = PhoneCredential.phoneKey(phone);
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                Map<String, Object> profile = new HashMap<>();
                profile.put("displayName", name);
                profile.put("phoneE164", phone);
                profile.put("phoneKey", phoneKey);
                profile.put("loginEmail", PhoneCredential.emailForPhone(phone));
                profile.put("createdAt", FieldValue.serverTimestamp());
                Map<String, Object> wallet = new HashMap<>();
                wallet.put("balancePiasters", 2000000L);
                wallet.put("currency", "EGP");
                wallet.put("demo", true);

                db.runTransaction(transaction -> {
                    transaction.set(db.collection("users").document(uid), profile);
                    transaction.set(db.collection("wallets").document(uid), wallet);
                    return null;
                }).addOnSuccessListener(v -> {
                    try {
                        DevicePinManager.save(this, uid, pin);
                        Intent home = new Intent(this, HomeActivity.class);
                        home.putExtra(HomeActivity.KEY_PHONE, PhoneUtil.local(phone));
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(home);
                        finish();
                    } catch (Exception e) {
                        restoreButton();
                        Toast.makeText(this, "Account created, but device security setup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).addOnFailureListener(e -> {
                    // Avoid leaving an Auth account without its matching Firestore profile.
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        FirebaseAuth.getInstance().getCurrentUser().delete().addOnCompleteListener(x -> FirebaseAuth.getInstance().signOut());
                    }
                    restoreButton();
                    Toast.makeText(this, "Could not save account data: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            });
    }

    private void restoreButton() {
        btnCreate.setEnabled(true);
        btnCreate.setText("Create account");
    }
}
