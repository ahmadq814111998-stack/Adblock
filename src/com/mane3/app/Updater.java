package com.mane3.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** In-app updates from the public hassala-updates repo (folder mane3/). */
public class Updater {
    public static final String VERSION_URL = "https://raw.githubusercontent.com/ahmadq814111998-stack/adblock/main/updates/version.json";
    public static volatile String state = "";      // "", checking, latest, available, downloading, installing, error
    public static volatile String latestName = "";
    public static volatile String notes = "";
    public static volatile int progress = 0;
    static volatile String apkUrl = null;
    static volatile int latestCode = 0;

    public static int myCode(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    public static String myName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    static String get(String u) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(u + "?t=" + System.currentTimeMillis()).openConnection();
        con.setConnectTimeout(10000); con.setReadTimeout(15000);
        con.setUseCaches(false);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally { con.disconnect(); }
    }

    /** Blocking; call from a background thread. */
    public static void check(Context c) {
        state = "checking";
        try {
            JSONObject o = new JSONObject(get(VERSION_URL));
            latestCode = o.getInt("versionCode");
            latestName = o.optString("versionName", "");
            notes = o.optString("notes", "");
            apkUrl = o.getString("apk");
            state = latestCode > myCode(c) ? "available" : "latest";
            c.getSharedPreferences("m", 0).edit().putLong("updCheck", System.currentTimeMillis()).apply();
        } catch (Exception e) { state = "error"; }
    }

    public static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    public static void askInstallPermission(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
    }

    /** Blocking: download then hand the APK to the system installer. */
    public static void downloadAndInstall(Context c) {
        if (apkUrl == null) { check(c); if (!"available".equals(state)) return; }
        File f = new File(c.getCacheDir(), "update.apk");
        try {
            state = "downloading"; progress = 0;
            HttpURLConnection con = (HttpURLConnection) new URL(apkUrl + "?t=" + System.currentTimeMillis()).openConnection();
            con.setConnectTimeout(15000); con.setReadTimeout(30000);
            long total = con.getContentLength(), got = 0;
            try (InputStream in = new BufferedInputStream(con.getInputStream()); OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); got += n; if (total > 0) progress = (int) (got * 100 / total); }
            }
            con.disconnect();
            state = "installing";
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(c.getPackageName());
            int id = pi.createSession(sp);
            PackageInstaller.Session s = pi.openSession(id);
            try (InputStream in = new FileInputStream(f); OutputStream out = s.openWrite("mane3.apk", 0, f.length())) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 /* FLAG_MUTABLE */ : 0);
            PendingIntent p = PendingIntent.getBroadcast(c, 7, new Intent(c, InstallReceiver.class), flags);
            s.commit(p.getIntentSender());
            s.close();
        } catch (Exception e) {
            state = "error";
        }
    }
}
