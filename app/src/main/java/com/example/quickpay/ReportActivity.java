package com.example.quickpay;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {
    private TextView textMonth, textSummary, textHistory;
    private BarChartView chart;
    private Calendar month;
    private final CloudWalletRepository cloud = new CloudWalletRepository();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        textMonth = findViewById(R.id.textMonth);
        textSummary = findViewById(R.id.textSummary);
        textHistory = findViewById(R.id.textHistory);
        chart = findViewById(R.id.chartMonths);
        Button prev = findViewById(R.id.btnPrev), next = findViewById(R.id.btnNext), back = findViewById(R.id.btnBack);
        month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 1);
        prev.setOnClickListener(v -> { month.add(Calendar.MONTH, -1); showMonth(); });
        next.setOnClickListener(v -> { if (!isCurrentMonth()) { month.add(Calendar.MONTH, 1); showMonth(); } });
        back.setOnClickListener(v -> Anim.close(this));
    }

    @Override protected void onResume() { super.onResume(); showMonth(); }

    private boolean isCurrentMonth() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) == month.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == month.get(Calendar.MONTH);
    }

    private void showMonth() {
        textMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.US).format(month.getTime()));
        textSummary.setText("Loading cloud report...");
        textHistory.setText("");
        chart.setData(new double[0], new String[0]);
        if (cloud.uid() == null) {
            textSummary.setText("Please log in again");
            return;
        }

        cloud.sentTransactions().addOnSuccessListener(sent ->
            cloud.receivedTransactions().addOnSuccessListener(received -> {
                ArrayList<DocumentSnapshot> sentDocs = new ArrayList<>(sent.getDocuments());
                ArrayList<DocumentSnapshot> receivedDocs = new ArrayList<>(received.getDocuments());
                Comparator<DocumentSnapshot> newestFirst = (a, b) -> {
                    Timestamp ta = a.getTimestamp("createdAt"), tb = b.getTimestamp("createdAt");
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                };
                Collections.sort(sentDocs, newestFirst);
                Collections.sort(receivedDocs, newestFirst);

                double sentSum = 0, receivedSum = 0;
                int count = 0;
                StringBuilder lines = new StringBuilder();
                double[] monthly = new double[6];
                String[] labels = new String[6];
                Calendar first = (Calendar) month.clone();
                first.add(Calendar.MONTH, -5);
                for (int i = 0; i < 6; i++) {
                    Calendar c = (Calendar) first.clone();
                    c.add(Calendar.MONTH, i);
                    labels[i] = new SimpleDateFormat("MMM", Locale.US).format(c.getTime());
                }

                ArrayList<DocumentSnapshot> thisMonth = new ArrayList<>();
                for (DocumentSnapshot d : sentDocs) {
                    Calendar c = calendarOf(d);
                    if (c == null) continue;
                    long p = amountOf(d);
                    int ix = monthIndex(c, first);
                    if (ix >= 0 && ix < 6) monthly[ix] += p / 100.0;
                    if (sameMonth(c, month)) {
                        sentSum += p / 100.0;
                        count++;
                        thisMonth.add(d);
                    }
                }
                for (DocumentSnapshot d : receivedDocs) {
                    Calendar c = calendarOf(d);
                    if (c == null) continue;
                    long p = amountOf(d);
                    if (sameMonth(c, month)) {
                        receivedSum += p / 100.0;
                        count++;
                        thisMonth.add(d);
                    }
                }
                Collections.sort(thisMonth, newestFirst);
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                for (DocumentSnapshot d : thisMonth) {
                    boolean isSent = cloud.uid().equals(d.getString("senderUid"));
                    Timestamp ts = d.getTimestamp("createdAt");
                    String date = ts == null ? "Pending date" : dateFormat.format(ts.toDate());
                    String status = d.getString("status");
                    if (status == null) status = "completed";
                    lines.append(isSent ? "Sent  -" : "Received  +")
                         .append(DatabaseHelper.money(amountOf(d) / 100.0))
                         .append("\n").append(date)
                         .append("  •  ").append(status).append("\n\n");
                }
                textSummary.setText("Money sent: " + DatabaseHelper.money(sentSum)
                        + "\nMoney received: " + DatabaseHelper.money(receivedSum)
                        + "\nTotal transactions: " + count);
                textHistory.setText(lines.length() == 0 ? "No transactions this month" : lines.toString().trim());
                chart.setData(monthly, labels);
            }).addOnFailureListener(e -> showError("Could not load received transactions", e))
        ).addOnFailureListener(e -> showError("Could not load sent transactions", e));
    }

    private void showError(String prefix, Exception e) {
        textSummary.setText(prefix + ": " + (e.getLocalizedMessage() == null ? "Unknown error" : e.getLocalizedMessage()));
        textHistory.setText("Transactions could not be loaded. Check your connection and Firestore access rules.");
    }

    private Calendar calendarOf(DocumentSnapshot d) {
        Timestamp ts = d.getTimestamp("createdAt");
        if (ts == null) return null;
        Calendar c = Calendar.getInstance();
        c.setTime(ts.toDate());
        return c;
    }

    private long amountOf(DocumentSnapshot d) {
        Long p = d.getLong("amountPiasters");
        return p == null ? 0L : p;
    }

    private boolean sameMonth(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH) == b.get(Calendar.MONTH);
    }

    private int monthIndex(Calendar c, Calendar first) {
        return (c.get(Calendar.YEAR) - first.get(Calendar.YEAR)) * 12
                + c.get(Calendar.MONTH) - first.get(Calendar.MONTH);
    }
}
