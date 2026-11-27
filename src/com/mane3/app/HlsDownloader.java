package com.mane3.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/** Downloads an HLS (m3u8) stream into one video file in Downloads/Mane3. Unencrypted streams only. */
public class HlsDownloader {
    public interface Done { void on(String msg); }

    static final Set<String> active = Collections.synchronizedSet(new HashSet<String>());

    public static void start(final Context ctx, final String url, final String title, final Map<String, String> headers, final Done done) {
        if (!active.add(url)) { done.on("هالفيديو يتحمّل الحين"); return; }
        new Thread(new Runnable() { public void run() {
            String msg;
            try { msg = HlsDownloader.run(ctx.getApplicationContext(), url, title, headers); }
            catch (Exception e) { msg = "❌ ما قدرت أحمّله: " + e.getMessage(); }
            active.remove(url);
            done.on(msg);
        }}, "hls").start();
    }

    static byte[] fetch(String u, Map<String, String> h) throws IOException {
        IOException last = null;
        for (int t = 0; t < 4; t++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                for (Map.Entry<String, String> e : h.entrySet()) if (e.getValue() != null) c.setRequestProperty(e.getKey(), e.getValue());
                if (c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
                try (InputStream in = new BufferedInputStream(c.getInputStream())) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                    return b.toByteArray();
                }
            } catch (IOException e) { last = e; try { Thread.sleep(800L * (t + 1)); } catch (InterruptedException ignored) {} }
            finally { if (c != null) c.disconnect(); }
        }
        throw last;
    }

    static String resolve(String base, String rel) throws IOException { return new URL(new URL(base), rel.trim()).toString(); }

    static String attr(String line, String key) {
        int i = line.indexOf(key + "=");
        if (i < 0) return null;
        String v = line.substring(i + key.length() + 1);
        if (v.startsWith("\"")) { int j = v.indexOf('"', 1); return j > 0 ? v.substring(1, j) : v.substring(1); }
        int j = v.indexOf(','); return j >= 0 ? v.substring(0, j) : v;
    }

    /** Reads the playlist: returns {init (or null), List<String> segments}. */
    static Object[] plan(String url, Map<String, String> h) throws Exception {

        String pl = new String(fetch(url, h), "UTF-8"), base = url;
        if (!pl.contains("#EXTM3U")) throw new IOException("مو رابط بث");
        // master playlist -> best quality
        if (pl.contains("#EXT-X-STREAM-INF")) {
            String[] ls = pl.split("\n");
            long best = -1; String pick = null;
            for (int i = 0; i < ls.length; i++) {
                if (ls[i].startsWith("#EXT-X-STREAM-INF")) {
                    String bw = attr(ls[i], "BANDWIDTH");
                    long b = 0; try { b = Long.parseLong(bw.trim()); } catch (Exception ignored) {}
                    for (int j = i + 1; j < ls.length; j++) {
                        String x = ls[j].trim();
                        if (x.isEmpty() || x.startsWith("#")) continue;
                        if (b > best) { best = b; pick = x; }
                        break;
                    }
                }
            }
            if (pick == null) throw new IOException("ما لقيت جودة");
            base = resolve(url, pick);
            pl = new String(fetch(base, h), "UTF-8");
        }
        if (pl.contains("METHOD=AES-128") || pl.contains("METHOD=SAMPLE-AES")) throw new IOException("الفيديو مشفّر ومحمي، ما ينحمل");
        String init = null;
        List<String> segs = new ArrayList<>();
        for (String raw : pl.split("\n")) {
            String x = raw.trim();
            if (x.startsWith("#EXT-X-MAP")) { String u = attr(x, "URI"); if (u != null) init = resolve(base, u); }
            else if (!x.isEmpty() && !x.startsWith("#")) segs.add(resolve(base, x));
        }
        if (segs.isEmpty()) throw new IOException("فاضي");
        if (!pl.contains("#EXT-X-ENDLIST")) throw new IOException("هذا بث مباشر، ما له نهاية");
        return new Object[]{init, segs};
    }

    @SuppressWarnings("unchecked")
    static String run(Context ctx, String url, String title, Map<String, String> h) throws Exception {
        Object[] pn = plan(url, h);
        String init = (String) pn[0];
        List<String> segs = (List<String>) pn[1];

        boolean mp4 = init != null || segs.get(0).toLowerCase(Locale.ROOT).matches(".*\\.(m4s|mp4)(\\?.*)?$");
        String name = (title == null || title.trim().isEmpty() ? "video" : title.trim()).replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").replaceAll("\\s+", " ");
        if (name.length() > 80) name = name.substring(0, 80);
        name = name + "-" + System.currentTimeMillis() % 100000 + (mp4 ? ".mp4" : ".ts");
        String mime = mp4 ? "video/mp4" : "video/mp2t";

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        int nid = 100 + (int) (System.currentTimeMillis() % 1000);
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("dl", "التحميلات", NotificationManager.IMPORTANCE_LOW));
            nb = new Notification.Builder(ctx, "dl");
        } else nb = new Notification.Builder(ctx);
        nb.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("📥 " + name).setOngoing(true).setOnlyAlertOnce(true);

        OutputStream out; Uri uri = null; File file = null;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put("_display_name", name); v.put("mime_type", mime);
            v.put("relative_path", "Download/Mane3"); v.put("is_pending", 1);
            uri = ctx.getContentResolver().insert(Uri.parse("content://media/external/downloads"), v);
            if (uri == null) throw new IOException("ما قدرت أسوي الملف");
            out = ctx.getContentResolver().openOutputStream(uri);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mane3");
            dir.mkdirs(); file = new File(dir, name);
            out = new FileOutputStream(file);
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            if (init != null) out.write(fetch(init, h));
            final Map<String, String> hh = h;
            int total = segs.size();
            for (int i = 0; i < total; i += 12) {
                List<Future<byte[]>> fs = new ArrayList<>();
                for (int j = i; j < Math.min(total, i + 12); j++) {
                    final String s = segs.get(j);
                    fs.add(pool.submit(new Callable<byte[]>() { public byte[] call() throws Exception { return fetch(s, hh); } }));
                }
                for (Future<byte[]> f : fs) out.write(f.get());
                int done = Math.min(total, i + 12);
                nb.setProgress(total, done, false).setContentText(done * 100 / total + "%");
                nm.notify(nid, nb.build());
            }
            out.close();
        } catch (Exception e) {
            try { out.close(); } catch (Exception ignored) {}
            if (uri != null) ctx.getContentResolver().delete(uri, null, null);
            if (file != null) file.delete();
            nm.cancel(nid);
            throw e;
        } finally { pool.shutdownNow(); }
        if (uri != null) { ContentValues v = new ContentValues(); v.put("is_pending", 0); ctx.getContentResolver().update(uri, v, null, null); }
        Uri open = uri != null ? uri : Uri.fromFile(file);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(open, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        nb.setOngoing(false).setProgress(0, 0, false).setSmallIcon(android.R.drawable.stat_sys_download_done)
          .setContentTitle("✅ خلص التحميل").setContentText(name).setAutoCancel(true);
        if (uri != null) nb.setContentIntent(PendingIntent.getActivity(ctx, nid, view, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        nm.notify(nid, nb.build());
        return "✅ خلص التحميل: التنزيلات ← Mane3";
    }
}
