package com.mane3.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

/** Turns protection back on after a restart or an update, if it was on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!c.getSharedPreferences("m", 0).getBoolean("on", false)) return;
        if (VpnService.prepare(c) != null) return;
        Intent s = new Intent(c, AdVpn.class);
        try { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s); } catch (Exception ignored) {}
    }
}
