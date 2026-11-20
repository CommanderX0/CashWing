package com.example.quickpay;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** Stores only a keyed verifier. The six-digit PIN itself is never persisted. */
public final class DevicePinManager {
    private static final String STORE = "cashwing_device_security";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    public static void save(Context context, String uid, String pin) throws Exception {
        if (pin == null || !pin.matches("\\d{6}")) throw new IllegalArgumentException("PIN must be 6 digits");
        String alias = alias(uid);
        SecretKey key = getOrCreateKey(alias);
        String verifier = hmac(key, pin);
        prefs(context).edit().putString("pin_" + uid, verifier).apply();
    }

    public static boolean verify(Context context, String uid, String pin) {
        try {
            if (uid == null || pin == null || !pin.matches("\\d{6}")) return false;
            SharedPreferences p = prefs(context);
            long lockedUntil = p.getLong("locked_" + uid, 0L);
            if (System.currentTimeMillis() < lockedUntil) return false;
            String expected = p.getString("pin_" + uid, null);
            if (expected == null) return false;
            String actual = hmac(getOrCreateKey(alias(uid)), pin);
            boolean ok = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
            if (ok) {
                p.edit().remove("attempts_" + uid).remove("locked_" + uid).apply();
                return true;
            }
            int attempts = p.getInt("attempts_" + uid, 0) + 1;
            if (attempts >= 5) p.edit().putInt("attempts_" + uid, 0).putLong("locked_" + uid, System.currentTimeMillis() + 60_000L).apply();
            else p.edit().putInt("attempts_" + uid, attempts).apply();
            return false;
        } catch (Exception e) { return false; }
    }

    public static boolean isConfigured(Context context, String uid) {
        return uid != null && prefs(context).contains("pin_" + uid);
    }

    public static void clear(Context context, String uid) {
        if (uid == null) return;
        prefs(context).edit().remove("pin_" + uid).apply();
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            ks.deleteEntry(alias(uid));
        } catch (Exception ignored) {}
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(STORE, Context.MODE_PRIVATE); }
    private static String alias(String uid) { return "CashWingPinHmac_" + uid.replaceAll("[^A-Za-z0-9_-]", "_"); }

    private static SecretKey getOrCreateKey(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
        ks.load(null);
        if (ks.containsAlias(alias)) return (SecretKey) ks.getKey(alias, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256).build());
        return generator.generateKey();
    }

    private static String hmac(SecretKey key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.US, "%02x", b));
        return out.toString();
    }
}
