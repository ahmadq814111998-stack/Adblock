package com.mane3.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/** Lock chosen apps (by their domains) during set hours, e.g. Instagram at night. */
public class Schedule {
    static final String[][] GROUPS = {
        {"instagram", "📸 إنستقرام", "instagram.com,cdninstagram.com,ig.me,instagr.am"},
        {"tiktok", "🎵 تيك توك", "tiktok.com,tiktokv.com,tiktokcdn.com,tiktokcdn-us.com,tiktokv.us,byteoversea.com,ibytedtos.com,ibyteimg.com,muscdn.com,musical.ly,tik-tokapi.com"},
        {"snapchat", "👻 سناب", "snapchat.com,sc-cdn.net,snap-dev.net,snapkit.com,snapads.com,feelinsonice-hrd.appspot.com,sc-static.net,sc-prod.net"},
        {"youtube", "▶️ يوتيوب", "youtube.com,youtu.be,googlevideo.com,ytimg.com,youtube-nocookie.com,youtubei.googleapis.com,youtubekids.com"},
        {"x", "𝕏 تويتر/X", "twitter.com,x.com,twimg.com,t.co"},
        {"facebook", "📘 فيسبوك", "facebook.com,facebook.net,fb.com,fbcdn.net,messenger.com"},
        {"netflix", "🎬 نتفلكس وشاهد", "netflix.com,nflxvideo.net,nflximg.net,nflxext.com,nflxso.net,shahid.mbc.net,mbc.net"},
        {"games", "🎮 ألعاب أونلاين", "supercell.com,clashofclans.com,pubgmobile.com,igamecj.com,roblox.com,rbxcdn.com,epicgames.com,habby.mobi,habby.com,ea.com,king.com"},
    };

    static volatile boolean loaded = false, on = false;
    static volatile int from = 23 * 60, to = 6 * 60;
    static volatile Set<String> domains = new HashSet<>();
    static volatile Set<String> groups = new HashSet<>();

    static SharedPreferences p(Context c) { return c.getSharedPreferences("m", 0); }

    public static synchronized void reload(Context c) {
        SharedPreferences sp = p(c);
        on = sp.getBoolean("sch_on", false);
        from = sp.getInt("sch_from", 23 * 60);
        to = sp.getInt("sch_to", 6 * 60);
        groups = new HashSet<>(sp.getStringSet("sch_groups", new HashSet<>(Arrays.asList("instagram", "tiktok"))));
        Set<String> d = new HashSet<>();
        for (String[] g : GROUPS) if (groups.contains(g[0])) d.addAll(Arrays.asList(g[2].split(",")));
        domains = d;
        loaded = true;
    }

    public static boolean active(Context c) {
        if (!loaded) reload(c);
        if (!on || domains.isEmpty()) return false;
        Calendar k = Calendar.getInstance();
        int now = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
        if (from == to) return true;
        return from < to ? (now >= from && now < to) : (now >= from || now < to);
    }

    public static boolean blocks(Context c, String name) {
        if (name == null || !active(c)) return false;
        String n = name.toLowerCase(Locale.ROOT);
        Set<String> d = domains;
        int i = 0;
        while (true) {
            if (d.contains(n.substring(i))) return true;
            int dot = n.indexOf('.', i);
            if (dot < 0) return false;
            i = dot + 1;
        }
    }

    public static String json(Context c) {
        if (!loaded) reload(c);
        try {
            JSONObject o = new JSONObject();
            o.put("on", on).put("from", from).put("to", to).put("active", active(c));
            JSONArray a = new JSONArray();
            for (String[] g : GROUPS) a.put(new JSONObject().put("id", g[0]).put("label", g[1]).put("on", groups.contains(g[0])));
            o.put("groups", a);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    public static void save(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            Set<String> g = new HashSet<>();
            JSONArray a = o.getJSONArray("groups");
            for (int i = 0; i < a.length(); i++) g.add(a.getString(i));
            p(c).edit().putBoolean("sch_on", o.getBoolean("on")).putInt("sch_from", o.getInt("from")).putInt("sch_to", o.getInt("to"))
                .putStringSet("sch_groups", g).apply();
        } catch (Exception ignored) {}
        reload(c);
    }
}
