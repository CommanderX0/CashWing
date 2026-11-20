package com.example.quickpay;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class QrActivity extends AppCompatActivity {
    private String phone = "";
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() == null) return;
        String value = result.getContents().trim();
        if (value.startsWith("CASHWING:")) value = value.substring("CASHWING:".length());
        String normalized = PhoneUtil.toE164(value);
        if (normalized.isEmpty()) { Toast.makeText(this, "This is not a valid CashWing QR", Toast.LENGTH_LONG).show(); return; }
        Intent i = new Intent(this, TransferActivity.class);
        i.putExtra(HomeActivity.KEY_PHONE, phone);
        i.putExtra(TransferActivity.KEY_PREFILL_RECEIVER, PhoneUtil.local(normalized));
        startActivity(i);
    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_qr);
        phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        if (phone == null || phone.trim().isEmpty()) phone = PhoneCredential.phoneForEmail(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getEmail());
        ((TextView)findViewById(R.id.textQrPhone)).setText(PhoneUtil.local(phone));
        ImageView image = findViewById(R.id.imageQr);
        try {
            BitMatrix matrix = new QRCodeWriter().encode("CASHWING:" + phone, BarcodeFormat.QR_CODE, 720, 720);
            Bitmap bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888);
            for (int x=0; x<720; x++) for (int y=0; y<720; y++) bitmap.setPixel(x,y,matrix.get(x,y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
            image.setImageBitmap(bitmap);
        } catch (WriterException e) { Toast.makeText(this, "Could not create QR code", Toast.LENGTH_LONG).show(); }
        findViewById(R.id.btnScanQr).setOnClickListener(v -> {
            ScanOptions options = new ScanOptions(); options.setPrompt("Scan a CashWing account QR"); options.setBeepEnabled(false); options.setOrientationLocked(false); options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            scanner.launch(options);
        });
        findViewById(R.id.btnQrBack).setOnClickListener(v -> finish());
    }
}
