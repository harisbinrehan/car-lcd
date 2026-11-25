# The car itself is cut out of each mockup (exact pixels, no background), given a soft ground shadow,
# and paired with a "lights on" glow overlay. No rectangular crop, so nothing can be clipped.
import sys, json
from PIL import Image, ImageDraw, ImageFilter
R=sys.argv[1]
M={
 "silver": dict(lamps=[(770,470,1.0),(1183,462,.7),(748,645,.5)],box=(590,430)),
 "sky":    dict(lamps=[(885,535,1.0),(1298,522,.7),(878,707,.5)],box=(760,410)),
 "noir":   dict(lamps=[(392,458,1.0),(905,462,1.0),(343,636,.45),(930,648,.45)],box=(740,450)),
 "amber":  dict(lamps=[(385,438,1.0),(828,442,1.0),(335,592,.4),(827,612,.4)],box=(690,380)),
 "crimson":dict(lamps=[(447,457,1.0),(925,457,1.0),(403,618,.4),(952,625,.4)],box=(640,400)),
 "forest": dict(lamps=[(413,358,1.0),(765,366,1.0),(372,497,.4),(785,516,.4)],box=(680,380)),
 "violet": dict(lamps=[(915,437,1.0),(1300,418,.7),(905,598,.45)],box=(700,400)),
 "gold":   dict(lamps=[(430,402,1.0),(873,407,1.0),(385,548,.4),(885,570,.4)],box=(620,400)),
 "ivory":  dict(lamps=[(385,357,1.0),(765,367,1.0),(782,520,.4)],box=(640,400)),
}
M["classic"]=dict(M["violet"],box=(520,300),src="violet")
sizes={}; meta={}
for k,m in M.items():
    src=Image.open(f"cut_{m.get('src',k)}.png").convert("RGBA"); sx=src.width/1672.0
    bx0,by0,bx1,by1=src.split()[3].point(lambda v:255 if v>100 else 0).getbbox()
    car=src.crop((bx0,by0,bx1,by1)); cw,ch=car.size
    ms,mt,mb=int(24*sx),int(8*sx),int(46*sx)
    W,H=cw+2*ms,ch+mt+mb
    sh=Image.new("L",(W,H),0); d=ImageDraw.Draw(sh)
    d.ellipse((ms+cw*0.03,mt+ch*0.86,ms+cw*0.97,mt+ch*1.035),fill=175)
    sh=sh.filter(ImageFilter.GaussianBlur(15*sx))
    base=Image.new("RGBA",(W,H),(0,0,0,255)); base.putalpha(sh); base.alpha_composite(car,(ms,mt))
    sc=min(m["box"][0]/W, m["box"][1]/H); w,h=round(W*sc),round(H*sc)
    base.resize((w,h),Image.LANCZOS).save(f"{R}/drawable-nodpi/car_{k}.png",optimize=True)
    g=Image.new("RGBA",(w,h),(0,0,0,0))
    def blob(cx,cy,r,col,al,bl):
        global g
        l=Image.new("L",(w,h),0); ImageDraw.Draw(l).ellipse((cx-r,cy-r*.8,cx+r,cy+r*.8),fill=al)
        c=Image.new("RGBA",(w,h),col+(255,)); c.putalpha(l.filter(ImageFilter.GaussianBlur(bl))); g=Image.alpha_composite(g,c)
    for (lx,ly,s) in m["lamps"]:
        cx,cy=(lx*sx-bx0+ms)*sc,(ly*sx-by0+mt)*sc; u=s*sc/0.7
        blob(cx,cy,44*u,(255,236,200),150,20*u); blob(cx,cy,14*u,(255,250,235),255,5*u); blob(cx,cy,6*u,(255,255,255),255,1.5)
    g.save(f"{R}/drawable-nodpi/lights_{k}.png",optimize=True)
    sizes[k]=(w,h); meta[k]=dict(bx0=bx0,by0=by0,ms=ms,mt=mt,sc=sc,cw=cw,w=w,h=h,src=m.get('src',k),lamps=m['lamps'],sx=sx)
    for bgc,tag in (((40,40,48),"d"),((236,236,240),"l")):
        chk=Image.new("RGBA",(w,h),bgc+(255,)); chk.alpha_composite(Image.open(f"{R}/drawable-nodpi/car_{k}.png")); chk.alpha_composite(g); chk.convert("RGB").save(f"chk_{k}_{tag}.png")
json.dump(sizes,open("sizes.json","w")); json.dump(meta,open("meta.json","w")); print(sizes)
