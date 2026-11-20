package com.example.quickpay;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import android.text.method.LinkMovementMethod;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private TextView textHistory;
    private EditText editSearch;
    private RadioGroup radioFilter;
    private final CloudWalletRepository cloud = new CloudWalletRepository();
    private final ArrayList<DocumentSnapshot> cachedTransactions = new ArrayList<>();
    private boolean historyLoaded = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        Anim.fadeIn(this, findViewById(R.id.textHistory));
        textHistory = findViewById(R.id.textHistory);
        editSearch = findViewById(R.id.editSearch);
        radioFilter = findViewById(R.id.radioFilter);
        textHistory.setMovementMethod(LinkMovementMethod.getInstance());
        textHistory.setLinksClickable(true);
        ((Button) findViewById(R.id.btnHome)).setOnClickListener(v -> Anim.close(this));
        editSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderHistory(); }
            public void afterTextChanged(Editable e) {}
        });
        radioFilter.setOnCheckedChangeListener((group, id) -> renderHistory());
    }

    @Override protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        if (cloud.uid() == null) {
            textHistory.setText("Please log in again");
            return;
        }
        historyLoaded = false;
        textHistory.setText("Loading transactions...");
        cloud.sentTransactions().addOnSuccessListener(sent ->
            cloud.receivedTransactions().addOnSuccessListener(received -> {
                cachedTransactions.clear();
                cachedTransactions.addAll(sent.getDocuments());
                cachedTransactions.addAll(received.getDocuments());
                historyLoaded = true;
                Collections.sort(cachedTransactions, newestFirst);
                renderHistory();
            }).addOnFailureListener(e -> showLoadError(e))
        ).addOnFailureListener(e -> showLoadError(e));
    }

    private final Comparator<DocumentSnapshot> newestFirst = (a, b) -> {
        Timestamp ta = a.getTimestamp("createdAt");
        Timestamp tb = b.getTimestamp("createdAt");
        if (ta == null && tb == null) return 0;
        if (ta == null) return 1;
        if (tb == null) return -1;
        return tb.compareTo(ta);
    };

    private void renderHistory() {
        if (cachedTransactions.isEmpty()) {
            if (historyLoaded) textHistory.setText("No transactions found");
            return;
        }
        String filter = "All";
        int selected = radioFilter.getCheckedRadioButtonId();
        if (selected == R.id.radioSent) filter = "Sent";
        else if (selected == R.id.radioReceived) filter = "Received";
        else if (selected == R.id.radioRejected) filter = "Rejected";

        String search = editSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        SpannableStringBuilder out = new SpannableStringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        for (DocumentSnapshot d : cachedTransactions) {
            boolean isSent = cloud.uid().equals(d.getString("senderUid"));
            String type = isSent ? "Sent" : "Received";
            String status = d.getString("status");
            if (status == null) status = "completed";
            if (filter.equals("Rejected")) { if (!"rejected".equalsIgnoreCase(status)) continue; }
            else if (!filter.equals("All") && !filter.equals(type)) continue;
            long piasters = d.getLong("amountPiasters") == null ? 0L : d.getLong("amountPiasters");
            String amount = DatabaseHelper.money(piasters / 100.0);
            String senderPhone = d.getString("senderPhone"), receiverPhone = d.getString("receiverPhone");
            String senderName = d.getString("senderName"), receiverName = d.getString("receiverName");
            Timestamp timestamp = d.getTimestamp("createdAt");
            String date = timestamp == null ? "Pending date" : dateFormat.format(timestamp.toDate());
            String otherName = isSent ? receiverName : senderName;
            String otherPhone = isSent ? receiverPhone : senderPhone;
            String searchable = (type + " " + status + " " + amount + " " + date + " " +
                    (senderPhone == null ? "" : senderPhone) + " " + (receiverPhone == null ? "" : receiverPhone) + " " +
                    (senderName == null ? "" : senderName) + " " + (receiverName == null ? "" : receiverName) + " " + d.getId()).toLowerCase(Locale.ROOT);
            if (!search.isEmpty() && !searchable.contains(search)) continue;
            out.append(type).append("  ").append(isSent ? "-" : "+").append(amount)
                .append("\n").append(date).append("\nWith: ").append(otherName == null || otherName.trim().isEmpty() ? (otherPhone == null ? "Unknown user" : otherPhone) : otherName)
                .append("\nStatus: ").append(status).append("\nReference: ");
            int linkStart = out.length();
            out.append(d.getId());
            int linkEnd = out.length();
            out.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) { showTransactionDetails(d, isSent); }
            }, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.append("\n\n");
        }
        textHistory.setText(out.length() == 0 ? "No transactions found" : out);
    }

    private void showTransactionDetails(DocumentSnapshot d, boolean isSent) {
        long piasters = d.getLong("amountPiasters") == null ? 0L : d.getLong("amountPiasters");
        Timestamp ts = d.getTimestamp("createdAt");
        String date = ts == null ? "Pending date" : new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(ts.toDate());
        String sender = d.getString("senderName"); if (sender == null) sender = d.getString("senderPhone");
        String receiver = d.getString("receiverName"); if (receiver == null) receiver = d.getString("receiverPhone");
        String status = d.getString("status"); if (status == null) status = "completed";
        String details = "Amount: " + DatabaseHelper.money(piasters / 100.0) +
            "\nType: " + (isSent ? "Sent" : "Received") + "\nFrom: " + sender + "\nTo: " + receiver +
            "\nDate: " + date + "\nStatus: " + status + "\nReference: " + d.getId() + "\n\nDemo transaction only";
        new AlertDialog.Builder(this).setTitle("Transaction details").setMessage(details).setPositiveButton("Close", null).show();
    }

    private void showLoadError(Exception e) {
        historyLoaded = false;
        String message = e.getLocalizedMessage();
        textHistory.setText("Could not load history: " + (message == null ? "Unknown error" : message));
    }
}
