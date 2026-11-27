package com.mane3.app;
import java.io.*; import java.util.*;
public class NormalSitesTest { public static void main(String[] a) throws Exception {
  Rules.LongList l=new Rules.LongList(); for(String f:a) Rules.addStream(new FileInputStream(f), l);
  long[] arr=Arrays.copyOf(l.a,l.n); Arrays.sort(arr); Rules.hashes=arr; Rules.allow=new HashSet<>(); Rules.block=new HashSet<>();
  int bad=0; for(String d:new String[]{"www.google.com","google.com","m.youtube.com","www.facebook.com","www.kooora.com","web.topcinema.red","www.alanba.com.kw","github.com","www.amazon.com","x.com"}){ int c=Rules.check(d); if(c==1) bad++; System.out.println(c+" "+d);} System.out.println("normal sites blocked: "+bad+" | entries="+l.n);
}}
