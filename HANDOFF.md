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
  (`SERVERS`, default AdGuard 94.140.14.14) with a 3-min cache. Own package always disallowed (so browser/updater bypass the VPN).
  Foreground notification. `wanted` = user switched on, `running` = tunnel up.
  **Pause for "VPN detected" apps** (banks): apps in pref `excluded` → watcher thread (UsageStats, needs Usage Access) closes the tunnel while they're in front.
  Banks check at launch, so `LaunchActivity` + pinned shortcut (⏸ badge) pauses first, then opens the app (`PAUSE` action, resumes when user leaves).
- `Rules`: 1.4M domains as sorted 64-bit FNV hashes (`rules.bin` cache), suffix match; custom allow/block sets. `Lists`: catalog (HaGeZi, OISD, AdAway, StevenBlack, 1Hosts, TIF, native trackers, gambling, NSFW) downloaded from GitHub raw, weekly refresh. `assets/base.txt.gz` = offline fallback.
- `Dns`: IPv4/UDP/DNS parse + build (pure Java, unit-testable).
- `MainActivity` = WebView UI `assets/index.html` + `Bridge` (@JavascriptInterface "M"). Tabs: الرئيسية / الأنواع / السجل / قوائمي / البرامج / ⚙️ الإعدادات.
- `BrowserActivity`: native WebView browser. Ad blocking via `shouldInterceptRequest` (Rules → stub JS/GIF responses so anti-adblock doesn't notice),
  popup/redirect guard (`onCreateWindow`, `guardNav`, silent; listed under 🛡️), **injects `assets/inject.js` into player iframes** (fetches iframe HTML itself, follows redirects, adds `<base>`),
  stealth mode 🥷 per site (auto when an "disable adblock" wall is detected), tap-to-zap 🎯 (selectors saved per host in prefs `zap`),
  word filter 🙈 (hides posts/videos whose text matches; default dance words), video panel (seek bar, swipe/double-tap, ±10/30, skip ad, cinema/zoom, speed, cast, download),
  resume position, tabs (URL-only), bookmarks/history (prefs `b`), PiP + background audio.
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
