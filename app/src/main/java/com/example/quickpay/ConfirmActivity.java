package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ConfirmActivity extends AppCompatActivity {
    private final CloudWalletRepository cloud = new CloudWalletRepository();
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        Anim.zoomIn(this, findViewById(R.id.textAmount));
        TextView textTo = findViewById(R.id.textTo), textAmount = findViewById(R.id.textAmount), textAfter = findViewById(R.id.textAfter);
        Button btnConfirm = findViewById(R.id.btnConfirm), btnCancel = findViewById(R.id.btnCancel);
        EditText editPin = findViewById(R.id.editConfirmPin);
        String phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        String receiver = getIntent().getStringExtra(ResultActivity.KEY_RECEIVER);
        String amountText = getIntent().getStringExtra(ResultActivity.KEY_AMOUNT);
        if (phone == null || receiver == null || amountText == null) { Toast.makeText(this, "Missing transfer data", Toast.LENGTH_SHORT).show(); finish(); return; }
        double amount;
        try { amount = Double.parseDouble(amountText); }
        catch (NumberFormatException e) { finish(); return; }
        final long piasters = Math.round(amount * 100.0);
        final double finalAmount = amount;
        textAmount.setText(DatabaseHelper.money(amount));
        textTo.setText("Loading receiver...");
        cloud.findUser(PhoneUtil.toE164(receiver)).addOnSuccessListener(result -> {
            String name = result.isEmpty() ? "CashWing user" : result.getDocuments().get(0).getString("displayName");
            textTo.setText((name == null ? "CashWing user" : name) + "\n" + receiver);
        }).addOnFailureListener(e -> textTo.setText(receiver));
        cloud.wallet().addOnSuccessListener(w -> textAfter.setText("Balance after transfer: " + DatabaseHelper.money(CloudWalletRepository.egp(w) - finalAmount)));
        btnConfirm.setOnClickListener(v -> {
            String pin = editPin.getText().toString().trim();
            String uid = cloud.uid();
            if (!DevicePinManager.verify(this, uid, pin)) { Toast.makeText(this, "Incorrect device PIN", Toast.LENGTH_LONG).show(); return; }
            btnConfirm.setEnabled(false);
            cloud.transfer(PhoneUtil.toE164(receiver), piasters).addOnSuccessListener(r -> {
                Intent result = new Intent(this, ResultActivity.class);
                result.putExtra(HomeActivity.KEY_PHONE, phone);
                result.putExtra(ResultActivity.KEY_RECEIVER, receiver);
                result.putExtra(ResultActivity.KEY_AMOUNT, DatabaseHelper.money(finalAmount));
                startActivity(result); finish();
            }).addOnFailureListener(e -> { btnConfirm.setEnabled(true); Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show(); });
        });
        btnCancel.setOnClickListener(v -> Anim.close(this));
    }
}
