package com.mane3.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Blocklist engine: sorted 64-bit hashes of blocked domains + small custom allow/block sets. */
public class Rules {
    public static volatile long[] hashes = new long[0];
    public static volatile Set<String> allow = new HashSet<>();
    public static volatile Set<String> block = new HashSet<>();
    public static volatile int count = 0;

    public static final int PASS = 0, BLOCK_LIST = 1, BLOCK_CUSTOM = 2, ALLOW_CUSTOM = 3;

    public static long hash(CharSequence s, int from) {
        long h = 0xcbf29ce484222325L;
        for (int i = from; i < s.length(); i++) { h ^= s.charAt(i); h *= 0x100000001b3L; }
        return h;
    }

    public static int check(String name) {
        if (name == null || name.isEmpty()) return PASS;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
        Set<String> a = allow, b = block;
        // walk suffixes: a.b.c -> a.b.c, b.c, c
        int i = 0;
        boolean allowed = false;
        while (true) {
            String suf = n.substring(i);
            if (a.contains(suf)) { allowed = true; break; }
            int dot = n.indexOf('.', i);
            if (dot < 0) break;
            i = dot + 1;
        }
        if (allowed) return ALLOW_CUSTOM;
        long[] h = hashes;
        i = 0;
        while (true) {
            if (b.contains(n.substring(i))) return BLOCK_CUSTOM;
            if (h.length > 0 && Arrays.binarySearch(h, hash(n, i)) >= 0) return BLOCK_LIST;
            int dot = n.indexOf('.', i);
            if (dot < 0) break;
            i = dot + 1;
        }
        return PASS;
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("m", 0); }

    public static Set<String> getSet(Context c, String key) {
        return new TreeSet<>(prefs(c).getStringSet(key, new HashSet<String>()));
    }

    public static void putSet(Context c, String key, Set<String> s) {
        prefs(c).edit().putStringSet(key, new HashSet<>(s)).apply();
        if (key.equals("allow")) allow = new HashSet<>(s);
        if (key.equals("block")) block = new HashSet<>(s);
    }

    /** Clean a user/list entry into a bare domain, or null. */
    public static String clean(String line) {
        int hashPos = line.indexOf('#');
        if (hashPos >= 0) line = line.substring(0, hashPos);
        line = line.trim();
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null;
        String[] parts = line.split("\\s+");
        String d = parts.length >= 2 && (parts[0].equals("0.0.0.0") || parts[0].equals("127.0.0.1") || parts[0].equals("::") || parts[0].equals("::1")) ? parts[1] : parts[0];
        if (d.startsWith("||")) d = d.substring(2);
        if (d.endsWith("^")) d = d.substring(0, d.length() - 1);
        if (d.startsWith("*.")) d = d.substring(2);
        if (d.startsWith(".")) d = d.substring(1);
        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        d = d.toLowerCase(Locale.ROOT);
        if (d.indexOf('.') < 0 || d.equals("localhost") || d.equals("localhost.localdomain") || d.equals("broadcasthost")
                || d.equals("local") || d.equals("0.0.0.0")) return null;
        for (int k = 0; k < d.length(); k++) {
            char ch = d.charAt(k);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '_')) return null;
        }
        return d;
    }

    static class LongList {
        long[] a = new long[1 << 16]; int n = 0;
        void add(long v) { if (n == a.length) a = Arrays.copyOf(a, a.length * 2); a[n++] = v; }
    }

    static void addStream(InputStream in, LongList out) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"), 1 << 16);
        String line;
        while ((line = r.readLine()) != null) {
            String d = clean(line);
            if (d != null) out.add(hash(d, 0));
        }
    }

    /** Rebuild hash table from base asset + every enabled, downloaded list; write cache. */
    public static synchronized void compile(Context c) {
        LongList l = new LongList();
        try { addStream(new GZIPInputStream(c.getAssets().open("base.txt.gz")), l); } catch (Exception ignored) {}
        Set<String> on = Lists.enabled(c);
        for (Lists.L x : Lists.ALL) {
            if (!on.contains(x.id)) continue;
            File f = Lists.file(c, x.id);
            if (!f.exists()) continue;
            try (InputStream in = new FileInputStream(f)) { addStream(in, l); } catch (Exception ignored) {}
        }
        long[] arr = Arrays.copyOf(l.a, l.n);
        l = null;
        Arrays.sort(arr);
        int u = 0;
        for (int k = 0; k < arr.length; k++) if (k == 0 || arr[k] != arr[k - 1]) arr[u++] = arr[k];
        arr = Arrays.copyOf(arr, u);
        hashes = arr;
        count = u;
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(c.getFilesDir(), "rules.bin")), 1 << 16))) {
            o.writeInt(u);
            for (long v : arr) o.writeLong(v);
        } catch (Exception ignored) {}
        prefs(c).edit().putInt("count", u).apply();
    }

    /** Fast load from cache (or compile if missing). */
    public static synchronized void load(Context c) {
        allow = new HashSet<>(prefs(c).getStringSet("allow", new HashSet<String>()));
        block = new HashSet<>(prefs(c).getStringSet("block", new HashSet<String>()));
        if (hashes.length > 0) return;
        File f = new File(c.getFilesDir(), "rules.bin");
        if (f.exists()) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 16))) {
                int n = in.readInt();
                long[] arr = new long[n];
                for (int k = 0; k < n; k++) arr[k] = in.readLong();
                hashes = arr; count = n;
                return;
            } catch (Exception ignored) {}
        }
        compile(c);
    }
}
