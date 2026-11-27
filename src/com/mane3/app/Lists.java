package com.mane3.app;

import android.content.Context;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/** Catalog of downloadable blocklists. */
public class Lists {
    public static class L {
        public final String id, cat, name, desc, url; public final boolean def;
        L(String id, String cat, String name, String desc, boolean def, String url) {
            this.id = id; this.cat = cat; this.name = name; this.desc = desc; this.def = def; this.url = url;
        }
    }

    static final String H = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/";

    public static final L[] ALL = {
        // ads & trackers
        new L("hpro", "ads", "HaGeZi Pro++", "دعايات + متتبّعات + إحصائيات (قوية)", true, H + "pro.plus-onlydomains.txt"),
        new L("oisd", "ads", "OISD Big", "دعايات ومتتبّعات بدون ما يخرب المواقع", true, "https://raw.githubusercontent.com/sjhgvr/oisd/main/domainswild2_big.txt"),
        new L("adaway", "ads", "AdAway", "دعايات الجوال والألعاب", true, "https://raw.githubusercontent.com/AdAway/adaway.github.io/master/hosts.txt"),
        new L("steven", "ads", "StevenBlack", "دعايات + مواقع ضارة", true, "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        new L("onehosts", "ads", "1Hosts Lite", "دعايات ومتتبّعات (إضافية)", false, "https://raw.githubusercontent.com/badmojr/1Hosts/master/Lite/domains.wildcards"),
        new L("hpop", "ads", "Pop-up Ads", "النوافذ المنبثقة والتحويلات المزعجة في المواقع", true, H + "popupads-onlydomains.txt"),
        new L("perflyst", "ads", "Android Tracking", "تتبّع أندرويد", true, "https://raw.githubusercontent.com/Perflyst/PiHoleBlocklist/master/android-tracking.txt"),
        // security
        new L("hdoh", "sec", "منع التحايل على المانع", "يمنع البرامج اللي تحاول تتجاوز مانع (DNS مشفّر و VPN وبروكسي)", true, H + "doh-vpn-proxy-bypass-onlydomains.txt"),
        new L("hult", "ads", "HaGeZi Ultimate 🔥", "الأقوى: يمنع أكثر شي، بس ممكن يخرّب بعض المواقع", false, H + "ultimate-onlydomains.txt"),
        new L("htif", "sec", "Threat Intelligence", "فيروسات + احتيال + تصيّد + تعدين (كبيرة)", true, H + "tif-onlydomains.txt"),
        // browser filter lists (EasyList syntax): full rules in متصفح مانع, plain domains also go to the device list
        new L("easylist", "browser", "EasyList", "أشهر قائمة دعايات في العالم (قواعد كاملة + إخفاء المربعات)", true, "https://raw.githubusercontent.com/easylist/easylist/gh-pages/easylist.txt"),
        new L("easyprivacy", "browser", "EasyPrivacy", "يمنع التتبّع والإحصائيات", true, "https://raw.githubusercontent.com/easylist/easylist/gh-pages/easyprivacy.txt"),
        new L("listear", "browser", "Liste AR", "دعايات المواقع العربية", true, "https://raw.githubusercontent.com/easylist/listear/master/Liste_AR.txt"),
        new L("agmobile", "browser", "AdGuard Mobile", "دعايات مواقع الجوال", true, "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_11_Mobile/filter.txt"),
        new L("agbase", "browser", "AdGuard Base", "قائمة AdGuard الكبيرة (إضافية، تقيلة شوي)", false, "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_2_Base/filter.txt"),
        // native telemetry
        new L("hsamsung", "native", "Samsung", "تجسس سامسونج", false, H + "native.samsung-onlydomains.txt"),
        new L("hxiaomi", "native", "Xiaomi", "تجسس شاومي", false, H + "native.xiaomi-onlydomains.txt"),
        new L("hhuawei", "native", "Huawei", "تجسس هواوي", false, H + "native.huawei-onlydomains.txt"),
        new L("happle", "native", "Apple", "تتبّع أبل", false, H + "native.apple-onlydomains.txt"),
        new L("htiktok", "native", "TikTok", "تتبّع تيك توك", false, H + "native.tiktok-onlydomains.txt"),
        new L("hwin", "native", "Windows/Office", "تتبّع مايكروسوفت", false, H + "native.winoffice-onlydomains.txt"),
        // content
        new L("hgamble", "content", "قمار", "مواقع وبرامج القمار والمراهنات", false, H + "gambling-onlydomains.txt"),
        new L("hnsfw", "content", "محتوى للكبار", "مواقع إباحية", false, H + "nsfw-onlydomains.txt"),
    };

    public static File file(Context c, String id) {
        File d = new File(c.getFilesDir(), "lists");
        d.mkdirs();
        return new File(d, id + ".txt");
    }

    /** Lists added as default in later versions get switched on once for people who already chose their lists. */
    static final String[] NEW_DEFAULTS_V7 = {"easylist", "easyprivacy", "listear", "agmobile", "hdoh"};

    public static Set<String> enabled(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("m", 0);
        Set<String> s = sp.getStringSet("lists", null);
        if (s == null) { s = new HashSet<>(); for (L x : ALL) if (x.def) s.add(x.id); return s; }
        s = new HashSet<>(s);
        if (!sp.getBoolean("listsV7", false)) {
            s.addAll(Arrays.asList(NEW_DEFAULTS_V7));
            sp.edit().putStringSet("lists", s).putBoolean("listsV7", true).apply();
        }
        return s;
    }

    public static void setEnabled(Context c, Set<String> s) {
        c.getSharedPreferences("m", 0).edit().putStringSet("lists", new HashSet<>(s)).apply();
    }

    public interface Progress { void on(String id, int done, int total, boolean ok); }

    /** Download all enabled lists (or only missing ones), then recompile. */
    public static int update(Context c, boolean onlyMissing, Progress p) {
        Set<String> on = enabled(c);
        List<L> todo = new ArrayList<>();
        for (L x : ALL) if (on.contains(x.id) && (!onlyMissing || !file(c, x.id).exists())) todo.add(x);
        int ok = 0, i = 0;
        for (L x : todo) {
            boolean good = download(x.url, file(c, x.id));
            if (good) ok++;
            i++;
            if (p != null) p.on(x.id, i, todo.size(), good);
        }
        Rules.compile(c);
        try { Filters.load(c); } catch (Throwable ignored) {}
        c.getSharedPreferences("m", 0).edit().putLong("updated", System.currentTimeMillis()).apply();
        return ok;
    }

    static boolean download(String url, File dest) {
        File tmp = new File(dest.getPath() + ".part");
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "Mane3");
            if (con.getResponseCode() != 200) return false;
            try (InputStream in = new BufferedInputStream(con.getInputStream());
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] b = new byte[1 << 16]; int n; long total = 0;
                while ((n = in.read(b)) > 0) { out.write(b, 0, n); total += n; }
                if (total < 50) return false;
            }
            return tmp.renameTo(dest);
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally { if (con != null) con.disconnect(); }
    }
}
