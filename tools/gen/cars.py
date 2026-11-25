# cars.py <res>: every theme shows the same car (cut_premium.png, the owner's studio picture with the
# plate blanked), sized to that theme's slot and given a soft ground shadow. The picture was taken with
# the lamps on, so car_<theme>.png is the lamps-off version and lights_<theme>.png puts the real lit
# pixels back on top: fully faded in, it is the original picture.
import sys, json, numpy as np, cv2
from PIL import Image, ImageDraw, ImageFilter
import carlib as C
R = sys.argv[1]
# the slot each theme's layout gives the car (dp); the car is fitted inside it
BOX = {"silver": (590, 389), "sky": (744, 410), "noir": (685, 450), "amber": (654, 380), "crimson": (600, 400),
       "forest": (676, 380), "violet": (697, 400), "gold": (620, 399), "ivory": (640, 370), "classic": (520, 298)}
off, changed = C.lights_off()
bx0, by0, bx1, by1 = C.SRC.split()[3].point(lambda v: 255 if v > 100 else 0).getbbox()
cw, ch = bx1 - bx0, by1 - by0
ms, mt, mb = 26, 8, 40                                        # room for the shadow
Wc, Hc = cw + 2 * ms, ch + mt + mb
def canvas(img):                                              # crop to the car and add the margins
    out = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 0)); out.alpha_composite(img.crop((bx0, by0, bx1, by1)), (ms, mt)); return out
# shadow: the car is seen from above, so it sits under the body and trails to the far side
sh = Image.new("L", (Wc, Hc), 0); d = ImageDraw.Draw(sh)
d.polygon([(ms + cw * 0.02, mt + ch * 0.50), (ms + cw * 0.10, mt + ch * 0.58), (ms + cw * 0.36, mt + ch * 1.00),
           (ms + cw * 0.62, mt + ch * 1.03), (ms + cw * 0.99, mt + ch * 0.80), (ms + cw * 1.0, mt + ch * 0.55),
           (ms + cw * 0.5, mt + ch * 0.45)], fill=150)
sh = sh.filter(ImageFilter.GaussianBlur(14))
base = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 255)); base.putalpha(sh)
base.alpha_composite(canvas(C.rgba(off, C.ALPHA)))
# the lit pixels, wherever switching off changed anything (plus a soft edge where nothing changed anyway)
support = cv2.dilate((changed > 0.004).astype(np.float32), np.ones((7, 7), np.uint8))
la = np.clip(cv2.GaussianBlur(support, (0, 0), 1.5) * 1.6, 0, 1) * C.ALPHA
glow = canvas(C.rgba(C.RGB, la))
sizes = {}; meta = {}
for k, (bw, bh) in BOX.items():
    sc = min(bw / Wc, bh / Hc); w, h = round(Wc * sc), round(Hc * sc)
    base.resize((w, h), Image.LANCZOS).save(f"{R}/drawable-nodpi/car_{k}.png", optimize=True)
    glow.resize((w, h), Image.LANCZOS).save(f"{R}/drawable-nodpi/lights_{k}.png", optimize=True)
    sizes[k] = (w, h); meta[k] = dict(bx0=bx0, by0=by0, ms=ms, mt=mt, sc=sc, w=w, h=h, Wc=Wc, Hc=Hc)
json.dump(sizes, open("sizes.json", "w")); json.dump(meta, open("meta.json", "w")); print(sizes)
