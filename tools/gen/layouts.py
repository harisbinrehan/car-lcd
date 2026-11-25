import sys, json
R=sys.argv[1]; SZ=json.load(open("sizes.json"))
NS='xmlns:android="http://schemas.android.com/apk/res/android"'
CLICK='android:clickable="true" android:focusable="true"'
BL='@drawable/focus_bg'
MED='android:fontFamily="sans-serif-medium"'; LIGHT='android:fontFamily="sans-serif-light"'; BOLD='android:textStyle="bold"'
CARD='android:background="@drawable/card_bg"'; GC='android:layout_gravity="center"'
def idattr(i): return f'android:id="@+id/{i}" ' if i else ''
def T(text="",size=16,color="cFg",id=None,extra=""):
    return f'<TextView {idattr(id)}android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="{text}" android:textSize="{size}sp" android:textColor="?attr/{color}" android:includeFontPadding="false" {extra}/>\n'
def I(src,w,h=None,id=None,extra=""):
    return f'<ImageView {idattr(id)}android:layout_width="{w}dp" android:layout_height="{h or w}dp" android:src="@drawable/{src}" {extra}/>\n'
def L(ch,orient="horizontal",w="match_parent",h="wrap_content",extra="",id=None):
    return f'<LinearLayout {idattr(id)}android:layout_width="{w}" android:layout_height="{h}" android:orientation="{orient}" android:baselineAligned="false" {extra}>\n'+"".join(ch)+'</LinearLayout>\n'
def F(ch,w="match_parent",h="wrap_content",extra="",id=None):
    return f'<FrameLayout {idattr(id)}android:layout_width="{w}" android:layout_height="{h}" {extra}>\n'+"".join(ch)+'</FrameLayout>\n'
def V(w,h,bg,extra=""): return f'<View android:layout_width="{w}" android:layout_height="{h}" android:background="{bg}" {extra}/>\n'
def SP(): return '<Space android:layout_width="0dp" android:layout_height="1dp" android:layout_weight="1" />\n'
def ms(n): return f'android:layout_marginStart="{n}dp"'
def mt(n): return f'android:layout_marginTop="{n}dp"'
def W1(): return 'android:layout_weight="1"'
A=dict(radio=("tile_radio","ic_radio","Radio"),bt=("tile_bt","ic_bluetooth","Bluetooth"),aa=("tile_auto","ic_aa","Android Auto"),
   eq=("tile_eq","ic_eq","Equalizer"),yt=("tile_youtube","ic_youtube","YouTube"),music=("item_music","ic_music","Music"),
   apps=("item_apps","ic_apps","Apps"),theme=("item_theme","ic_theme","Themes"),set=("item_settings","ic_settings","Settings"))
def row(k,w="match_parent",h="0dp",extra='android:layout_weight="1"',icon=34,size=21,grav="center",gap=16):
    i,ic,lb=A[k]
    return L([I(ic,icon),T(lb,size,extra=f'{ms(gap)} {MED}')],w=w,h=h,id=i,extra=f'android:gravity="{grav}" android:background="@drawable/tile_bg" {CLICK} {extra}')
def nav(k,icon=24,size=16,w="0dp",extra='android:layout_weight="1"'):
    i,ic,lb=A[k]
    return L([I(ic,icon),T(lb,size,extra=f'{ms(9)} {MED}')],w=w,h="match_parent",id=i,extra=f'android:gravity="center" android:background="{BL}" {CLICK} {extra}')
def icon(k,box=76,ic_sz=34,size=15):
    i,ic,lb=A[k]
    return L([F([I(ic,ic_sz,extra=GC)],w=f"{box}dp",h=f"{box}dp",extra='android:background="@drawable/tile_bg"'),T(lb,size,extra=f'{mt(8)} {MED}')],
             "vertical",w="0dp",h="match_parent",id=i,extra=f'{W1()} android:gravity="center" android:background="{BL}" {CLICK}')
