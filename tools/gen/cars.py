# cars.py <res>: each theme shows its own car picture (cut_<picture>.png, cut out of the owner's mockups,
# plates blanked; see THEMES in carlib.py, "classic" shares violet's), sized to that theme's slot and given
# a soft ground shadow. The pictures were taken with the lamps on, so car_<theme>.png is the lamps-off
# version and lights_<theme>.png puts the real lit pixels back on top: fully faded in, it is the original
# picture. Where the lamps are is in geo_<picture>.json. Also writes sizes.json (for layouts.py) and
# meta.json (for parts.py).
import sys, json, numpy as np, cv2
from PIL import Image, ImageDraw, ImageFilter
import carlib as C

def layers(P):
    """The lamps-off car on its shadow, and the lit pixels that go on top, both at cutout size plus margins."""
    off, changed = P.lights_off()
    bx0, by0, bx1, by1 = P.bbox; cw, ch = bx1 - bx0, by1 - by0
    Wc, Hc = cw + 2 * C.MS, ch + C.MT + C.MB
    sh = Image.new("L", (Wc, Hc), 0)
    ImageDraw.Draw(sh).ellipse((C.MS + cw * 0.03, C.MT + ch * 0.86, C.MS + cw * 0.97, C.MT + ch * 1.035), fill=175)
    sh = sh.filter(ImageFilter.GaussianBlur(15))
    base = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 255)); base.putalpha(sh)
    base.alpha_composite(P.canvas(C.rgba(off, P.ALPHA)))
    # the lit pixels, wherever switching off changed anything (plus a soft edge where nothing changed anyway)
    support = cv2.dilate((changed > 0.004).astype(np.float32), np.ones((7, 7), np.uint8))
    la = np.clip(cv2.GaussianBlur(support, (0, 0), 1.5) * 1.6, 0, 1) * P.ALPHA
    return base, P.canvas(C.rgba(P.RGB, la))

if __name__ == "__main__":
    R = sys.argv[1]; sizes = {}; meta = {}; done = {}
    for k, name in C.THEMES.items():
        P = C.picture(name)
        if name not in done: done[name] = layers(P)
        base, glow = done[name]; m = P.frame(C.BOX[k]); w, h = m["w"], m["h"]
        base.resize((w, h), Image.LANCZOS).save(f"{R}/drawable-nodpi/car_{k}.png", optimize=True)
        glow.resize((w, h), Image.LANCZOS).save(f"{R}/drawable-nodpi/lights_{k}.png", optimize=True)
        sizes[k] = (w, h); meta[k] = m
    json.dump(sizes, open("sizes.json", "w")); json.dump(meta, open("meta.json", "w")); print(sizes)
