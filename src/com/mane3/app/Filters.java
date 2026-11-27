package com.mane3.app;

import android.content.Context;

import java.io.*;
import java.util.*;

/**
 * Browser filter engine for EasyList-style lists (network rules + element hiding), like a small uBlock.
 * Network rules are indexed by a token so each request only checks a handful of rules.
 */
public class Filters {
    // request types
    public static final int T_OTHER = 1, T_SCRIPT = 2, T_IMAGE = 4, T_CSS = 8, T_XHR = 16, T_FRAME = 32, T_MEDIA = 64, T_FONT = 128, T_DOC = 256, T_PING = 512, T_WS = 1024;
    static final int T_ALL = 0xFFFF & ~T_DOC;

    static class Rule {
        String pat;          // pattern without anchors
        boolean host;        // ||  hostname anchor
        boolean start, end;  // |   anchors
        int types = T_ALL;
        int party;           // 0 any, 1 third-party only, 2 first-party only
        String[] incDom, excDom;
        boolean docEx, genHide, elemHide;   // exception flags ($document / $generichide / $elemhide)
    }

    static final HashMap<String, ArrayList<Rule>> block = new HashMap<>(), allow = new HashMap<>();
    static final ArrayList<Rule> blockAny = new ArrayList<>(), allowAny = new ArrayList<>();
    static final LinkedHashSet<String> genericSel = new LinkedHashSet<>();
    static final HashMap<String, ArrayList<String>> specificSel = new HashMap<>();
    static final HashMap<String, HashSet<String>> exceptSel = new HashMap<>();
    static volatile boolean ready = false;
    static volatile int netCount = 0, cosCount = 0;
    static String genericCss = "";
    static final Set<String> COMMON = new HashSet<>(Arrays.asList("http", "https", "www", "com", "net", "org", "js", "html", "php", "cdn", "static", "img", "images"));

    // ---------- loading ----------
    public static void load(Context c) {
        ArrayList<File> fs = new ArrayList<>();
        Set<String> on = Lists.enabled(c);
        for (Lists.L x : Lists.ALL) {
            if (!"browser".equals(x.cat) || !on.contains(x.id)) continue;
            File f = Lists.file(c, x.id);
            if (f.exists()) fs.add(f);
        }
        loadFiles(fs);
    }

