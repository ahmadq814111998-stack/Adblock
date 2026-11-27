package com.mane3.app;
import java.io.*; import java.util.*;
public class TT { public static void main(String[] a) throws Exception {
  Rules.LongList l=new Rules.LongList();
  for(String f:a) Rules.addStream(new FileInputStream(f), l);
  long[] arr=Arrays.copyOf(l.a,l.n); Arrays.sort(arr); Rules.hashes=arr;
  for(String d:"googleads.g.doubleclick.net pagead2.googlesyndication.com admob.com google-analytics.com d.applovin.com ads.unity3d.com outcome-ssp.supersonicads.com api.vungle.com mintegral.com api-access.pangle.io an.facebook.com ads-api.twitter.com ads.yahoo.com aax.amazon-adsystem.com cdn.taboola.com widgets.outbrain.com settings.crashlytics.com app-measurement.com t.appsflyer.com app.adjust.com".split(" "))
    System.out.println(Rules.check(d)+" "+d);
}}
