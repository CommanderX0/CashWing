package com.example.quickpay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "quickpay.db";
    private static final int DB_VERSION = 5; // v3: PINs hashed, v4: deleted accounts + login lock, v5: indexes
    private static final int MAX_ATTEMPTS = 3; // wrong PINs before the account is locked

    public static final String CHARITY_PHONE = "00000000000";
    public static final String CHARITY_NAME = "Charity Fund";

    public static final String COMPLETED = "Completed";
    public static final String REJECTED = "Rejected";

    private static DatabaseHelper instance;

    // one shared helper for the whole app (safer than a new helper in every screen)
    public static synchronized DatabaseHelper get(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users ("
                + "phone TEXT PRIMARY KEY, "
                + "name TEXT, "
                + "pin TEXT, "
                + "balance REAL, "
                + "active INTEGER DEFAULT 1, "          // 0 = deleted account
                + "failed INTEGER DEFAULT 0, "          // wrong PINs in a row
                + "locked_until TEXT DEFAULT '')");     // login is locked until this time

        db.execSQL("CREATE TABLE transactions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "sender TEXT, "
                + "receiver TEXT, "
                + "amount REAL, "
                + "status TEXT, "
                + "time TEXT DEFAULT (datetime('now','localtime')))");

        createIndexes(db);

        ContentValues fund = new ContentValues();
        fund.put("phone", CHARITY_PHONE);
        fund.put("name", CHARITY_NAME);
        fund.put("pin", "");
        fund.put("balance", 0.0);
        db.insert("users", null, fund);
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_sender ON transactions(sender)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_receiver ON transactions(receiver)");
    }

    // Upgrades keep the users' data (the old version deleted everything on every upgrade)
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            Cursor c = db.rawQuery("SELECT phone, pin FROM users WHERE pin != ''", null);
            while (c.moveToNext()) {
                db.execSQL("UPDATE users SET pin = ? WHERE phone = ?",
                        new Object[]{hashPin(c.getString(0), c.getString(1)), c.getString(0)});
            }
            c.close();
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE users ADD COLUMN active INTEGER DEFAULT 1");
            db.execSQL("ALTER TABLE users ADD COLUMN failed INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE users ADD COLUMN locked_until TEXT DEFAULT ''");
        }
        if (oldVersion < 5) {
            createIndexes(db);
        }
    }

    // true only for an account that is not deleted
    public boolean userExists(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT phone FROM users WHERE phone = ? AND active = 1",
                new String[]{phone});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    // true if the phone was ever used, even by a deleted account
    private boolean phoneTaken(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT phone FROM users WHERE phone = ?",
                new String[]{phone});
        boolean taken = c.moveToFirst();
        c.close();
        return taken;
    }

    public boolean registerUser(String name, String phone, String pin) {
        if (phoneTaken(phone)) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("phone", phone);
        values.put("name", name);
        values.put("pin", hashPin(phone, pin));
        values.put("balance", 20000.0);
        long result = db.insert("users", null, values);
        return result != -1;
    }

    public boolean login(String phone, String pin) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT phone FROM users WHERE phone = ? AND pin = ? AND active = 1",
                new String[]{phone, hashPin(phone, pin)});
        boolean ok = c.moveToFirst();
        c.close();
        return ok;
    }

    // Login with a limit: after 3 wrong PINs the account is locked for 5 minutes.
    // Returns "OK" or the message to show to the user.
    public String tryLogin(String phone, String pin) {
        if (!userExists(phone)) {
            return "Wrong phone or PIN";
        }

        SQLiteDatabase db = getWritableDatabase();

        // is the account locked now? (the time is compared inside SQL)
        Cursor c = db.rawQuery("SELECT phone FROM users WHERE phone = ? "
                + "AND locked_until > datetime('now','localtime')", new String[]{phone});
        boolean locked = c.moveToFirst();
        c.close();
        if (locked) {
            return "Too many wrong attempts. Try again after 5 minutes";
        }

        if (login(phone, pin)) {
            db.execSQL("UPDATE users SET failed = 0, locked_until = '' WHERE phone = ?",
                    new Object[]{phone});
            return "OK";
        }

        // wrong PIN: count it
        db.execSQL("UPDATE users SET failed = failed + 1 WHERE phone = ?", new Object[]{phone});
        int failed = 0;
        c = db.rawQuery("SELECT failed FROM users WHERE phone = ?", new String[]{phone});
        if (c.moveToFirst()) {
            failed = c.getInt(0);
        }
        c.close();

        if (failed >= MAX_ATTEMPTS) {
            db.execSQL("UPDATE users SET failed = 0, "
                    + "locked_until = datetime('now','localtime','+5 minutes') WHERE phone = ?",
                    new Object[]{phone});
            return "Too many wrong attempts. Try again after 5 minutes";
        }
        return "Wrong phone or PIN (" + (MAX_ATTEMPTS - failed) + " attempts left)";
    }

    public void changePin(String phone, String newPin) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE users SET pin = ? WHERE phone = ?",
                new Object[]{hashPin(phone, newPin), phone});
    }

    // The account is switched off, but its transactions stay in the database,
    // so the other users keep a correct history (they see "Deleted account").
    public void deleteUser(String phone) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE users SET active = 0, pin = '', balance = 0 WHERE phone = ?",
                new Object[]{phone});
    }

    public String getName(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT name, active FROM users WHERE phone = ?",
                new String[]{phone});
        String name = "Unknown";
        if (c.moveToFirst()) {
            if (c.getInt(1) == 1) {
                name = c.getString(0);
            } else {
                name = "Deleted account";
            }
        }
        c.close();
        return name;
    }

    public double getBalance(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT balance FROM users WHERE phone = ?",
                new String[]{phone});
        double balance = 0;
        if (c.moveToFirst()) {
            balance = c.getDouble(0);
        }
        c.close();
        return balance;
    }

    // returns "OK" or the error message
    // The balance check and the update happen inside ONE database transaction,
    // so two transfers at the same time can never spend the same money twice.
    public String transfer(String sender, String receiver, double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            return "Invalid amount";
        }
        if (sender.equals(receiver)) {
            return "You cannot send money to yourself";
        }
        if (!userExists(receiver)) {
            return "Receiver account not found";
        }

        SQLiteDatabase db = getWritableDatabase();
        boolean enough = false;
        db.beginTransaction(); // all steps or nothing
        try {
            SQLiteStatement take = db.compileStatement(
                    "UPDATE users SET balance = ROUND(balance - ?, 2) "
                            + "WHERE phone = ? AND active = 1 AND balance + 0.001 >= ?");
            take.bindDouble(1, amount);
            take.bindString(2, sender);
            take.bindDouble(3, amount);
            if (take.executeUpdateDelete() == 1) {
                db.execSQL("UPDATE users SET balance = ROUND(balance + ?, 2) WHERE phone = ?",
                        new Object[]{amount, receiver});
                db.execSQL("INSERT INTO transactions (sender, receiver, amount, status) VALUES (?, ?, ?, ?)",
                        new Object[]{sender, receiver, amount, COMPLETED});
                enough = true;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (!enough) {
            addTransaction(sender, receiver, amount, REJECTED);
            return "Insufficient balance";
        }
        return "OK";
    }

    private void addTransaction(String sender, String receiver, double amount, String status) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT INTO transactions (sender, receiver, amount, status) VALUES (?, ?, ?, ?)",
                new Object[]{sender, receiver, amount, status});
    }

    public String getHistory(String phone, int limit, boolean compact) {
        return getHistory(phone, limit, compact, "All", "");
    }

    // filter: All / Sent / Received / Rejected, search: name or phone of the other person
    public String getHistory(String phone, int limit, boolean compact, String filter, String search) {
        return getHistory(phone, limit, compact, filter, search, "");
    }

    // month: "yyyy-MM" to show only that month, or "" for everything
    public String getHistory(String phone, int limit, boolean compact, String filter, String search, String month) {
        String q = search.trim().toLowerCase();

        String sql = "SELECT sender, receiver, amount, time, status FROM transactions "
                + "WHERE (sender = ? OR (receiver = ? AND status = 'Completed'))";
        String[] args;
        if (month.isEmpty()) {
            args = new String[]{phone, phone};
        } else {
            sql = sql + " AND strftime('%Y-%m', time) = ?";
            args = new String[]{phone, phone, month};
        }

        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(sql + " ORDER BY id DESC", args);

        StringBuilder text = new StringBuilder();
        Map<String, String> names = new HashMap<>(); // one name lookup per person, not per line
        int shown = 0; // the limit counts only the lines that pass the filter and the search
        while (c.moveToNext() && shown < limit) {
            String sender = c.getString(0);
            String receiver = c.getString(1);
            double amount = c.getDouble(2);
            String time = c.getString(3);
            String status = c.getString(4);

            String other;
            String type;
            if (sender.equals(phone)) {
                other = receiver;
                type = REJECTED.equals(status) ? "Rejected" : "Sent";
            } else {
                other = sender;
                type = "Received";
            }

            if (!filter.equals("All") && !filter.equals(type)) {
                continue;
            }
            String otherName = names.get(other);
            if (otherName == null) {
                otherName = getName(other);
                names.put(other, otherName);
            }
            if (!q.isEmpty() && !otherName.toLowerCase().contains(q) && !other.contains(q)) {
                continue;
            }

            String line;
            if (type.equals("Rejected")) {
                line = "Rejected: " + money(amount) + " to " + otherName;
            } else if (type.equals("Sent")) {
                line = "Sent " + money(amount) + " to " + otherName;
            } else {
                line = "Received " + money(amount) + " from " + otherName;
            }

            if (compact) {
                text.append(line).append("\n");
            } else {
                text.append(line).append("\n").append(time).append("  |  ").append(status).append("\n\n");
            }
            shown = shown + 1;
        }
        c.close();

        if (text.length() == 0) {
            if (!filter.equals("All") || !q.isEmpty()) {
                return "No matching transactions.";
            }
            if (!month.isEmpty()) {
                return "No transactions in this month.";
            }
            return "No transactions yet.";
        }
        return text.toString().trim();
    }

    // money sent each day for the last `days` days (oldest first), used by the chart
    public double[] getDailySent(String phone, int days) {
        double[] result = new double[days];
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SQLiteDatabase db = getReadableDatabase();
        for (int i = 0; i < days; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, i - (days - 1));
            Cursor c = db.rawQuery("SELECT SUM(amount) FROM transactions "
                            + "WHERE sender = ? AND status = 'Completed' AND date(time) = ?",
                    new String[]{phone, format.format(day.getTime())});
            if (c.moveToFirst()) {
                result[i] = c.getDouble(0);
            }
            c.close();
        }
        return result;
    }

    public static String[] lastDays(int days) {
        String[] labels = new String[days];
        SimpleDateFormat format = new SimpleDateFormat("EEE", Locale.US);
        for (int i = 0; i < days; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, i - (days - 1));
            labels[i] = format.format(day.getTime());
        }
        return labels;
    }

    // "2026-11" style key for a month, matches strftime('%Y-%m', time)
    public static String monthKey(Calendar month) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(month.getTime());
    }

    public double getSentInMonth(String phone, String month) {
        return sumMonth("sender", phone, month);
    }

    public double getReceivedInMonth(String phone, String month) {
        return sumMonth("receiver", phone, month);
    }

    private double sumMonth(String column, String phone, String month) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(amount) FROM transactions WHERE " + column
                        + " = ? AND status = 'Completed' AND strftime('%Y-%m', time) = ?",
                new String[]{phone, month});
        double total = 0;
        if (c.moveToFirst()) {
            total = c.getDouble(0);
        }
        c.close();
        return total;
    }

    public int getCountInMonth(String phone, String month) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM transactions "
                        + "WHERE (sender = ? OR receiver = ?) AND status = 'Completed' "
                        + "AND strftime('%Y-%m', time) = ?",
                new String[]{phone, phone, month});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    // money sent in each of the `months` months ending at `end` (oldest first), used by the chart
    public double[] getMonthlySent(String phone, Calendar end, int months) {
        double[] result = new double[months];
        for (int i = 0; i < months; i++) {
            Calendar m = (Calendar) end.clone();
            m.add(Calendar.MONTH, i - (months - 1));
            result[i] = getSentInMonth(phone, monthKey(m));
        }
        return result;
    }

    public static String[] monthLabels(Calendar end, int months) {
        String[] labels = new String[months];
        SimpleDateFormat format = new SimpleDateFormat("MMM", Locale.US);
        for (int i = 0; i < months; i++) {
            Calendar m = (Calendar) end.clone();
            m.add(Calendar.MONTH, i - (months - 1));
            labels[i] = format.format(m.getTime());
        }
        return labels;
    }

    // id of the newest completed transfer received by this phone (0 if none)
    public long getLastReceivedId(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT MAX(id) FROM transactions "
                + "WHERE receiver = ? AND status = 'Completed'", new String[]{phone});
        long id = 0;
        if (c.moveToFirst()) {
            id = c.getLong(0);
        }
        c.close();
        return id;
    }

    // message about the transfers received after `afterId` ("" if there are none)
    public String getReceivedSince(String phone, long afterId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT sender, amount FROM transactions "
                        + "WHERE receiver = ? AND status = 'Completed' AND id > ? ORDER BY id",
                new String[]{phone, String.valueOf(afterId)});
        int count = 0;
        double total = 0;
        String lastSender = "";
        while (c.moveToNext()) {
            lastSender = c.getString(0);
            total = total + c.getDouble(1);
            count++;
        }
        c.close();

        if (count == 0) {
            return "";
        }
        if (count == 1) {
            return "You received " + money(total) + " from " + getName(lastSender);
        }
        return "You received " + money(total) + " (" + count + " transfers)";
    }

    public double getTotalSent(String phone) {
        return sum("SELECT SUM(amount) FROM transactions WHERE sender = ? AND status = 'Completed'", phone);
    }

    public double getTotalReceived(String phone) {
        return sum("SELECT SUM(amount) FROM transactions WHERE receiver = ? AND status = 'Completed'", phone);
    }

    private double sum(String sql, String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(sql, new String[]{phone});
        double total = 0;
        if (c.moveToFirst()) {
            total = c.getDouble(0);
        }
        c.close();
        return total;
    }

    // the PIN is never saved as plain text, only its SHA-256 hash (salted with the phone)
    public static String hashPin(String phone, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((phone + ":" + pin).getBytes(StandardCharsets.UTF_8));
            String hex = "";
            for (byte b : bytes) {
                hex = hex + String.format("%02x", b);
            }
            return hex;
        } catch (NoSuchAlgorithmException e) {
            return pin;
        }
    }

    // Egyptian mobile number: 01 + (0,1,2,5) + 8 digits
    public static boolean isValidPhone(String phone) {
        return phone.matches("01[0125][0-9]{8}");
    }

    public static String normalize(String s) {
        String out = "";
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') {
                ch = (char) ('0' + (ch - '\u0660'));
            } else if (ch >= '\u06F0' && ch <= '\u06F9') {
                ch = (char) ('0' + (ch - '\u06F0'));
            } else if (ch == '\u066B' || ch == ',') {
                ch = '.';
            }
            out = out + ch;
        }
        return out;
    }

    public static String money(double value) {
        return String.format(Locale.US, "%.2f EGP", value);
    }
}
