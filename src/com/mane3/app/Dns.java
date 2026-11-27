package com.mane3.app;

/** Pure-Java IPv4/UDP/DNS helpers (no Android deps, unit-testable). */
public class Dns {
    public static class Query {
        public byte[] src = new byte[4], dst = new byte[4];
        public int sport, dport;
        public byte[] dns;
        public String name;
        public int qtype;
    }

    public static Query parse(byte[] p) {
        if (p.length < 28 || (p[0] >> 4 & 0xF) != 4) return null;
        int ihl = (p[0] & 0xF) * 4;
        if (p[9] != 17 || p.length < ihl + 8 + 12) return null;
        Query q = new Query();
        System.arraycopy(p, 12, q.src, 0, 4);
        System.arraycopy(p, 16, q.dst, 0, 4);
        q.sport = u16(p, ihl);
        q.dport = u16(p, ihl + 2);
        if (q.dport != 53) return null;
        int ulen = u16(p, ihl + 4);
        int dlen = Math.min(ulen - 8, p.length - ihl - 8);
        if (dlen < 12) return null;
        q.dns = new byte[dlen];
        System.arraycopy(p, ihl + 8, q.dns, 0, dlen);
        StringBuilder sb = new StringBuilder();
        int i = 12;
        while (i < dlen) {
            int l = q.dns[i] & 0xFF;
            if (l == 0) { i++; break; }
            if (l > 63 || i + 1 + l > dlen) return null;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(q.dns, i + 1, l, java.nio.charset.StandardCharsets.US_ASCII));
            i += 1 + l;
        }
        q.name = sb.toString();
        q.qtype = (i + 2 <= dlen) ? u16(q.dns, i) : 0;
        return q;
    }

    /** Answer with 0.0.0.0 for A, :: for AAAA, empty NOERROR otherwise. */
    public static byte[] blockedAnswer(byte[] dns) {
        int qend = 12;
        while (qend < dns.length && (dns[qend] & 0xFF) != 0) qend += 1 + (dns[qend] & 0xFF);
        qend += 5; // zero byte + qtype + qclass
        if (qend > dns.length) qend = dns.length;
        int qtype = qend >= 4 ? u16(dns, qend - 4) : 0;
        int alen = qtype == 1 ? 4 : qtype == 28 ? 16 : -1;
        byte[] r = new byte[qend + (alen > 0 ? 12 + alen : 0)];
        System.arraycopy(dns, 0, r, 0, qend);
        r[2] = (byte) (0x81 | (dns[2] & 0x01)); // QR=1, RD copied
        r[3] = (byte) 0x80;                     // RA=1, NOERROR
        r[4] = 0; r[5] = 1;
        r[6] = 0; r[7] = (byte) (alen > 0 ? 1 : 0);
        r[8] = r[9] = r[10] = r[11] = 0;
        if (alen > 0) {
            int o = qend;
            r[o] = (byte) 0xC0; r[o + 1] = 12;          // name pointer
            r[o + 2] = 0; r[o + 3] = (byte) qtype;     // type
            r[o + 4] = 0; r[o + 5] = 1;                // class IN
            r[o + 6] = 0; r[o + 7] = 0; r[o + 8] = 0; r[o + 9] = 60; // TTL
            r[o + 10] = 0; r[o + 11] = (byte) alen;    // rdlength, rdata = zeros
        }
        return r;
    }

    /** AdGuard answers blocked names with 0.0.0.0 / ::. */
    public static boolean looksBlocked(byte[] r) {
        if (r.length < 12 || u16(r, 6) == 0) return false;
        int i = 12;
        while (i < r.length && (r[i] & 0xFF) != 0) i += 1 + (r[i] & 0xFF);
        i += 5;
        if (i + 12 > r.length) return false;
        if ((r[i] & 0xC0) == 0xC0) i += 2; else return false;
        int type = u16(r, i), rdlen = u16(r, i + 8);
        int d = i + 10;
        if ((type != 1 && type != 28) || d + rdlen > r.length) return false;
        for (int k = 0; k < rdlen; k++) if (r[d + k] != 0) return false;
        return true;
    }

    /** Build IPv4/UDP packet back to the asking app. */
    public static byte[] wrap(Query q, byte[] dns) {
        int total = 28 + dns.length;
        byte[] p = new byte[total];
        p[0] = 0x45; p[2] = (byte) (total >> 8); p[3] = (byte) total;
        p[6] = 0x40; p[8] = 64; p[9] = 17;
        System.arraycopy(q.dst, 0, p, 12, 4);
        System.arraycopy(q.src, 0, p, 16, 4);
        int sum = 0;
        for (int i = 0; i < 20; i += 2) sum += u16(p, i);
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        sum = ~sum & 0xFFFF;
        p[10] = (byte) (sum >> 8); p[11] = (byte) sum;
        p[20] = (byte) (q.dport >> 8); p[21] = (byte) q.dport;
        p[22] = (byte) (q.sport >> 8); p[23] = (byte) q.sport;
        int ul = 8 + dns.length;
        p[24] = (byte) (ul >> 8); p[25] = (byte) ul;
        System.arraycopy(dns, 0, p, 28, dns.length);
        return p;
    }

    static int u16(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }
}
