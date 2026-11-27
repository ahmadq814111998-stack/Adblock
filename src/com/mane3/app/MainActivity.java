package com.mane3.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0B1220"));
        getWindow().setNavigationBarColor(Color.parseColor("#0B1220"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B1220"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(this), "M");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
        handleExtras(getIntent());
    }

    void toggleVpn() {
        if (AdVpn.wanted) {
            startService(new Intent(this, AdVpn.class).setAction(AdVpn.STOP));
        } else {
            Intent i = VpnService.prepare(this);
            if (i != null) startActivityForResult(i, 7);
            else onActivityResult(7, RESULT_OK, null);
        }
    }

    void restartVpn() {
        if (AdVpn.wanted) startService(new Intent(this, AdVpn.class).setAction(AdVpn.RESTART));
    }

    void startVpnNow() {
        Intent i = VpnService.prepare(this);
        if (i != null) startActivityForResult(i, 7);
        else onActivityResult(7, RESULT_OK, null);
    }

    // ---------- backup files ----------
    void backupFile() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "mane3-backup-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".json");
        startActivityForResult(i, 21);
    }

    void restoreFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(i, 22);
    }

    void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); handleExtras(i); }

    void handleExtras(Intent i) {
        if (i == null) return;
        if (i.getBooleanExtra("askStart", false)) startVpnNow();
        if (i.getBooleanExtra("askStop", false))
            PinLock.ask(this, "إيقاف المانع", new Runnable() { public void run() { startService(new Intent(MainActivity.this, AdVpn.class).setAction(AdVpn.STOP)); } });
        i.removeExtra("askStart"); i.removeExtra("askStop");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 21 && res == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.OutputStream o = getContentResolver().openOutputStream(data.getData(), "wt")) {
                Stats.flush(this);
                o.write(Backup.export(this).getBytes("UTF-8"));
                toast("💾 انحفظت النسخة الاحتياطية");
            } catch (Exception e) { toast("ما قدرت أحفظ: " + e.getMessage()); }
            return;
        }
        if (req == 22 && res == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                Backup.restore(this, b.toString("UTF-8"));
                toast("✅ رجعت كل إعداداتك");
                new Thread(new Runnable() { public void run() { Lists.update(getApplicationContext(), true, null); } }).start();
                restartVpn();
                web.reload();
            } catch (Exception e) { toast("❌ هذا مو ملف نسخة احتياطية من مانع"); }
            return;
        }
        if (req == 7 && res == RESULT_OK) {
            Intent i = new Intent(this, AdVpn.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        }
    }

    void js(final String code) {
        runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(code, null); } });
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK) { web.evaluateJavascript("back()", null); return true; }
        return super.onKeyDown(k, e);
    }

    void exit() { runOnUiThread(new Runnable() { public void run() { moveTaskToBack(true); } }); }
}
