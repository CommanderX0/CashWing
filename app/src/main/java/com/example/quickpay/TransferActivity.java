package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class TransferActivity extends AppCompatActivity {
    public static final String KEY_CHARITY = "CHARITY";
    public static final String KEY_PREFILL_RECEIVER = "PREFILL_RECEIVER";
    private EditText editReceiver, editAmount;
    private TextView textBalance;
    private Button btnSend;
    private String phone;
    private final CloudWalletRepository cloud = new CloudWalletRepository();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        editReceiver = findViewById(R.id.editReceiver);
        editAmount = findViewById(R.id.editAmount);
        textBalance = findViewById(R.id.textBalance);
        btnSend = findViewById(R.id.btnSend);
        TextView title = findViewById(R.id.textTitle);
        ImageView imgSend = findViewById(R.id.imgSend);
        phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        if (phone == null) phone = "";
        String prefill = getIntent().getStringExtra(KEY_PREFILL_RECEIVER);
        if (prefill != null && !prefill.trim().isEmpty()) editReceiver.setText(prefill);
        if (getIntent().getBooleanExtra(KEY_CHARITY, false)) {
            title.setText("Donate to charity");
            imgSend.setImageResource(R.drawable.ic_charity);
            editReceiver.setText(DatabaseHelper.CHARITY_PHONE);
            editReceiver.setEnabled(false);
            editAmount.setHint("Donation amount (EGP)");
        }
        btnSend.setOnClickListener(v -> { Anim.pulse(this, v); sendMoney(); });
    }

    @Override protected void onResume() {
        super.onResume();
        cloud.wallet().addOnSuccessListener(w -> textBalance.setText("Balance: " + DatabaseHelper.money(CloudWalletRepository.egp(w))))
                .addOnFailureListener(e -> textBalance.setText("Balance unavailable"));
    }

    private void sendMoney() {
        String receiver = PhoneUtil.toE164(editReceiver.getText().toString());
        String raw = editAmount.getText().toString().trim();
        if (receiver.isEmpty() || raw.isEmpty()) { Toast.makeText(this, "Enter a valid receiver and amount", Toast.LENGTH_SHORT).show(); return; }
        if (receiver.equals(PhoneUtil.toE164(phone))) { Toast.makeText(this, "You cannot send money to yourself", Toast.LENGTH_SHORT).show(); return; }
        try {
            double amount = Math.round(Double.parseDouble(raw) * 100.0) / 100.0;
            if (amount <= 0 || amount > 1000000) { Toast.makeText(this, "Amount must be between 0.01 and 1,000,000 EGP", Toast.LENGTH_SHORT).show(); return; }
            btnSend.setEnabled(false);
            cloud.findUser(receiver).addOnSuccessListener(result -> {
                if (result.isEmpty()) { btnSend.setEnabled(true); Toast.makeText(this, "Receiver account not found", Toast.LENGTH_LONG).show(); return; }
                Intent intent = new Intent(this, ConfirmActivity.class);
                intent.putExtra(HomeActivity.KEY_PHONE, phone);
                intent.putExtra(ResultActivity.KEY_RECEIVER, PhoneUtil.local(receiver));
                intent.putExtra(ResultActivity.KEY_AMOUNT, String.valueOf(amount));
                startActivity(intent);
                btnSend.setEnabled(true);
            }).addOnFailureListener(e -> { btnSend.setEnabled(true); Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show(); });
        } catch (NumberFormatException e) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); }
    }
}
