package com.mane3.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.security.MessageDigest;

/** Optional PIN that protects switching protection off. */
public class PinLock {
    static String hash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(("mane3:" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return pin; }
    }

    public static boolean has(Context c) { return c.getSharedPreferences("m", 0).getString("pin", null) != null; }

    public static boolean check(Context c, String pin) {
        String h = c.getSharedPreferences("m", 0).getString("pin", null);
        return h == null || (pin != null && h.equals(hash(pin.trim())));
    }

    static volatile long unlockedUntil = 0;
    public static void grant() { unlockedUntil = System.currentTimeMillis() + 90000; }

    public static boolean bioOn(Context c) { return c.getSharedPreferences("m", 0).getBoolean("bio", false); }
    public static void setBio(Context c, boolean on) { c.getSharedPreferences("m", 0).edit().putBoolean("bio", on).apply(); }

    public static boolean bioAvailable(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 28) return false;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                Object bm = c.getSystemService("biometric");
                java.lang.reflect.Method m = bm.getClass().getMethod("canAuthenticate");
                return ((Integer) m.invoke(bm)) == 0;
            }
            return c.getPackageManager().hasSystemFeature("android.hardware.fingerprint");
        } catch (Throwable t) { return false; }
    }

    /** Sets a new PIN (empty = remove). Needs the old PIN (or a fresh unlock) if one exists. */
    public static boolean set(Context c, String oldPin, String newPin) {
        if (!check(c, oldPin) && System.currentTimeMillis() > unlockedUntil) return false;
        if (newPin == null || newPin.trim().isEmpty()) c.getSharedPreferences("m", 0).edit().remove("pin").apply();
        else c.getSharedPreferences("m", 0).edit().putString("pin", hash(newPin.trim())).apply();
        return true;
    }

    /** Native PIN prompt; runs ok right away when no PIN is set. */
    public static void ask(final Activity a, final String why, final Runnable ok) {
        if (!has(a)) { ok.run(); return; }
        if (bioOn(a) && android.os.Build.VERSION.SDK_INT >= 28 && bioAvailable(a)) {
            try {
                final android.hardware.biometrics.BiometricPrompt bp = new android.hardware.biometrics.BiometricPrompt.Builder(a)
                    .setTitle("🔒 " + why)
                    .setNegativeButton("الرقم السري", a.getMainExecutor(), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { askPin(a, why, ok); } })
                    .build();
                bp.authenticate(new android.os.CancellationSignal(), a.getMainExecutor(), new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) { grant(); ok.run(); }
                });
                return;
            } catch (Throwable ignored) {}
        }
        askPin(a, why, ok);
    }

    static void askPin(final Activity a, final String why, final Runnable ok) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint("الرقم السري");
        new AlertDialog.Builder(a).setTitle("🔒 " + why).setView(e)
            .setPositiveButton("تمام", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (check(a, e.getText().toString())) { grant(); ok.run(); }
                else Toast.makeText(a, "❌ الرقم غلط", Toast.LENGTH_SHORT).show();
            }})
            .setNegativeButton("إلغاء", null).show();
    }
}
