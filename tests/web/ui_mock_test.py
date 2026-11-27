from playwright.sync_api import sync_playwright
mock = """
window.M={_on:false,_l:{allow:[],block:['bad.com']},
state(){return JSON.stringify({on:this._on,blocked:1234,total:9876,allBlocked:55555,rules:1424029,updated:Date.now(),server:'AdGuard (يمنع دعايات)',busy:true,busyMsg:'تحميل القوائم 2 من 7'})},
toggle(){this._on=!this._on},lists(){return JSON.stringify([{id:'hpro',cat:'ads',name:'HaGeZi Pro++',desc:'دعايات',on:true,have:true,size:5000000},{id:'htif',cat:'sec',name:'TIF',desc:'فيروسات',on:true,have:false,size:0},{id:'hx',cat:'native',name:'Xiaomi',desc:'x',on:false,have:false,size:0}])},
setList(){},updateAll(){},log(f){return JSON.stringify([[Date.now(),'d.applovin.com','b'],[Date.now(),'www.google.com','a'],[Date.now(),'ads.x.com','bs']])},
custom(){return JSON.stringify(this._l)},addCustom(k,d){this._l[k].push(d);return d},removeCustom(){},servers(){return JSON.stringify([{id:'adguard',name:'AdGuard',ip:'94.140.14.14',on:true}])},setServer(){},
apps(){return JSON.stringify([{pkg:'com.habby.survivor',label:'Survivor!.io',ex:false},{pkg:'com.whatsapp',label:'WhatsApp',ex:false}])},setExcluded(){},vpnSettings(){},exit(){},test(d){return d.indexOf('adjust')>=0?'a':'b'},open(){},firstRun(){return false}};
"""
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/opt/google/chrome/chrome')
    pg=b.new_page(viewport={'width':390,'height':844},device_scale_factor=2)
    errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.add_init_script(mock)
    pg.goto('file:///home/claude/mane3/app/assets/index.html')
    pg.wait_for_timeout(500); pg.click('#big'); pg.wait_for_timeout(700)
    pg.screenshot(path='/tmp/s1.png')
    for t in ['lists','log','mine','apps','dns']:
        pg.click(f'nav a[data-p={t}]'); pg.wait_for_timeout(300)
        if t=='log': pg.click('.lg >> nth=0'); pg.wait_for_timeout(200); pg.screenshot(path='/tmp/s_log.png'); pg.click('#sheet .btn.r >> nth=0')
        if t=='apps': pg.wait_for_timeout(200); pg.click('#apps .sw >> nth=0')
        pg.screenshot(path=f'/tmp/s_{t}.png')
    print('errors',errs)
    b.close()
