package com.mane3.app;
import java.io.*; import java.util.*;
/** Downloads an HLS stream with HlsDownloader.plan + fetch and joins it, like the app does. */
public class HlsTest { @SuppressWarnings("unchecked") public static void main(String[] a) throws Exception {
  Map<String,String> h=new HashMap<>(); h.put("User-Agent","test");
  Object[] p=HlsDownloader.plan(a[0],h); List<String> segs=(List<String>)p[1];
  System.out.println("init="+p[0]+" segments="+segs.size()+" first="+segs.get(0));
  try(OutputStream o=new FileOutputStream(a[1])){ if(p[0]!=null)o.write(HlsDownloader.fetch((String)p[0],h)); for(String s:segs)o.write(HlsDownloader.fetch(s,h)); }
}}
