package com.mane3.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/** Export / import every setting (lists, zapped ads, filters, bookmarks, PIN…) as one JSON file. */
public class Backup {
    static final String[] FILES = {"m", "zap", "pos", "b", "stats"};

    public static String export(Context c) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "mane3");
        root.put("version", Updater.myName(c));
        root.put("time", System.currentTimeMillis());
        JSONObject all = new JSONObject();
        for (String f : FILES) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences(f, 0).getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject x = new JSONObject();
                if (v instanceof String) x.put("t", "s").put("v", v);
                else if (v instanceof Boolean) x.put("t", "b").put("v", v);
                else if (v instanceof Integer) x.put("t", "i").put("v", v);
                else if (v instanceof Long) x.put("t", "l").put("v", v);
                else if (v instanceof Float) x.put("t", "f").put("v", (double) (Float) v);
                else if (v instanceof Set) x.put("t", "S").put("v", new JSONArray((Set<?>) v));
                else continue;
                o.put(e.getKey(), x);
            }
            all.put(f, o);
        }
        root.put("prefs", all);
        return root.toString(1);
    }

    public static void restore(Context c, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"mane3".equals(root.optString("app"))) throw new Exception("not a Mane3 backup");
        JSONObject all = root.getJSONObject("prefs");
        for (String f : FILES) {
            JSONObject o = all.optJSONObject(f);
            if (o == null) continue;
            SharedPreferences.Editor e = c.getSharedPreferences(f, 0).edit().clear();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject x = o.getJSONObject(k);
                String t = x.getString("t");
                switch (t) {
                    case "s": e.putString(k, x.getString("v")); break;
                    case "b": e.putBoolean(k, x.getBoolean("v")); break;
                    case "i": e.putInt(k, x.getInt("v")); break;
                    case "l": e.putLong(k, x.getLong("v")); break;
                    case "f": e.putFloat(k, (float) x.getDouble("v")); break;
                    case "S": {
                        JSONArray a = x.getJSONArray("v"); Set<String> s = new HashSet<>();
                        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                        e.putStringSet(k, s); break;
                    }
                }
            }
            e.commit();
        }
        Stats.loaded = false; Stats.days.clear(); Stats.top.clear();
        Rules.allow = new HashSet<>(c.getSharedPreferences("m", 0).getStringSet("allow", new HashSet<String>()));
        Rules.block = new HashSet<>(c.getSharedPreferences("m", 0).getStringSet("block", new HashSet<String>()));
    }
}
