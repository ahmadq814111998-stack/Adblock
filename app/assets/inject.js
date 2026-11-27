(function () {
  if (window.__m) return;
  window.__m = 1;
  window.__mrate = window.__mrate || 1;
  var STEALTH = !!window.__stealth;
  try {
    var _as = Element.prototype.attachShadow;
    if (_as && !_as.__m) {
      var nas = function (o) { var r = _as.call(this, Object.assign({}, o || {}, { mode: 'open' })); try { (window.__msh = window.__msh || []).push(this); } catch (e) {} return r; };
      nas.__m = 1;
      Element.prototype.attachShadow = nas;
    }
  } catch (e) {}
  if (window.__bg) {
    try {
      Object.defineProperty(document, 'hidden', { get: function () { return false; }, configurable: true });
      Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; }, configurable: true });
      Object.defineProperty(document, 'webkitHidden', { get: function () { return false; }, configurable: true });
      Object.defineProperty(document, 'webkitVisibilityState', { get: function () { return 'visible'; }, configurable: true });
      document.hasFocus = function () { return true; };
      // Capture on window runs before any listener on document, even ones the site added earlier.
      var stop = function (e) { e.stopImmediatePropagation(); };
      ['visibilitychange', 'webkitvisibilitychange', 'blur'].forEach(function (ev) {
        window.addEventListener(ev, stop, true);
        document.addEventListener(ev, stop, true);
      });
      // If the site still pauses the video right after the screen went off, play it again (only when it was playing).
      var wasPlaying = new WeakSet(), lastUser = 0;
      ['touchstart', 'mousedown', 'keydown'].forEach(function (ev) { window.addEventListener(ev, function () { lastUser = Date.now(); }, true); });
      document.addEventListener('play', function (e) { if (e.target && e.target.tagName) wasPlaying.add(e.target); }, true);
      document.addEventListener('pause', function (e) {
        var v = e.target;
        if (!v || !wasPlaying.has(v) || v.ended) return;
        if (Date.now() - lastUser < 1500) { wasPlaying.delete(v); return; }          // you paused it yourself
        if (window.__mpaused) { wasPlaying.delete(v); return; }                     // paused by the app (sleep timer, ⏯)
        if (!window.__mbg) return;                                                    // only while the app is in the background
        var now = Date.now();
        v.__mres = (v.__mres || []).filter(function (t) { return now - t < 30000; });
        if (v.__mres.length >= 2) return;                                             // the system keeps pausing it: stop fighting (that caused stutter)
        v.__mres.push(now);
        setTimeout(function () { try { if (v.paused) v.play(); } catch (x) {} }, 400);
      }, true);
    } catch (e) {}
  }
  var TOP = (function () { try { return window.top === window; } catch (e) { return false; } })();
  var B = window.MB || { media: function () {}, video: function () {}, wall: function () {}, skipped: function () {} };

  // ---------- ad hiding ----------
  // Real ad slots. In stealth mode they are moved off-screen (keep their size so detectors are happy).
  var REAL = "ins.adsbygoogle,[id^='div-gpt-ad'],[id^='google_ads_iframe'],iframe[id^='google_ads'],iframe[src*='doubleclick'],"
    + "iframe[src*='googlesyndication'],iframe[src*='amazon-adsystem'],[id^='taboola-'],.trc_rbox_container,.OUTBRAIN,"
    + "[data-ad-slot],[data-google-query-id],[aria-label='Advertisement'],[aria-label='إعلان'],.ytp-ad-overlay-container,"
    + "ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-ad-slot-renderer,ytm-promoted-sparkles-web-renderer,"
    + "ytm-companion-ad-renderer,.ytd-banner-promo-renderer";
  // Generic names that anti-adblock scripts use as bait: only hidden in normal mode.
  var GENERIC = ".ad-banner,.ad_banner,.banner-ad,.advertisement,.ads-container,.ad-slot,.ad-container,#ad-container,.sticky-ad,.adBanner";
  var css = STEALTH
    ? REAL + "{position:absolute!important;left:-10000px!important;top:-10000px!important;pointer-events:none!important}"
    : REAL + "," + GENERIC + "{display:none!important}";
  // cookie banners and "open in app" nags
  css += "#onetrust-banner-sdk,#onetrust-consent-sdk,#CybotCookiebotDialog,.qc-cmp2-container,#qc-cmp2-container,.cc-window,.cc-banner,#cookie-law-info-bar,"
    + "#cookieConsent,#cookie-consent,.cookie-consent,.cookie-banner,#cookie-banner,.cookie-notice,#cookie-notice,[aria-label='cookieconsent'],.fc-consent-root,"
    + "#didomi-host,.truste_box_overlay,#truste-consent-track,.osano-cm-window,#usercentrics-root,.smartbanner,#smartbanner,.smart-banner,"
    + ".app-banner,.open-in-app,.XPromoPopup,shreddit-async-loader[bundlename='app_selector'],xpromo-app-selector,.ytm-promoted-sparkles-web-renderer"
    + "{display:none!important}";
  if (window.__dark && TOP) css += "html{filter:invert(.92) hue-rotate(180deg)!important;background:#fff!important}"
    + "img,video,picture,canvas,iframe,svg image,[style*='background-image']{filter:invert(1) hue-rotate(180deg)!important}";
  if (window.__cos) try {
    var cs2 = document.createElement('style');
    cs2.id = '__mcos';
    cs2.textContent = window.__cos;
    (document.head || document.documentElement).appendChild(cs2);
  } catch (e) {}
  try {
    var st = document.createElement('style');
    st.textContent = css;
    (document.head || document.documentElement).appendChild(st);
  } catch (e) {}

  // ---------- user "zapped" elements for this site ----------
  var ZR = window.__zr || [];
  function applyZap() {
    if (!ZR.length) return;
    var z = document.getElementById('__mzap');
    if (!z) { z = document.createElement('style'); z.id = '__mzap'; (document.head || document.documentElement).appendChild(z); }
    z.textContent = ZR.join(',') + '{display:none!important;visibility:hidden!important}';
  }
  try { applyZap(); } catch (e) {}

  // ---------- tell the app about player iframes so it can inject into them ----------
  function reportFrame(f) {
    try {
      if (!f.hasAttribute('allowfullscreen')) f.setAttribute('allowfullscreen', '');
      var al = f.getAttribute('allow') || '';
      if (al.indexOf('fullscreen') < 0) f.setAttribute('allow', (al ? al + '; ' : '') + 'fullscreen; autoplay; picture-in-picture');
    } catch (e) {}
    try {
      var u = f.src;
      if (u && u.indexOf('http') === 0 && f.__mrep !== u) { f.__mrep = u; B.frame && B.frame(u); }
    } catch (e) {}
  }
  try {
    document.querySelectorAll('iframe').forEach(reportFrame);
    new MutationObserver(function (ms) {
      ms.forEach(function (m) {
        if (m.type === 'attributes' && m.target.tagName === 'IFRAME') reportFrame(m.target);
        m.addedNodes && m.addedNodes.forEach(function (nd) {
          if (nd.tagName === 'IFRAME') reportFrame(nd);
          else if (nd.querySelectorAll) nd.querySelectorAll('iframe').forEach(reportFrame);
        });
      });
    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] });
  } catch (e) {}

  // ---------- videos ----------
  function mainVideo() {
    var vs = [].slice.call(document.querySelectorAll('video'));
    if (!vs.length) return null;
    var playing = vs.filter(function (v) { return !v.paused && v.readyState > 1; });
    if (playing.length) return playing[0];
    return vs.sort(function (a, b) { return b.clientWidth * b.clientHeight - a.clientWidth * a.clientHeight; })[0];
  }
  window.__mv = mainVideo;
  window.__seek = function (s) {
    var v = mainVideo(); if (!v) return 0;
    var t = v.currentTime + s;
    if (isFinite(v.duration)) t = Math.min(Math.max(0, t), v.duration - 0.2);
    v.currentTime = Math.max(0, t);
    return 1;
  };
  window.__pp = function () {
    var v = mainVideo(); if (!v) return 0;
    window.__mpaused = true; setTimeout(function () { window.__mpaused = false; }, 1500);
    if (v.paused) v.play(); else v.pause();
    return 1;
  };

  // ---------- cinema / zoom (video here, or the player iframe from the top page) ----------
  function zoomLocal(mode) {
    var s = document.getElementById('__mz');
    document.querySelectorAll('[data-mz]').forEach(function (x) { x.removeAttribute('data-mz'); });
    if (!mode) { if (s) s.remove(); return 1; }
    var fit = mode >= 2 ? 'cover' : 'contain';
    var sc = mode == 3 ? 'scale(1.3)' : mode == 4 ? 'scale(1.6)' : 'none';
    var target = mainVideo(), isFrame = false;
    if (!target && TOP) {
      var fr = [].slice.call(document.querySelectorAll('iframe')).filter(function (f) { var r = f.getBoundingClientRect(); return r.width > 150 && r.height > 80; });
      fr.sort(function (a, b) { var x = a.getBoundingClientRect(), y = b.getBoundingClientRect(); return y.width * y.height - x.width * x.height; });
      target = fr[0]; isFrame = !!target;
    }
    if (!target) return 0;
    target.setAttribute('data-mz', '1');
    if (!s) { s = document.createElement('style'); s.id = '__mz'; document.documentElement.appendChild(s); }
    var base = 'position:fixed!important;inset:0!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483647!important;background:#000!important;border:0!important;margin:0!important;';
    s.textContent = (isFrame ? 'iframe[data-mz]{' + base + '}' : 'video[data-mz]{' + base + 'object-fit:' + fit + '!important;transform:' + sc + '!important}')
      + 'html,body{overflow:hidden!important}';
    return 1;
  }

  // ---------- zap mode: tap an ad to remove it for good ----------
  var zapOn = false, zapHi = null;
  function selOf(el) {
    function part(e) {
      var t = e.tagName.toLowerCase();
      if (e.id && !/\d{3,}|[A-Za-z0-9]{12,}/.test(e.id)) return t + '#' + CSS.escape(e.id);
      var cl = [].slice.call(e.classList || []).filter(function (c) { return !/\d{3,}/.test(c) && c.length < 40; }).slice(0, 3);
      return t + cl.map(function (c) { return '.' + CSS.escape(c); }).join('');
    }
    var p = [], e = el;
    for (var i = 0; e && e.nodeType === 1 && e !== document.body && e !== document.documentElement && i < 4; i++) {
      var s = part(e); p.unshift(s);
      if (s.indexOf('#') >= 0) break;
      e = e.parentElement;
    }
    return p.join(' > ');
  }
  function pick(el) {
    var t = el;
    for (var e = el; e && e !== document.body && e !== document.documentElement; e = e.parentElement) {
      var cs = getComputedStyle(e);
      if (cs.position === 'fixed' || cs.position === 'absolute' || cs.position === 'sticky') t = e;
    }
    if (t.querySelector && t.querySelector('video') && !el.closest('video')) t = el;
    return t;
  }
  function zapHandler(e) {
    if (!zapOn) return;
    e.preventDefault(); e.stopPropagation();
    var pt = e.changedTouches ? e.changedTouches[0] : e;
    var el = document.elementFromPoint(pt.clientX, pt.clientY);
    if (!el || el === document.body || el === document.documentElement || el.tagName === 'VIDEO') return;
    var t = pick(el), sel = selOf(t);
    t.remove();
    zapOn = false;
    try { B.zapped(location.hostname.replace(/^www\./, ''), sel); } catch (x) {}
  }
  ['touchend', 'click'].forEach(function (ev) { document.addEventListener(ev, zapHandler, true); });
  document.addEventListener('touchstart', function (e) { if (zapOn) { e.preventDefault(); e.stopPropagation(); } }, { capture: true, passive: false });

  // ---------- next episode ----------
  function epNum(t) {
    var m = String(t || '').replace(/[٠-٩]/g, function (d) { return '٠١٢٣٤٥٦٧٨٩'.indexOf(d); })
      .match(/(?:الحلقة|حلقة|episode|ep\.?|e)\s*(\d{1,4})(?!\d)/i);
    return m ? +m[1] : null;
  }
  function nextEpLink() {
    var h1 = document.querySelector('h1');
    var cur = epNum((h1 ? h1.innerText : '') + ' ' + document.title);
    if (cur == null) cur = epNum(decodeURIComponent(location.pathname).replace(/-/g, ' '));
    if (cur == null) return null;
    var found = null;
    document.querySelectorAll('a[href]').forEach(function (a) {
      if (found) return;
      var t = (a.innerText || a.getAttribute('title') || '').trim();
      if (!t || t.length > 90) return;
      var n = epNum(t);
      if (n === cur + 1 && a.href.indexOf('http') === 0 && a.href.split('#')[0] !== location.href.split('#')[0]) found = a;
    });
    return found;
  }
  var epBox = null, epTimer = null;
  function cancelNext() { clearInterval(epTimer); if (epBox) epBox.remove(); epBox = null; }
  function offerNext(auto) {
    var a = nextEpLink();
    if (!a) return 0;
    cancelNext();
    var left = 6;
    epBox = document.createElement('div');
    epBox.style.cssText = 'position:fixed;left:50%;bottom:90px;transform:translateX(-50%);z-index:2147483647;background:#0F172Aee;color:#fff;border:1px solid #334155;border-radius:16px;padding:14px 16px;font:600 15px system-ui,sans-serif;text-align:center;direction:rtl;min-width:240px';
    var label = (a.innerText || '').replace(/\s+/g, ' ').trim();
    epBox.innerHTML = '<div>⏭️ ' + label.replace(/[<>&]/g, '') + '</div><div id="__epc" style="color:#94A3B8;font-weight:400;margin:6px 0 10px"></div>';
    var go = document.createElement('button'), no = document.createElement('button');
    go.textContent = '▶ شغّلها الحين'; no.textContent = 'إلغاء';
    [go, no].forEach(function (b) { b.style.cssText = 'margin:0 4px;padding:8px 14px;border-radius:10px;border:0;font:600 14px system-ui;color:#fff;background:' + (b === go ? '#15803D' : '#334155'); epBox.appendChild(b); });
    go.onclick = function () { cancelNext(); location.href = a.href; };
    no.onclick = cancelNext;
    document.documentElement.appendChild(epBox);
    var c = epBox.querySelector('#__epc');
    if (!auto) { c.textContent = ''; return 1; }
    c.textContent = 'تبدا بعد ' + left;
    epTimer = setInterval(function () {
      left--;
      if (left <= 0) { cancelNext(); location.href = a.href; } else c.textContent = 'تبدا بعد ' + left;
    }, 1000);
    return 1;
  }
  function onEnded(v) {
    if (v && (isAd() || !isFinite(v.duration) || v.duration < 180)) return;
    if (TOP) { if (window.__autonext !== false) offerNext(true); }
    else try { parent.postMessage({ __mend: 1 }, '*'); } catch (e) {}
  }

  // ---------- reader mode / text for read-aloud ----------
  function article() {
    if (!document.body) return null;
    var best = null, score = new Map();
    document.querySelectorAll('p, pre, li, blockquote').forEach(function (p) {
      var t = (p.innerText || '').trim();
      if (t.length < 40) return;
      var a = p.parentElement, b = a && a.parentElement;
      if (a) score.set(a, (score.get(a) || 0) + t.length);
      if (b) score.set(b, (score.get(b) || 0) + t.length / 2);
    });
    var top = 0;
    score.forEach(function (v, el) { if (v > top) { top = v; best = el; } });
    var sem = document.querySelector('article, [itemprop="articleBody"], .entry-content, .post-content, .article-body, main');
    if (sem && (sem.innerText || '').length > top * 0.8) best = sem;
    return top > 400 || (best && (best.innerText || '').length > 400) ? best : null;
  }
  window.__readText = function () {
    var a = article(), h = document.querySelector('h1');
    return ((h ? h.innerText + '. ' : '') + (a ? a.innerText : (document.body ? document.body.innerText : ''))).slice(0, 30000);
  };
  function reader() {
    var a = article();
    if (!a) return 0;
    var old = document.getElementById('__mreader'); if (old) old.remove();
    var c = a.cloneNode(true);
    c.querySelectorAll('script,style,iframe,form,nav,aside,button,input,select,noscript,svg,video,[class*="share"],[class*="related"],[class*="comment"],[class*="ad-"],[id*="ad-"],ins').forEach(function (x) { x.remove(); });
    c.querySelectorAll('*').forEach(function (x) { x.removeAttribute('style'); x.removeAttribute('class'); x.removeAttribute('width'); x.removeAttribute('height'); });
    var h1 = document.querySelector('h1'), title = h1 ? h1.innerText : document.title;
    var w = document.createElement('div');
    w.id = '__mreader';
    w.setAttribute('dir', 'auto');
    w.style.cssText = 'position:fixed;inset:0;z-index:2147483647;overflow:auto;background:#111827;color:#E5E7EB;padding:24px 20px 80px;font:19px/1.9 system-ui,"Noto Naskh Arabic",sans-serif;-webkit-text-size-adjust:100%';
    var st = document.createElement('style');
    st.textContent = '#__mreader img{max-width:100%;height:auto;border-radius:10px;margin:10px 0}#__mreader a{color:#7DD3FC}#__mreader h1{font-size:26px;line-height:1.5;margin:8px 0 18px}#__mreader h2,#__mreader h3{font-size:21px;margin:22px 0 8px}#__mreader p{margin:0 0 16px}';
    var close = document.createElement('div');
    close.textContent = '✕ رجوع للصفحة';
    close.style.cssText = 'position:sticky;top:0;float:left;background:#1F2937;color:#fff;padding:8px 14px;border-radius:12px;font-size:15px;cursor:pointer';
    close.onclick = function () { w.remove(); document.documentElement.style.overflow = ''; };
    var h = document.createElement('h1'); h.textContent = title;
    w.appendChild(st); w.appendChild(close); w.appendChild(h); w.appendChild(c);
    document.documentElement.appendChild(w);
    document.documentElement.style.overflow = 'hidden';
    return 1;
  }

  // ---------- commands: run here, then pass down to every iframe ----------
  function local(cmd, a) {
    var v = mainVideo();
    if (cmd === 'seek') return window.__seek(a);
    if (cmd === 'seekto') {
      if (!v || !isFinite(v.duration)) return 0;
      v.currentTime = Math.min(Math.max(0, a), v.duration - 0.2);
      return 1;
    }
    if (cmd === 'pp') return window.__pp();
    if (cmd === 'rate') {
      window.__mrate = a;
      document.querySelectorAll('video,audio').forEach(function (x) { if (!isAd()) x.playbackRate = a; });
      return v ? 1 : 0;
    }
    if (cmd === 'skip') return window.__skipAd();
    if (cmd === 'zoom') return zoomLocal(a);
    if (cmd === 'zap') { zapOn = !!a; return 1; }
    if (cmd === 'reader') return TOP ? reader() : 0;
    if (cmd === 'nextep') return TOP ? offerNext(false) : 0;
    if (cmd === 'pip') { window.__mpip = !!a; if (!a) window.__mbig = null; return 1; }
    if (cmd === 'play') { var pv = mainVideo(); if (pv) { try { pv.play(); } catch (e) {} return 1; } return 0; }
    if (cmd === 'gest') { window.__gest = !!a; return 1; }
    if (cmd === 'dtap') { window.__dtap = !!a; return 1; }
    if (cmd === 'cine') { window.__cine = !!a; return 1; }
    if (cmd === 'pause') { window.__mpaused = true; document.querySelectorAll('video,audio').forEach(function (x) { try { x.pause(); } catch (e) {} }); setTimeout(function () { window.__mpaused = false; }, 1500); return 1; }
    if (cmd === 'bgstate') {
      window.__mbg = !!a;
      if (!a) {   // back in front: if a video that should be playing is stuck, nudge it so sound and picture sync again
        document.querySelectorAll('video').forEach(function (x) {
          if (x.paused || x.ended || isAd()) return;
          var t0 = x.currentTime;
          setTimeout(function () {
            if (!x.paused && Math.abs(x.currentTime - t0) < 0.05) { try { x.currentTime = t0 + 0.05; x.play(); } catch (e) {} }
          }, 900);
        });
      }
      return 1;
    }
    if (cmd === 'intro') { window.__intro = a; return 1; }
    if (cmd === 'hush') { document.querySelectorAll('video,audio').forEach(function (v) { try { v.pause(); v.muted = true; } catch (e) {} }); return 1; }
    return 0;
  }
  function relay(cmd, a) {
    var n = 0;
    document.querySelectorAll('iframe').forEach(function (f) { try { f.contentWindow.postMessage({ __mc: cmd, a: a }, '*'); n++; } catch (e) {} });
    return n;
  }
  window.__cmd = function (cmd, a) {
    var r = local(cmd, a) || 0;
    var n = relay(cmd, a);
    return r ? 1 : (n ? 2 : 0);
  };
  window.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || typeof d !== 'object') return;
    if (d.__mc) { local(d.__mc, d.a); relay(d.__mc, d.a); }
    if (d.__mend) { if (TOP) { if (window.__autonext !== false) offerNext(true); } else try { parent.postMessage({ __mend: 1 }, '*'); } catch (x) {} }
    if (d.__mi) { if (TOP) { window.__vi = d.__mi; window.__vit = Date.now(); } else try { parent.postMessage({ __mi: d.__mi }, '*'); } catch (x) {} }
    if (d.__mfv) { window.__fv = true; if (!TOP) try { parent.postMessage({ __mfv: 1 }, '*'); } catch (x) {} }
  });

  function hook() {
    document.querySelectorAll('video').forEach(function (v) {
      if (v.__mh) return;
      v.__mh = 1;
      v.addEventListener('play', function () { if (window.__mrate != 1 && !isAd()) v.playbackRate = window.__mrate; });
      v.addEventListener('ended', function () { onEnded(v); });
      v.addEventListener('playing', function () {
        var it = +window.__intro || 0;
        if (it > 0 && !v.__introDone && !isAd() && v.currentTime < 3 && isFinite(v.duration) && v.duration > it + 60) {
          v.__introDone = 1; v.currentTime = it;
          try { bubble('⏭️ تخطّيت المقدمة'); bubT = setTimeout(function () { if (bub) bub.style.display = 'none'; }, 1200); } catch (e) {}
        }
      });
    });
  }

  function isAd() {
    return !!document.querySelector('.ad-showing,.ad-interrupting,.videoAdUi,.ima-ad-container[style*="display: block"]');
  }

  // ---------- video time info (for the app's seek bar) ----------
  function info() {
    var v = mainVideo();
    if (v && isFinite(v.duration) && v.duration > 1 && !isAd()) return { t: v.currentTime, d: v.duration, p: v.paused ? 1 : 0, s: /^https?:/.test(v.currentSrc || '') ? v.currentSrc : '', w: v.videoWidth || 0, h: v.videoHeight || 0 };
    return null;
  }
  window.__vinfo = function () {
    var i = info();
    if (!i && window.__vi && Date.now() - (window.__vit || 0) < 2500) i = window.__vi;
    return i ? JSON.stringify(i) : '';
  };

  // ---------- swipe left / right on the video to seek ----------
  var sx = null, sy = 0, st = 0, sw = false, target = 0, bub = null, bubT = null;
  function bubble(txt) {
    var host = document.fullscreenElement || document.webkitFullscreenElement || document.body;
    if (!bub) {
      bub = document.createElement('div');
      bub.style.cssText = 'position:fixed;left:50%;top:40%;transform:translate(-50%,-50%);background:rgba(0,0,0,.75);color:#fff;'
        + 'font:700 22px system-ui,sans-serif;padding:12px 20px;border-radius:14px;z-index:2147483647;pointer-events:none;direction:ltr;white-space:nowrap';
    }
    if (bub.parentNode !== host) host.appendChild(bub);
    bub.textContent = txt; bub.style.display = 'block';
    clearTimeout(bubT);
  }
  function fmt(s) { s = Math.max(0, Math.floor(s)); var h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (x < 10 ? '0' : '') + x; }
  document.addEventListener('touchstart', function (e) {
    sx = null;
    var v = mainVideo();
    if (!v || !isFinite(v.duration) || v.duration < 5 || e.touches.length !== 1) return;
    var r = v.getBoundingClientRect(), t = e.touches[0];
    if (t.clientY < r.top || t.clientY > r.bottom - 40 || t.clientX < r.left || t.clientX > r.right) return;
    sx = t.clientX; sy = t.clientY; st = v.currentTime; sw = false;
  }, { passive: true, capture: true });
  var vw = false, vside = '', vy = 0;
  function cine() { return !!(window.__cine || document.fullscreenElement || document.webkitFullscreenElement); }
  document.addEventListener('touchmove', function (e) {
    if (sx === null) return;
    var t = e.touches[0], dx = t.clientX - sx, dy = t.clientY - sy;
    if (vw) {
      var dd = (vy - t.clientY) / innerHeight;
      if (Math.abs(dd) > 0.015) {
        vy = t.clientY;
        try { bubble(vside === 'b' ? B.bright(dd * 1.6) : B.vol(dd * 1.6)); } catch (x) {}
      }
      return;
    }
    if (!sw) {
      if (Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy) * 1.5) { if (!window.__gest) { sx = null; return; } sw = true; }
      else if (cine() && Math.abs(dy) > 24 && Math.abs(dy) > Math.abs(dx) * 1.5) { vw = true; vside = sx < innerWidth / 2 ? 'b' : 'v'; vy = t.clientY; return; }
      else { if (Math.abs(dy) > 30) sx = null; return; }
    }
    var v = mainVideo(); if (!v) return;
    var span = Math.min(900, Math.max(60, v.duration * 0.3));   // full-width swipe = 30% of the video (1..15 min)
    target = Math.min(Math.max(0, st + dx / innerWidth * span), v.duration - 0.5);
    var d = target - st;
    bubble((d >= 0 ? '⏩ +' : '⏪ -') + fmt(Math.abs(d)) + '   ' + fmt(target) + ' / ' + fmt(v.duration));
  }, { passive: true, capture: true });
  document.addEventListener('dblclick', function (e) {
    if (window.__dtap === false) return;
    var v = mainVideo();
    if (!v) return;
    var r = v.getBoundingClientRect();
    if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) { e.stopImmediatePropagation(); e.preventDefault(); }
  }, true);
  var lastTap = 0, lastX = 0, tapX = 0, tapY = 0;
  document.addEventListener('touchstart', function (e) { if (e.touches.length === 1) { tapX = e.touches[0].clientX; tapY = e.touches[0].clientY; } }, { passive: true, capture: true });
  document.addEventListener('touchend', function (e) {
    if (vw) { vw = false; sx = null; bubT = setTimeout(function () { if (bub) bub.style.display = 'none'; }, 700); return; }
    if (sx !== null && !sw) {
      var v = mainVideo(), now = Date.now();
      if (window.__dtap !== false && v && now - lastTap < 320 && Math.abs(tapX - lastX) < 60) {
        var r = v.getBoundingClientRect(), rel = (tapX - r.left) / r.width;
        if (rel < 0.33) { window.__seek(-10); bubble('⏪ 10'); }
        else if (rel > 0.67) { window.__seek(10); bubble('⏩ 10'); }
        else { if (v.paused) { v.play(); bubble('▶'); } else { v.pause(); bubble('⏸'); } }
        bubT = setTimeout(function () { if (bub) bub.style.display = 'none'; }, 600);
        lastTap = 0;
      } else { lastTap = now; lastX = tapX; }
    }
  }, { passive: true, capture: true });
  document.addEventListener('touchend', function () {
    if (sx !== null && sw) {
      var v = mainVideo();
      if (v) v.currentTime = target;
      bubT = setTimeout(function () { if (bub) bub.style.display = 'none'; }, 700);
    }
    sx = null; sw = false;
  }, { passive: true, capture: true });

  // ---------- auto skip video ads ----------
  var SKIP = ".ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytp-ad-skip-button-container button,"
    + ".videoAdUiSkipButton,.vast-skip-button,.jw-skip,.skip-ad-button,.skipButton,[class*='skip-ad'],[class*='skipAd'],[id*='skip-ad'],[id*='skipAd']";
  var lastSkip = 0;
  function skipAds(force) {
    var did = false;
    document.querySelectorAll(SKIP).forEach(function (b) {
      if (b.offsetParent !== null) { try { b.click(); did = true; } catch (e) {} }
    });
    document.querySelectorAll('.ytp-ad-overlay-close-button,.ytp-ad-overlay-close-container').forEach(function (b) { try { b.click(); } catch (e) {} });
    var adv = document.querySelector('.ad-showing video,.ad-interrupting video,.videoAdUi video');
    if (adv) {
      adv.muted = true;
      try { adv.playbackRate = 16; } catch (e) {}
      if (isFinite(adv.duration) && adv.duration > 0) { try { adv.currentTime = adv.duration - 0.1; } catch (e) {} }
      did = true;
    }
    if (did && Date.now() - lastSkip > 4000) { lastSkip = Date.now(); try { B.skipped(); } catch (e) {} }
    if (!adv) {
      var v = mainVideo();
      if (v && v.muted && v.__autoMuted) { v.muted = false; v.__autoMuted = false; }
    } else adv.__autoMuted = true;
    return did;
  }
  window.__skipAd = function () {
    var d = skipAds(true);
    if (!d) { var v = mainVideo(); if (v && v.duration && v.duration < 120 && v.currentTime < v.duration) { v.currentTime = v.duration - 0.1; d = true; } }
    return d ? 1 : 0;
  };

  // ---------- anti-adblock walls ----------
  var KEYS = ['ad blocker', 'adblocker', 'ad-blocker', 'adblock', 'ad block', 'disable your ad', 'turn off your ad', 'whitelist our', 'whitelist this',
    'allowlist', 'blocking ads', 'using an ad', 'مانع الإعلانات', 'مانع الاعلانات', 'مانع إعلانات', 'مانع اعلانات', 'حاجب الإعلانات', 'حاجب الاعلانات',
    'أداة حظر الإعلانات', 'اداة حظر الاعلانات', 'حظر الإعلانات', 'حظر الاعلانات', 'برنامج منع الإعلانات', 'adblocker detected', 'bloqueur de pub'];
  function hasKey(t) {
    t = (t || '').toLowerCase();
    for (var i = 0; i < KEYS.length; i++) if (t.indexOf(KEYS[i]) >= 0) return true;
    return false;
  }
  // ---------- cheap search: look at text nodes only, then climb to the floating box ----------
  function textHits(words) {
    var out = [], seen = new Set();
    function walk(root) {
      if (!root) return;
      var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null), nd, c = 0;
      while ((nd = w.nextNode()) && c < 20000) {
        c++;
        var v = nd.nodeValue;
        if (!v || v.length < 3) continue;
        v = v.toLowerCase();
        for (var i = 0; i < words.length; i++) if (v.indexOf(words[i]) >= 0) {
          var el = nd.parentElement || (nd.parentNode && nd.parentNode.host);
          if (el && !seen.has(el)) { seen.add(el); out.push(el); }
          break;
        }
      }
    }
    walk(document.body);
    (window.__msh || []).forEach(function (h) { if (h.isConnected && h.shadowRoot) walk(h.shadowRoot); });
    return out;
  }
  function up(el) { return el.parentElement || (el.parentNode && el.parentNode.host) || null; }
  function isFloat(cs) { return cs.position === 'fixed' || cs.position === 'absolute' || cs.position === 'sticky' || (parseInt(cs.zIndex) || 0) > 1000; }
  var dirty = true, lastHeavy = 0;
  try { new MutationObserver(function () { dirty = true; }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] }); } catch (e) {}

  function walls() {
    var out = [], W = innerWidth, H = innerHeight;
    textHits(KEYS).forEach(function (el) {
      for (var e = el, d = 0; e && e !== document.body && d < 12; e = up(e), d++) {
        if (e.nodeType !== 1) continue;
        var cs = getComputedStyle(e);
        if (cs.position !== 'fixed' && cs.position !== 'sticky' && e.getAttribute('role') !== 'dialog' && e.tagName !== 'DIALOG') continue;
        if (cs.display === 'none' || cs.visibility === 'hidden') continue;
        var r = e.getBoundingClientRect();
        if (r.width * r.height >= W * H * 0.12 && out.indexOf(e) < 0) { out.push(e); break; }
      }
    });
    return out;
  }
  function shallow() {
    var c = [];
    if (!document.body) return c;
    [].forEach.call(document.body.children, function (a) { c.push(a); [].forEach.call(a.children, function (b) { c.push(b); }); });
    return c;
  }
  function backdrops() {
    var out = [], W = innerWidth, H = innerHeight;
    shallow().forEach(function (e) {
      var cs = getComputedStyle(e);
      if (cs.position !== 'fixed') return;
      var r = e.getBoundingClientRect();
      if (r.width >= W * 0.9 && r.height >= H * 0.9 && (parseInt(cs.zIndex) || 0) > 10 && (e.innerText || '').trim().length < 40 && !e.querySelector('video')) out.push(e);
    });
    return out;
  }
  function unlock() {
    [document.documentElement, document.body].forEach(function (x) {
      if (!x) return;
      x.style.setProperty('overflow', 'auto', 'important');
      x.style.setProperty('position', 'static', 'important');
    });
    document.querySelectorAll('body > *').forEach(function (x) {
      var f = getComputedStyle(x).filter;
      if (f && f !== 'none' && f.indexOf('blur') >= 0) x.style.setProperty('filter', 'none', 'important');
    });
  }
  var reported = false, removed = 0;
  function checkWalls() {
    if (!TOP) return;
    var w = walls();
    if (!w.length) return;
    if (STEALTH) {
      w.forEach(function (e) { e.remove(); removed++; });
      backdrops().forEach(function (e) { e.remove(); });
      unlock();
    } else if (!reported) {
      reported = true;
      try { B.wall(); } catch (e) {}
    }
  }
  window.__unwall = function () { var w = walls(); w.forEach(function (e) { e.remove(); }); backdrops().forEach(function (e) { e.remove(); }); unlock(); return w.length; };

  // Neutralise common anti-adblock libraries (helps when they load late)
  if (STEALTH) {
    try {
      var Fake = function () {};
      Fake.prototype = { check: function () { return false; }, emitEvent: function () { return this; }, clearEvent: function () {}, on: function (d, f) { if (!d) try { f(); } catch (e) {} return this; },
        onDetected: function () { return this; }, onNotDetected: function (f) { try { f(); } catch (e) {} return this; }, setOption: function () { return this; } };
      ['FuckAdBlock', 'BlockAdBlock', 'SniffAdBlock'].forEach(function (n) { window[n] = Fake; });
      window.fuckAdBlock = window.blockAdBlock = window.sniffAdBlock = new Fake();
      window.canRunAds = true; window.isAdBlockActive = false; window.adblockDetected = false; window.adsBlocked = false;
    } catch (e) {}
  }

  // ---------- invisible click-hijack layers (tap anywhere -> ad) ----------
  function hijackers() {
    var W = innerWidth, H = innerHeight, n = 0;
    var cand = shallow().concat([].slice.call(document.querySelectorAll('body a, body ins, body iframe')));
    document.querySelectorAll('video').forEach(function (v) {
      for (var p = v.parentElement, d = 0; p && d < 3; p = p.parentElement, d++) [].forEach.call(p.children, function (c) { cand.push(c); });
    });
    cand.forEach(function (e) {
      if (!e.isConnected) return;
      var cs = getComputedStyle(e);
      if (cs.position !== 'fixed' && cs.position !== 'absolute') return;
      if (e.querySelector('video') || e.tagName === 'VIDEO') return;
      var r = e.getBoundingClientRect();
      if (r.width < W * 0.6 || r.height < H * 0.5) return;
      var z = parseInt(cs.zIndex) || 0;
      var op = parseFloat(cs.opacity);
      var bg = cs.backgroundColor;
      var clear = op < 0.15 || ((bg === 'transparent' || bg === 'rgba(0, 0, 0, 0)') && cs.backgroundImage === 'none');
      var txt = (e.innerText || '').trim().length;
      var linky = e.tagName === 'A' || (e.children.length === 1 && e.children[0].tagName === 'A') || (e.children.length === 0 && (e.hasAttribute('onclick') || cs.cursor === 'pointer'));
      if (e.tagName === 'IFRAME') {
        var src = e.getAttribute('src') || '';
        if (op < 0.15 && z > 100) { e.remove(); n++; }
        return;
      }
      if (clear && linky && txt < 3 && z >= 0) { e.remove(); n++; }
    });
    return n;
  }

  // ---------- fake "click to continue / allow / not a robot" ad dialogs ----------
  var LURE = ['انقر للمزيد', 'اضغط للمزيد', 'انقر للمتابعة', 'اضغط للمتابعة', 'انقر هنا للمتابعة', 'اضغط على سماح', 'انقر على سماح',
    'لست روبوت', 'انك لست روبوت', 'أنك لست روبوت', 'لست انسان آلي', 'not a robot', 'you are not a robot', 'are you a robot',
    'click allow', 'press allow', 'tap allow', 'click "allow"', 'to continue, click', 'click to continue', 'press "allow"',
    'your device may be infected', 'جهازك مصاب', 'تم اختراق', 'you won', 'لقد ربحت', 'مبروك لقد', 'تأكيد أنك', 'confirm you are'];
  var ATT = ['انتباه', 'تنبيه', 'attention', 'warning!', 'تحذير'];
  var BTN = ['نعم', 'يلغي', 'إلغاء', 'الغاء', 'أكثر', 'سماح', 'اسمح', 'موافق', 'allow', 'cancel', 'yes', 'more', 'continue'];
  function fullText(e) {
    var t = e.innerText || '';
    if (e.shadowRoot) t += ' ' + (e.shadowRoot.textContent || '');
    e.querySelectorAll && e.querySelectorAll('*').forEach(function (c) { if (c.shadowRoot) t += ' ' + (c.shadowRoot.textContent || ''); });
    return t.toLowerCase().replace(/\s+/g, ' ');
  }
  function isLure(t) {
    if (!t || t.length > 260) return false;
    for (var i = 0; i < LURE.length; i++) if (t.indexOf(LURE[i]) >= 0) return true;
    var a = false, b = 0;
    for (i = 0; i < ATT.length; i++) if (t.indexOf(ATT[i]) >= 0) { a = true; break; }
    if (!a) return false;
    for (i = 0; i < BTN.length; i++) if (t.indexOf(BTN[i]) >= 0) b++;
    return b >= 1;
  }
  var LWORDS = LURE.concat(ATT);
  function lures() {
    var n = 0;
    textHits(LWORDS).forEach(function (el) {
      var best = null;
      for (var e = el, d = 0; e && e !== document.body && e !== document.documentElement && d < 14; e = up(e), d++) {
        if (e.nodeType !== 1 || !e.isConnected) continue;
        var t = fullText(e);
        if (t.length > 260) break;
        if (e.querySelector && e.querySelector('video')) break;
        if (isFloat(getComputedStyle(e)) && isLure(t)) best = e;
      }
      if (!best) {   // inside a shadow root: remove the host if it floats
        var r = el.getRootNode && el.getRootNode();
        if (r && r.host && isLure(fullText(r.host)) && isFloat(getComputedStyle(r.host))) best = r.host;
      }
      if (best && best.isConnected) { best.remove(); n++; }
    });
    return n;
  }
  function frameLures() {
    var n = 0;
    document.querySelectorAll('iframe').forEach(function (f) {
      var d = null;
      try { d = f.contentDocument; } catch (e) {}
      if (!d || !d.body) return;
      if (d.querySelector('video')) return;
      if (isLure(fullText(d.body))) {
        var t = f;
        while (t.parentElement && t.parentElement !== document.body && t.parentElement.children.length === 1) {
          var cs = getComputedStyle(t.parentElement);
          t = t.parentElement;
          if (cs.position === 'fixed' || cs.position === 'absolute') break;
        }
        t.remove(); n++;
      }
    });
    return n;
  }
  window.__lures = function () { return lures() + frameLures(); };

  // ---------- word filter: hide posts / videos whose text has the user's words ----------
  var WF = (window.__wf || []).map(function (w) { return String(w).trim().toLowerCase(); }).filter(function (w) { return w.length > 1; });
  var WFRE = null;
  if (WF.length) {
    try {
      WFRE = new RegExp(WF.map(function (w) {
        var e = w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        return /^[a-z0-9]/.test(w) ? '(^|[^a-z0-9])' + e : e;     // latin words: match word start ("dance" not "guidance")
      }).join('|'), 'i');
    } catch (e) { WFRE = null; }
  }
  function wfHits() {
    var out = [], seen = new Set();
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null), nd, c = 0;
    while ((nd = w.nextNode()) && c < 30000) {
      c++;
      var v = nd.nodeValue;
      if (v && v.length > 2 && WFRE.test(v)) { var el = nd.parentElement; if (el && !seen.has(el)) { seen.add(el); out.push(el); } }
    }
    // alt / aria-label / title attributes (thumbnails, reels)
    document.querySelectorAll('[aria-label],[title],img[alt]').forEach(function (el) {
      var t = (el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.getAttribute('alt') || '');
      if (t.length > 2 && WFRE.test(t) && !seen.has(el)) { seen.add(el); out.push(el); }
    });
    return out;
  }
  function cardOf(el) {
    var H = innerHeight, W = innerWidth, best = null, media = /^(IMG|VIDEO|PICTURE|CANVAS)$/.test(el.tagName);
    for (var e = el, d = 0; e && e !== document.body && e !== document.documentElement && d < 12; e = e.parentElement, d++) {
      var r = e.getBoundingClientRect();
      if (r.height > H * 1.6 || (r.width > W * 1.05 && r.height > H)) break;
      if (!(media || e.querySelector('video,img,picture,canvas'))) continue;
      best = e;
      if (e === el) continue;
      if (media && (e.innerText || '').trim().length < 3) continue;   // climb from a thumbnail up to its card
      if (r.height > 140) break;
    }
    return best;
  }
  function silence(root) {
    (root.tagName === 'VIDEO' || root.tagName === 'AUDIO' ? [root] : [].slice.call(root.querySelectorAll('video,audio'))).forEach(function (v) {
      try { v.pause(); v.muted = true; v.removeAttribute('autoplay'); } catch (e) {}
    });
  }
  var wfPage = false;
  function wordFilter() {
    if (!WFRE || !document.body) return;
    wfHits().forEach(function (el) {
      var c = cardOf(el);
      if (c && !c.__wfh) { c.__wfh = 1; silence(c); c.style.setProperty('display', 'none', 'important'); }
    });
    if (TOP && !wfPage) {
      var h1 = document.querySelector('h1');
      var t = document.title + ' ' + (h1 ? h1.innerText : '') + ' ' + ((document.querySelector('meta[property="og:title"]') || {}).content || '');
      if (WFRE.test(t)) {
        wfPage = true;
        var b = document.createElement('div');
        b.style.cssText = 'position:fixed;inset:0;z-index:2147483646;background:#0B1220;color:#E6EDF7;display:flex;flex-direction:column;align-items:center;justify-content:center;font:700 20px system-ui,sans-serif;text-align:center;padding:30px';
        b.innerHTML = '<div style="font-size:60px">🙈</div><div>انحجب هالفيديو</div><div style="font:400 14px system-ui;color:#8EA0BC;margin-top:8px">فيه كلمات من فلترك</div>';
        b.onclick = function () { history.back(); };
        document.documentElement.appendChild(b);
      }
    }
    if (wfPage) { silence(document); relay('hush', 1); }
  }

  // ---------- media scan ----------
  var lastVid = null;
  function scan() {
    hook();
    var s = [];
    document.querySelectorAll('video,video source,audio,audio source').forEach(function (v) {
      var u = v.currentSrc || v.src;
      if (u && u.indexOf('http') == 0 && s.indexOf(u) < 0) s.push(u);
    });
    if (s.length) try { B.media(JSON.stringify(s), document.title || ''); } catch (e) {}
    if (TOP && !window.__mpip) {
      var big = false, W = innerWidth, H = innerHeight;
      if (!document.fullscreenElement && !document.webkitFullscreenElement) {
        document.querySelectorAll('video, iframe').forEach(function (el) {
          if (big || el.id === '__mreader') return;
          var r = el.getBoundingClientRect();
          if (el.tagName === 'VIDEO' && !(el.videoWidth > el.videoHeight * 1.2)) return;   // vertical videos (shorts/reels) stay portrait
          if (r.width >= W * 0.95 && r.height >= H * 0.85 && r.top <= H * 0.08 && el.getAttribute('data-mz') == null) big = true;
        });
      }
      if (big !== window.__mbig) { window.__mbig = big; try { B.orient(big); } catch (e) {} }
    }
    var mine = !!document.querySelector('video');
    if (mine && !TOP) { try { parent.postMessage({ __mfv: 1 }, '*'); } catch (e) {} }
    if (TOP) {
      var hv = mine || !!window.__fv;
      if (hv !== lastVid) { lastVid = hv; try { B.video(hv); } catch (e) {} }
    }
  }
  var n = 0;
  setInterval(function () {
    n++;
    skipAds(false);
    if (!TOP) { var vi = info(); if (vi) try { parent.postMessage({ __mi: vi }, '*'); } catch (e) {} }
    var now = Date.now();
    var tiny = !!window.__mpip;   // picture-in-picture: skip the heavy clean-up scans
    if (!tiny && ((dirty && now - lastHeavy > 1200) || now - lastHeavy > 6000)) {
      dirty = false; lastHeavy = now;
      try { lures(); } catch (e) {}
      try { frameLures(); } catch (e) {}
      try { hijackers(); } catch (e) {}
      try { applyZap(); } catch (e) {}
      try { wordFilter(); } catch (e) {}
    }
    if (wfPage && n % 2 === 0) try { silence(document); relay('hush', 1); } catch (e) {}
    if (n % 3 === 0) scan();
    if (n <= 40 && n % 4 === 0) checkWalls();   // first ~20s
    else if (n % 30 === 0) checkWalls();        // then every 15s
  }, 500);
  scan();
})();
