package com.example.quickpay;

/** Maps an Egyptian phone number to a private, deterministic Firebase email identity.
 * The user still signs in with phone + PIN; the generated email is never shown in the UI.
 */
public final class PhoneCredential {
    private static final String AUTH_DOMAIN = "cashwing-login.com";
    private PhoneCredential() {}

    public static String emailForPhone(String e164) {
        if (e164 == null || !e164.matches("\\+20\\d{10}")) return "";
        return e164.substring(1) + "@" + AUTH_DOMAIN;
    }

    public static String phoneForEmail(String email) {
        String suffix = "@" + AUTH_DOMAIN;
        if (email == null || !email.endsWith(suffix)) return "";
        String key = email.substring(0, email.length() - suffix.length());
        return key.matches("20\\d{10}") ? "+" + key : "";
    }

    public static String phoneKey(String e164) {
        return e164 != null && e164.matches("\\+20\\d{10}") ? e164.substring(1) : "";
    }
}
