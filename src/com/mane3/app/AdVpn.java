package com.mane3.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local DNS-only VPN: only DNS packets (to the fake server 10.111.0.53) enter the tunnel.
 * Each query is checked against the local blocklists, then forwarded to the chosen DNS server.
 */
public class AdVpn extends VpnService implements Runnable {
    public static final String STOP = "com.mane3.app.STOP";
    public static final String RESTART = "com.mane3.app.RESTART";
    public static final String PAUSE = "com.mane3.app.PAUSE";
    private volatile String manualPkg = null;
    private volatile long manualSince = 0;
    private volatile boolean manualSeen = false;
    static final String FAKE_DNS = "10.111.0.53";

    /** id, name, primary, secondary, kind (ads = blocks ads, family = ads/adult, sec = malware, plain = no filter, isp = your network). */
    public static final String[][] SERVERS = {
        {"isp", "DNS شبكتك (زين / Ooredoo / STC)", "", "", "isp"},
        {"adguard", "AdGuard (يمنع دعايات)", "94.140.14.14", "94.140.15.15", "ads"},
        {"adguard_family", "AdGuard عائلي (دعايات + كبار)", "94.140.14.15", "94.140.15.16", "family"},
        {"adguard_plain", "AdGuard بدون فلترة", "94.140.14.140", "94.140.14.141", "plain"},
        {"controld", "Control D (دعايات + تتبّع)", "76.76.2.2", "76.76.10.2", "ads"},
        {"controld_sec", "Control D (فيروسات)", "76.76.2.1", "76.76.10.1", "sec"},
        {"controld_plain", "Control D بدون فلترة", "76.76.2.0", "76.76.10.0", "plain"},
        {"alternate", "Alternate DNS (دعايات)", "76.76.19.19", "76.223.122.150", "ads"},
        {"quad9", "Quad9 (حماية من الفيروسات)", "9.9.9.9", "149.112.112.112", "sec"},
        {"quad9_plain", "Quad9 بدون فلترة", "9.9.9.10", "149.112.112.10", "plain"},
        {"cf_security", "Cloudflare (حماية)", "1.1.1.2", "1.0.0.2", "sec"},
        {"cf_family", "Cloudflare عائلي (حماية + كبار)", "1.1.1.3", "1.0.0.3", "family"},
        {"cloudflare", "Cloudflare (أسرع، بدون فلترة)", "1.1.1.1", "1.0.0.1", "plain"},
        {"google", "Google (بدون فلترة)", "8.8.8.8", "8.8.4.4", "plain"},
        {"opendns", "OpenDNS (حماية)", "208.67.222.222", "208.67.220.220", "sec"},
        {"opendns_family", "OpenDNS عائلي", "208.67.222.123", "208.67.220.123", "family"},
        {"cleanbrowsing_family", "CleanBrowsing عائلي", "185.228.168.168", "185.228.169.168", "family"},
        {"cleanbrowsing_adult", "CleanBrowsing (يمنع الكبار)", "185.228.168.10", "185.228.169.11", "family"},
        {"cleanbrowsing_sec", "CleanBrowsing (حماية)", "185.228.168.9", "185.228.169.9", "sec"},
        {"comodo", "Comodo (حماية)", "8.26.56.26", "8.20.247.20", "sec"},
        {"dnssb", "DNS.SB (بدون فلترة، خصوصية)", "185.222.222.222", "45.11.45.11", "plain"},
        {"dns0", "DNS0.eu (حماية)", "193.110.81.0", "185.253.5.0", "sec"},
        {"dns0_kids", "DNS0.eu أطفال", "193.110.81.1", "185.253.5.1", "family"},
        {"yandex", "Yandex (بدون فلترة)", "77.88.8.8", "77.88.8.1", "plain"},
        {"yandex_family", "Yandex عائلي", "77.88.8.7", "77.88.8.3", "family"},
        {"dns4eu_noads", "DNS4EU (دعايات + حماية)", "86.54.11.13", "86.54.11.213", "ads"},
        {"dns4eu_child_noads", "DNS4EU عائلي (دعايات + كبار)", "86.54.11.11", "86.54.11.211", "family"},
        {"dns4eu_child", "DNS4EU عائلي", "86.54.11.12", "86.54.11.212", "family"},
        {"dns4eu", "DNS4EU (حماية)", "86.54.11.1", "86.54.11.201", "sec"},
        {"dns4eu_plain", "DNS4EU بدون فلترة", "86.54.11.100", "86.54.11.200", "plain"},
        {"cira_family", "CIRA Canadian Shield عائلي", "149.112.121.30", "149.112.122.30", "family"},
        {"cira", "CIRA Canadian Shield (حماية)", "149.112.121.20", "149.112.122.20", "sec"},
        {"cira_plain", "CIRA Canadian Shield بدون فلترة", "149.112.121.10", "149.112.122.10", "plain"},
        {"ultradns_sec", "UltraDNS (حماية)", "156.154.70.2", "156.154.71.2", "sec"},
        {"ultradns", "UltraDNS بدون فلترة", "64.6.64.6", "64.6.65.6", "plain"},
        {"gcore", "Gcore (بدون فلترة)", "95.85.95.85", "2.56.220.2", "plain"},
        {"quad101", "Quad101 (بدون فلترة)", "101.101.101.101", "101.102.103.104", "plain"},
        {"level3", "Level3 (بدون فلترة)", "4.2.2.1", "4.2.2.2", "plain"},
        {"he", "Hurricane Electric (بدون فلترة)", "74.82.42.42", "74.82.42.42", "plain"},
    };

