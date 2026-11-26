# Shared by cars.py and parts.py. Each theme shows its own car picture again (cut_<picture>.png, the
# cutouts from the owner's mockups with the plate blanked); "classic" shares the violet picture.
# A picture's geometry lives in geo_<picture>.json, in that cutout's own pixels (see GEO below), and
# this file knows how a picture's lamps look switched off and how an indicator section looks lit.
# cut_premium.png / geo_premium.json (the single studio picture of 2.3) are kept as a spare; to try it,
# point a theme at "premium" in THEMES.
#
# GEO, per picture (polygons are [x,y] lists):
#   lamp_near, lamp_far  inner = the glass of the headlamp; cores = [x,y,r] of each lens / bulb;
#                        turn + bulb = the section that flashes amber and its hottest point;
#                        optional off = {gamma, level, lens, ...} to tune how it is darkened
#   fogs                 one entry per visible fog lamp: core [x,y,r] (also the indicator tap point);
#                        only when the lamp is lit in the picture: recess [x,y,rx,ry], spill [x,y,rx,ry]
#   spills               optional [x,y,rx,ry,strength]: lamp glow lying on the paint, to be taken off
#   body_ref             a patch of plain paint [x0,y0,x1,y1] (gives the paint's tint)
#   wheel_f, wheel_r     rim centre c, radii rx/ry and lean (tilt, degrees)
#   mirror               head polygon, turn strip + bulb, hinge, sill (the direction in which the door's
#                        lines run behind the head: the gap is filled by carrying pixels along it; a
#                        list of directions blends them, the nearer edge counting for more),
#                        optional fills = [{area, sill}] for parts of the gap that need another
#                        direction, fold_deg / fold_scale (how far it swings, how much shorter it looks)
import json, numpy as np, cv2
from PIL import Image, ImageDraw, ImageFilter

PICTURES = ("silver", "sky", "noir", "amber", "crimson", "forest", "violet", "gold", "ivory")
THEMES = {p: p for p in PICTURES}; THEMES["classic"] = "violet"
# themes added later reuse one of the nine pictures
THEMES.update(nordic="gold", midnight="noir", mocha="sky", carbon="crimson")
# the slot each theme's layout gives the car (dp); the car with its shadow margins is fitted inside it
BOX = {"silver": (590, 430), "sky": (760, 410), "noir": (740, 450), "amber": (690, 380), "crimson": (640, 400),
       "forest": (680, 380), "violet": (700, 400), "gold": (620, 400), "ivory": (640, 400), "classic": (520, 300),
       "nordic": (640, 440), "midnight": (720, 390), "mocha": (700, 470), "carbon": (600, 400)}
MS, MT, MB = 24, 8, 46                                           # margins around the car: room for the shadow
Y3 = np.array([0.299, 0.587, 0.114], np.float32)