def sq(k,ic_sz=38,size=17,w="0dp",h="match_parent",extra='android:layout_weight="1"',bg="@drawable/tile_bg"):
    i,ic,lb=A[k]
    return L([I(ic,ic_sz),T(lb,size,extra=f'{mt(9)} {MED}')],"vertical",w=w,h=h,id=i,extra=f'android:gravity="center" android:background="{bg}" {CLICK} {extra}')
def mini(k,sz=26):
    i,ic,lb=A[k]
    return I(ic,sz+20,id=i,extra=f'android:padding="10dp" android:background="{BL}" {CLICK} android:contentDescription="{lb}"')
def car(k,extra):
    w,h=SZ[k]
    return f'<com.harislcd.launcher.CarView android:id="@+id/car_view" android:layout_width="{w}dp" android:layout_height="{h}dp" android:contentDescription="Toyota Vitz" android:focusable="true" {extra}/>\n'
def compass(s,extra=""): return f'<com.harislcd.launcher.CompassView android:id="@+id/compass_view" android:layout_width="{s}dp" android:layout_height="{s}dp" {extra}/>\n'
def clock(ts,ps,font=LIGHT,extra=""):
    return L([T("9:41",ts,id="txt_time",extra=font),T("PM",ps,"cMuted",id="txt_ampm",extra=f'{ms(7)} {MED}')],w="wrap_content",extra=extra)
def date(sz,extra=""): return T("Wednesday, November 25",sz,"cMuted",id="txt_date",extra=extra)
def hilo(sz=15,extra=""):
    return L([T("High",sz,"cMuted"),T("--°",sz,id="txt_high",extra=f'{ms(6)} {MED}'),T("Low",sz,"cMuted",extra=ms(16)),T("--°",sz,id="txt_low",extra=f'{ms(6)} {MED}')],w="wrap_content",extra=extra)
def weather_row(icon_sz,temp_sz):
    return [I("ic_sun",icon_sz,id="img_weather"),L([T("--°",temp_sz,id="txt_temp",extra=LIGHT),T("",15,"cSoft",id="txt_cond",extra=mt(2))],"vertical",w="wrap_content",extra=ms(14))]
def wmini(extra=""): return L([I("ic_sun",26,id="img_weather"),T("--°",22,id="txt_temp",extra=ms(8)),T("",14,"cMuted",id="txt_cond",extra=ms(10))],w="wrap_content",extra=f'android:gravity="center_vertical" {extra}')
def speed(sz,usz=17): return [T("0",sz,id="txt_speed",extra=LIGHT),T("km/h",usz,"cMuted",extra=f'{ms(8)} {MED}')]
def heading(sz,dsz=15): return L([T("--",sz,id="txt_heading_card",extra=LIGHT),T("",dsz,"cMuted",id="txt_heading_deg")],"vertical",w="wrap_content",extra=f'android:gravity="center" {ms(14)}')
def label(t,extra=""): return T(t,12.5,"cMuted",extra=f'{MED} android:letterSpacing="0.16" android:textAllCaps="true" {extra}')
def title(sz=13,sp="0.42",extra="",color="cMuted"): return T("VITZ HYBRID",sz,color,extra=f'{MED} android:letterSpacing="{sp}" {extra}')
def div(h=44,mx=24): return V("1dp",f"{h}dp","?attr/cHair",f'android:layout_marginStart="{mx}dp" android:layout_marginEnd="{mx}dp"')
def wcard(extra="",box=70):
    return L([F([I("ic_sun",int(box*0.58),id="img_weather",extra=GC)],w=f"{box}dp",h=f"{box}dp",extra='android:background="@drawable/tile_bg"'),
      L([T("--°",32,id="txt_temp",extra=MED),T("",15,"cSoft",id="txt_cond")],"vertical",w="wrap_content",extra=ms(16)),SP(),hilo(14)],w="0dp",h="match_parent",extra=f'{"" if "layout_weight" in extra else W1()} {CARD} android:gravity="center_vertical" android:paddingStart="20dp" android:paddingEnd="20dp" {extra}')
