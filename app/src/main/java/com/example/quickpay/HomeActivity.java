package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class HomeActivity extends AppCompatActivity {
    public static final String KEY_PHONE = "PHONE";
    private String phone;
    private TextView textWelcome, textBalance, textRecent;
    private final CloudWalletRepository cloud = new CloudWalletRepository();
    private android.view.View[] money;
    private ListenerRegistration incomingListener;
    private boolean firstIncomingSnapshot = true;
    private static final String CHANNEL_ID = "cashwing_transactions";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Anim.zoomIn(this, findViewById(R.id.imgWallet));
        Anim.fadeIn(this, findViewById(R.id.textWelcome));
        Anim.zoomIn(this, findViewById(R.id.textBalance));
        textWelcome = findViewById(R.id.textWelcome); textBalance = findViewById(R.id.textBalance); textRecent = findViewById(R.id.textRecent);
        money = new android.view.View[]{findViewById(R.id.money1), findViewById(R.id.money2), findViewById(R.id.money3), findViewById(R.id.money4), findViewById(R.id.money5)};
        phone = getIntent().getStringExtra(KEY_PHONE);
        if (cloud.uid() == null) { goLogin(); return; }
        ((Button)findViewById(R.id.btnSend)).setOnClickListener(v -> open(TransferActivity.class, false));
        ((Button)findViewById(R.id.btnDonate)).setOnClickListener(v -> open(TransferActivity.class, true));
        ((Button)findViewById(R.id.btnHistory)).setOnClickListener(v -> open(HistoryActivity.class, false));
        ((Button)findViewById(R.id.btnProfile)).setOnClickListener(v -> open(ProfileActivity.class, false));
        ((Button)findViewById(R.id.btnLogout)).setOnClickListener(v -> goLogin());
    }

    @Override protected void onStart() {
        super.onStart();
        startIncomingNotifications();
    }

    @Override protected void onStop() {
        if (incomingListener != null) { incomingListener.remove(); incomingListener = null; }
        super.onStop();
    }

    private void startIncomingNotifications() {
        String uid = cloud.uid();
        if (uid == null) return;
        createNotificationChannel();
        firstIncomingSnapshot = true;
        incomingListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("transactions").whereEqualTo("receiverUid", uid)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null || snapshot == null) return;
                if (firstIncomingSnapshot) { firstIncomingSnapshot = false; return; }
                for (com.google.firebase.firestore.DocumentChange change : snapshot.getDocumentChanges()) {
                    if (change.getType() != com.google.firebase.firestore.DocumentChange.Type.ADDED) continue;
                    DocumentSnapshot d = change.getDocument();
                    Long amount = d.getLong("amountPiasters");
                    String sender = d.getString("senderName");
                    if (sender == null || sender.trim().isEmpty()) sender = d.getString("senderPhone");
                    if (sender == null) sender = "Someone";
                    showIncomingNotification(sender, amount == null ? 0L : amount);
                }
            });
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 901);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "CashWing transactions", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Alerts when your demo wallet receives a transfer");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showIncomingNotification(String sender, long piasters) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("CashWing: money received")
            .setContentText("Received " + DatabaseHelper.money(piasters / 100.0) + " from " + sender)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).build();
        NotificationManagerCompat.from(this).notify((int)(System.currentTimeMillis() & 0x7fffffff), notification);
    }

    @Override protected void onResume() {
        super.onResume();
        if (cloud.uid() == null) return;
        cloud.profile().addOnSuccessListener(p -> {
            String name = p.getString("displayName");
            textWelcome.setText("Welcome, " + (name == null ? "CashWing user" : name));
        }).addOnFailureListener(e -> textWelcome.setText("Welcome to CashWing"));
        cloud.wallet().addOnSuccessListener(w -> textBalance.setText(DatabaseHelper.money(CloudWalletRepository.egp(w))))
                .addOnFailureListener(e -> textBalance.setText("Balance unavailable"));
        loadRecent();
    }

    private void loadRecent() {
        cloud.sentTransactions().addOnSuccessListener(sent -> cloud.receivedTransactions().addOnSuccessListener(received -> {
            java.util.ArrayList<DocumentSnapshot> all = new java.util.ArrayList<>();
            all.addAll(sent.getDocuments()); all.addAll(received.getDocuments());
            all.sort((a,b) -> {
                com.google.firebase.Timestamp ta = a.getTimestamp("createdAt"), tb = b.getTimestamp("createdAt");
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });

            int count = Math.min(3, all.size());
            if (count == 0) {
                textRecent.setText("No transactions yet");
                return;
            }
            String[] lines = new String[count];
            int[] completed = {0};
            for (int i = 0; i < count; i++) {
                final int index = i;
                DocumentSnapshot d = all.get(i);
                boolean sentByMe = cloud.uid().equals(d.getString("senderUid"));
                long amount = d.getLong("amountPiasters") == null ? 0L : d.getLong("amountPiasters");
                String otherUid = sentByMe ? d.getString("receiverUid") : d.getString("senderUid");
                String fallback = sentByMe ? d.getString("receiverPhone") : d.getString("senderPhone");
                String savedName = sentByMe ? d.getString("receiverName") : d.getString("senderName");
                final String direction = sentByMe ? "Sent " : "Received ";
                final String relation = sentByMe ? " to " : " from ";
                final String amountText = DatabaseHelper.money(amount / 100.0);
                if (savedName != null && !savedName.trim().isEmpty()) {
                    lines[index] = direction + amountText + relation + savedName.trim();
                    completed[0]++;
                    if (completed[0] == count) showRecentLines(lines);
                } else if (otherUid != null && !otherUid.isEmpty()) {
                    cloud.userProfile(otherUid).addOnSuccessListener(profile -> {
                        String name = profile.getString("displayName");
                        if (name == null || name.trim().isEmpty()) name = fallback;
                        if (name == null || name.trim().isEmpty()) name = "Unknown user";
                        lines[index] = direction + amountText + relation + name;
                        completed[0]++;
                        if (completed[0] == count) showRecentLines(lines);
                    }).addOnFailureListener(error -> {
                        String name = (fallback == null || fallback.trim().isEmpty()) ? "Unknown user" : fallback;
                        lines[index] = direction + amountText + relation + name;
                        completed[0]++;
                        if (completed[0] == count) showRecentLines(lines);
                    });
                } else {
                    String name = (fallback == null || fallback.trim().isEmpty()) ? "Unknown user" : fallback;
                    lines[index] = direction + amountText + relation + name;
                    completed[0]++;
                    if (completed[0] == count) showRecentLines(lines);
                }
            }
        })).addOnFailureListener(e -> textRecent.setText("Could not load transactions: " + e.getLocalizedMessage()));
    }

    private void showRecentLines(String[] lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line != null) out.append(line).append("\n");
        }
        textRecent.setText(out.length() == 0 ? "No transactions yet" : out.toString().trim());
    }

    private void open(Class<?> cls, boolean charity) {
        Intent i = new Intent(this, cls); i.putExtra(KEY_PHONE, phone);
        if (charity) i.putExtra(TransferActivity.KEY_CHARITY, true);
        Anim.open(this, i);
    }
    private void goLogin() { Intent i = new Intent(this, MainActivity.class); Anim.openClear(this, i); }
}
