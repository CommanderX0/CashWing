package com.example.quickpay;

public final class PhoneUtil {
    private PhoneUtil() {}
    public static String toE164(String input) {
        if (input == null) return "";
        String p = input.trim().replace(" ", "").replace("-", "");
        if (p.startsWith("+20") && p.matches("\\+20\\d{10}")) return p;
        if (p.startsWith("0020") && p.matches("0020\\d{10}")) return "+" + p.substring(2);
        if (p.matches("01\\d{9}")) return "+20" + p.substring(1);
        return "";
    }
    public static String local(String e164) {
        if (e164 != null && e164.matches("\\+20\\d{10}")) return "0" + e164.substring(3);
        return e164 == null ? "" : e164;
    }
}