class Picture:
    def __init__(self, name):
        self.name = name
        self.G = json.load(open(f"geo_{name}.json"))
        self.SRC = Image.open(f"cut_{name}.png").convert("RGBA")
        self.W, self.H = self.SRC.size
        self.RGB = np.asarray(self.SRC.convert("RGB")).astype(np.float32)
        self.ALPHA = np.asarray(self.SRC.split()[3]).astype(np.float32) / 255.0
        self.LUM = self.RGB @ Y3
        self.YY, self.XX = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
        self.bbox = self.SRC.split()[3].point(lambda v: 255 if v > 100 else 0).getbbox()
        self._off = None

    def poly(self, points, feather=1.5):
        m = Image.new("L", (self.W * 4, self.H * 4), 0)          # drawn 4x and reduced: smooth sub-pixel edges
        ImageDraw.Draw(m).polygon([(p[0] * 4 + 2, p[1] * 4 + 2) for p in points], fill=255)
        m = m.resize((self.W, self.H), Image.BOX)
        if feather: m = m.filter(ImageFilter.GaussianBlur(feather))
        return np.asarray(m).astype(np.float32) / 255.0

    def blob(self, cx, cy, rx, ry=None, soft=0.35):
        """1 in the middle of the ellipse, easing to 0 at its edge."""
        ry = ry or rx
        d = np.sqrt(((self.XX - cx) / rx) ** 2 + ((self.YY - cy) / ry) ** 2)
        return np.clip((1.0 - d) / soft, 0, 1)

    def lights_off(self):
        """The picture was taken with the lamps on. Returns it with them off, and where it was changed (0..1)."""
        if self._off is not None: return self._off
        G, RGB, LUM = self.G, self.RGB, self.LUM
        out = RGB.copy(); changed = np.zeros((self.H, self.W), np.float32)
        cool = np.array([0.94, 0.98, 1.05], np.float32)
        x0, y0, x1, y1 = G["body_ref"]; ref = RGB[y0:y1, x0:x1].reshape(-1, 3).mean(0); ref = ref / (ref @ Y3)
        def apply(m, target):
            nonlocal out, changed
            out = out * (1 - m[..., None]) + target * m[..., None]; changed = np.maximum(changed, m)
        for key in ("lamp_near", "lamp_far"):
            L = G[key]; o = dict(gamma=1.5, level=0.9, bright=150.0, knee=62.0, gain=0.42, lens=0.55, warm=55.0); o.update(L.get("off", {}))
            inner = self.poly(L["inner"], 2.0)
            if o["gamma"]:                                                     # the whole glass: darker, same contrast, no warmth
                lum = 255.0 * (LUM / 255.0) ** o["gamma"] * o["level"]
                apply(inner, lum[..., None] * cool)
            else:                                                              # only what glows (the studio picture)
                warm = np.clip((RGB[..., 0] - RGB[..., 2]) / o["warm"], 0, 1)
                bright = np.clip((LUM - o["bright"]) / 70.0, 0, 1)
                lum = np.minimum(LUM, o["knee"] + o["gain"] * LUM)             # glass and chrome with no bulb behind them
                apply(inner * np.maximum(warm, bright), lum[..., None] * cool)
            for cx, cy, r in L.get("cores", []):
                k = self.blob(cx, cy, r * 1.25, soft=0.5)
                l2 = out @ Y3
                apply(k * o["lens"], (l2 * 0.5)[..., None] * cool)             # the lens itself goes dark
        warm = np.clip((RGB[..., 0] - RGB[..., 2]) / 55.0, 0, 1)
        bright = np.clip((LUM - 150.0) / 70.0, 0, 1)
        glass = np.maximum(self.poly(G["lamp_near"]["inner"], 2.0), self.poly(G["lamp_far"]["inner"], 2.0))
        for sp in G.get("spills", []):                                         # lamp glow lying on the paint
            sx, sy, srx, sry = sp[:4]; st = sp[4] if len(sp) > 4 else 1.0
            s = self.blob(sx, sy, srx, sry, soft=0.8) * np.maximum(warm, bright) * st * (1 - glass)   # the glass is already done
            apply(s, np.minimum(LUM * 0.86, 50 + 0.48 * LUM)[..., None] * ref)
        for F in G.get("fogs", []):
            if "spill" in F:
                sx, sy, srx, sry = F["spill"]; s = self.blob(sx, sy, srx, sry, soft=0.8) * np.maximum(warm, bright)
                apply(s, np.minimum(LUM * 0.86, 50 + 0.48 * LUM)[..., None] * ref)
            if "recess" in F:
                rx_, ry_, rrx, rry = F["recess"]; rec = self.blob(rx_, ry_, rrx, rry, soft=0.25)
                apply(rec * 0.9, (np.minimum(LUM * 0.5, 30 + 0.22 * LUM))[..., None] * cool)    # dark housing
                cx, cy, r = F["core"]; k = self.blob(cx, cy, r, soft=0.3)
                gl = 46 + 26 * self.blob(cx - r * 0.25, cy - r * 0.3, r * 0.6, soft=1.0)        # a little shine on the glass
                apply(k, gl[..., None] * cool)
        self._off = (np.clip(out, 0, 255), changed)
        return self._off

    def turn_lit(self, points, bulb):
        """An indicator section switched on: the lamp's own pixels turned amber, hottest at the bulb. Returns rgb, alpha."""
        m = self.poly(points, 1.2)
        xs = [p[0] for p in points]; ys = [p[1] for p in points]
        size = max(max(xs) - min(xs), max(ys) - min(ys))
        amber = np.array([255, 150, 16], np.float32); hot = np.array([255, 232, 150], np.float32)
        lum = np.clip(self.LUM / 255.0, 0, 1)
        lit = amber * (0.50 + 0.75 * lum[..., None])
        d = np.sqrt((self.XX - bulb[0]) ** 2 + (self.YY - bulb[1]) ** 2)
        core = np.exp(-(d / (size * 0.26)) ** 2)
        lit = lit * (1 - 0.9 * core[..., None]) + hot * 0.9 * core[..., None]
        halo = cv2.GaussianBlur(m, (0, 0), size * 0.30) * 0.75 + cv2.GaussianBlur(m, (0, 0), size * 0.85) * 0.35
        halo = np.clip(halo, 0, 0.8) * np.clip(cv2.GaussianBlur(self.ALPHA, (0, 0), 2.0) * 1.2, 0, 1)
        a = np.maximum(m * 0.97, halo)
        rgb = lit * m[..., None] + np.array([255, 166, 40], np.float32) * (1 - m[..., None])
        return np.clip(rgb, 0, 255), np.clip(a, 0, 1)

    def frame(self, box):
        """How this picture sits in a theme's slot: the car's bounding box plus margins, scaled to fit the box."""
        bx0, by0, bx1, by1 = self.bbox
        Wc, Hc = bx1 - bx0 + 2 * MS, by1 - by0 + MT + MB
        sc = min(box[0] / Wc, box[1] / Hc)
        return dict(bx0=bx0, by0=by0, ms=MS, mt=MT, sc=sc, w=round(Wc * sc), h=round(Hc * sc), Wc=Wc, Hc=Hc, src=self.name)

    def canvas(self, img):
        """Crop a full-size layer to the car and add the margins."""
        bx0, by0, bx1, by1 = self.bbox
        out = Image.new("RGBA", (bx1 - bx0 + 2 * MS, by1 - by0 + MT + MB), (0, 0, 0, 0))
        out.alpha_composite(img.crop((bx0, by0, bx1, by1)), (MS, MT)); return out

_pics = {}
def picture(name):
    if name not in _pics: _pics[name] = Picture(name)
    return _pics[name]

def rgba(rgb, a):
    return Image.fromarray(np.dstack([rgb, a * 255.0]).round().astype(np.uint8), "RGBA")