def scard(extra=""): return L([label("Speed"),SP()]+speed(52),w="0dp",h="match_parent",extra=f'{W1()} {CARD} android:gravity="center_vertical" android:paddingStart="24dp" android:paddingEnd="26dp" {extra}')
def hcard(extra=""): return L([compass(80),heading(32),SP(),label("Heading")],w="0dp",h="match_parent",extra=f'{W1()} {CARD} android:gravity="center_vertical" android:paddingStart="20dp" android:paddingEnd="24dp" {extra}')
def root(kind,ch,orient="vertical"):
    ex=f'{NS} android:background="@drawable/screen_bg" android:padding="14dp"'
    return '<?xml version="1.0" encoding="utf-8"?>\n'+(L(ch,orient,h="match_parent",extra=ex) if kind=="L" else F(ch,h="match_parent",extra=ex))
def save(name,xml): open(f"{R}/layout/activity_main_{name}.xml","w").write(xml)
PADNAV='android:paddingStart="16dp" android:paddingEnd="16dp"'
ONE='android:maxLines="1" android:includeFontPadding="false"'
MARQ=' android:singleLine="true" android:ellipsize="marquee" android:marqueeRepeatLimit="marquee_forever"'
def npbtn(id,ic,sz=52):
    link='android:nextFocusRight="@+id/np_play"' if id=="np_prev" else 'android:nextFocusLeft="@+id/np_play"'
    return I(ic,sz,id=id,extra=f'android:padding="13dp" android:background="{BL}" {link} {CLICK}')
def npplay(sz=58): return I("ic_play",sz,id="np_play",extra=f'android:padding="15dp" android:background="@drawable/np_play_bg" android:layout_marginStart="4dp" android:layout_marginEnd="4dp" android:nextFocusLeft="@+id/np_prev" android:nextFocusRight="@+id/np_next" {CLICK}')
def nptv(id,text,size,color,extra=""):
    if "marquee" not in extra: extra+=' android:ellipsize="end"'
    return f'<TextView android:id="@+id/{id}" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="{text}" android:textSize="{size}sp" android:textColor="?attr/{color}" {ONE} {extra}/>\n'
def np(w="0dp",h="match_parent",extra="",wt=True,art=True,bg=None,weight="1"):
    txt=L([nptv("np_title","Nothing playing",19,"cFg",MED+MARQ),nptv("np_sub","Tap play to start",14,"cMuted",mt(3))],"vertical",w="0dp",extra=f'{W1()} android:layout_marginStart="{14 if art else 2}dp" android:layout_marginEnd="6dp"')
    pic=[F([I("ic_music",26,extra=GC)],w="50dp",h="50dp",extra='android:background="@drawable/tile_bg"')] if art else []
    return L(pic+[txt,npbtn("np_prev","ic_prev"),npplay(),npbtn("np_next","ic_next")],w=w,h=h,id="np_root",
             extra=f'android:gravity="center_vertical" android:minHeight="64dp" android:background="{bg or BL}" android:clickable="true" android:focusable="false" '+(f'android:layout_weight="{weight}" ' if wt else "")+extra)
def npcard(extra="",art=True,weight="1.3"): return np(art=art,weight=weight,bg="@drawable/card_focus_bg",extra=f'android:paddingStart="16dp" android:paddingEnd="8dp" {extra}')
def npv(extra="",w="match_parent",h="wrap_content",small=False):
    c='android:gravity="center"'
    return L([nptv("np_title","Nothing playing",18 if small else 19,"cFg",f'{MED} {c} {MARQ}'),nptv("np_sub","Tap play to start",13 if small else 14,"cMuted",f'{mt(2)} {c}'),
              L([npbtn("np_prev","ic_prev",46 if small else 52),npplay(50 if small else 58),npbtn("np_next","ic_next",46 if small else 52)],w="wrap_content",extra=f'android:layout_gravity="center_horizontal" android:gravity="center_vertical" {mt(4)}')],
             "vertical",w=w,h=h,id="np_root",extra=f'android:gravity="center" android:background="{BL}" android:clickable="true" android:focusable="false" {extra}')
