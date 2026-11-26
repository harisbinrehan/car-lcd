# Taps each car part in every theme on the emulator and measures whether the picture reacts.
import subprocess, re, time, io, sys
from PIL import Image, ImageChops, ImageStat
import os
S=os.environ.get("CARLCD_WORK","/tmp/carlcd"); os.makedirs(S,exist_ok=True)
ADB_BIN=os.environ.get("ADB","adb")
ADB=[ADB_BIN,"-s","emulator-5554"]
def sh(*a): return subprocess.run(ADB+["shell"]+list(a),capture_output=True,text=True).stdout
def shot(): return Image.open(io.BytesIO(subprocess.run(ADB+["exec-out","screencap","-p"],capture_output=True).stdout)).convert("RGB")
def tap(x,y): sh("input","tap",str(int(x)),str(int(y)))
java=open(os.path.join(os.path.dirname(os.path.abspath(__file__)),"../../app/src/main/java/com/harislcd/launcher/CarParts.java")).read()
parts={k:[float(v.rstrip("f")) for v in vals.split(",")] for k,vals in re.findall(r'case "(\w+)": return new float\[\]\{([^}]*)\}',java)}
def diff(a,b,box): return ImageStat.Stat(ImageChops.difference(a.crop(box),b.crop(box)).convert("L")).mean[0]
themes=sys.argv[1:] or list(parts)
ok_all=True
for t in themes:
    subprocess.run([os.path.join(os.path.dirname(os.path.abspath(__file__)),"emu.sh"),"set",t,"dark"]); time.sleep(6)
    # absolute position of the car from the view hierarchy dump (offsets are relative to each parent)
    dump=sh("dumpsys","activity","com.harislcd.launcher/.MainActivity"); stack=[]; m=None
    for line in dump.splitlines():
        g=re.match(r'^(\s*)\S+\{[0-9a-f]+ \S+ \S+ (-?\d+),(-?\d+)-(-?\d+),(-?\d+)',line)
        if not g: continue
        ind=len(g.group(1)); l,tp,r,bt=map(int,g.groups()[1:])
        while stack and stack[-1][0]>=ind: stack.pop()
        ox=sum(v[1] for v in stack); oy=sum(v[2] for v in stack)
        if "app:id/car_view" in line: m=(ox+l,oy+tp,ox+r,oy+bt); break
        stack.append((ind,l,tp))
    if not m: print(t,"car_view NOT FOUND"); ok_all=False; continue
    class _M:
        def __init__(s,v): s.v=v
        def groups(s): return s.v
    m=_M(m)
    x0,y0,x1,y1=map(int,m.groups()); p=parts[t]; sx=(x1-x0)/p[0]; sy=(y1-y0)/p[1]
    P=lambda i,j:(x0+p[i]*sx,y0+p[j]*sy)
    wbox=lambda i:(int(x0+(p[i]-p[i+2])*sx),int(y0+(p[i+1]-p[i+3])*sy),int(x0+(p[i]+p[i+2])*sx),int(y0+(p[i+1]+p[i+3])*sy))
    mbox=(int(x0+p[12]*sx),int(y0+p[13]*sy),int(x0+(p[12]+p[14])*sx),int(y0+(p[13]+p[15])*sy))
    mtap=(x0+(p[12]+p[14]/2)*sx,y0+(p[13]+p[15]*0.72)*sy)      # the mirror head sits in the lower part of its patch
    full=(x0,y0,x1,y1); res={}
    # wheels
    tap(*P(2,3)); time.sleep(1.5); a=shot(); time.sleep(0.4); b=shot()
    res["wheel spin"]=diff(a,b,wbox(2))>1.0 and diff(a,b,wbox(7))>0.5
    tap(*P(2,3)); time.sleep(5); a=shot(); time.sleep(0.4); b=shot()
    res["wheel stop"]=diff(a,b,wbox(2))<0.5
    # mirror
    a=shot(); tap(*mtap); time.sleep(2.0); b=shot()
    res["mirror fold"]=diff(a,b,mbox)>2.0
    tap(*mtap); time.sleep(2.0); c=shot()
    res["mirror open"]=diff(a,c,mbox)<1.5
    # indicators: a tap on the fog lamp starts them; the strip on the mirror is one of the lamps that flash
    fx,fy=x0+p[21]*sx,y0+p[22]*sy; fbox=mbox
    tap(fx,fy); frames=[]
    for _ in range(8): frames.append(shot()); time.sleep(0.1)
    res["indicator blink"]=max(diff(f,g,fbox) for f in frames for g in frames)>1.0
    tap(fx,fy); time.sleep(1.0); a=shot(); time.sleep(0.5); b=shot()
    res["indicator stop"]=diff(a,b,fbox)<0.8
    # lights: tap the middle of the bonnet/windscreen, away from every other part
    hx,hy=x0+(x1-x0)*0.5,y0+(y1-y0)*0.30
    a=shot(); tap(hx,hy); time.sleep(1.0); b=shot(); tap(hx,hy); time.sleep(1.0); c=shot()
    res["lights toggle"]=diff(a,b,full)>0.25 or diff(b,c,full)>0.25
    bad=[k for k,v in res.items() if not v]; ok_all&=not bad
    print(f"{t:8s}", "PASS" if not bad else "FAIL: "+", ".join(bad), flush=True)
crash=str(sh("logcat","-d","-s","AndroidRuntime:E").count("FATAL EXCEPTION"))
print("crashes in log:",crash); print("ALL PASS" if ok_all and crash=="0" else "SOME FAILED")