    public static synchronized void loadFiles(List<File> files) {
        ready = false;
        block.clear(); allow.clear(); blockAny.clear(); allowAny.clear();
        genericSel.clear(); specificSel.clear(); exceptSel.clear();
        int n = 0;
        for (File f : files) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 1 << 16)) {
                String line;
                while ((line = r.readLine()) != null) if (parse(line.trim())) n++;
            } catch (Exception ignored) {}
        }
        // generic CSS in chunks, so one selector the browser doesn't understand only drops its own chunk
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for (String s : genericSel) {
            sb.append(k == 0 ? "" : ",").append(s);
            if (++k == 150) { sb.append("{display:none!important}"); k = 0; }
        }
        if (k > 0) sb.append("{display:none!important}");
        genericCss = sb.toString();
        cosCount = genericSel.size() + specificSel.size();
        netCount = n;
        ready = true;
    }

    static final String[] BAD_COS = {":-abp", ":has-text", ":contains", ":xpath", ":upward", ":remove", ":style", ":matches-", ":min-text", ":watch-attr", ":nth-ancestor", ":others", ":if(", ":if-not", "+js(", ":matches-path", ":shadow"};

    static boolean parse(String l) {
        if (l.isEmpty() || l.charAt(0) == '!' || l.charAt(0) == '[') return false;
        int ci = l.indexOf("##"), ei = l.indexOf("#@#");
        if (l.contains("#?#") || l.contains("#$#") || l.contains("#%#") || l.contains("#@?#") || l.contains("#@$#") || l.contains("#+js")) return false;
        if (ei >= 0) {   // element-hiding exception
            String sel = l.substring(ei + 3).trim();
            String doms = l.substring(0, ei);
            if (doms.isEmpty()) { genericSel.remove(sel); return false; }
            for (String d : doms.split(",")) { d = d.trim().toLowerCase(Locale.ROOT); if (!d.isEmpty() && !d.startsWith("~")) { HashSet<String> h = exceptSel.get(d); if (h == null) exceptSel.put(d, h = new HashSet<>()); h.add(sel); } }
            return false;
        }
        if (ci >= 0) {   // element hiding
            String sel = l.substring(ci + 2).trim();
            if (sel.isEmpty() || sel.length() > 300) return false;
            String low = sel.toLowerCase(Locale.ROOT);
            for (String b : BAD_COS) if (low.contains(b)) return false;
            String doms = l.substring(0, ci).trim();
            if (doms.isEmpty()) { genericSel.add(sel); return false; }
            boolean any = false;
            for (String d : doms.split(",")) {
                d = d.trim().toLowerCase(Locale.ROOT);
                if (d.isEmpty()) continue;
                if (d.startsWith("~")) { HashSet<String> h = exceptSel.get(d.substring(1)); if (h == null) exceptSel.put(d.substring(1), h = new HashSet<>()); h.add(sel); continue; }
                ArrayList<String> a = specificSel.get(d); if (a == null) specificSel.put(d, a = new ArrayList<>()); a.add(sel); any = true;
            }
            if (!any) genericSel.add(sel);   // only negated domains -> generic with exceptions
            return false;
        }
        // ---------- network rule ----------
        boolean ex = l.startsWith("@@");
        if (ex) l = l.substring(2);
        if (l.startsWith("/") && l.endsWith("/") && l.length() > 2) return false;   // regex rules: skipped
        Rule r = new Rule();
        int di = l.lastIndexOf('$');
        if (di >= 0 && !(l.startsWith("/") && l.indexOf('/', 1) > di)) {
            String opts = l.substring(di + 1);
            l = l.substring(0, di);
            int inc = 0, exc = 0;
            for (String o : opts.split(",")) {
                o = o.trim().toLowerCase(Locale.ROOT);
                boolean neg = o.startsWith("~");
                String on = neg ? o.substring(1) : o;
                int t = typeOf(on);
                if (t != 0) { if (neg) exc |= t; else inc |= t; continue; }
                switch (on) {
                    case "third-party": case "3p": r.party = neg ? 2 : 1; break;
                    case "first-party": case "1p": r.party = neg ? 1 : 2; break;
                    case "important": case "match-case": case "all": case "popup": case "badfilter": break;
                    case "document": case "doc": if (ex) r.docEx = true; else inc |= T_DOC; break;
                    case "generichide": case "ghide": r.genHide = true; break;
                    case "elemhide": case "ehide": r.elemHide = true; break;
                    default:
                        if (on.startsWith("domain=") || on.startsWith("from=")) {
                            ArrayList<String> i = new ArrayList<>(), e = new ArrayList<>();
                            for (String d : on.substring(on.indexOf('=') + 1).split("\\|")) { if (d.startsWith("~")) e.add(d.substring(1)); else if (!d.isEmpty()) i.add(d); }
                            if (!i.isEmpty()) r.incDom = i.toArray(new String[0]);
                            if (!e.isEmpty()) r.excDom = e.toArray(new String[0]);
                        } else if (on.startsWith("redirect") || on.startsWith("rewrite")) {
                            // still a block
                        } else return false;   // removeparam, csp, header, replace, … : not supported -> skip the rule
                }
            }
            if (opts.contains("badfilter")) return false;
            if (inc != 0) r.types = inc; else if (exc != 0) r.types = T_ALL & ~exc;
            if ((r.genHide || r.elemHide || r.docEx) && inc == 0) r.types = 0;
        }
        if (l.startsWith("||")) { r.host = true; l = l.substring(2); }
        else if (l.startsWith("|")) { r.start = true; l = l.substring(1); }
        if (l.endsWith("|")) { r.end = true; l = l.substring(0, l.length() - 1); }
        l = l.toLowerCase(Locale.ROOT);
        while (l.startsWith("*")) { l = l.substring(1); r.host = false; r.start = false; }
        while (l.endsWith("*")) l = l.substring(0, l.length() - 1);
        if (l.isEmpty() && r.incDom == null && !r.docEx && !r.genHide && !r.elemHide) return false;
        if (l.length() < 3 && !r.host && r.incDom == null) return false;   // too broad
        r.pat = l;
        String tok = token(l, r.host || r.start);
        HashMap<String, ArrayList<Rule>> map = ex ? allow : block;
        if (tok == null) (ex ? allowAny : blockAny).add(r);
        else { ArrayList<Rule> a = map.get(tok); if (a == null) map.put(tok, a = new ArrayList<>(2)); a.add(r); }
        return true;
    }

    static int typeOf(String o) {
        switch (o) {
            case "script": return T_SCRIPT;
            case "image": return T_IMAGE;
            case "stylesheet": case "css": return T_CSS;
            case "xmlhttprequest": case "xhr": return T_XHR;
            case "subdocument": case "frame": return T_FRAME;
            case "media": return T_MEDIA;
            case "font": return T_FONT;
            case "ping": case "beacon": return T_PING;
            case "websocket": return T_WS;
            case "other": case "object": case "object-subrequest": return T_OTHER;
        }
        return 0;
    }

    static boolean tc(char c) { return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '%'; }

    /** Best token that is guaranteed to appear as a whole token in any URL the rule matches. */
    static String token(String p, boolean anchoredStart) {
        String best = null;
        int i = 0, n = p.length();
        while (i < n) {
            if (!tc(p.charAt(i))) { i++; continue; }
            int j = i;
            while (j < n && tc(p.charAt(j))) j++;
            boolean leftOk = i == 0 ? anchoredStart : p.charAt(i - 1) != '*';
            boolean rightOk = j < n && p.charAt(j) != '*';
            if (leftOk && rightOk && j - i >= 3) {
                String t = p.substring(i, j);
                if (!COMMON.contains(t) && (best == null || t.length() > best.length())) best = t;
            }
            i = j;
        }
        return best;
    }

    // ---------- matching ----------
    static boolean sepChar(char c) { return !(tc(c) || c == '_' || c == '-' || c == '.'); }

    /** Pattern with * and ^ against url from position ui. */
    static boolean m(String u, int ui, String p, int pi, boolean end) {
        while (pi < p.length()) {
            char pc = p.charAt(pi);
            if (pc == '*') {
                pi++;
                if (pi == p.length()) return true;
                for (int k = ui; k <= u.length(); k++) if (m(u, k, p, pi, end)) return true;
                return false;
            }
            if (pc == '^') {
                if (ui == u.length()) { pi++; continue; }   // ^ also matches the end
                if (!sepChar(u.charAt(ui))) return false;
                ui++; pi++; continue;
            }
            if (ui >= u.length() || u.charAt(ui) != pc) return false;
            ui++; pi++;
        }
        return !end || ui == u.length();
    }

    static boolean urlMatch(Rule r, String u, int hostStart, int hostEnd) {
        String p = r.pat;
        if (p.isEmpty()) return true;
        if (r.host) {
            // try every label start inside the hostname
            for (int i = hostStart; i < hostEnd; i++) {
                if (i == hostStart || u.charAt(i - 1) == '.') if (m(u, i, p, 0, r.end)) return true;
            }
            return false;
        }
        if (r.start) return m(u, 0, p, 0, r.end);
        // find literal prefix to jump quickly
        int lit = 0;
        while (lit < p.length() && p.charAt(lit) != '*' && p.charAt(lit) != '^') lit++;
        if (lit > 0) {
            String pre = p.substring(0, lit);
            for (int i = u.indexOf(pre); i >= 0; i = u.indexOf(pre, i + 1)) if (m(u, i, p, 0, r.end)) return true;
            return false;
        }
        for (int i = 0; i < u.length(); i++) if (m(u, i, p, 0, r.end)) return true;
        return false;
    }

    static boolean domIn(String host, String[] list) {
        for (String d : list) if (host.equals(d) || host.endsWith("." + d)) return true;
        return false;
    }

    static boolean optsMatch(Rule r, int type, boolean third, String pageHost) {
        if ((r.types & type) == 0) return false;
        if (r.party == 1 && !third) return false;
        if (r.party == 2 && third) return false;
        if (r.incDom != null && (pageHost == null || !domIn(pageHost, r.incDom))) return false;
        if (r.excDom != null && pageHost != null && domIn(pageHost, r.excDom)) return false;
        return true;
    }

    static boolean any(HashMap<String, ArrayList<Rule>> map, ArrayList<Rule> anyList, String u, List<String> toks, int hs, int he, int type, boolean third, String page) {
        for (String t : toks) {
            ArrayList<Rule> a = map.get(t);
            if (a == null) continue;
            for (Rule r : a) if (optsMatch(r, type, third, page) && urlMatch(r, u, hs, he)) return true;
        }
        for (Rule r : anyList) if (optsMatch(r, type, third, page) && urlMatch(r, u, hs, he)) return true;
        return false;
    }

    static List<String> tokens(String u) {
        ArrayList<String> out = new ArrayList<>();
        int i = 0, n = u.length();
        while (i < n) {
            if (!tc(u.charAt(i))) { i++; continue; }
            int j = i;
            while (j < n && tc(u.charAt(j))) j++;
            if (j - i >= 3) out.add(u.substring(i, j));
            i = j;
        }
        return out;
    }

    /** true = block this request. */
    public static boolean shouldBlock(String url, String host, String pageHost, int type) {
        if (!ready || url == null || host == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        int hs = u.indexOf("://"); hs = hs < 0 ? 0 : hs + 3;
        int he = hs; while (he < u.length() && u.charAt(he) != '/' && u.charAt(he) != ':' && u.charAt(he) != '?') he++;
        boolean third = pageHost != null && !pageHost.isEmpty() && !site(host).equals(site(pageHost));
        List<String> toks = tokens(u);
        if (!any(block, blockAny, u, toks, hs, he, type, third, pageHost)) return false;
        return !any(allow, allowAny, u, toks, hs, he, type, third, pageHost);
    }

    /** Page-level exceptions: 1 = whole page allowed ($document), 2 = no generic hiding, 4 = no hiding at all. */
    public static int pageFlags(String url, String host) {
        if (!ready || url == null || host == null) return 0;
        String u = url.toLowerCase(Locale.ROOT);
        int hs = u.indexOf("://"); hs = hs < 0 ? 0 : hs + 3;
        int he = hs; while (he < u.length() && u.charAt(he) != '/' && u.charAt(he) != ':' && u.charAt(he) != '?') he++;
        int f = 0;
        List<String> toks = tokens(u);
        ArrayList<Rule> cand = new ArrayList<>(allowAny);
        for (String t : toks) { ArrayList<Rule> a = allow.get(t); if (a != null) cand.addAll(a); }
        for (Rule r : cand) {
            if (!(r.docEx || r.genHide || r.elemHide)) continue;
            if (r.incDom != null && !domIn(host, r.incDom)) continue;
            if (!urlMatch(r, u, hs, he)) continue;
            if (r.docEx) f |= 1;
            if (r.genHide) f |= 2;
            if (r.elemHide) f |= 4;
        }
        return f;
    }

    static String site(String h) {
        h = h.toLowerCase(Locale.ROOT);
        String[] p = h.split("\\.");
        if (p.length <= 2) return h;
        String sl = p[p.length - 2];
        boolean cc = p[p.length - 1].length() == 2 && (sl.equals("com") || sl.equals("net") || sl.equals("org") || sl.equals("co") || sl.equals("gov") || sl.equals("edu"));
        int keep = cc ? 3 : 2;
        StringBuilder b = new StringBuilder();
        for (int i = p.length - keep; i < p.length; i++) { if (b.length() > 0) b.append('.'); b.append(p[i]); }
        return b.toString();
    }

    /** CSS for a page: generic (unless the site opts out) + site-specific, minus the site's exceptions. */
    public static String css(String host, int flags, boolean generic) {
        if (!ready || host == null || (flags & 5) != 0) return "";
        host = host.toLowerCase(Locale.ROOT);
        HashSet<String> exc = new HashSet<>();
        ArrayList<String> spec = new ArrayList<>();
        String h = host;
        while (true) {
            HashSet<String> e = exceptSel.get(h); if (e != null) exc.addAll(e);
            ArrayList<String> s = specificSel.get(h); if (s != null) spec.addAll(s);
            int dot = h.indexOf('.');
            if (dot < 0) break;
            h = h.substring(dot + 1);
        }
        StringBuilder sb = new StringBuilder();
        if (generic && (flags & 2) == 0) {
            if (exc.isEmpty()) sb.append(genericCss);
            else {
                int k = 0;
                for (String s : genericSel) {
                    if (exc.contains(s)) continue;
                    sb.append(k == 0 ? "" : ",").append(s);
                    if (++k == 150) { sb.append("{display:none!important}"); k = 0; }
                }
                if (k > 0) sb.append("{display:none!important}");
            }
        }
        int k = 0;
        for (String s : spec) {
            if (exc.contains(s)) continue;
            sb.append(k == 0 ? "" : ",").append(s);
            if (++k == 100) { sb.append("{display:none!important}"); k = 0; }
        }
        if (k > 0) sb.append("{display:none!important}");
        return sb.toString();
    }
}