def dcard(extra=""): return L(speed(46)+[SP(),compass(72),heading(26)],w="0dp",h="match_parent",extra=f'{"" if "layout_weight" in extra else W1()} {CARD} android:gravity="center_vertical" android:paddingStart="22dp" android:paddingEnd="22dp" {extra}')
def infobar(h=92,extra=""):
    return L(speed(42)+[div(44,20),compass(62),heading(26),div(44,18),np(),div(44,8),mini("music",28),mini("theme",28),mini("set",28)],
      h=f"{h}dp",extra=f'android:gravity="center_vertical" android:paddingStart="30dp" android:paddingEnd="14dp" {extra}')

# CRIMSON
top=F([wmini('android:layout_gravity="start|center_vertical"'),
       L([V("190dp","2dp","?attr/cAccent"),L([clock(34,18,BOLD),date(13,'android:letterSpacing="0.12"')],"vertical",w="wrap_content",extra='android:gravity="center_horizontal" android:layout_marginStart="22dp" android:layout_marginEnd="22dp"'),V("190dp","2dp","?attr/cAccent")],w="wrap_content",extra=f'{GC} android:gravity="center_vertical"')],h="62dp")
side=lambda ks: L([row(k,extra=f'{W1()} android:paddingStart="34dp" {mt(0 if n==0 else 12)}',grav="center_vertical") for n,k in enumerate(ks)],"vertical",w="250dp",h="match_parent")
mid=L([side(["radio","bt","aa"]),F([car("crimson",GC)],w="0dp",h="match_parent",extra=W1()),side(["eq","yt","apps"])],h="0dp",extra=f'{W1()} {mt(10)}')
save("crimson",root("L",[top,mid,infobar(92,f'{CARD} {mt(12)}')]))

# IVORY
iv=[car("ivory",f'android:layout_gravity="top|center_horizontal" android:layout_marginTop="{(446-SZ["ivory"][1])//2+6}dp"'),
    L([V("4dp","match_parent","?attr/cAccent"),L([clock(62,20),date(17)],"vertical",w="wrap_content",extra=ms(16))],w="wrap_content",extra='android:layout_gravity="top|start" android:layout_marginTop="10dp"'),
    L([mini("theme"),mini("set")],w="wrap_content",extra='android:layout_gravity="top|end"'),
    L([L([wcard(),npcard(ms(14)),dcard(ms(14))],h="112dp"),L([nav(k) for k in ["radio","bt","aa","eq","yt","music","apps"]],h="52dp",extra=mt(6))],"vertical",extra='android:layout_gravity="bottom"')]
save("ivory",root("F",iv))

# GOLD
gtop=L([SP(),mini("theme"),mini("set")],h="48dp",extra='android:gravity="center_vertical"')
gl=L([clock(66,22),date(18,mt(4)),V("92dp","1dp","?attr/cAccent",mt(22)),
      L([L(weather_row(46,40),extra='android:gravity="center_vertical"'),hilo(15,mt(14))],"vertical",extra=f'{CARD} android:padding="20dp" {mt(26)}')],"vertical",w="290dp",h="match_parent",extra='android:gravity="center_vertical"')
gr=L([L([label("Driving"),V("0dp","1dp","?attr/cAccent",f'{W1()} {ms(14)}')],extra='android:gravity="center_vertical"'),
      L(speed(54,17),w="wrap_content",extra=f'android:layout_gravity="center_horizontal" android:gravity="bottom" {mt(18)}'),
      compass(84,f'android:layout_gravity="center_horizontal" {mt(10)}'),
      L([T("--",28,id="txt_heading_card",extra=LIGHT),T("",16,"cMuted",id="txt_heading_deg",extra=ms(10))],w="wrap_content",extra=f'android:layout_gravity="center_horizontal" android:gravity="center_vertical" {mt(10)}'),V("match_parent","1dp","?attr/cHair",mt(12)),npv(mt(10))],
     "vertical",w="270dp",extra=f'{CARD} android:padding="20dp" android:layout_gravity="center_vertical"')