    /** The phone network's own DNS (not the VPN's), IPv4 only. */
    public static String[] ispDns(Context c) {
        ArrayList<String> out = new ArrayList<>();
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (android.net.Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null || nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) || !nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                android.net.LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (java.net.InetAddress a : lp.getDnsServers())
                    if (a instanceof java.net.Inet4Address && !out.contains(a.getHostAddress())) out.add(a.getHostAddress());
            }
        } catch (Exception ignored) {}
        return out.toArray(new String[0]);
    }

    /** Server with real addresses (the network one is looked up now; falls back to Cloudflare). */
    public static String[] resolved(Context c, String[] s) {
        if (!"isp".equals(s[0])) return s;
        String[] d = ispDns(c);
        return new String[]{s[0], s[1], d.length > 0 ? d[0] : "1.1.1.1", d.length > 1 ? d[1] : (d.length > 0 ? d[0] : "1.0.0.1"), s[4]};
    }


    static final String[] BYPASS_DNS = {"8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3", "9.9.9.9", "149.112.112.112",
        "208.67.222.222", "208.67.220.220", "94.140.14.14", "94.140.15.15", "94.140.14.15", "94.140.15.16", "76.76.2.2", "76.76.10.2",
        "185.228.168.9", "185.228.169.9", "45.90.28.0", "45.90.30.0", "8.26.56.26", "8.20.247.20", "77.88.8.8", "77.88.8.1",
        "94.140.14.140", "94.140.14.141", "76.76.2.0", "76.76.2.1", "76.76.10.0", "76.76.10.1", "76.76.19.19", "76.223.122.150", "9.9.9.10", "149.112.112.10",
        "9.9.9.11", "149.112.112.11", "208.67.222.123", "208.67.220.123", "185.228.168.168", "185.228.169.168", "185.228.168.10", "185.228.169.11",
        "185.222.222.222", "45.11.45.11", "193.110.81.0", "185.253.5.0", "193.110.81.1", "185.253.5.1", "77.88.8.7", "77.88.8.3", "4.2.2.1", "4.2.2.2",
        "86.54.11.1", "86.54.11.201", "86.54.11.11", "86.54.11.211", "86.54.11.12", "86.54.11.212", "86.54.11.13", "86.54.11.213", "86.54.11.100", "86.54.11.200",
        "149.112.121.10", "149.112.122.10", "149.112.121.20", "149.112.122.20", "149.112.121.30", "149.112.122.30", "64.6.64.6", "64.6.65.6",
        "156.154.70.2", "156.154.71.2", "95.85.95.85", "2.56.220.2", "101.101.101.101", "101.102.103.104", "74.82.42.42"};
    public static volatile boolean running = false;   // tunnel is up
    public static volatile boolean wanted = false;    // user switched it on
    public static volatile String pausedFor = null;   // package that paused it
    private Thread watcher;
    private boolean schActive = false;
    private String lastFg = null;
    public static final AtomicInteger total = new AtomicInteger();
    public static final AtomicInteger blocked = new AtomicInteger();
    static final ArrayDeque<String[]> LOG = new ArrayDeque<>();

    private ParcelFileDescriptor tun;
    private Thread loop;
    private FileOutputStream out;
    private ExecutorService pool;
    private volatile String[] upstream = {"94.140.14.14", "94.140.15.15"};
    private SharedPreferences prefs;

    public static void log(String name, String status) {
        synchronized (LOG) {
            LOG.addFirst(new String[]{String.valueOf(System.currentTimeMillis()), name, status});
            while (LOG.size() > 500) LOG.removeLast();
        }
    }

    public static List<String[]> logCopy() { synchronized (LOG) { return new ArrayList<>(LOG); } }

    public static String[] server(Context c) {
        String id = c.getSharedPreferences("m", 0).getString("server", "adguard");
        for (String[] s : SERVERS) if (s[0].equals(id)) return s;
        return SERVERS[0];
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (STOP.equals(a)) { fullStop(); return START_NOT_STICKY; }
        wanted = true;
        getSharedPreferences("m", 0).edit().putBoolean("on", true).apply();
        if (PAUSE.equals(a)) {
            String pkg = intent.getStringExtra("pkg");
            if (pkg != null) {
                manualPkg = pkg; manualSince = System.currentTimeMillis(); manualSeen = false;
                pausedFor = pkg;
                stopVpn();
                note();
                startWatcher();
                return START_STICKY;
            }
        }
        note();
        if (RESTART.equals(a) && running) stopVpn();
        if (!running && pausedFor == null) {
            String fg = foreground();
            if (fg != null && pauseSet().contains(fg)) pausedFor = fg; else startVpn();
            note();
        }
        startWatcher();
        return START_STICKY;
    }

    private void fullStop() {
        wanted = false;
        getSharedPreferences("m", 0).edit().putBoolean("on", false).apply();
        Stats.flush(this);
        QsTile.refresh(this);
        Widget.refresh(this);
        pausedFor = null;
        stopVpn();
        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    // ---------- background chores: update notice + weekly report ----------
    void chores() {
        final SharedPreferences sp = getSharedPreferences("m", 0);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("updCheck", 0) > 12 * 3600 * 1000L) {
            Updater.check(this);
            if ("available".equals(Updater.state) && !Updater.latestName.equals(sp.getString("updNotified", ""))) {
                sp.edit().putString("updNotified", Updater.latestName).apply();
                notify2(11, "🔄 في تحديث جديد لمانع " + Updater.latestName, Updater.notes.isEmpty() ? "اضغط وحدّث" : Updater.notes);
            }
        }
        long last = sp.getLong("weekly", 0);
        if (last == 0) sp.edit().putLong("weekly", now).apply();
        else if (now - last > 7 * 86400000L) {
            sp.edit().putLong("weekly", now).apply();
            long w = Stats.week(this);
            if (w > 0) notify2(12, "📊 تقرير الأسبوع", "مانع منع " + String.format(Locale.US, "%,d", w) + " دعاية ومتتبّع هالأسبوع 💪");
        }
    }

    void notify2(int id, String title, String text) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            android.app.Notification.Builder nb;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new android.app.NotificationChannel("info", "تنبيهات مانع", android.app.NotificationManager.IMPORTANCE_DEFAULT));
                nb = new android.app.Notification.Builder(this, "info");
            } else nb = new android.app.Notification.Builder(this);
            int ic = getResources().getIdentifier("ic_tile", "drawable", getPackageName());
            nb.setSmallIcon(ic != 0 ? ic : android.R.drawable.ic_lock_lock).setContentTitle(title).setContentText(text).setAutoCancel(true)
              .setContentIntent(android.app.PendingIntent.getActivity(this, id, new Intent(this, MainActivity.class), android.app.PendingIntent.FLAG_IMMUTABLE));
            nm.notify(id, nb.build());
        } catch (Exception ignored) {}
    }

    // ---------- pause while "VPN detected" apps (banks…) are open ----------
    Set<String> pauseSet() {
        return getSharedPreferences("m", 0).getStringSet("excluded", new HashSet<String>());
    }

    public static boolean usageOk(Context c) {
        try {
            android.app.AppOpsManager ao = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            return ao.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName())
                    == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    String foreground() {
        try {
            android.app.usage.UsageStatsManager um = (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = um.queryEvents(now - 15000, now);
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String fg = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) fg = e.getPackageName();
            }
            if (fg != null) lastFg = fg;
        } catch (Exception ignored) {}
        return lastFg;
    }

    private synchronized void startWatcher() {
        if (watcher != null && watcher.isAlive()) return;
        watcher = new Thread(new Runnable() { public void run() {
            Set<String> set = pauseSet();
            int k = 0;
            while (wanted) {
                try {
                    if (++k % 5 == 0) set = pauseSet();
                    if (k % 240 == 0) Widget.refresh(AdVpn.this);           // ~1 min
                    if (k % 2400 == 1) chores();                             // ~10 min
                    if (k % 8 == 0) {   // lock-time started/ended: reconnect so apps drop open connections and DNS cache
                        boolean act = Schedule.active(AdVpn.this);
                        if (act != schActive) {
                            schActive = act;
                            if (running && pausedFor == null) { stopVpn(); Thread.sleep(400); startVpn(); }
                        }
                    }
                    if (manualPkg != null) {
                        boolean ok = usageOk(AdVpn.this);
                        String fg = ok ? foreground() : null;
                        long age = System.currentTimeMillis() - manualSince;
                        if (fg != null && fg.equals(manualPkg)) manualSeen = true;
                        boolean leave = ok
                            ? (manualSeen ? (fg != null && !fg.equals(manualPkg) && !fg.equals(getPackageName())) : age > 25000)
                            : age > 10 * 60 * 1000L;
                        if (leave) { manualPkg = null; pausedFor = null; startVpn(); note(); }
                        Thread.sleep(300);
                        continue;
                    }
                    if (!set.isEmpty() && usageOk(AdVpn.this)) {
                        String fg = foreground();
                        if (fg != null && set.contains(fg)) {
                            if (pausedFor == null || !pausedFor.equals(fg)) { pausedFor = fg; stopVpn(); note(); }
                        } else if (pausedFor != null && fg != null) {
                            pausedFor = null; startVpn(); note();
                        }
                    } else if (pausedFor != null) { pausedFor = null; startVpn(); note(); }
                    Thread.sleep(250);
                } catch (InterruptedException ie) { break; } catch (Exception ignored) {}
            }
        }}, "fg-watch");
        watcher.start();
    }

    public static String label(Context c, String pkg) {
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    void note() {
        QsTile.refresh(this);
        Widget.refresh(this);
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            android.app.Notification.Builder nb;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel("m", "مانع", android.app.NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                nb = new android.app.Notification.Builder(this, "m");
            } else nb = new android.app.Notification.Builder(this);
            String t = pausedFor != null ? "⏸ موقف عشان " + label(this, pausedFor) : "🛡️ يمنع الدعايات";
            Intent open = new Intent(this, MainActivity.class);
            int ic = getResources().getIdentifier("ic_tile", "drawable", getPackageName());
            nb.setSmallIcon(ic != 0 ? ic : android.R.drawable.ic_lock_lock).setContentTitle("مانع").setContentText(t).setOngoing(true)
              .setContentIntent(android.app.PendingIntent.getActivity(this, 0, open, android.app.PendingIntent.FLAG_IMMUTABLE));
            startForeground(1, nb.build());
        } catch (Exception ignored) {}
    }

    static volatile AdVpn instance;

    /** Our own direct sockets (speed test) must not go through the tunnel. */
    public static void protectSocket(java.net.DatagramSocket s) { AdVpn v = instance; if (v != null) try { v.protect(s); } catch (Exception ignored) {} }

    private synchronized void startVpn() {
        instance = this;
        try {
            prefs = getSharedPreferences("m", 0);
            synchronized (CACHE) { CACHE.clear(); }
            if (Rules.hashes.length == 0) {
                final Context lc = getApplicationContext();
                new Thread(new Runnable() { public void run() { Rules.load(lc); } }).start();
            }
            String[] s = server(this);
            s = resolved(this, s);
            upstream = new String[]{s[2], s[3], s[2].startsWith("1.") ? "8.8.8.8" : "1.1.1.1"};
            Builder b = new Builder();
            b.setSession("مانع الدعايات");
            b.addAddress("10.111.0.1", 32);
            b.addDnsServer(FAKE_DNS);
            b.addRoute(FAKE_DNS, 32);
            // Apps that skip the phone's DNS and ask public servers directly: send those through us too.
            if (getSharedPreferences("m", 0).getBoolean("strict", true))
                for (String ip : BYPASS_DNS) { try { b.addRoute(ip, 32); } catch (Exception ignored) {} }
            b.setBlocking(true);
            // our own browser goes through the tunnel too, so blocked ads really fail (forwarding sockets are protect()ed)
            for (String pkg : prefs.getStringSet("excluded", new HashSet<String>())) {
                try { b.addDisallowedApplication(pkg); } catch (Exception ignored) {}
            }
            tun = b.establish();
            if (tun == null) return;
            out = new FileOutputStream(tun.getFileDescriptor());
            pool = Executors.newFixedThreadPool(32);
            running = true;
            blocked.set(0); total.set(0);
            loop = new Thread(this, "dns-loop");
            loop.start();
            // weekly list refresh in background
            final Context ctx = getApplicationContext();
            long upd = prefs.getLong("updated", 0);
            if (upd > 0 && System.currentTimeMillis() - upd > 7L * 86400000L) {
                new Thread(new Runnable() { public void run() {
                    try { Thread.sleep(5000); } catch (Exception ignored) {}
                    Lists.update(ctx, false, null);
                }}).start();
            }
        } catch (Exception e) {
            stopVpn();
        }
    }

    private synchronized void stopVpn() {
        boolean was = running;
        running = false;
        try { if (loop != null) loop.interrupt(); } catch (Exception ignored) {}
        try { if (pool != null) pool.shutdownNow(); } catch (Exception ignored) {}
        try { if (tun != null) tun.close(); } catch (Exception ignored) {}
        tun = null; loop = null; pool = null; out = null;
        if (was && prefs != null) {
            prefs.edit().putLong("allBlocked", prefs.getLong("allBlocked", 0) + blocked.get()).apply();
        }
    }

    @Override public void onRevoke() { fullStop(); }
    @Override public void onDestroy() { wanted = false; stopVpn(); super.onDestroy(); }

    @Override
    public void run() {
        try {
            FileInputStream in = new FileInputStream(tun.getFileDescriptor());
            byte[] buf = new byte[32767];
            while (running) {
                int n = in.read(buf);
                if (n <= 0) { Thread.sleep(20); continue; }
                final byte[] pkt = new byte[n];
                System.arraycopy(buf, 0, pkt, 0, n);
                pool.execute(new Runnable() { public void run() { handle(pkt); } });
            }
        } catch (Exception ignored) {}
    }

    void handle(byte[] p) {
        try {
            Dns.Query q = Dns.parse(p);
            if (q == null) {
                byte[] rst = Dns.tcpReset(p);   // encrypted DNS / other traffic to public DNS servers: refuse quickly
                if (rst != null) synchronized (this) { if (out != null && running) out.write(rst); }
                return;
            }
            total.incrementAndGet();
            if (System.currentTimeMillis() < dnsOpenUntil && !Schedule.blocks(this, q.name)) {
                // 🥷 a stealth site is open in the browser: let its ads load for real (kept off-screen there), no cache either way
                byte[] open = forwardTo(q.dns, OPEN_UPSTREAM);
                if (open == null) return;
                log(q.name, "ao");
                byte[] resp0 = Dns.wrap(q, open);
                synchronized (this) { if (out != null && running) out.write(resp0); }
                return;
            }
            int r = Rules.check(q.name);
            byte[] answer;
            if (Schedule.blocks(this, q.name)) {
                answer = Dns.blockedAnswer(q.dns);
                log(q.name, "bt");
            } else if (r == Rules.BLOCK_LIST || r == Rules.BLOCK_CUSTOM) {
                answer = Dns.blockedAnswer(q.dns);
                blocked.incrementAndGet();
                Stats.hit(this, q.name);
                log(q.name, r == Rules.BLOCK_CUSTOM ? "bc" : "b");
            } else {
                answer = cached(q);
                if (answer == null) { answer = forward(q.dns); if (answer != null) store(q, answer); }
                if (answer == null) return;
                if (r != Rules.ALLOW_CUSTOM && cnameBlocked(answer)) {
                    answer = Dns.blockedAnswer(q.dns);
                    blocked.incrementAndGet();
                    Stats.hit(this, q.name);
                    log(q.name, "b");
                } else if (r != Rules.ALLOW_CUSTOM && Dns.looksBlocked(answer)) {
                    blocked.incrementAndGet();
                Stats.hit(this, q.name);
                    log(q.name, "bs");
                } else log(q.name, r == Rules.ALLOW_CUSTOM ? "ac" : "a");
            }
            byte[] resp = Dns.wrap(q, answer);
            synchronized (this) { if (out != null && running) out.write(resp); }
        } catch (Exception ignored) {}
    }

    // ---------- small DNS cache (fewer waits, faster pages) ----------
    static final LinkedHashMap<String, Object[]> CACHE = new LinkedHashMap<String, Object[]>(512, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) { return size() > 3000; }
    };

    static String key(Dns.Query q) { return q.qtype + ":" + (q.name == null ? "" : q.name.toLowerCase(Locale.ROOT)); }

    byte[] cached(Dns.Query q) {
        Object[] e;
        synchronized (CACHE) { e = CACHE.get(key(q)); }
        if (e == null || (Long) e[1] < System.currentTimeMillis()) return null;
        byte[] r = ((byte[]) e[0]).clone();
        r[0] = q.dns[0]; r[1] = q.dns[1];   // match the asker's query id
        return r;
    }

    void store(Dns.Query q, byte[] r) {
        if (r.length < 12) return;
        int rcode = r[3] & 0x0F;
        if (rcode != 0 && rcode != 3) return;            // only NOERROR / NXDOMAIN
        long ttl = rcode == 3 ? 60_000 : 180_000;
        synchronized (CACHE) { CACHE.put(key(q), new Object[]{r.clone(), System.currentTimeMillis() + ttl}); }
    }

    /** A tracker hiding behind the site's own subdomain (CNAME cloaking)? */
    static boolean cnameBlocked(byte[] answer) {
        for (String n : Dns.cnames(answer)) {
            int c = Rules.check(n);
            if (c == Rules.ALLOW_CUSTOM) return false;
            if (c == Rules.BLOCK_LIST || c == Rules.BLOCK_CUSTOM) return true;
        }
        return false;
    }

    /** Set by the browser while a 🥷 stealth page is in front (refreshed every half second, expires on its own). */
    public static volatile long dnsOpenUntil = 0;
    static final String[] OPEN_UPSTREAM = {"1.1.1.1", "8.8.8.8"};

    byte[] forward(byte[] dns) { return forwardTo(dns, upstream); }

    byte[] forwardTo(byte[] dns, String[] ups) {
        for (String up : ups) {
            DatagramSocket s = null;
            try {
                s = new DatagramSocket();
                protect(s);
                s.setSoTimeout(1800);
                s.send(new DatagramPacket(dns, dns.length, InetAddress.getByName(up), 53));
                byte[] r = new byte[4096];
                DatagramPacket dp = new DatagramPacket(r, r.length);
                s.receive(dp);
                byte[] res = new byte[dp.getLength()];
                System.arraycopy(r, 0, res, 0, res.length);
                return res;
            } catch (Exception ignored) {
            } finally { if (s != null) s.close(); }
        }
        return null;
    }
}
