package com.mane3.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

/** Shortcut target: pause the VPN first, then open the bank app so it never sees a VPN. */
public class LaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String pkg = getIntent().getStringExtra("pkg");
        if (pkg == null) { finish(); return; }
        final Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) { Toast.makeText(this, "البرنامج مو موجود", Toast.LENGTH_SHORT).show(); finish(); return; }
        long wait = 0;
        if (AdVpn.wanted) {
            startService(new Intent(this, AdVpn.class).setAction(AdVpn.PAUSE).putExtra("pkg", pkg));
            wait = 1300;
        }
        new Handler().postDelayed(new Runnable() { public void run() {
            try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
            finish();
            overridePendingTransition(0, 0);
        }}, wait);
    }
}