gmid=L([gl,F([car("gold",GC)],w="0dp",h="match_parent",extra=W1()),gr],h="0dp",extra=W1())
save("gold",root("L",[gtop,V("match_parent","1dp","?attr/cAccent",'android:alpha="0.55"'),gmid,V("match_parent","1dp","?attr/cAccent",'android:alpha="0.35"'),
     L([icon(k) for k in ["radio","bt","aa","eq","yt","music","apps"]],h="124dp",extra='android:paddingStart="60dp" android:paddingEnd="60dp"')]))

# FOREST
g=lambda ks,first: L([row(k,w="0dp",h="match_parent",extra=f'{W1()} {ms(0 if n==0 else 12)}',icon=30,size=20) for n,k in enumerate(ks)],h="70dp",extra="" if first else mt(12))
fo=[car("forest",f'android:layout_gravity="top|center_horizontal" android:layout_marginTop="{(408-SZ["forest"][1])//2+4}dp"'),
    L([clock(58,20),date(16,'android:letterSpacing="0.12"')],"vertical",w="wrap_content",extra='android:layout_gravity="top|start" android:layout_marginStart="14dp" android:layout_marginTop="6dp"'),
    L([wmini('android:layout_marginEnd="10dp"'),mini("music"),mini("theme"),mini("set")],w="wrap_content",extra='android:layout_gravity="top|end" android:gravity="center_vertical"'),
    L([L([label("Speed"),L(speed(58),w="wrap_content",extra=f'android:gravity="bottom" {mt(8)}')],"vertical",w="0dp",extra=f'{W1()} android:gravity="center"'),
       L([np(w="match_parent",h="68dp",wt=False,bg="@drawable/card_focus_bg",extra='android:paddingStart="14dp" android:paddingEnd="8dp" android:layout_marginBottom="10dp"'),g(["radio","bt","aa"],True),g(["eq","yt","apps"],False)],"vertical",w="760dp"),
       L([compass(92),heading(32)],w="0dp",extra=f'{W1()} android:gravity="center"')],extra='android:layout_gravity="bottom" android:gravity="center_vertical"')]
save("forest",root("F",fo))

# VIOLET
i_,ic_,_=A["theme"]
themepill=L([I(ic_,22),T("Themes",15,extra=f'{ms(8)} {MED}')],w="wrap_content",id=i_,extra=f'android:gravity="center_vertical" android:background="@drawable/tile_bg" android:paddingStart="14dp" android:paddingEnd="16dp" android:paddingTop="9dp" android:paddingBottom="9dp" {CLICK} {mt(12)}')
vw=L(weather_row(52,42)+[SP(),L([label("Weather",'android:layout_gravity="end"'),hilo(15,mt(12))],"vertical",w="wrap_content")],w="0dp",h="match_parent",extra=f'{W1()} {CARD} android:gravity="center_vertical" android:paddingStart="26dp" android:paddingEnd="26dp"')
vd=L(speed(58)+[SP(),compass(80),heading(32)],w="0dp",h="match_parent",extra=f'{W1()} {CARD} android:gravity="center_vertical" android:paddingStart="28dp" android:paddingEnd="30dp" {ms(14)}')
vi=[car("violet",f'android:layout_gravity="top|center_horizontal" android:layout_marginTop="{66+(450-SZ["violet"][1])//2}dp"'),
    L([nav(k,22,15) for k in ["radio","bt","aa","eq","yt","music","apps","set"]],h="66dp",extra=CARD),
    L([themepill],"vertical",w="wrap_content",extra='android:layout_gravity="top|start" android:layout_marginTop="84dp" android:layout_marginStart="12dp"'),
    L([clock(34,20,BOLD),date(16,mt(2))],"vertical",w="wrap_content",extra='android:layout_gravity="top|end" android:layout_marginTop="84dp" android:layout_marginEnd="12dp" android:gravity="end"'),
    L([vw,npcard(ms(14)),vd],h="104dp",extra='android:layout_gravity="bottom"')]
