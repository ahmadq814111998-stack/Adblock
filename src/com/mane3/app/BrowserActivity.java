package com.mane3.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;

import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.util.*;

public class BrowserActivity extends Activity {
    static final String HOME = "file:///android_asset/home.html";
    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f};

    WebView web;
    EditText url;
    ProgressBar prog;
    LinearLayout top, bottom, overlay;
    FrameLayout full;
    TextView bDl, bSpeed, bShield, oSpeed;
    View customView;
    WebChromeClient.CustomViewCallback customCb;
    float rate = 1f;
    int zoomMode = 0; // 0 off, 1 cinema-fit, 2 fill, 3 zoom 1.3, 4 zoom 1.6
    int blockedHere = 0;
    String defaultUA;
    boolean desktop = false;
    final LinkedHashMap<String, String> media = new LinkedHashMap<>();
    float dp;

    static String INJ = null;
    final Set<String> frameSet = Collections.synchronizedSet(new HashSet<String>());

    static String noFrag(String u) { int i = u.indexOf('#'); return i >= 0 ? u.substring(0, i) : u; }

    String zapJson(String host) {
        Set<String> z = getSharedPreferences("zap", 0).getStringSet("z:" + baseHost(host), null);
        return z == null ? "[]" : new JSONArray(z).toString();
    }

    static final String WF_DEFAULT = "رقص، رقصة، رقصات، يرقص، ترقص، يرقصون، راقصة، رقاصة، الرقص، dance، dancing، dancer، twerk، belly dance، bellydance، choreography، hot girl، sexy، bikini، مثيرة، إغراء، اغراء";

    String wfJson() {
        android.content.SharedPreferences sp = getSharedPreferences("m", 0);
        if (!sp.getBoolean("wf_on", true)) return "[]";
        JSONArray a = new JSONArray();
        for (String w : sp.getString("wf_words", WF_DEFAULT).split("[،,\\n]+")) { w = w.trim(); if (w.length() > 1) a.put(w); }
        return a.toString();
    }

    boolean bgOn() { return getSharedPreferences("b", 0).getBoolean("bg", true); }
    boolean pipOn() { return getSharedPreferences("b", 0).getBoolean("pip", true); }

    String pre(String host) { return "window.__stealth=" + isStealth() + ";window.__zr=" + zapJson(host) + ";window.__wf=" + wfJson() + ";window.__bg=" + bgOn() + ";"; }

    // ---------- tabs ----------
    static class Tab { String url, title; Tab(String u, String t) { url = u; title = t; } }
    final ArrayList<Tab> tabs = new ArrayList<>();
    int curTab = 0;
    TextView bTabs;

    void saveTab() {
        if (tabs.isEmpty()) return;
        Tab t = tabs.get(curTab);
        if (web.getUrl() != null) t.url = web.getUrl();
        t.title = web.getTitle();
    }

    void persistTabs() {
        try {
            saveTab();
            JSONArray a = new JSONArray();
            for (Tab t : tabs) a.put(new JSONArray().put(t.url == null ? HOME : t.url).put(t.title == null ? "" : t.title));
            getSharedPreferences("b", 0).edit().putString("tabs", a.toString()).putInt("cur", curTab).apply();
        } catch (Exception ignored) {}
    }

    void loadTabs() {
        try {
            JSONArray a = new JSONArray(getSharedPreferences("b", 0).getString("tabs", "[]"));
            for (int i = 0; i < a.length(); i++) tabs.add(new Tab(a.getJSONArray(i).getString(0), a.getJSONArray(i).optString(1)));
            curTab = Math.min(getSharedPreferences("b", 0).getInt("cur", 0), Math.max(0, tabs.size() - 1));
        } catch (Exception ignored) {}
    }

    void updateTabsBtn() { if (bTabs != null) bTabs.setText(tabs.size() > 9 ? "⧉" : "⧉" + tabs.size()); }

    void newTab(String u) {
        saveTab();
        tabs.add(new Tab(u, ""));
        curTab = tabs.size() - 1;
        web.loadUrl(u);
        web.clearHistory();
        updateTabsBtn();
        persistTabs();
    }

    void switchTab(int i) {
        if (i == curTab || i < 0 || i >= tabs.size()) return;
        saveTab();
        curTab = i;
        web.loadUrl(tabs.get(i).url == null ? HOME : tabs.get(i).url);
        updateTabsBtn();
        persistTabs();
    }

    void closeTab(int i) {
        if (tabs.size() <= 1) { tabs.get(0).url = HOME; tabs.get(0).title = ""; web.loadUrl(HOME); persistTabs(); return; }
        tabs.remove(i);
        if (curTab >= tabs.size()) curTab = tabs.size() - 1;
        else if (i < curTab) curTab--;
        web.loadUrl(tabs.get(curTab).url == null ? HOME : tabs.get(curTab).url);
        updateTabsBtn();
        persistTabs();
    }

    void tabsDialog() {
        saveTab();
        final List<String> items = new ArrayList<>();
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            String name = t.title != null && !t.title.isEmpty() ? t.title : (t.url == null || t.url.startsWith("file:") ? "الصفحة الرئيسية" : t.url);
            if (name.length() > 45) name = name.substring(0, 45) + "…";
            items.add((i == curTab ? "▶ " : "    ") + name);
        }
        final int n = tabs.size();
        items.add("➕ تبويب جديد");
        items.add("✕ سكّر هالتبويب");
        if (n > 1) items.add("🧹 سكّر كل التبويبات");
        new AlertDialog.Builder(this).setTitle("⧉ التبويبات (" + n + ")").setItems(items.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) {
                if (w < n) switchTab(w);
                else if (w == n) newTab(HOME);
                else if (w == n + 1) closeTab(curTab);
                else { tabs.clear(); tabs.add(new Tab(HOME, "")); curTab = 0; web.loadUrl(HOME); updateTabsBtn(); persistTabs(); }
            }
        }).show();
    }

    // ---------- bookmarks & history ----------
    JSONArray jarr(String key) {
        try { return new JSONArray(getSharedPreferences("b", 0).getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    void putArr(String key, JSONArray a) { getSharedPreferences("b", 0).edit().putString(key, a.toString()).apply(); }

    int bmIndex(String u) {
        JSONArray a = jarr("bm");
        for (int i = 0; i < a.length(); i++) if (u.equals(a.optJSONObject(i).optString("u"))) return i;
        return -1;
    }

    void toggleBookmark() {
        String u = web.getUrl();
        if (u == null || !u.startsWith("http")) { toast("افتح موقع أول"); return; }
        JSONArray a = jarr("bm");
        int i = bmIndex(u);
        try {
            if (i >= 0) { a.remove(i); toast("☆ انشال من المفضلة"); }
            else { a.put(new org.json.JSONObject().put("u", u).put("t", web.getTitle() == null ? u : web.getTitle())); toast("⭐ انضاف للمفضلة"); }
        } catch (Exception ignored) {}
        putArr("bm", a);
    }

    void bookmarksDialog(final boolean edit) {
        final JSONArray a = jarr("bm");
        if (a.length() == 0) { toast("المفضلة فاضية • من ⋮ اختار «⭐ أضف للمفضلة»"); return; }
        String[] items = new String[a.length()];
        for (int i = 0; i < a.length(); i++) items[i] = (edit ? "🗑️ " : "⭐ ") + a.optJSONObject(i).optString("t");
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(edit ? "اضغط على اللي تبي تشيله" : "⭐ المفضلة")
            .setItems(items, new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
                if (edit) { a.remove(w); putArr("bm", a); toast("انشال"); bookmarksDialog(true); }
                else web.loadUrl(a.optJSONObject(w).optString("u"));
            }});
        if (!edit) b.setNeutralButton("🗑️ تعديل", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) { bookmarksDialog(true); } });
        b.setNegativeButton("إغلاق", null).show();
    }

    void addHistory(String u, String t) {
        if (u == null || !u.startsWith("http")) return;
        try {
            JSONArray a = jarr("hist"), out = new JSONArray();
            out.put(new org.json.JSONObject().put("u", u).put("t", t == null || t.isEmpty() ? u : t).put("ts", System.currentTimeMillis()));
            for (int i = 0; i < a.length() && out.length() < 200; i++) if (!u.equals(a.optJSONObject(i).optString("u"))) out.put(a.get(i));
            putArr("hist", out);
        } catch (Exception ignored) {}
    }

    void historyDialog() {
        final JSONArray a = jarr("hist");
        if (a.length() == 0) { toast("السجل فاضي"); return; }
        int n = Math.min(60, a.length());
        String[] items = new String[n];
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d/M HH:mm", Locale.US);
        for (int i = 0; i < n; i++) { org.json.JSONObject o = a.optJSONObject(i); items[i] = f.format(new Date(o.optLong("ts"))) + "  " + o.optString("t"); }
        new AlertDialog.Builder(this).setTitle("🕘 السجل").setItems(items, new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
                web.loadUrl(a.optJSONObject(w).optString("u")); } })
            .setNeutralButton("🧹 امسحه", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) { putArr("hist", new JSONArray()); toast("انمسح السجل"); } })
            .setNegativeButton("إغلاق", null).show();
    }

    /** Data for the home page (only served to our own home page). */
    String homeJson() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("bm", jarr("bm"));
            JSONArray h = jarr("hist"), cont = new JSONArray();
            android.content.SharedPreferences pos = getSharedPreferences("pos", 0);
            for (int i = 0; i < h.length() && cont.length() < 6; i++) {
                org.json.JSONObject x = h.optJSONObject(i);
                String u = x.optString("u"), key = u.length() > 300 ? u.substring(0, 300) : u;
                float p = pos.getFloat(key, 0);
                if (p > 30) cont.put(new org.json.JSONObject().put("u", u).put("t", x.optString("t")).put("p", tfmt(p)));
            }
            o.put("cont", cont);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    // ---------- picture-in-picture & background audio ----------
    volatile boolean playing = false;
    boolean pipZoomed = false;

    void enterPip() {
        if (Build.VERSION.SDK_INT < 26) { toast("جهازك ما يدعم النافذة الصغيرة"); return; }
        try {
            pipZoomed = zoomMode == 0 && customView == null;
            if (pipZoomed) web.evaluateJavascript("window.__cmd&&__cmd('zoom',1)", null);
            top.setVisibility(View.GONE); bottom.setVisibility(View.GONE); vpanel.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE); seekB.row.setVisibility(View.GONE);
            enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(16, 9)).build());
        } catch (Exception e) { toast("ما قدرت أفتح النافذة الصغيرة"); }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (playing && pipOn() && Build.VERSION.SDK_INT >= 26) enterPip();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (inPip) return;
        if (pipZoomed) {
            pipZoomed = false;
            web.evaluateJavascript("window.__cmd&&__cmd('zoom',0)", null);
            top.setVisibility(View.VISIBLE); bottom.setVisibility(View.VISIBLE);
            web.evaluateJavascript("!!(document.querySelector('video')||window.__fv)", new ValueCallback<String>() { public void onReceiveValue(String v) { vpanel.setVisibility("true".equals(v) ? View.VISIBLE : View.GONE); } });
        } else if (zoomMode > 0 || customView != null) showOverlay();
    }

    void wordFilterDialog() {
        final android.content.SharedPreferences sp = getSharedPreferences("m", 0);
        final boolean on = sp.getBoolean("wf_on", true);
        final EditText et = new EditText(this);
        et.setText(sp.getString("wf_words", WF_DEFAULT));
        et.setMinLines(4);
        et.setTextSize(15);
        et.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(20), px(8), px(20), 0);
        TextView tip = new TextView(this);
        tip.setText("أي فيديو أو منشور فيه وحدة من هالكلمات (بالعنوان أو الوصف أو الهاشتاق) ينحجب ويسكت صوته.\nافصل بين الكلمات بفاصلة «،».");
        tip.setTextSize(14);
        box.addView(tip);
        box.addView(et);
        new AlertDialog.Builder(this).setTitle("🙈 فلتر المحتوى " + (on ? "(شغّال)" : "(طافي)")).setView(box)
            .setPositiveButton("حفظ", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
                final String words = et.getText().toString();
                PinLock.ask(BrowserActivity.this, "تعديل الفلتر", new Runnable() { public void run() {
                    sp.edit().putString("wf_words", words).putBoolean("wf_on", true).apply();
                    toast("🙈 انحفظ"); web.reload(); } }); } })
            .setNeutralButton(on ? "طفّه" : "شغّله", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
                Runnable r = new Runnable() { public void run() { sp.edit().putBoolean("wf_on", !on).apply(); toast(on ? "انطفى الفلتر" : "🙈 الفلتر شغّال"); web.reload(); } };
                if (on) PinLock.ask(BrowserActivity.this, "إيقاف الفلتر", r); else r.run(); } })
            .setNegativeButton("إلغاء", null).show();
    }
    LinearLayout vbtns;
    SeekUi seekA, seekB;
    final ArrayList<String[]> recentBlocked = new ArrayList<>();
    final android.os.Handler h = new android.os.Handler();

    static String tfmt(double s) {
        if (s < 0 || Double.isNaN(s)) s = 0;
        int t = (int) s, hh = t / 3600, mm = (t % 3600) / 60, ss = t % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, mm, ss) : String.format(Locale.US, "%d:%02d", mm, ss);
    }

    /** Time label + draggable seek bar, always left-to-right. */
    class SeekUi {
        LinearLayout row; TextView cur, dur; SeekBar bar; boolean dragging = false;
        SeekUi() {
            row = new LinearLayout(BrowserActivity.this);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(10), px(2), px(10), 0);
            cur = new TextView(BrowserActivity.this); cur.setTextColor(Color.WHITE); cur.setTextSize(13); cur.setText("0:00");
            dur = new TextView(BrowserActivity.this); dur.setTextColor(Color.parseColor("#94A3B8")); dur.setTextSize(13); dur.setText("0:00");
            bar = new SeekBar(BrowserActivity.this);
            bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            bar.setPadding(px(14), px(10), px(14), px(10));
            row.addView(cur);
            row.addView(bar, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(dur);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar b, int p, boolean user) { if (user) cur.setText(tfmt(p)); }
                public void onStartTrackingTouch(SeekBar b) { dragging = true; }
                public void onStopTrackingTouch(SeekBar b) {
                    dragging = false;
                    web.evaluateJavascript("window.__cmd&&__cmd('seekto'," + b.getProgress() + ")", null);
                }
            });
        }
        void set(double t, double d) {
            if (dragging) return;
            bar.setMax((int) Math.max(1, d));
            bar.setProgress((int) t);
            cur.setText(tfmt(t)); dur.setText(tfmt(d));
        }
    }

    String resumeChecked = null;
    long lastSave = 0;
    void resume(double t, double d) {
        String page = web.getUrl();
        if (page == null || !page.startsWith("http") || d < 120) return;
        android.content.SharedPreferences sp = getSharedPreferences("pos", 0);
        String key = page.length() > 300 ? page.substring(0, 300) : page;
        if (!key.equals(resumeChecked)) {
            resumeChecked = key;
            float saved = sp.getFloat(key, 0);
            if (saved > 30 && saved < d - 60 && t < 10) {
                web.evaluateJavascript("window.__cmd&&__cmd('seekto'," + (int) saved + ")", null);
                toast("▶ كمّلت من " + tfmt(saved));
                return;
            }
        }
        if (System.currentTimeMillis() - lastSave > 4000 && t > 30) {
            lastSave = System.currentTimeMillis();
            android.content.SharedPreferences.Editor e = sp.edit();
            if (t > d - 90) e.remove(key); else e.putFloat(key, (float) t);
            if (sp.getAll().size() > 300) e.clear();
            e.apply();
        }
    }

    final Runnable poll = new Runnable() { public void run() {
        boolean need = vpanel.getVisibility() == View.VISIBLE || zoomMode > 0 || customView != null;
        if (need) {
            web.evaluateJavascript("window.__vinfo?__vinfo():''", new ValueCallback<String>() { public void onReceiveValue(String v) {
                try {
                    String js = new org.json.JSONArray("[" + v + "]").getString(0);
                    if (js.isEmpty()) { seekB.row.setVisibility(View.GONE); playing = false; return; }
                    org.json.JSONObject o = new org.json.JSONObject(js);
                    double t = o.getDouble("t"), d = o.getDouble("d");
                    playing = o.optInt("p", 1) == 0;
                    resume(t, d);
                    seekA.set(t, d); seekB.set(t, d);
                    seekA.row.setVisibility(View.VISIBLE);
                    seekB.row.setVisibility(zoomMode > 0 || customView != null ? View.VISIBLE : View.GONE);
                } catch (Exception e) { }
            }});
        } else seekB.row.setVisibility(View.GONE);
        h.postDelayed(this, 500);
    }};
    LinearLayout popbar;
    TextView popText;
    volatile String popUrl = null, allowOnce = null;
    int popsBlocked = 0;
    final Runnable hidePop = new Runnable() { public void run() { popbar.setVisibility(View.GONE); } };
    volatile String pageHost = "";
    volatile String curUrl = "";
    Set<String> stealth;
    LinearLayout vpanel;
    int skippedHere = 0;

    static final String STUB_ADSBYGOOGLE = "window.adsbygoogle=window.adsbygoogle||[];try{window.adsbygoogle.loaded=true;window.adsbygoogle.push=function(){}}catch(e){}";
    static final String STUB_GPT = "(function(){var n=function(){},s={addService:function(){return s},setTargeting:function(){return s},defineSizeMapping:function(){return s},setCollapseEmptyDiv:function(){return s},addSize:function(){return s},build:function(){return[]},getSlotElementId:function(){return''},setClickUrl:function(){return s},setForceSafeFrame:function(){return s},clearTargeting:function(){return s}};"
        + "var p={addEventListener:n,removeEventListener:n,enableSingleRequest:n,collapseEmptyDivs:n,setTargeting:function(){return p},disableInitialLoad:n,refresh:n,clear:n,getSlots:function(){return[]},setRequestNonPersonalizedAds:n,setPrivacySettings:n,enableLazyLoad:n,set:n,get:n,display:n,setCentering:n,updateCorrelator:n};"
        + "var g=window.googletag=window.googletag||{};g.apiReady=true;g.pubadsReady=true;g.defineSlot=function(){return s};g.defineOutOfPageSlot=function(){return s};g.pubads=function(){return p};g.companionAds=function(){return p};g.content=function(){return p};g.sizeMapping=function(){return s};g.enableServices=n;g.display=n;g.destroySlots=n;g.setAdIframeTitle=n;"
        + "var q=g.cmd||[];g.cmd={push:function(f){try{f()}catch(e){}return 1}};for(var i=0;i<q.length;i++)try{q[i]()}catch(e){}})();";
    static final byte[] GIF = {71,73,70,56,57,97,1,0,1,0,-128,0,0,0,0,0,0,0,0,33,-7,4,1,0,0,0,0,44,0,0,0,0,1,0,1,0,0,2,2,68,1,0,59};

    static String baseHost(String h) {
        if (h == null) return "";
        h = h.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) h = h.substring(4); else if (h.startsWith("m.")) h = h.substring(2);
        return h;
    }

    boolean isStealth() { return stealth.contains(pageHost); }

    void setStealth(String host, boolean on) {
        if (on) stealth.add(host); else stealth.remove(host);
        getSharedPreferences("m", 0).edit().putStringSet("stealth", new HashSet<>(stealth)).apply();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.parseColor("#0B1220"));
        getWindow().setNavigationBarColor(Color.parseColor("#0B1220"));
        new Thread(new Runnable() { public void run() { Rules.load(getApplicationContext()); } }).start();
        stealth = Collections.synchronizedSet(new HashSet<>(getSharedPreferences("m", 0).getStringSet("stealth", new HashSet<String>())));
        if (INJ == null) {
            try (java.io.InputStream in = getAssets().open("inject.js")) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                INJ = bo.toString("UTF-8");
            } catch (Exception e) { INJ = ""; }
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0B1220"));
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // top bar
        top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(px(6), px(6), px(6), px(6));
        top.setBackgroundColor(Color.parseColor("#0B1220"));
        bShield = btn("🛡️", new View.OnClickListener() { public void onClick(View v) {
            shieldDialog();
        }});
        top.addView(bShield);
        url = new EditText(this);
        url.setSingleLine(true);
        url.setTextSize(15);
        url.setTextColor(Color.WHITE);
        url.setHintTextColor(Color.parseColor("#64748B"));
        url.setHint("ابحث أو اكتب رابط");
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_GO);
        url.setSelectAllOnFocus(true);
        url.setPadding(px(14), px(8), px(14), px(8));
        GradientDrawable ug = new GradientDrawable();
        ug.setColor(Color.parseColor("#1E293B")); ug.setCornerRadius(px(22));
        url.setBackground(ug);
        url.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) { go(url.getText().toString()); return true; }
        });
        top.addView(url, new LinearLayout.LayoutParams(0, px(44), 1));
        bTabs = btn("⧉1", new View.OnClickListener() { public void onClick(View v) { tabsDialog(); } });
        bTabs.setTextSize(16);
        top.addView(bTabs);
        top.addView(btn("⟳", new View.OnClickListener() { public void onClick(View v) { web.reload(); } }));
        main.addView(top);

        prog = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        prog.setMax(100);
        main.addView(prog, new LinearLayout.LayoutParams(-1, px(3)));

        web = new WebView(this);
        main.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        // video panel (shown when the page has a video)
        vpanel = new LinearLayout(this);
        vpanel.setOrientation(LinearLayout.VERTICAL);
        vpanel.setBackgroundColor(Color.parseColor("#13233F"));
        vpanel.setVisibility(View.GONE);
        seekA = new SeekUi();
        vpanel.addView(seekA.row);
        vbtns = new LinearLayout(this);
        vpanel.addView(vbtns);
        addPanel("⏪10", new View.OnClickListener() { public void onClick(View v) { seek(-10); } });
        addPanel("⏯", new View.OnClickListener() { public void onClick(View v) { web.evaluateJavascript("window.__cmd&&__cmd('pp',0)", null); } });
        addPanel("⏩10", new View.OnClickListener() { public void onClick(View v) { seek(10); } });
        addPanel("⏩30", new View.OnClickListener() { public void onClick(View v) { seek(30); } });
        addPanel("⏭️دعاية", new View.OnClickListener() { public void onClick(View v) { skipAd(); } });
        addPanel("🎬", new View.OnClickListener() { public void onClick(View v) { setZoom(1); } });
        main.addView(vpanel);

        // bar shown when a popup / redirect gets blocked
        popbar = new LinearLayout(this);
        popbar.setGravity(Gravity.CENTER_VERTICAL);
        popbar.setBackgroundColor(Color.parseColor("#3B1D1D"));
        popbar.setPadding(px(12), 0, px(4), 0);
        popbar.setVisibility(View.GONE);
        popText = new TextView(this);
        popText.setTextColor(Color.parseColor("#FECACA"));
        popText.setTextSize(13);
        popText.setSingleLine(true);
        popText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        popbar.addView(popText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView po = btn("افتحها", new View.OnClickListener() { public void onClick(View v) {
            popbar.setVisibility(View.GONE);
            if (popUrl != null) { allowOnce = popUrl; web.loadUrl(popUrl); }
        }});
        po.setTextSize(14); po.setTextColor(Color.parseColor("#93C5FD"));
        popbar.addView(po);
        TextView px2 = btn("✕", new View.OnClickListener() { public void onClick(View v) { popbar.setVisibility(View.GONE); } });
        px2.setTextSize(16);
        popbar.addView(px2);
        main.addView(popbar, new LinearLayout.LayoutParams(-1, px(42)));

        // bottom bar
        bottom = new LinearLayout(this);
        bottom.setBackgroundColor(Color.parseColor("#0E1626"));
        bottom.setPadding(0, px(4), 0, px(4));
        addBottom("◀", new View.OnClickListener() { public void onClick(View v) { if (web.canGoBack()) web.goBack(); } });
        addBottom("▶", new View.OnClickListener() { public void onClick(View v) { if (web.canGoForward()) web.goForward(); } });
        addBottom("🏠", new View.OnClickListener() { public void onClick(View v) { web.loadUrl(HOME); } });
        bSpeed = addBottom("⚡1x", new View.OnClickListener() { public void onClick(View v) { speedDialog(); } });
        bDl = addBottom("📥", new View.OnClickListener() { public void onClick(View v) { downloadDialog(); } });
        addBottom("⛶", new View.OnClickListener() { public void onClick(View v) { zoomDialog(); } });
        addBottom("📺", new View.OnClickListener() { public void onClick(View v) { castDialog(); } });
        addBottom("⋮", new View.OnClickListener() { public void onClick(View v) { menuDialog(); } });
        main.addView(bottom);

        // fullscreen container (native video fullscreen)
        full = new FrameLayout(this);
        full.setBackgroundColor(Color.BLACK);
        full.setVisibility(View.GONE);
        root.addView(full, new FrameLayout.LayoutParams(-1, -1));

        // floating controls for fullscreen / cinema mode
        overlay = new LinearLayout(this);
        overlay.setVisibility(View.GONE);
        overlay.setPadding(px(6), px(6), px(6), px(6));
        GradientDrawable og = new GradientDrawable();
        og.setColor(Color.parseColor("#99000000")); og.setCornerRadius(px(14));
        overlay.setBackground(og);
        overlay.addView(obtn("⏪", new View.OnClickListener() { public void onClick(View v) { seek(-10); } }));
        overlay.addView(obtn("⏯", new View.OnClickListener() { public void onClick(View v) { web.evaluateJavascript("window.__cmd&&__cmd('pp',0)", null); } }));
        overlay.addView(obtn("⏩", new View.OnClickListener() { public void onClick(View v) { seek(10); } }));
        overlay.addView(obtn("⏭️", new View.OnClickListener() { public void onClick(View v) { skipAd(); } }));
        oSpeed = obtn("⚡1x", new View.OnClickListener() { public void onClick(View v) { cycleSpeed(); } });
        overlay.addView(oSpeed);
        overlay.addView(obtn("🔍", new View.OnClickListener() { public void onClick(View v) { cycleZoom(); } }));
        overlay.addView(obtn("📺", new View.OnClickListener() { public void onClick(View v) { castDialog(); } }));
        overlay.addView(obtn("✕", new View.OnClickListener() { public void onClick(View v) { exitFull(); } }));
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        olp.setMargins(px(12), px(12), px(12), px(12));
        root.addView(overlay, olp);

        // seek bar at the bottom in cinema / fullscreen
        seekB = new SeekUi();
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(Color.parseColor("#99000000")); sg.setCornerRadius(px(14));
        seekB.row.setBackground(sg);
        seekB.row.setVisibility(View.GONE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        slp.setMargins(px(16), px(8), px(16), px(14));
        root.addView(seekB.row, slp);
        poll.run();

        setContentView(root);
        setupWeb();

        Intent i = getIntent();
        loadTabs();
        if (i != null && i.getData() != null) {
            tabs.add(new Tab(i.getData().toString(), "")); curTab = tabs.size() - 1;
            web.loadUrl(i.getData().toString());
        } else if (!tabs.isEmpty()) web.loadUrl(tabs.get(curTab).url == null ? HOME : tabs.get(curTab).url);
        else { tabs.add(new Tab(HOME, "")); curTab = 0; web.loadUrl(HOME); }
        updateTabsBtn();

        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3);
    }

    @SuppressLint("SetJavaScriptEnabled")
    void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        defaultUA = s.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public String home() {
                String cu = curUrl;
                return cu != null && cu.startsWith("file:///android_asset/") ? homeJson() : "{}";
            }
            @JavascriptInterface public void frame(String u) {
                if (u != null && u.startsWith("http")) frameSet.add(noFrag(u));
            }
            @JavascriptInterface public void zapped(String host, String sel) {
                if (sel == null || sel.trim().isEmpty() || sel.length() > 400) return;
                android.content.SharedPreferences sp = getSharedPreferences("zap", 0);
                String h = baseHost(host);
                Set<String> z = new HashSet<>(sp.getStringSet("z:" + h, new HashSet<String>()));
                z.add(sel);
                Set<String> hs = new HashSet<>(sp.getStringSet("p:" + pageHost, new HashSet<String>()));
                hs.add(h);
                sp.edit().putStringSet("z:" + h, z).putStringSet("p:" + pageHost, hs).apply();
                runOnUiThread(new Runnable() { public void run() {
                    web.evaluateJavascript("window.__cmd&&__cmd('zap',0)", null);
                    toast("✅ انمسحت • بتنمسح بروحها كل مرة في هالموقع");
                }});
            }
            @JavascriptInterface public void video(final boolean has) {
                runOnUiThread(new Runnable() { public void run() { vpanel.setVisibility(has && zoomMode == 0 ? View.VISIBLE : View.GONE); } });
            }
            @JavascriptInterface public void skipped() {
                skippedHere++;
                runOnUiThread(new Runnable() { public void run() { updateShield(); } });
            }
            @JavascriptInterface public void wall() {
                runOnUiThread(new Runnable() { public void run() { onWall(); } });
            }
            @JavascriptInterface public void media(String json, String title) {
                try {
                    JSONArray a = new JSONArray(json);
                    for (int k = 0; k < a.length(); k++) addMedia(a.getString(k), title);
                } catch (Exception ignored) {}
            }
        }, "MB");

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String u, Bitmap f) {
                blockedHere = 0; skippedHere = 0; popsBlocked = 0;
                synchronized (recentBlocked) { recentBlocked.clear(); }
                pageHost = baseHost(Uri.parse(u).getHost());
                curUrl = u;
                vpanel.setVisibility(View.GONE);
                updateShield();
                inject();
                synchronized (media) { media.clear(); }
                updateDl();
                showUrl(u);
            }
            @Override public void onPageFinished(WebView v, String u) {
                curUrl = u;
                addHistory(u, v.getTitle());
                saveTab();
                showUrl(u);
                updateShield();
                inject();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return guardNav(r, u);
                if (u.startsWith("file:")) return false;
                try {
                    Intent i = u.startsWith("intent:") ? Intent.parseUri(u, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                    i.addCategory(Intent.CATEGORY_BROWSABLE); i.setComponent(null); i.setSelector(null);
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        Intent i = Intent.parseUri(u, Intent.URI_INTENT_SCHEME);
                        String fb = i.getStringExtra("browser_fallback_url");
                        if (fb != null) v.loadUrl(fb);
                    } catch (Exception ignored) {}
                }
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost();
                if (r.isForMainFrame()) pageHost = baseHost(host);
                if (host != null && !r.isForMainFrame() && !isStealth()) {
                    int c = Rules.check(host);
                    if (c == Rules.BLOCK_LIST || c == Rules.BLOCK_CUSTOM) {
                        blockedHere++;
                        return stub(u, r);
                    }
                }
                if (!r.isForMainFrame() && "GET".equals(r.getMethod()) && host != null) {
                    Map<String, String> rh = r.getRequestHeaders();
                    String acc = rh == null ? null : rh.get("Accept");
                    if (acc == null && rh != null) acc = rh.get("accept");
                    boolean known = frameSet.contains(noFrag(u.toString()));
                    if (((acc != null && acc.startsWith("text/html")) || known) && !skipFrame(u.toString())) {
                        WebResourceResponse fr = frame(u.toString(), rh);
                        if (fr != null) return fr;
                    }
                }
                String p = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
                if (p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".m3u8") || p.endsWith(".mkv")
                        || p.endsWith(".mov") || p.endsWith(".mp3") || p.endsWith(".m4a")) addMedia(u.toString(), null);
                return null;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView v, boolean dialog, final boolean gesture, android.os.Message msg) {
                final WebView tmp = new WebView(BrowserActivity.this);
                final boolean[] done = {false};
                tmp.setWebViewClient(new WebViewClient() {
                    void handle(WebView w, String u) {
                        if (done[0] || u == null || u.equals("about:blank")) return;
                        done[0] = true;
                        onPopup(u);
                        w.stopLoading();
                        w.post(new Runnable() { public void run() { tmp.destroy(); } });
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) { handle(w, r.getUrl().toString()); return true; }
                    @Override public void onPageStarted(WebView w, String u, Bitmap f) { handle(w, u); }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) msg.obj;
                t.setWebView(tmp);
                msg.sendToTarget();
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest req) { req.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) { cb.invoke(origin, false, false); }
            @Override public boolean onJsAlert(WebView v, String u, String m, JsResult r) { if (m != null && m.length() < 120) toast(m); r.confirm(); return true; }
            @Override public boolean onJsConfirm(WebView v, String u, String m, JsResult r) { r.cancel(); return true; }
            @Override public void onProgressChanged(WebView v, int p) {
                prog.setProgress(p);
                prog.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
                if (p > 30) inject();
            }
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view; customCb = cb;
                full.addView(view, new FrameLayout.LayoutParams(-1, -1));
                full.setVisibility(View.VISIBLE);
                immersive(true);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                showOverlay();
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                full.removeView(customView);
                full.setVisibility(View.GONE);
                customView = null;
                if (customCb != null) customCb.onCustomViewHidden();
                customCb = null;
                if (zoomMode == 0) { immersive(false); overlay.setVisibility(View.GONE); }
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String u, String ua, String cd, String mime, long len) {
                String l = (u + " " + mime + " " + cd).toLowerCase(Locale.ROOT);
                boolean apk = l.contains(".apk") || l.contains("android.package-archive") || l.contains(".exe") || l.contains(".bat");
                String hst = Uri.parse(u).getHost();
                if ((apk && !tappedLink(u)) || (hst != null && blockedHost(hst))) { blockPop("🚫 ملف نزل بروحه: " + URLUtil.guessFileName(u, cd, mime), u); return; }
                download(u, cd, mime);
            }
        });

        web.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                WebView.HitTestResult h = web.getHitTestResult();
                final String extra = h.getExtra();
                if (extra == null || !extra.startsWith("http")) return false;
                final boolean img = h.getType() == WebView.HitTestResult.IMAGE_TYPE || h.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
                final String[] items = img ? new String[]{"📥 حمّل الصورة", "🔗 انسخ الرابط", "↗️ افتح"} : new String[]{"↗️ افتح", "⧉ افتح في تبويب جديد", "🔗 انسخ الرابط", "📥 حمّل الرابط", "📤 شارك"};
                new AlertDialog.Builder(BrowserActivity.this).setTitle(extra).setItems(items, new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
                    String it = items[w];
                    if (it.startsWith("⧉")) newTab(extra);
                    else if (it.contains("حمّل")) download(extra, null, null);
                    else if (it.contains("انسخ")) copy(extra);
                    else if (it.contains("افتح")) web.loadUrl(extra);
                    else share(extra);
                }}).show();
                return true;
            }
        });
    }

    void inject() {
        web.evaluateJavascript(pre(pageHost) + INJ, null);
        if (rate != 1f) applyRate();
        if (zoomMode > 0) applyZoom();
    }

    void addMedia(String u, String title) {
        if (u == null || u.startsWith("blob:") || u.startsWith("data:")) return;
        synchronized (media) {
            if (media.containsKey(u)) return;
            media.put(u, title == null ? "" : title);
            while (media.size() > 40) media.remove(media.keySet().iterator().next());
        }
        runOnUiThread(new Runnable() { public void run() { updateDl(); } });
    }

    void updateDl() {
        int n; synchronized (media) { n = media.size(); }
        bDl.setText(n > 0 ? "📥" + n : "📥");
        bDl.setTextColor(Color.parseColor(n > 0 ? "#4ADE80" : "#E6EDF7"));
    }

    void showUrl(String u) {
        if (url.hasFocus()) return;
        url.setText(u == null || u.startsWith("file:") ? "" : u);
    }

    void go(String q) {
        q = q.trim();
        if (q.isEmpty()) return;
        String target;
        if (q.startsWith("http://") || q.startsWith("https://")) target = q;
        else if (!q.contains(" ") && q.contains(".") && !q.endsWith(".")) target = "https://" + q;
        else target = "https://www.google.com/search?q=" + Uri.encode(q);
        web.loadUrl(target);
        url.clearFocus();
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(url.getWindowToken(), 0);
        web.requestFocus();
    }

    // ---------- speed ----------
    void speedDialog() {
        String[] items = new String[SPEEDS.length];
        int sel = 3;
        for (int k = 0; k < SPEEDS.length; k++) { items[k] = fmt(SPEEDS[k]) + "x" + (SPEEDS[k] == 1f ? " (عادي)" : ""); if (SPEEDS[k] == rate) sel = k; }
        new AlertDialog.Builder(this).setTitle("⚡ سرعة الفيديو").setSingleChoiceItems(items, sel, new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
            setRate(SPEEDS[w]); d.dismiss();
        }}).show();
    }

    void cycleSpeed() {
        float[] quick = {1f, 1.25f, 1.5f, 2f, 3f};
        int k = 0;
        for (int j = 0; j < quick.length; j++) if (quick[j] == rate) k = j + 1;
        setRate(quick[k % quick.length]);
    }

    void setRate(float r) {
        rate = r;
        String t = "⚡" + fmt(r) + "x";
        bSpeed.setText(t); oSpeed.setText(t);
        bSpeed.setTextColor(Color.parseColor(r == 1f ? "#E6EDF7" : "#FBBF24"));
        applyRate();
        toast("السرعة " + fmt(r) + "x");
    }

    void applyRate() {
        web.evaluateJavascript("window.__mrate=" + rate + ";window.__cmd&&__cmd('rate'," + rate + ")", null);
    }

    static String fmt(float f) { return f == (int) f ? String.valueOf((int) f) : String.valueOf(f); }

    // ---------- zoom / cinema ----------
    static final String[] ZOOMS = {"إيقاف", "🎬 وضع السينما (الفيديو يملا الشاشة)", "🔲 ملء الشاشة (يقص الأطراف)", "🔍 تكبير 1.3x", "🔍 تكبير 1.6x"};

    void zoomDialog() {
        String[] items = {"🎬 وضع السينما (الفيديو يملا الشاشة)", "🔲 ملء الشاشة بدون أطراف سودا", "🔍 تكبير الفيديو 1.3x", "🔍 تكبير الفيديو 1.6x",
                "🔠 تكبير الخط", "⏹️ رجّع عادي"};
        new AlertDialog.Builder(this).setTitle("⛶ تكبير").setItems(items, new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
            if (w <= 3) setZoom(w + 1);
            else if (w == 4) textSizeDialog();
            else setZoom(0);
        }}).show();
    }

    void cycleZoom() { setZoom(zoomMode >= 4 || zoomMode == 0 ? 1 : zoomMode + 1); }

    void setZoom(final int m) {
        if (m > 0) {
            web.evaluateJavascript("window.__cmd?__cmd('zoom'," + m + "):0", new ValueCallback<String>() {
                public void onReceiveValue(String v) {
                    if ("0".equals(v) || "null".equals(v)) { toast("ما لقيت فيديو • شغّل الفيديو أول"); return; }
                    boolean was = zoomMode > 0;
                    zoomMode = m;
                    top.setVisibility(View.GONE); bottom.setVisibility(View.GONE); vpanel.setVisibility(View.GONE); popbar.setVisibility(View.GONE);
                    immersive(true);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    showOverlay();
                    if (!was) toast("🎬 اضغط 🔍 تكبّر • ✕ تطلع");
                    else toast(ZOOMS[m].replaceAll("\\(.*\\)", "").trim());
                }
            });
        } else {
            zoomMode = 0;
            web.evaluateJavascript("window.__cmd&&__cmd('zoom',0)", null);
            if (customView == null) {
                top.setVisibility(View.VISIBLE); bottom.setVisibility(View.VISIBLE);
                web.evaluateJavascript("!!(document.querySelector('video')||window.__fv)", new ValueCallback<String>() { public void onReceiveValue(String v) { vpanel.setVisibility("true".equals(v) ? View.VISIBLE : View.GONE); } });
                immersive(false);
                overlay.setVisibility(View.GONE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
    }

    void applyZoom() {
        web.evaluateJavascript("window.__cmd&&__cmd('zoom'," + zoomMode + ")", null);
    }

    void exitFull() {
        if (customView != null && customCb != null) web.getWebChromeClient().onHideCustomView();
        setZoom(0);
    }

    void showOverlay() {
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
    }

    void immersive(boolean on) {
        View d = getWindow().getDecorView();
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            d.setSystemUiVisibility(0);
        }
    }

    void textSizeDialog() {
        final int[] sizes = {85, 100, 115, 130, 150, 175, 200};
        String[] items = new String[sizes.length];
        int cur = web.getSettings().getTextZoom(), sel = 1;
        for (int k = 0; k < sizes.length; k++) { items[k] = sizes[k] + "%"; if (sizes[k] == cur) sel = k; }
        new AlertDialog.Builder(BrowserActivity.this).setTitle("🔠 حجم الخط").setSingleChoiceItems(items, sel, new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
            web.getSettings().setTextZoom(sizes[w]); d.dismiss();
        }}).show();
    }

    // ---------- downloads ----------
    List<String[]> mediaList() {
        List<String[]> l = new ArrayList<>();
        synchronized (media) { for (Map.Entry<String, String> e : media.entrySet()) l.add(new String[]{e.getKey(), e.getValue()}); }
        Collections.reverse(l);
        return l;
    }

    void downloadDialog() {
        final List<String[]> l = mediaList();
        String page = web.getUrl() == null ? "" : web.getUrl();
        if (isProtected(page)) {
            msg("📥 التحميل", "هالموقع ما يسمح بتحميل الفيديو (يوتيوب ونتفلكس وشاهد وأمثالها محمية).\n\nتقدر تسرّعه وتكبّره وتعرضه على التلفزيون عادي.");
            return;
        }
        if (l.isEmpty()) {
            msg("📥 التحميل", "ما لقيت فيديو في هالصفحة للحين.\n\nشغّل الفيديو أول، وبعدين اضغط 📥 مرة ثانية.\n\nوتقدر بعد تضغط ضغطة طويلة على أي رابط أو صورة وتختار «حمّل».");
            return;
        }
        String[] items = new String[l.size() + 1];
        for (int k = 0; k < l.size(); k++) {
            String u = l.get(k)[0];
            String name = URLUtil.guessFileName(u, null, null);
            String kind = u.toLowerCase(Locale.ROOT).contains(".m3u8") ? "📡 بث" : u.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|m4a)(\\?.*)?$") ? "🎵" : "🎬";
            items[k] = kind + "  " + name;
        }
        items[l.size()] = "📂 افتح التحميلات";
        new AlertDialog.Builder(BrowserActivity.this).setTitle("📥 الفيديوهات في الصفحة (" + l.size() + ")").setItems(items, new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
            if (w == l.size()) { openDownloads(); return; }
            final String u = l.get(w)[0];
            if (u.toLowerCase(Locale.ROOT).contains(".m3u8")) {
                new AlertDialog.Builder(BrowserActivity.this).setTitle("📡 فيديو بث (m3u8)")
                    .setMessage("أحمّله لك كفيديو واحد في التنزيلات ← Mane3؟\nيطلع لك التقدّم في الإشعارات، وخل البرنامج مفتوح أو المانع شغّال لين يخلص.")
                    .setPositiveButton("📥 حمّله", new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface x,int y){hls(u);}})
                    .setNegativeButton("افتح ببرنامج ثاني", new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface x,int y){openExternal(u);}})
                    .setNeutralButton("انسخ الرابط", new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface x,int y){copy(u);}})
                    .show();
                return;
            }
            new AlertDialog.Builder(BrowserActivity.this).setTitle(URLUtil.guessFileName(u, null, null))
                .setItems(new String[]{"📥 حمّل", "▶️ افتح ببرنامج ثاني", "🔗 انسخ الرابط"}, new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface x,int y){
                    if (y == 0) download(u, null, null); else if (y == 1) openExternal(u); else copy(u);
                }}).show();
        }}).show();
    }

    static boolean isProtected(String page) {
        String p = page.toLowerCase(Locale.ROOT);
        return p.contains("youtube.com") || p.contains("youtu.be") || p.contains("netflix.com") || p.contains("shahid.mbc.net")
            || p.contains("primevideo.com") || p.contains("disneyplus.com") || p.contains("osn") || p.contains("tod.tv");
    }

    void download(String u, String cd, String mime) {
        try {
            if (isProtected(u) || u.contains("googlevideo.com")) { toast("هالموقع ما يسمح بالتحميل"); return; }
            String name = URLUtil.guessFileName(u, cd, mime);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(u));
            String ck = CookieManager.getInstance().getCookie(u);
            if (ck != null) r.addRequestHeader("Cookie", ck);
            r.addRequestHeader("User-Agent", web.getSettings().getUserAgentString());
            if (web.getUrl() != null && web.getUrl().startsWith("http")) r.addRequestHeader("Referer", web.getUrl());
            r.setTitle(name);
            r.setDescription("متصفح مانع");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Mane3/" + name);
            if (mime != null) r.setMimeType(mime);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            toast("📥 بدا التحميل: " + name);
        } catch (Exception e) {
            toast("ما قدرت أحمّل: " + e.getMessage());
        }
    }

    void openDownloads() {
        try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); } catch (Exception e) { toast("افتح «الملفات» ← التنزيلات ← Mane3"); }
    }

    void openExternal(String u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(u), u.toLowerCase(Locale.ROOT).contains(".m3u8") ? "application/x-mpegURL" : "video/*");
            startActivity(Intent.createChooser(i, "افتح الفيديو في…"));
        } catch (Exception e) { toast("ما في برنامج فيديو"); }
    }

    // ---------- cast ----------
    void castDialog() {
        final List<String[]> l = mediaList();
        List<String> items = new ArrayList<>();
        items.add("📺 اعرض شاشة التلفون على التلفزيون (Smart View / Cast)");
        if (!l.isEmpty()) items.add("▶️ أرسل الفيديو لبرنامج يدعم Chromecast");
        items.add("❓ طريقة العرض");
        final String[] arr = items.toArray(new String[0]);
        new AlertDialog.Builder(BrowserActivity.this).setTitle("📺 التلفزيون").setItems(arr, new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
            String it = arr[w];
            if (it.startsWith("📺")) screenCast();
            else if (it.startsWith("▶️")) openExternal(l.get(0)[0]);
            else msg("📺 طريقة العرض",
                    "1. خل التلفون والتلفزيون على نفس الواي فاي.\n" +
                    "2. اضغط «اعرض شاشة التلفون» واختار التلفزيون.\n" +
                    "   (في سامسونج: نزّل لوحة الإشعارات واضغط Smart View)\n" +
                    "3. شغّل الفيديو واضغط ⛶ ← وضع السينما عشان يملا شاشة التلفزيون.\n\n" +
                    "إذا التلفزيون Chromecast أو Google TV: اختار «أرسل الفيديو» وافتحه ببرنامج مثل Web Video Caster.");
        }}).show();
    }

    void screenCast() {
        String[] actions = {"android.settings.CAST_SETTINGS", "android.settings.WIFI_DISPLAY_SETTINGS"};
        for (String a : actions) {
            try { startActivity(new Intent(a)); toast("اختار التلفزيون من القائمة"); return; } catch (Exception ignored) {}
        }
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.samsung.android.smartmirroring");
            if (i != null) { startActivity(i); return; }
        } catch (Exception ignored) {}
        msg("📺", "نزّل لوحة الإشعارات من فوق، واضغط Smart View أو «بث الشاشة» واختار التلفزيون.");
    }

    // ---------- menu ----------
    void menuDialog() {
        final String cur = web.getUrl() == null ? "" : web.getUrl();
        final boolean bm = cur.startsWith("http") && bmIndex(cur) >= 0;
        final List<String> items = new ArrayList<>(Arrays.asList(
            bm ? "☆ شيل من المفضلة" : "⭐ أضف للمفضلة",
            "⭐ المفضلة",
            "🕘 السجل",
            "⧉ التبويبات",
            "🖼️ نافذة صغيرة (الفيديو فوق البرامج)",
            "🎧 الصوت بالخلفية: " + (bgOn() ? "شغّال ✅" : "طافي"),
            "🖼️ نافذة صغيرة تلقائية لما تطلع: " + (pipOn() ? "شغّالة ✅" : "طافية"),
            isStealth() ? "🛡️ رجّع المنع العادي لهالموقع" : "🥷 الوضع الخفي لهالموقع (إذا قال طفّ المانع)",
            "🙈 فلتر المحتوى (الرقص وغيره)",
            "🔄 تحديث", "📤 شارك الرابط", "🔗 انسخ الرابط",
            desktop ? "📱 وضع الجوال" : "🖥️ وضع الكمبيوتر",
            "🔠 حجم الخط", "📂 التحميلات", "🌍 افتح في متصفح ثاني", "🛡️ المانع والإعدادات", "🧹 امسح الكوكيز والكاش"));
        new AlertDialog.Builder(BrowserActivity.this).setItems(items.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
            String it = items.get(w);
            android.content.SharedPreferences bp = getSharedPreferences("b", 0);
            if (it.startsWith("⭐ أضف") || it.startsWith("☆")) toggleBookmark();
            else if (it.startsWith("⭐ المفضلة")) bookmarksDialog(false);
            else if (it.startsWith("🕘")) historyDialog();
            else if (it.startsWith("⧉")) tabsDialog();
            else if (it.startsWith("🖼️ نافذة صغيرة (")) { if (playing || customView != null || zoomMode > 0) enterPip(); else toast("شغّل الفيديو أول"); }
            else if (it.startsWith("🎧")) { bp.edit().putBoolean("bg", !bgOn()).apply(); toast(bgOn() ? "🎧 الصوت يكمّل لما تطلع" : "الصوت يوقف لما تطلع"); web.reload(); }
            else if (it.startsWith("🖼️ نافذة صغيرة تلقائية")) { bp.edit().putBoolean("pip", !pipOn()).apply(); toast(pipOn() ? "🖼️ إذا طلعت والفيديو شغّال يصير نافذة صغيرة" : "انطفت النافذة التلقائية"); }
            else if (it.startsWith("🥷") || it.startsWith("🛡️ رجّع")) toggleStealth();
            else if (it.startsWith("🙈")) wordFilterDialog();
            else if (it.startsWith("🔄")) web.reload();
            else if (it.startsWith("📤")) share(cur);
            else if (it.startsWith("🔗")) copy(cur);
            else if (it.startsWith("📱") || it.startsWith("🖥️")) { desktop = !desktop; web.getSettings().setUserAgentString(desktop ? DESKTOP_UA : defaultUA); web.reload(); }
            else if (it.startsWith("🔠")) textSizeDialog();
            else if (it.startsWith("📂")) openDownloads();
            else if (it.startsWith("🌍")) { try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(cur)), "افتح في…")); } catch (Exception ignored) {} }
            else if (it.startsWith("🛡️ المانع")) startActivity(new Intent(BrowserActivity.this, MainActivity.class));
            else if (it.startsWith("🧹")) {
                web.clearCache(true);
                CookieManager.getInstance().removeAllCookies(null);
                WebStorage.getInstance().deleteAllData();
                toast("انمسح");
            }
        }}).show();
    }

    // ---------- inject our script into player iframes (they're on other sites) ----------
    static final String FRAME_PRE = "window.__frame=1;(function(){var f=function(){return{closed:false,close:function(){},focus:function(){},blur:function(){},postMessage:function(){},"
        + "document:{write:function(){},writeln:function(){},close:function(){},open:function(){}},location:{href:'',replace:function(){},assign:function(){}}}};"
        + "try{Object.defineProperty(window,'open',{value:f,writable:false,configurable:false})}catch(e){window.open=f}})();";

    static boolean skipFrame(String u) {
        String l = u.toLowerCase(Locale.ROOT);
        return l.contains("recaptcha") || l.contains("hcaptcha") || l.contains("challenges.cloudflare") || l.contains("turnstile")
            || l.contains("accounts.google") || l.contains("paypal") || l.contains("stripe") || l.contains("checkout")
            || l.contains("knet") || l.contains("myfatoorah") || l.contains("tap.company") || l.contains("login") || l.contains("signin")
            || l.contains("facebook.com/plugins") || l.contains("disqus");
    }

    WebResourceResponse frame(String url, Map<String, String> reqHeaders) {
        java.net.HttpURLConnection c = null;
        try {
            String cur = url;
            int code = 0;
            Map<String, List<String>> hf = null;
            for (int hop = 0; hop < 6; hop++) {
                c = (java.net.HttpURLConnection) new java.net.URL(cur).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                if (reqHeaders != null) for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                    String k = e.getKey();
                    if (k == null || k.equalsIgnoreCase("Accept-Encoding") || k.equalsIgnoreCase("Cookie")) continue;
                    c.setRequestProperty(k, e.getValue());
                }
                if (reqHeaders == null || (!reqHeaders.containsKey("Accept") && !reqHeaders.containsKey("accept")))
                    c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                String ck = CookieManager.getInstance().getCookie(cur);
                if (ck != null) c.setRequestProperty("Cookie", ck);
                code = c.getResponseCode();
                hf = c.getHeaderFields();
                for (Map.Entry<String, List<String>> e : hf.entrySet())
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase("Set-Cookie"))
                        for (String v : e.getValue()) CookieManager.getInstance().setCookie(cur, v);
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    String loc = new java.net.URL(new java.net.URL(cur), c.getHeaderField("Location")).toString();
                    c.disconnect();
                    String lh = Uri.parse(loc).getHost();
                    if (lh != null && blockedHost(lh)) return stubHtml();
                    cur = loc;
                    continue;
                }
                break;
            }
            if (code < 200 || code >= 300) { c.disconnect(); return null; }
            String ct = c.getContentType();
            if (ct == null || !ct.toLowerCase(Locale.ROOT).contains("html")) { c.disconnect(); return null; }
            String cs = "UTF-8";
            int ci = ct.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (ci >= 0) cs = ct.substring(ci + 8).replace("\"", "").split(";")[0].trim();
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String html = bo.toString(cs);
            String tag = "<script>" + FRAME_PRE + pre(Uri.parse(cur).getHost()) + INJ + "</script>";
            if (!cur.equals(url) && !html.toLowerCase(Locale.ROOT).contains("<base")) tag = "<base href=\"" + cur.replace("\"", "%22") + "\">" + tag;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<head[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) html = html.substring(0, m.end()) + tag + html.substring(m.end());
            else {
                m = java.util.regex.Pattern.compile("<html[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
                html = m.find() ? html.substring(0, m.end()) + tag + html.substring(m.end()) : tag + html;
            }
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<String, List<String>> e : hf.entrySet()) {
                String k = e.getKey();
                if (k == null) continue;
                String kl = k.toLowerCase(Locale.ROOT);
                if (kl.equals("content-length") || kl.equals("content-encoding") || kl.equals("transfer-encoding") || kl.equals("set-cookie")
                    || kl.startsWith("content-security-policy") || kl.equals("content-type")) continue;
                out.put(k, android.text.TextUtils.join(", ", e.getValue()));
            }
            String reason = c.getResponseMessage();
            if (reason == null || reason.trim().isEmpty()) reason = "OK";
            return new WebResourceResponse("text/html", cs, code, reason, out, new ByteArrayInputStream(html.getBytes(cs)));
        } catch (Throwable t) {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
            return null;
        }
    }

    WebResourceResponse stubHtml() {
        return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream("<html><body></body></html>".getBytes()));
    }

    // ---------- popups & hijack redirects ----------
    static String site(String h) {
        h = baseHost(h);
        String[] p = h.split("\\.");
        if (p.length <= 2) return h;
        String sl = p[p.length - 2];
        boolean cc = p[p.length - 1].length() == 2 && (sl.equals("com") || sl.equals("net") || sl.equals("org") || sl.equals("co") || sl.equals("gov") || sl.equals("edu"));
        int keep = cc ? 3 : 2;
        StringBuilder b = new StringBuilder();
        for (int i = p.length - keep; i < p.length; i++) { if (b.length() > 0) b.append('.'); b.append(p[i]); }
        return b.toString();
    }

    boolean blockedHost(String h) {
        int c = Rules.check(h);
        return c == Rules.BLOCK_LIST || c == Rules.BLOCK_CUSTOM;
    }

    /** True if the tap the user just made was on a link to this url (or same site). */
    boolean tappedLink(String u) {
        try {
            WebView.HitTestResult h = web.getHitTestResult();
            String ex = h.getExtra();
            int t = h.getType();
            if (ex == null) return false;
            if (ex.equals(u)) return true;
            if (t == WebView.HitTestResult.SRC_ANCHOR_TYPE || t == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
                return site(Uri.parse(ex).getHost()).equals(site(Uri.parse(u).getHost()));
        } catch (Exception ignored) {}
        return false;
    }

    boolean guardNav(WebResourceRequest r, String u) {
        if (!r.isForMainFrame()) return false;
        if (u.equals(allowOnce)) { allowOnce = null; return false; }
        String host = Uri.parse(u).getHost();
        if (host == null) return false;
        if (blockedHost(host)) { blockPop("🚫 منعت تحويل لدعاية: " + baseHost(host), u); return true; }
        if (r.isRedirect() || pageHost.isEmpty()) return false;
        if (!site(host).equals(site(pageHost)) && !tappedLink(u)) {
            blockPop("🚫 الموقع حاول يحوّلك لـ " + baseHost(host), u);
            return true;
        }
        return false;
    }

    void onPopup(String u) {
        String host = Uri.parse(u).getHost();
        if (host != null && !blockedHost(host) && tappedLink(u) && !u.equals(web.getUrl())) {
            allowOnce = u;
            web.loadUrl(u);
            return;
        }
        blockPop("🚫 منعت نافذة دعاية" + (host == null ? "" : ": " + baseHost(host)), u);
    }

    void blockPop(final String text, final String u) {
        popsBlocked++;
        synchronized (recentBlocked) {
            recentBlocked.add(0, new String[]{text.replace("🚫 ", ""), u});
            while (recentBlocked.size() > 15) recentBlocked.remove(recentBlocked.size() - 1);
        }
        runOnUiThread(new Runnable() { public void run() { updateShield(); } });
    }

    void updateShield() {
        int n = popsBlocked + blockedHere + skippedHere;
        bShield.setText((isStealth() ? "🥷" : "🛡️") + (n > 0 ? String.valueOf(n) : ""));
    }

    void hls(String u) {
        if (isProtected(web.getUrl() == null ? "" : web.getUrl())) { toast("هالموقع ما يسمح بالتحميل"); return; }
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", web.getSettings().getUserAgentString());
        String ref = web.getUrl();
        if (ref != null && ref.startsWith("http")) { h.put("Referer", ref); h.put("Origin", Uri.parse(ref).getScheme() + "://" + Uri.parse(ref).getHost()); }
        String ck = CookieManager.getInstance().getCookie(u);
        if (ck != null) h.put("Cookie", ck);
        toast("📥 بدا التحميل • شوف الإشعارات");
        HlsDownloader.start(this, u, web.getTitle(), h, new HlsDownloader.Done() { public void on(final String m) {
            runOnUiThread(new Runnable() { public void run() { toast(m); } });
        }});
    }

    // ---------- video controls ----------
    void seek(int s) {
        web.evaluateJavascript("window.__cmd?__cmd('seek'," + s + "):0", new ValueCallback<String>() {
            public void onReceiveValue(String v) { if ("0".equals(v)) toast("ما في فيديو"); }
        });
    }

    void skipAd() {
        web.evaluateJavascript("window.__cmd?__cmd('skip',0):0", new ValueCallback<String>() {
            public void onReceiveValue(String v) { toast("1".equals(v) ? "⏭️ تخطّيت" : "2".equals(v) ? "⏭️ حاولت أتخطّى" : "ما في دعاية الحين"); }
        });
    }

    // ---------- anti-adblock ----------
    WebResourceResponse stub(Uri u, WebResourceRequest r) {
        String path = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
        String acc = r.getRequestHeaders() == null ? "" : String.valueOf(r.getRequestHeaders().get("Accept"));
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Cache-Control", "no-cache");
        String body = null, mime = "text/plain";
        if (path.endsWith("adsbygoogle.js") || path.contains("show_ads")) { body = STUB_ADSBYGOOGLE; mime = "application/javascript"; }
        else if (path.contains("gpt.js") || path.contains("pubads_impl") || path.contains("gpt/")) { body = STUB_GPT; mime = "application/javascript"; }
        else if (path.endsWith(".js") || acc.contains("javascript")) { body = "(function(){})();"; mime = "application/javascript"; }
        else if (path.matches(".*\\.(gif|png|jpe?g|webp)$") || acc.startsWith("image/")) {
            return new WebResourceResponse("image/gif", null, 200, "OK", h, new ByteArrayInputStream(GIF));
        }
        else if (path.endsWith(".css")) { body = ""; mime = "text/css"; }
        else if (acc.contains("json")) { body = "{}"; mime = "application/json"; }
        else body = "";
        try {
            return new WebResourceResponse(mime, "utf-8", 200, "OK", h, new ByteArrayInputStream(body.getBytes("UTF-8")));
        } catch (Exception e) { return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0])); }
    }

    long lastWall = 0;
    void onWall() {
        if (isStealth() || pageHost.isEmpty() || System.currentTimeMillis() - lastWall < 5000) return;
        lastWall = System.currentTimeMillis();
        setStealth(pageHost, true);
        updateShield();
        toast("🥷 الموقع كشف المانع • حوّلت للوضع الخفي");
        web.reload();
    }

    void toggleStealth() {
        if (pageHost.isEmpty()) return;
        boolean on = !isStealth();
        setStealth(pageHost, on);
        updateShield();
        toast(on ? "🥷 الوضع الخفي لـ " + pageHost : "🛡️ المنع العادي لـ " + pageHost);
        web.reload();
    }

    void shieldDialog() {
        final boolean st = isStealth();
        final List<String[]> rb;
        synchronized (recentBlocked) { rb = new ArrayList<>(recentBlocked); }
        final android.content.SharedPreferences sp = getSharedPreferences("zap", 0);
        final Set<String> zh = sp.getStringSet("p:" + pageHost, new HashSet<String>());
        final List<String> items = new ArrayList<>();
        items.add("🎯 امسح دعاية بإصبعك");
        items.add("🧹 شيل رسالة «انتباه» الحين");
        if (!rb.isEmpty()) items.add("📋 اللي انمنع (" + rb.size() + ")");
        items.add(st ? "🛡️ رجّع المنع العادي" : "🥷 الوضع الخفي (إذا قال طفّ المانع)");
        if (!zh.isEmpty()) items.add("↩️ رجّع اللي مسحته في هالموقع");
        String title = (pageHost.isEmpty() ? "المانع" : pageHost) + "\n🚫 " + (blockedHere + popsBlocked) + " انمنع  •  ⏭️ " + skippedHere + " دعاية فيديو";
        new AlertDialog.Builder(this).setTitle(title).setItems(items.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) {
                String it = items.get(w);
                if (it.startsWith("🎯")) startZap();
                else if (it.startsWith("🧹")) web.evaluateJavascript("window.__unwall?(__unwall()+(window.__lures?__lures():0)):0", new ValueCallback<String>() {
                        public void onReceiveValue(String v) { toast("0".equals(v) ? "ما لقيتها • جرّب 🎯 امسح دعاية بإصبعك" : "✅ انشالت"); } });
                else if (it.startsWith("📋")) blockedList(rb);
                else if (it.startsWith("↩️")) {
                    android.content.SharedPreferences.Editor e = sp.edit();
                    for (String hh : zh) e.remove("z:" + hh);
                    e.remove("p:" + pageHost).apply();
                    toast("رجّعت كل شي"); web.reload();
                }
                else toggleStealth();
            }
        }).show();
    }

    void startZap() {
        web.evaluateJavascript("window.__cmd?__cmd('zap',1):0", new ValueCallback<String>() {
            public void onReceiveValue(String v) {
                if ("0".equals(v) || "null".equals(v)) { toast("انتظر الصفحة تخلص تحميل"); return; }
                toast("🎯 اضغط على الدعاية اللي تبي تمسحها");
            }
        });
    }

    void blockedList(final List<String[]> rb) {
        String[] items = new String[rb.size()];
        for (int k = 0; k < rb.size(); k++) items[k] = rb.get(k)[0];
        new AlertDialog.Builder(this).setTitle("اضغط على اللي تبي تفتحه").setItems(items, new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) { allowOnce = rb.get(w)[1]; web.loadUrl(rb.get(w)[1]); } })
            .setNegativeButton("إلغاء", null).show();
    }

    TextView addPanel(String t, View.OnClickListener l) {
        TextView v = btn(t, l);
        v.setTextSize(14);
        v.setSingleLine(true);
        v.setPadding(px(2), 0, px(2), 0);
        vbtns.addView(v, new LinearLayout.LayoutParams(0, px(42), 1));
        return v;
    }

    // ---------- helpers ----------
    void share(String u) {
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, u), "شارك"));
    }

    void copy(String u) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("url", u));
        toast("انسخ");
    }

    void msg(String t, String m) { new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("تمام", null).show(); }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    int px(int d) { return (int) (d * dp); }

    TextView btn(String t, View.OnClickListener l) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(20); v.setTextColor(Color.parseColor("#E6EDF7"));
        v.setGravity(Gravity.CENTER); v.setPadding(px(10), 0, px(10), 0);
        v.setMinWidth(px(44)); v.setMinHeight(px(44));
        v.setOnClickListener(l);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        v.setBackgroundResource(tv.resourceId);
        return v;
    }

    TextView addBottom(String t, View.OnClickListener l) {
        TextView v = btn(t, l);
        v.setTextSize(17);
        v.setSingleLine(true);
        bottom.addView(v, new LinearLayout.LayoutParams(0, px(48), 1));
        return v;
    }

    TextView obtn(String t, View.OnClickListener l) {
        TextView v = btn(t, l);
        v.setTextSize(18);
        v.setTextColor(Color.WHITE);
        return v;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && i.getData() != null) newTab(i.getData().toString());
    }

    @Override
    public void onBackPressed() {
        if (customView != null || zoomMode > 0) { exitFull(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
    @Override protected void onPause() {
        super.onPause();
        persistTabs();
        boolean keep = (playing && bgOn()) || (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode());
        if (!keep) web.onPause();
    }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
