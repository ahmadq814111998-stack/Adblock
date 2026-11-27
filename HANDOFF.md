# مانع (Mane3) — handoff

Android ad blocker + ad-free browser for the owner (Kuwait, Arabic RTL). Package `com.mane3.app`.
Two launcher icons: **مانع** (`MainActivity`, blocker) and **متصفح مانع** (`BrowserActivity`).

## Build
`./build.sh` → `out/Mane3.apk`. No Android SDK, no Gradle:
`javac --release 8` against `tools/android.jar` (API 28 stubs) → `dx` (from dex2jar 2.4) → `apktool b app` (manifest/res/assets) → zip `classes.dex` in → `uber-apk-signer` (debug key, same every build so updates install over each other).
All tools download from GitHub on first run.

Java rules (dx limits): **no lambdas, no method refs** (use anonymous classes); Java 8 APIs only; APIs newer than 28 via literals/reflection (e.g. `FLAG_MUTABLE = 0x02000000`, `requestAddTileService` via reflection). `targetSdkVersion 33` (avoid Android 14 foreground-service-type rules).

## Release (every version)
1. Bump `versionCode` / `versionName` in `app/apktool.yml`.
2. `./build.sh`
3. `cp out/Mane3.apk updates/Mane3.apk`, update `updates/version.json` (versionCode, versionName, notes in Arabic).
4. Add a line below + a line in `PROMPTS.md`, commit, `git -c push.negotiate=true push`.
5. Tell the owner to tap «حدّث الحين» (home banner or ⚙️ الإعدادات). The in-app updater (`Updater.java`) reads
   `https://raw.githubusercontent.com/ahmadq814111998-stack/adblock/main/updates/version.json` (repo is public).

## Architecture
- `AdVpn` (VpnService): DNS-only tunnel. Only packets to fake DNS `10.111.0.53` enter; everything else bypasses.
  Each query → `Rules.check()` (local lists) → blocked = 0.0.0.0 answer; else forwarded (protected UDP socket) to the chosen server
  (`SERVERS`, default AdGuard 94.140.14.14) with a 3-min cache. Since 5.7 our own package goes through the tunnel too (browser ad requests get 0.0.0.0 and really fail; only adsbygoogle/gpt get stub scripts, and everything gets stubs when the VPN is off/paused).
  Foreground notification. `wanted` = user switched on, `running` = tunnel up.
  **Pause for "VPN detected" apps** (banks): apps in pref `excluded` → watcher thread (UsageStats, needs Usage Access) closes the tunnel while they're in front.
  Banks check at launch, so `LaunchActivity` + pinned shortcut (⏸ badge) pauses first, then opens the app (`PAUSE` action, resumes when user leaves).