save("violet",root("F",vi))

# SILVER: clock panel left, 2 x 3 grid of square tiles right, info bar below
sl=L([clock(54,18),date(16,f'{mt(6)} android:letterSpacing="0.1"'),V("match_parent","1dp","?attr/cHair",mt(24)),
      L([I("ic_sun",34,id="img_weather"),L([T("--°",28,id="txt_temp",extra=MED),T("",14,"cSoft",id="txt_cond")],"vertical",w="wrap_content",extra=ms(14))],extra=f'android:background="@drawable/tile_bg" android:gravity="center_vertical" android:padding="16dp" {mt(24)}')],
     "vertical",w="232dp",h="match_parent",extra='android:gravity="center_vertical"')
grow=lambda a,b,first: L([sq(a),sq(b,extra=f'{W1()} {ms(12)}')],h="0dp",extra=f'{W1()} '+("" if first else mt(12)))
sgrid=L([grow("radio","bt",True),grow("aa","eq",False),grow("yt","apps",False)],"vertical",w="312dp",h="match_parent")
smid=L([sl,V("1dp","match_parent","?attr/cHair",'android:layout_marginStart="12dp"'),F([car("silver",GC)],w="0dp",h="match_parent",extra=W1()),sgrid],h="0dp",extra=f'{W1()} {mt(4)}')
stop=L([V("120dp","1dp","?attr/cMuted"),title(17,"0.6",'android:layout_marginStart="22dp" android:layout_marginEnd="22dp"',"cFg"),V("120dp","1dp","?attr/cMuted")],h="34dp",extra='android:gravity="center"')
save("silver",root("L",[smid,V("match_parent","1dp","?attr/cHair",mt(8)),infobar(88)]))

# SKY: three glass cards across the top, a column of tiles on the right, clock bottom-left
sk=[car("sky",f'android:layout_gravity="bottom|start" android:layout_marginStart="{(1090-SZ["sky"][0])//2}dp" android:layout_marginBottom="{56+(444-SZ["sky"][1])//2}dp"'),
    L([wcard('android:layout_weight="1.02"',box=50),npcard(ms(14),art=False,weight="1.38"),dcard(ms(14)+' android:layout_weight="0.86"')],h="104dp",extra='android:layout_marginEnd="162dp"'),
    L([sq(k,ic_sz=32,size=15,w="match_parent",h="0dp",extra=f'{W1()} '+("" if n==0 else mt(10))) for n,k in enumerate(["radio","bt","aa","eq","yt"])],"vertical",w="148dp",h="match_parent",extra='android:layout_gravity="end" android:layout_marginBottom="60dp"'),
    L([clock(34,18,BOLD),date(15,'android:letterSpacing="0.1"')],"vertical",w="wrap_content",extra='android:layout_gravity="bottom|start" android:layout_marginStart="8dp"'),
    L([mini("music"),mini("apps"),mini("theme"),mini("set")],w="wrap_content",extra='android:layout_gravity="bottom|end"')]
save("sky",root("F",sk))

# NOIR: nothing but the car; links in the corners
def link(k,w=104,ic_sz=32,size=16):
    i,ic,lb=A[k]
    return L([I(ic,ic_sz),T(lb,size,extra=f'{mt(8)} {MED}')],"vertical",w=f"{w}dp",id=i,extra=f'android:gravity="center" android:background="{BL}" android:paddingTop="8dp" android:paddingBottom="8dp" {CLICK}')
