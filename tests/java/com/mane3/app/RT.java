package com.mane3.app;
import java.io.*; import java.util.*;
public class RT { public static void main(String[] a) throws Exception {
  long t=System.currentTimeMillis(); Rules.LongList l=new Rules.LongList();
  for(String f:a) Rules.addStream(new FileInputStream(f), l);
  long[] arr=Arrays.copyOf(l.a,l.n); Arrays.sort(arr); int u=0;
  for(int k=0;k<arr.length;k++) if(k==0||arr[k]!=arr[k-1]) arr[u++]=arr[k];
  Rules.hashes=Arrays.copyOf(arr,u);
  System.out.println("raw="+l.n+" unique="+u+" ms="+(System.currentTimeMillis()-t)+" MB="+(u*8/1048576));
  for(String d:new String[]{"d.applovin.com","x.y.doubleclick.net","googleads.g.doubleclick.net","www.google.com","api.habby.mobi","google.com","youtube.com","graph.facebook.com","settings.crashlytics.com","app-measurement.com","wa.me","nbk.com","ads.unity3d.com","config.unityads.unity3d.com"})
    System.out.println(d+" -> "+Rules.check(d));
  Rules.allow=new HashSet<>(Arrays.asList("doubleclick.net"));
  System.out.println("allow doubleclick -> "+Rules.check("ad.doubleclick.net"));
  Rules.block=new HashSet<>(Arrays.asList("example.com")); Rules.allow=new HashSet<>();
  System.out.println("block example -> "+Rules.check("a.b.example.com.")+" clean: "+Rules.clean("0.0.0.0 Ads.X.com # c")+" "+Rules.clean("||foo.com^")+" "+Rules.clean("*.bar.org")+" "+Rules.clean("127.0.0.1 localhost"));
}}