- `Rules`: 1.4M domains as sorted 64-bit FNV hashes (`rules.bin` cache), suffix match; custom allow/block sets. `Lists`: catalog (HaGeZi, OISD, AdAway, StevenBlack, 1Hosts, TIF, native trackers, gambling, NSFW) downloaded from GitHub raw, weekly refresh. `assets/base.txt.gz` = offline fallback.
- `Schedule`: lock-time groups (Instagram, TikTok, Snap, YouTube, X, Facebook, Netflix/Shahid, games) checked before Rules in `AdVpn.handle`.
- `Dns`: IPv4/UDP/DNS parse + build (pure Java, unit-testable).
- `MainActivity` = WebView UI `assets/index.html` + `Bridge` (@JavascriptInterface "M"). Tabs: الرئيسية / الأنواع / السجل / قوائمي / البرامج / ⚙️ الإعدادات.
- `BrowserActivity`: native WebView browser. Ad blocking via `shouldInterceptRequest` (Rules → stub JS/GIF responses so anti-adblock doesn't notice),
  popup/redirect guard (`onCreateWindow`, `guardNav`, silent; listed under 🛡️), **injects `assets/inject.js` into player iframes** (fetches iframe HTML itself, follows redirects, adds `<base>`),
  stealth mode 🥷 per site (auto when an "disable adblock" wall is detected), tap-to-zap 🎯 (selectors saved per host in prefs `zap`),
  word filter 🙈 (hides posts/videos whose text matches; default dance words), video panel (seek bar, swipe/double-tap, ±10/30, skip ad, cinema/zoom, speed, cast, download),
  resume position, real tabs (one WebView per tab in `webBox`, max 5 alive, oldest hidden ones freed; callbacks ignore non-current views except injection/history), bookmarks/history (prefs `b`), PiP + background audio.
- `inject.js`: runs in page + frames. Commands via `window.__cmd(cmd,arg)` relayed to iframes by postMessage (seek, seekto, pp, rate, skip, zoom, zap, hush). Heavy scans are throttled (MutationObserver + text-node search) — keep it that way (old full-DOM scans froze pages).
- Others: `Stats` (per-day + top domains), `PinLock` (SHA-256 PIN guards turning protection off), `Backup` (all prefs ⇄ JSON via SAF), `QsTile` (quick-settings button), `BootReceiver` (restart after boot/update), `Updater` + `InstallReceiver` (PackageInstaller session).

## Testing
Headless Chrome (`/opt/google/chrome/chrome`) + Playwright: `tests/web/*.html` are fixtures for inject.js (anti-adblock wall, lure dialogs incl. closed shadow DOM, hijack overlays, cross-origin player frame, word filter). Make a test video with `ffmpeg -f lavfi -i testsrc=duration=20:size=640x360:rate=25 -pix_fmt yuv420p v.mp4` and serve with `python3 -m http.server` on 8765 + 8766 (second origin for frames).
`tests/java`: Rules/Dns harnesses (run against real lists in a temp dir).

## Owner rules
- Reply in short Kuwaiti Arabic; he reads on his phone. Lead with what changed and how to use it.
- He wants every useful improvement done without asking each time.
- No red banners/popups for blocked things — silent, count on 🛡️.
- Don't touch his bank apps' data. Never add READ_SMS. Never store his personal files in the repo.
- Out of scope (declined): game speed hacks / bots (Survivor.io), downloading from YouTube/Netflix/Shahid.

## Versions
- 1.0 DNS VPN. 2.x lists, log, custom lists, apps, DNS choice, test card. 3.0 browser. 3.1 video panel + stealth. 3.2 popup/redirect guard. 3.3 iframe injection. 3.4–3.7 lure dialogs, zap. 3.5 seek bar/swipe, silent blocking. 3.6 double-tap, resume. 3.8–3.9 bank pause + safe shortcut. 4.0 clean mode. 4.1 performance (throttled scans, DNS cache). 4.2 word filter.
- 5.0 tabs/bookmarks/history, PiP, background audio, stats, PIN lock, backup, quick tile, boot start, updater.
- 5.1 updater points to this repo (`updates/`). First version that can update itself.
- 5.2 `Schedule` (lock app domain groups during set hours; VPN reconnects at start/end so open connections drop; log code `bt`), `HlsDownloader` (m3u8 → one .ts/.mp4 in Download/Mane3 via MediaStore; best variant; unencrypted VOD only; test: `tests/java/.../HlsTest.java` with an ffmpeg-made stream).
- 7.8 +14 servers (DNS4EU ×5, CIRA ×3, UltraDNS ×2 — 64.6.64.6 is alive under Vercara, Gcore, Quad101, Level3, HE); all added to BYPASS_DNS.
- 7.7 DNS servers: 25 entries with a 5th field kind (isp/ads/family/sec/plain); "isp" = underlying network DNS found via ConnectivityManager (`AdVpn.ispDns/resolved`); clean mode accepts any family server; more public DNS IPs in BYPASS_DNS.
- 7.6 per-site ⏸ off switch (`b` pref set `off`): no request handling at all for that page (no stubs, filters, frame injection), DNS open gate on, script runs with `__off` (no CSS/cleanup/walls/ad-skip; video tools stay). Frame `window.open` override is now a plain assignment (non-writable property could throw in players' strict-mode code).
- 7.5 fix: `backdrops()` removed the player's own "tap to play" cover (short text) → black player. Now only EMPTY, translucent layers without media/buttons/background image are removed (fixture `awplayer2.html`).
- 7.4 stealth "open gate": while a 🥷 page is in front (browser poll refreshes `AdVpn.dnsOpenUntil` +5s), the VPN answers every query via 1.1.1.1/8.8.8.8 without lists/cache (Schedule still applies; log code `ao`), and the browser lets ad requests load (hidden off-screen by stealth CSS, popups still blocked). Needed for players that only start after their ad loads. Blocked-answer TTL 60→10s.
- 7.3 stealth fixed for the VPN era: blocked hosts/filter hits get `stub()` (fake success) in stealth instead of going out to fail at DNS; walls detected inside player frames and as absolute boxes over the player (≤300 chars); frames report `MB.wall()` too; dark layers over the player removed in frames. Fixtures `awtop.html`/`awplayer.html`.
- 7.2 CRITICAL FIX: `Rules.clean` treated EasyList cosmetic lines `domain##sel` as "domain" (cut at #) → google.com, youtube.com, amazon… were DNS-blocked in 7.0/7.1. Now cosmetic/exception/option lines are rejected before comment stripping, and "#" glued to text is never a comment. Test: `GT.java`-style check that normal sites stay unblocked after compiling every default list.
- 7.1 review of our own hard-coded decisions: base list entries all confirmed by HaGeZi/OISD/StevenBlack/AdAway (kept); `isProtected` now by host (was substring "osn"); redirect guard skips `SAFE_NAV` (logins, payments, Kuwaiti banks, gov.kw); Verisign DNS removed from BYPASS_DNS; Schedule netflix group no longer blocks all of mbc.net; lure dialogs: «انتباه/تنبيه» only removed together with a lure word (fixture `tests/web/lure3.html`).
- 7.0 strongest blocking.
  Browser: `Filters.java` = EasyList-syntax engine (network rules indexed by token, `*`/`^`/`||`/`|` anchors, options: types, third-party, domain=, @@ exceptions, $document/$generichide/$elemhide page flags; regex & removeparam/csp/... skipped; procedural cosmetics skipped) + element hiding (generic in 150-selector chunks, per-host specific, #@# exceptions) passed as `window.__cos`. Lists cat "browser": EasyList, EasyPrivacy, Liste AR, AdGuard Mobile (on), AdGuard Base (off). Load ~0.3s/31MB on desktop JVM, ~15µs/request. Blocked requests get `stub()`. Generic cosmetics off in stealth mode. `inject()` sends a cheap `!!window.__m` check first (payload ~210KB).
  Device: public DNS IPs (`BYPASS_DNS`) routed into the tunnel (pref `strict`, default on): their UDP 53 is filtered like ours, TCP SYN gets RST (`Dns.tcpReset`) so DoH/DoT fail fast; CNAME uncloaking (`Dns.cnames` → Rules); HaGeZi DoH/VPN-bypass list (default on) and Ultimate (off). `Rules.clean` only accepts pure `||domain^` from adblock lists. `Lists.enabled` migrates new defaults once (`listsV7`); `Rules.load` runs `Lists.update(onlyMissing)` after an app update. Speed-test sockets are `AdVpn.protectSocket`ed.
- 6.8 ROOT CAUSE of PiP freeze: pseudo-fullscreen detection (6.3) saw the video filling the small window → requested landscape → config churn loop. Now `orient()` ignored in PiP, JS skips it while `__mpip` (cmd `pip`), heavy scans skipped in PiP; PiP aspect from `videoWidth/Height` (tall for Shorts/Reels).
- 6.7 PiP uses a native `VideoView` overlay (in BrowserActivity, so it works from onUserLeaveHint) when the video URL is known (`__vinfo().s` = http currentSrc, else last .m3u8 seen); web video paused; on return `stopNative(true)` seeks the page to the native position and plays; ✕ on the small window stops it; falls back to WebView PiP when no URL.
- 6.6 background resume limited to 2 per 30s per video (fighting the system caused stutter); pagehide/freeze no longer blocked; on return (`bgstate` 0) a stuck playing video gets nudged.
- 6.5 background play: visibility/blur/pagehide/freeze blocked on window capture (beats listeners added before our late injection); `bgstate` cmd marks background, and a site-initiated pause in background is undone unless the user touched the page or the app paused (`__mpaused`).
- 6.4 share the app: link (github raw redirect) or the APK itself (copied to Download/Mane3 via MediaStore); in settings, home and browser menu.
- 6.3 orientation: real fullscreen/cinema → SENSOR_LANDSCAPE, exit → PORTRAIT; pseudo-fullscreen (landscape video/iframe stretched over the screen) detected in scan → `MB.orient(big)` → landscape + hide bars; vertical videos ignored; player `dblclick` blocked over the video (stops players toggling fullscreen on double tap); iframes get `allowfullscreen`.
- 6.2 split: double-tap (`dtap`, default ON) and horizontal swipe (`gest`, default OFF) are separate menu toggles. Owner wants double-tap, not swipe.
- 6.1 horizontal swipe-seek and double-tap are OFF by default (`gest` pref → `window.__gest`, menu toggle, `__cmd('gest')`); vertical brightness/volume in cinema stays.
- 6.0 cinema/fullscreen controls (top overlay + bottom seek bar) hidden by default; `dispatchTouchEvent` ACTION_DOWN shows them, `hideCtl` hides after 3.5s (kept while dragging the seek bar).
- 5.9 speed test: warm-up + best of 6, all servers in parallel; colours + ⭐; sheet offers fastest ad-blocking and fastest overall; servers() has `ads`; srvPick refuses non-family while clean mode is on.
- 5.8 base list += the 126 domains of adblock.turtlecute.org (Turtlecute33/toolz `src/data/adblock_data.json`); in-app test uses the same list, failures first with «امنعه»; `Rules.load` recompiles in background when `rulesVer` ≠ versionCode.
- 5.7 browser no longer bypasses the VPN → real blocking (turtlecute test showed 0% because stubs look like success); live 🛡️ counter.
- 5.6 next episode (inject.js `epNum/nextEpLink/offerNext`; frame `ended` → `__mend` → top; 6s countdown; skip videos <3 min / ads; menu + `autonext` pref), lock via BiometricPrompt (`PinLock.ask` → fingerprint, PIN fallback, `grant()` 90s so setPin works after unlock; JS `askPin` now calls native `M.askLock`), browser lock cover (`lock` pref), DNS speed test (`Bridge.speedTest`, raw UDP query), wipe history/cookies/tabs on exit (`wipe`).
- 5.5 home-screen `Widget` (layout `res/layout/widget.xml`, ids via getIdentifier, toggle via explicit broadcast), `AdVpn.chores()` every ~10 min: update notification (12h) + weekly report; browser: pin site to home screen, reader mode (`__cmd('reader')`, `__readText`), read aloud (TextToSpeech, Arabic auto), search engine choice, data saver (block images), desktop mode remembered per host (`desk:<host>`). POST_NOTIFICATIONS requested on 13+.
- 5.4 browser: dark mode (CSS invert, `__dark`), find bar, translate (Google proxy), print/PDF, incognito tabs (no history/persist), sleep timer (minutes or end of video), skip intro per host (`intro:<host>`, JS on `playing`), vertical swipe in cinema = brightness (left) / volume (right) via `MB.bright/vol` (`__cine` flag), speed saved per host (`rate:<host>`), cookie-banner / open-in-app CSS.
- 5.3 real tabs: each tab keeps its own WebView + back history (`activate()`, `createWeb()`, `setupWeb(wv)`).
