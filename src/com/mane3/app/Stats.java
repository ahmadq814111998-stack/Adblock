package com.mane3.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/** Blocked-per-day + most blocked domains, kept in memory and flushed every ~30s. */
public class Stats {
    static final HashMap<String, Integer> top = new HashMap<>();
    static final TreeMap<String, Integer> days = new TreeMap<>();
    static boolean loaded = false;
    static long lastFlush = 0;

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(c.getSharedPreferences("stats", 0).getString("j", "{}"));
            JSONObject d = o.optJSONObject("days"), t = o.optJSONObject("top");
            if (d != null) for (Iterator<String> it = d.keys(); it.hasNext(); ) { String k = it.next(); days.put(k, d.getInt(k)); }
            if (t != null) for (Iterator<String> it = t.keys(); it.hasNext(); ) { String k = it.next(); top.put(k, t.getInt(k)); }
        } catch (Exception ignored) {}
    }

    public static synchronized int today(Context c) { load(c); Integer v = days.get(today()); return v == null ? 0 : v; }

    public static synchronized long week(Context c) {
        load(c);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        long sum = 0;
        for (int i = 0; i < 7; i++) { Integer v = days.get(f.format(cal.getTime())); if (v != null) sum += v; cal.add(java.util.Calendar.DAY_OF_YEAR, -1); }
        return sum;
    }

    public static synchronized void hit(Context c, String domain) {
        load(c);
        String d = today();
        Integer v = days.get(d); days.put(d, v == null ? 1 : v + 1);
        if (domain != null) {
            String k = domain.toLowerCase(Locale.ROOT);
            String[] p = k.split("\\.");
            if (p.length > 2) k = p[p.length - 2] + "." + p[p.length - 1];
            Integer t = top.get(k); top.put(k, t == null ? 1 : t + 1);
        }
        if (System.currentTimeMillis() - lastFlush > 30000) flush(c);
    }

    public static synchronized void flush(Context c) {
        load(c);
        lastFlush = System.currentTimeMillis();
        while (days.size() > 60) days.remove(days.firstKey());
        if (top.size() > 400) {
            List<Map.Entry<String, Integer>> l = new ArrayList<>(top.entrySet());
            Collections.sort(l, new Comparator<Map.Entry<String, Integer>>() { public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); } });
            top.clear();
            for (int i = 0; i < 250; i++) top.put(l.get(i).getKey(), l.get(i).getValue());
        }
        try {
            JSONObject o = new JSONObject();
            o.put("days", new JSONObject(new HashMap<String, Object>(days)));
            o.put("top", new JSONObject(new HashMap<String, Object>(top)));
            c.getSharedPreferences("stats", 0).edit().putString("j", o.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** {days:[[date,n]...last 7], top:[[domain,n]...10], total:n} */
    public static synchronized String json(Context c) {
        load(c);
        try {
            JSONObject o = new JSONObject();
            JSONArray d = new JSONArray();
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            cal.add(Calendar.DAY_OF_YEAR, -6);
            for (int i = 0; i < 7; i++) {
                String k = f.format(cal.getTime());
                Integer v = days.get(k);
                d.put(new JSONArray().put(k).put(v == null ? 0 : v));
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            o.put("days", d);
            List<Map.Entry<String, Integer>> l = new ArrayList<>(top.entrySet());
            Collections.sort(l, new Comparator<Map.Entry<String, Integer>>() { public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); } });
            JSONArray t = new JSONArray();
            for (int i = 0; i < Math.min(10, l.size()); i++) t.put(new JSONArray().put(l.get(i).getKey()).put(l.get(i).getValue()));
            o.put("top", t);
            long sum = 0; for (int v : days.values()) sum += v;
            o.put("total", sum);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    public static synchronized void reset(Context c) {
        days.clear(); top.clear(); loaded = true;
        c.getSharedPreferences("stats", 0).edit().clear().apply();
    }
}
