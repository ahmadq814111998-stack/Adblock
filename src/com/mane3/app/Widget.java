package com.mane3.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.widget.RemoteViews;

/** Home-screen widget: state + blocked today; tap to switch on / off. */
public class Widget extends AppWidgetProvider {
    static final String TOGGLE = "com.mane3.app.WIDGET_TOGGLE";

    static int id(Context c, String name, String type) { return c.getResources().getIdentifier(name, type, c.getPackageName()); }

    public static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
            if (ids.length == 0) return;
            RemoteViews v = new RemoteViews(c.getPackageName(), id(c, "widget", "layout"));
            boolean on = AdVpn.wanted;
            v.setTextViewText(id(c, "w_icon", "id"), on ? (AdVpn.pausedFor != null ? "⏸" : "🛡️") : "⛔");
            v.setTextViewText(id(c, "w_status", "id"), on ? (AdVpn.pausedFor != null ? "مانع موقف مؤقتاً" : "مانع شغّال") : "مانع طافي • اضغط");
            v.setTextColor(id(c, "w_status", "id"), on ? 0xFF4ADE80 : 0xFFF87171);
            v.setTextViewText(id(c, "w_count", "id"), "انمنع اليوم: " + Stats.today(c));
            Intent t = new Intent(c, Widget.class).setAction(TOGGLE);
            v.setOnClickPendingIntent(id(c, "w_root", "id"), PendingIntent.getBroadcast(c, 3, t, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            m.updateAppWidget(ids, v);
        } catch (Exception ignored) {}
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (!TOGGLE.equals(i.getAction())) return;
        if (AdVpn.wanted) {
            if (PinLock.has(c)) c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("askStop", true));
            else c.startService(new Intent(c, AdVpn.class).setAction(AdVpn.STOP));
        } else if (VpnService.prepare(c) == null) {
            Intent s = new Intent(c, AdVpn.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        } else c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("askStart", true));
    }
}
