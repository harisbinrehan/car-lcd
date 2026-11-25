import json
from PIL import Image, ImageDraw
P=json.load(open("parts.json")); ks=list(P)
sh=Image.new("RGB",(3*960,3*680),(60,60,70))
for n,k in enumerate(ks):
    src=Image.open(f"cut_{k}.png").convert("RGBA")
    bb=src.split()[3].point(lambda v:255 if v>100 else 0).getbbox(); car=src.crop(bb)
    sc=940/car.width; car=car.resize((940,int(car.height*sc)),Image.LANCZOS)
    ox,oy=(n%3)*960+10,(n//3)*680+10; sh.paste(car,(ox,oy),car); d=ImageDraw.Draw(sh)
    f=940/640.0
    for name,col in (("wf",(0,255,0)),("wr",(0,255,255)),("mir",(255,0,255))):
        cx,cy,rx,ry=[v*f for v in P[k][name]]
        d.ellipse((ox+cx-rx,oy+cy-ry,ox+cx+rx,oy+cy+ry),outline=col,width=2)
    d.text((ox+5,oy+2),k,fill=(255,255,255))
sh.save("parts_check.png")
