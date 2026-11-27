package com.mane3.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class Bridge {
    final MainActivity a;
    final Context c;
    static volatile boolean busy = false;
    static volatile String busyMsg = "";

    Bridge(MainActivity a) {
        this.a = a; this.c = a.getApplicationContext();
        new Thread(new Runnable() { public void run() { Rules.load(c); } }).start();
    }

    SharedPreferences p() { return c.getSharedPreferences("m", 0); }

    @JavascriptInterface public String state() {
        try {
            JSONObject o = new JSONObject();
            o.put("on", AdVpn.wanted);
            o.put("clean", p().getBoolean("clean", false));
            o.put("paused", AdVpn.pausedFor == null ? "" : AdVpn.label(c, AdVpn.pausedFor));
            o.put("usageOk", AdVpn.usageOk(c));
            o.put("pauseCount", p().getStringSet("excluded", new HashSet<String>()).size());
            o.put("blocked", AdVpn.blocked.get());
            o.put("total", AdVpn.total.get());
            o.put("allBlocked", p().getLong("allBlocked", 0) + (AdVpn.running ? AdVpn.blocked.get() : 0));
            o.put("rules", Rules.count > 0 ? Rules.count : p().getInt("count", 0));
            o.put("updated", p().getLong("updated", 0));
            o.put("server", AdVpn.server(c)[1]);
            o.put("busy", busy);
            o.put("busyMsg", busyMsg);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface public void toggle() {
        a.runOnUiThread(new Runnable() { public void run() { a.toggleVpn(); } });
    }

    @JavascriptInterface public String lists() {
        try {
            Set<String> on = Lists.enabled(c);
            JSONArray arr = new JSONArray();
            for (Lists.L x : Lists.ALL) {
                JSONObject o = new JSONObject();
                o.put("id", x.id); o.put("cat", x.cat); o.put("name", x.name); o.put("desc", x.desc);
                o.put("on", on.contains(x.id));
                java.io.File f = Lists.file(c, x.id);
                o.put("have", f.exists()); o.put("size", f.exists() ? f.length() : 0);
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) { return "[]"; }
    }

    @JavascriptInterface public void setList(String id, boolean on) {
        Set<String> s = Lists.enabled(c);
        if (on) s.add(id); else s.remove(id);
        Lists.setEnabled(c, s);
        runUpdate(true);
    }

    @JavascriptInterface public void updateAll() { runUpdate(false); }

    void runUpdate(final boolean onlyMissing) {
        if (busy) { pending = true; pendingMissing &= onlyMissing; return; }
        busy = true; busyMsg = "جاري التحميل…";
        new Thread(new Runnable() { public void run() {
            try {
                Lists.update(c, onlyMissing, new Lists.Progress() {
                    public void on(String id, int done, int total, boolean ok) {
                        busyMsg = "تحميل القوائم " + done + " من " + total + (ok ? "" : " (فشل: " + id + ")");
                    }
                });
            } catch (Throwable ignored) {}
            busy = false; busyMsg = "";
            if (pending) { boolean m = pendingMissing; pending = false; pendingMissing = true; runUpdate(m); }
            a.js("refresh()");
        }}).start();
    }
    volatile boolean pending = false, pendingMissing = true;

    @JavascriptInterface public String log(String filter) {
        try {
            JSONArray arr = new JSONArray();
            String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
            int n = 0;
            for (String[] e : AdVpn.logCopy()) {
                if (f.equals("blocked") && !e[2].startsWith("b")) continue;
                if (f.equals("allowed") && e[2].startsWith("b")) continue;
                if (!f.isEmpty() && !f.equals("blocked") && !f.equals("allowed") && !e[1].contains(f)) continue;
                JSONArray r = new JSONArray(); r.put(Long.parseLong(e[0])); r.put(e[1]); r.put(e[2]);
                arr.put(r);
                if (++n >= 300) break;
            }
            return arr.toString();
        } catch (Exception e) { return "[]"; }
    }

    @JavascriptInterface public String custom() {
        try {
            JSONObject o = new JSONObject();
            o.put("allow", new JSONArray(Rules.getSet(c, "allow")));
            o.put("block", new JSONArray(Rules.getSet(c, "block")));
            return o.toString();
        } catch (Exception e) { return "{\"allow\":[],\"block\":[]}"; }
    }

    /** kind = allow|block ; returns cleaned domain or "" if invalid. */
    @JavascriptInterface public String addCustom(String kind, String domain) {
        String d = Rules.clean(domain == null ? "" : domain);
        if (d == null) return "";
        String other = kind.equals("allow") ? "block" : "allow";
        Set<String> s = Rules.getSet(c, kind); s.add(d); Rules.putSet(c, kind, s);
        Set<String> o = Rules.getSet(c, other); if (o.remove(d)) Rules.putSet(c, other, o);
        return d;
    }

    @JavascriptInterface public void removeCustom(String kind, String domain) {
        Set<String> s = Rules.getSet(c, kind); s.remove(domain); Rules.putSet(c, kind, s);
    }

    @JavascriptInterface public String servers() {
        try {
            JSONArray arr = new JSONArray();
            String cur = AdVpn.server(c)[0];
            for (String[] s : AdVpn.SERVERS) {
                JSONObject o = new JSONObject();
                o.put("id", s[0]); o.put("name", s[1]); o.put("ip", s[2]); o.put("on", s[0].equals(cur));
                o.put("ads", s[0].startsWith("adguard") || s[0].equals("controld"));
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) { return "[]"; }
    }

    @JavascriptInterface public void setServer(String id) {
        p().edit().putString("server", id).apply();
        a.runOnUiThread(new Runnable() { public void run() { a.restartVpn(); } });
    }

    @JavascriptInterface public String apps() {
        try {
            PackageManager pm = c.getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ri = pm.queryIntentActivities(i, 0);
            Set<String> ex = p().getStringSet("excluded", new HashSet<String>());
            TreeMap<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (ResolveInfo r : ri) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                m.put(r.loadLabel(pm).toString() + "\u0000" + pkg, pkg);
            }
            JSONArray arr = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (!seen.add(e.getValue())) continue;
                JSONObject o = new JSONObject();
                o.put("pkg", e.getValue());
                o.put("label", e.getKey().substring(0, e.getKey().indexOf('\u0000')));
                o.put("ex", ex.contains(e.getValue()));
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) { return "[]"; }
    }

    @JavascriptInterface public void setExcluded(String pkg, boolean on) {
        Set<String> s = new HashSet<>(p().getStringSet("excluded", new HashSet<String>()));
        if (on) s.add(pkg); else s.remove(pkg);
        p().edit().putStringSet("excluded", s).apply();
        a.runOnUiThread(new Runnable() { public void run() { a.restartVpn(); } });
    }

    @JavascriptInterface public void vpnSettings() {
        try { a.startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)); } catch (Exception ignored) {}
    }

    @JavascriptInterface public void dnsSettings() {
        try { a.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); } catch (Exception ignored) {}
    }

    /** Clean mode: adult + gambling lists, AdGuard Family DNS (forces Google / YouTube / Bing safe search). */
    @JavascriptInterface public void setClean(boolean on) {
        SharedPreferences.Editor e = p().edit();
        Set<String> s = Lists.enabled(c);
        if (on) {
            String cur = p().getString("server", "adguard");
            if (!cur.equals("adguard_family")) e.putString("prevServer", cur);
            e.putString("server", "adguard_family").putBoolean("clean", true);
            s.add("hnsfw"); s.add("hgamble");
        } else {
            e.putString("server", p().getString("prevServer", "adguard")).putBoolean("clean", false);
            s.remove("hnsfw"); s.remove("hgamble");
        }
        e.apply();
        Lists.setEnabled(c, s);
        runUpdate(true);
        a.runOnUiThread(new Runnable() { public void run() { a.restartVpn(); } });
    }

    /** Android 13+: ask the system to add our quick-settings button. */
    @JavascriptInterface public String addTile() {
        if (android.os.Build.VERSION.SDK_INT < 33) return "manual";
        try {
            Object sbm = c.getSystemService("statusbar");
            java.lang.reflect.Method m = null;
            for (java.lang.reflect.Method x : sbm.getClass().getMethods()) if (x.getName().equals("requestAddTileService")) m = x;
            if (m == null) return "manual";
            int ic = c.getResources().getIdentifier("ic_tile", "drawable", c.getPackageName());
            m.invoke(sbm, new android.content.ComponentName(c, QsTile.class), "مانع",
                android.graphics.drawable.Icon.createWithResource(c, ic), a.getMainExecutor(),
                new java.util.function.Consumer<Integer>() { public void accept(Integer r) {} });
            return "ok";
        } catch (Throwable t) { return "manual"; }
    }

    // ---------- lock times ----------
    @JavascriptInterface public String schedule() { return Schedule.json(c); }
    @JavascriptInterface public void setSchedule(String json) {
        Schedule.save(c, json);
        a.runOnUiThread(new Runnable() { public void run() { a.restartVpn(); } });
    }

    // ---------- native lock (fingerprint or PIN) ----------
    @JavascriptInterface public void askLock(final String why, final int id) {
        a.runOnUiThread(new Runnable() { public void run() {
            PinLock.ask(a, why, new Runnable() { public void run() { a.js("window.__lockOk&&__lockOk(" + id + ")"); } });
        }});
    }
    @JavascriptInterface public boolean bioAvailable() { return PinLock.bioAvailable(c); }
    @JavascriptInterface public boolean bioOn() { return PinLock.bioOn(c); }
    @JavascriptInterface public void setBio(boolean on) { PinLock.setBio(c, on); }
    @JavascriptInterface public boolean browserLock() { return c.getSharedPreferences("b", 0).getBoolean("lock", false); }
    @JavascriptInterface public void setBrowserLock(boolean on) { c.getSharedPreferences("b", 0).edit().putBoolean("lock", on).apply(); }

    // ---------- DNS speed test ----------
    /** Best of 6 round trips. Warm-up query first so the server has the answer cached (we measure the network, not its cache misses). */
    static int ping(String ip) {
        int best = -1;
        java.net.DatagramSocket s = null;
        try {
            s = new java.net.DatagramSocket();
            AdVpn.protectSocket(s);
            s.setSoTimeout(1200);
            java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
            for (int t = 0; t < 7; t++) {
                try {
                    byte[] q = dnsQuery("google.com");
                    long t0 = System.nanoTime();
                    s.send(new java.net.DatagramPacket(q, q.length, addr, 53));
                    byte[] r = new byte[1500];
                    s.receive(new java.net.DatagramPacket(r, r.length));
                    int ms = (int) ((System.nanoTime() - t0) / 1000000);
                    if (t > 0 && (best < 0 || ms < best)) best = ms;   // t==0 is the warm-up
                } catch (java.net.SocketTimeoutException te) { /* lost packet: try again */ }
            }
        } catch (Exception ignored) {
        } finally { if (s != null) s.close(); }
        return best;
    }

    static byte[] dnsQuery(String name) {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        int id = (int) (Math.random() * 65535);
        b.write(id >> 8); b.write(id & 255); b.write(1); b.write(0); b.write(0); b.write(1);
        for (int i = 0; i < 6; i++) b.write(0);
        for (String part : name.split("\\.")) { b.write(part.length()); for (char ch : part.toCharArray()) b.write(ch); }
        b.write(0); b.write(0); b.write(1); b.write(0); b.write(1);
        return b.toByteArray();
    }

    @JavascriptInterface public void speedTest() {
        new Thread(new Runnable() { public void run() {
            try {
                final JSONObject o = new JSONObject();
                List<Thread> ts = new ArrayList<>();
                for (final String[] sv : AdVpn.SERVERS) {
                    Thread t = new Thread(new Runnable() { public void run() {
                        int ms = ping(sv[2]);
                        synchronized (o) { try { o.put(sv[0], ms); } catch (Exception ignored) {} }
                    }});
                    ts.add(t); t.start();
                }
                for (Thread t : ts) t.join(12000);
                a.js("window.__speed&&__speed(" + o.toString() + ")");
            } catch (Exception e) { a.js("window.__speed&&__speed({})"); }
        }}).start();
    }

    // ---------- share the app ----------
    static final String DL = "https://github.com/ahmadq814111998-stack/adblock/raw/main/updates/Mane3.apk";

    @JavascriptInterface public void shareLink() {
        String t = "🛡️ جرّب «مانع»: يمنع الدعايات من الألعاب والبرامج والمواقع، وفيه متصفح بدون دعايات مع تحميل وتسريع الفيديو.\n\n"
            + "📥 حمّله من هني:\n" + DL + "\n\n"
            + "بعد التحميل افتح الملف ووافق على «تثبيت من مصادر غير معروفة».";
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t);
        a.startActivity(Intent.createChooser(i, "شارك مانع"));
    }

    /** Copies our own APK to Downloads/Mane3 and shares the file itself. */
    @JavascriptInterface public void shareFile() {
        new Thread(new Runnable() { public void run() {
            try {
                java.io.File src = new java.io.File(c.getApplicationInfo().sourceDir);
                android.net.Uri uri;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put("_display_name", "Mane3-" + Updater.myName(c) + ".apk");
                    v.put("mime_type", "application/vnd.android.package-archive");
                    v.put("relative_path", "Download/Mane3");
                    uri = c.getContentResolver().insert(android.net.Uri.parse("content://media/external/downloads"), v);
                    try (java.io.InputStream in = new java.io.FileInputStream(src); java.io.OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                        byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                } else { a.runOnUiThread(new Runnable() { public void run() { shareLink(); } }); return; }
                final android.net.Uri u = uri;
                a.runOnUiThread(new Runnable() { public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("application/vnd.android.package-archive")
                        .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(Intent.createChooser(i, "أرسل ملف مانع"));
                }});
            } catch (Exception e) { a.runOnUiThread(new Runnable() { public void run() { shareLink(); } }); }
        }}).start();
    }

    @JavascriptInterface public boolean strict() { return p().getBoolean("strict", true); }
    @JavascriptInterface public void setStrict(boolean on) {
        p().edit().putBoolean("strict", on).apply();
        a.runOnUiThread(new Runnable() { public void run() { a.restartVpn(); } });
    }
    @JavascriptInterface public String engine() { return "{\"net\":" + Filters.netCount + ",\"cos\":" + Filters.cosCount + ",\"ready\":" + Filters.ready + "}"; }

    // ---------- stats ----------
    @JavascriptInterface public String stats() { return Stats.json(c); }
    @JavascriptInterface public void resetStats() { Stats.reset(c); }

    // ---------- backup ----------
    @JavascriptInterface public void backup() { a.runOnUiThread(new Runnable() { public void run() { a.backupFile(); } }); }
    @JavascriptInterface public void restore() { a.runOnUiThread(new Runnable() { public void run() { a.restoreFile(); } }); }

    // ---------- PIN ----------
    @JavascriptInterface public boolean hasPin() { return PinLock.has(c); }
    @JavascriptInterface public boolean checkPin(String p) { return PinLock.check(c, p); }
    @JavascriptInterface public boolean setPin(String oldPin, String newPin) { return PinLock.set(c, oldPin, newPin); }

    // ---------- updates ----------
    @JavascriptInterface public String version() { return Updater.myName(c); }
    @JavascriptInterface public String upd() {
        try {
            JSONObject o = new JSONObject();
            o.put("state", Updater.state); o.put("name", Updater.latestName); o.put("notes", Updater.notes);
            o.put("progress", Updater.progress); o.put("canInstall", Updater.canInstall(c));
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }
    @JavascriptInterface public void checkUpdate(boolean force) {
        long last = p().getLong("updCheck", 0);
        if (!force && System.currentTimeMillis() - last < 6 * 3600 * 1000L) return;
        new Thread(new Runnable() { public void run() { Updater.check(c); a.js("refresh()"); } }).start();
    }
    @JavascriptInterface public void doUpdate() {
        if (!Updater.canInstall(c)) { Updater.askInstallPermission(a); return; }
        new Thread(new Runnable() { public void run() { Updater.downloadAndInstall(c); a.js("refresh()"); } }).start();
    }

    @JavascriptInterface public void launchSafe(String pkg) {
        a.startActivity(new Intent(a, LaunchActivity.class).putExtra("pkg", pkg));
    }

    @JavascriptInterface public String pinShortcut(String pkg) {
        try {
            if (android.os.Build.VERSION.SDK_INT < 26) return "old";
            android.content.pm.ShortcutManager sm = a.getSystemService(android.content.pm.ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) return "no";
            PackageManager pm = c.getPackageManager();
            android.graphics.drawable.Drawable d = pm.getApplicationIcon(pkg);
            int sz = 192;
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(bm);
            d.setBounds(0, 0, sz, sz); d.draw(cv);
            // small shield badge so it's clear this icon pauses Mane3
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            pt.setColor(0xFF16A34A); cv.drawCircle(sz - 34, sz - 34, 30, pt);
            pt.setColor(0xFFFFFFFF); pt.setTextSize(34); pt.setTextAlign(android.graphics.Paint.Align.CENTER);
            cv.drawText("⏸", sz - 34, sz - 22, pt);
            String label = AdVpn.label(c, pkg);
            Intent it = new Intent(c, LaunchActivity.class).setAction(Intent.ACTION_VIEW).putExtra("pkg", pkg);
            android.content.pm.ShortcutInfo si = new android.content.pm.ShortcutInfo.Builder(c, "safe_" + pkg)
                .setShortLabel(label).setLongLabel(label + " (بدون مانع)")
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(bm)).setIntent(it).build();
            sm.requestPinShortcut(si, null);
            return "ok";
        } catch (Exception e) { return "err"; }
    }

    @JavascriptInterface public void usageSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + c.getPackageName()));
            a.startActivity(i);
        } catch (Exception e) {
            try { a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    @JavascriptInterface public void browser() {
        a.startActivity(new Intent(a, BrowserActivity.class));
    }

    @JavascriptInterface public void exit() { a.exit(); }

    @JavascriptInterface public void open(String url) {
        if (url == null || !url.startsWith("https://")) return;
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
    }

    @JavascriptInterface public String test(String domain) {
        String d = Rules.clean(domain == null ? "" : domain);
        if (d == null) return "bad";
        int r = Rules.check(d);
        return r == Rules.BLOCK_LIST ? "b" : r == Rules.BLOCK_CUSTOM ? "bc" : r == Rules.ALLOW_CUSTOM ? "ac" : "a";
    }

    @JavascriptInterface public boolean firstRun() {
        return p().getLong("updated", 0) == 0;
    }
}
