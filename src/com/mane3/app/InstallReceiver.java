package com.mane3.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(confirm); }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            Updater.state = "error";
            Toast.makeText(c, "ما تم التحديث: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), Toast.LENGTH_LONG).show();
        }
    }
}
