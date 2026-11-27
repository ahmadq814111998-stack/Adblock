package com.mane3.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick-settings button: switch Mane3 on / off from the notification shade. */
public class QsTile extends TileService {
    public static void refresh(Context c) {
        try { TileService.requestListeningState(c, new ComponentName(c, QsTile.class)); } catch (Exception ignored) {}
    }

    @Override public void onStartListening() { update(); }

    void update() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(AdVpn.wanted ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(AdVpn.wanted ? (AdVpn.pausedFor != null ? "مانع ⏸" : "مانع") : "مانع (طافي)");
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (AdVpn.wanted) {
            if (PinLock.has(this)) {   // locked: ask in the app
                Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("askStop", true);
                startActivityAndCollapse(i);
                return;
            }
            startService(new Intent(this, AdVpn.class).setAction(AdVpn.STOP));
        } else if (VpnService.prepare(this) == null) {
            Intent s = new Intent(this, AdVpn.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        } else {
            startActivityAndCollapse(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("askStart", true));
        }
        new android.os.Handler().postDelayed(new Runnable() { public void run() { update(); } }, 600);
    }
}
