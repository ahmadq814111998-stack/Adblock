import com.mane3.app.Dns;
public class T { public static void main(String[] a) throws Exception {
  byte[] dns = java.util.Base64.getDecoder().decode(a[0]);
  byte[] p = new byte[28+dns.length]; p[0]=0x45; p[9]=17;
  p[12]=10;p[13]=111;p[14]=0;p[15]=1; p[16]=10;p[17]=111;p[18]=0;p[19]=53;
  p[20]=(byte)0xC3;p[21]=0x50; p[22]=0;p[23]=53; int ul=8+dns.length; p[24]=(byte)(ul>>8);p[25]=(byte)ul;
  System.arraycopy(dns,0,p,28,dns.length);
  Dns.Query q = Dns.parse(p);
  System.out.println(q.name+" type="+q.qtype+" local="+Dns.isLocallyBlocked(q.name));
  byte[] ans = Dns.blockedAnswer(q.dns);
  System.out.println("looksBlocked="+Dns.looksBlocked(ans));
  byte[] w = Dns.wrap(q, ans);
  System.out.println("sport="+(((w[20]&255)<<8)|(w[21]&255))+" dport="+(((w[22]&255)<<8)|(w[23]&255)));
  System.out.println(java.util.Base64.getEncoder().encodeToString(ans));
}}
