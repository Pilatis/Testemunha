package com.testemunha;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** PIN do cofre: guardamos so o hash (salgado), nunca o PIN em texto. */
final class Lock {
    private Lock() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("lock", Context.MODE_PRIVATE);
    }

    static boolean hasPin(Context c) {
        return sp(c).contains("hash");
    }

    static void setPin(Context c, String pin) {
        String salt = randomSalt();
        sp(c).edit().putString("salt", salt).putString("hash", hash(pin, salt)).apply();
    }

    static void clearPin(Context c) {
        sp(c).edit().remove("salt").remove("hash").apply();
    }

    static boolean check(Context c, String pin) {
        String salt = sp(c).getString("salt", "");
        String hash = sp(c).getString("hash", "");
        return !hash.isEmpty() && hash.equals(hash(pin, salt));
    }

    private static String randomSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return toHex(b);
    }

    private static String hash(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes("UTF-8"));
            return toHex(md.digest(pin.getBytes("UTF-8")));
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