no=[car("noir",f'{GC} android:layout_marginTop="6dp"'),
    L([link("radio"),link("bt"),link("aa",126),link("music")],w="wrap_content",extra='android:layout_gravity="top|start"'),
    L([clock(46,22),date(16,'android:letterSpacing="0.12"'),wmini(mt(10))],"vertical",w="wrap_content",extra='android:layout_gravity="top|end" android:gravity="end" android:layout_marginEnd="8dp"'),
    L([np(w="400dp",h="wrap_content",wt=False),div(34,14)]+speed(36)+[div(34,14),compass(54),heading(22)],w="wrap_content",extra='android:layout_gravity="bottom|start" android:gravity="center_vertical" android:layout_marginStart="10dp" android:layout_marginBottom="8dp"'),
    L([link("eq"),link("yt"),link("apps"),link("theme"),link("set")],w="wrap_content",extra='android:layout_gravity="bottom|end"')]
save("noir",root("F",no))

# AMBER: big centred clock, two capsules of apps at the bottom with the driving info between them
def cell(k):
    i,ic,lb=A[k]
    return L([I(ic,30),T(lb,16,extra=f'{mt(8)} {MED}')],"vertical",w="0dp",h="match_parent",id=i,extra=f'{W1()} android:gravity="center" android:background="{BL}" {CLICK}')
caps=lambda ks: L([x for n,k in enumerate(ks) for x in (([V("1dp","52dp","?attr/cHair",'android:layout_gravity="center_vertical"')] if n else [])+[cell(k)])],w="0dp",h="match_parent",extra=f'android:layout_weight="1.25" {CARD}')
atop=F([L([L(speed(26,14)+[compass(64,ms(18)),T("--",22,id="txt_heading_card",extra=f'{ms(12)} {LIGHT}'),T("",13,"cMuted",id="txt_heading_deg",extra=ms(6))],w="wrap_content",extra='android:gravity="center_vertical"')],"vertical",w="wrap_content",extra='android:layout_gravity="start|center_vertical" android:layout_marginStart="8dp"'),
        L([clock(46,26),date(15,'android:letterSpacing="0.25"')],"vertical",w="wrap_content",extra=f'{GC} android:gravity="center_horizontal"'),
        L([wmini('android:layout_marginEnd="8dp"'),mini("music"),mini("theme"),mini("set")],w="wrap_content",extra='android:layout_gravity="end|center_vertical" android:gravity="center_vertical"')],h="84dp")
ainfo=npv(w="0dp",h="match_parent",small=True,extra=f'{W1()} android:layout_marginStart="14dp" android:layout_marginEnd="14dp"')
save("amber",root("L",[atop,F([car("amber",GC)],h="0dp",extra=W1()),L([caps(["radio","bt","aa"]),ainfo,caps(["eq","yt","apps"])],h="112dp")]))
p=f"{R}/layout/activity_main.xml"; c=open(p).read()
w,h=SZ["classic"]
a=c.index("            <!-- STAGE"); b=c.index("            <!-- RIGHT COLUMN -->")
strip=np(w="match_parent",h="72dp",wt=False,bg="@drawable/card_focus_bg",extra='android:layout_gravity="bottom" android:paddingStart="14dp" android:paddingEnd="8dp"')
c=c[:a]+f'''            <!-- STAGE: the car (tap its lights, wheels, mirror, fog lamps) with Now Playing underneath -->
            <FrameLayout
                android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"
                android:layout_marginStart="12dp" android:layout_marginEnd="12dp">
                <View
                    android:layout_width="460dp" android:layout_height="330dp"
                    android:layout_gravity="center" android:layout_marginBottom="40dp"
                    android:background="@drawable/halo" />
                <com.harislcd.launcher.CarView android:id="@+id/car_view"
                    android:layout_width="{w}dp" android:layout_height="{h}dp"
                    android:layout_gravity="center" android:layout_marginBottom="40dp"
                    android:focusable="true" android:contentDescription="Toyota Vitz" />
                {strip}            </FrameLayout>

'''+c[b:]
open(p,"w").write(c)
print("layouts written")
