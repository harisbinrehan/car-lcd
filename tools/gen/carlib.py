# Shared by cars.py and parts.py: the one car picture every theme uses, and how its lamps look off and on.
import json, numpy as np, cv2
from PIL import Image, ImageDraw, ImageFilter

G = json.load(open("car.json"))
SRC = Image.open("cut_premium.png").convert("RGBA")
W, H = SRC.size
RGB = np.asarray(SRC.convert("RGB")).astype(np.float32)
ALPHA = np.asarray(SRC.split()[3]).astype(np.float32) / 255.0
LUM = RGB @ np.array([0.299, 0.587, 0.114], np.float32)
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)

def poly(points, feather=1.5):
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).polygon([tuple(p) for p in points], fill=255)
    if feather: m = m.filter(ImageFilter.GaussianBlur(feather))
    return np.asarray(m).astype(np.float32) / 255.0

def blob(cx, cy, rx, ry=None, soft=0.35):
    """1 in the middle of the ellipse, easing to 0 at its edge."""
    ry = ry or rx
    d = np.sqrt(((XX - cx) / rx) ** 2 + ((YY - cy) / ry) ** 2)
    return np.clip((1.0 - d) / soft, 0, 1) ** 1.0

def lights_off():
    """The picture was taken with the lamps on. Returns it with them off, and where it was changed (0..1)."""
    out = RGB.copy(); changed = np.zeros((H, W), np.float32)
    warm = np.clip((RGB[..., 0] - RGB[..., 2]) / 55.0, 0, 1)
    bright = np.clip((LUM - 150.0) / 70.0, 0, 1)
    cool = np.array([0.94, 0.98, 1.05], np.float32)
    x0, y0, x1, y1 = G["body_ref"]; ref = RGB[y0:y1, x0:x1].reshape(-1, 3).mean(0); ref = ref / (ref @ np.array([0.299, 0.587, 0.114]))
    def apply(m, target):
        nonlocal out, changed
        out = out * (1 - m[..., None]) + target * m[..., None]; changed = np.maximum(changed, m)
    for key in ("lamp_near", "lamp_far"):
        L = G[key]; inner = poly(L["inner"], 2.0)
        lum = np.minimum(LUM, 62 + 0.42 * LUM)                       # glass and chrome with no bulb behind them
        apply(inner * np.maximum(warm, bright), lum[..., None] * cool)
        cx, cy, r = L["core"]; k = blob(cx, cy, r * 1.25, soft=0.5)
        l2 = (out @ np.array([0.299, 0.587, 0.114], np.float32))
        apply(k * 0.55, (l2 * 0.5)[..., None] * cool)                # the projector lens itself goes dark
    for key in ("fog_near", "fog_far"):
        F = G[key]
        sx, sy, srx, sry = F["spill"]; s = blob(sx, sy, srx, sry, soft=0.8) * np.maximum(warm, bright)
        lum = np.minimum(LUM * 0.86, 50 + 0.48 * LUM)
        apply(s, lum[..., None] * ref)                               # the lamp's glow on the paint
        rx_, ry_, rrx, rry = F["recess"]; rec = blob(rx_, ry_, rrx, rry, soft=0.25)
        apply(rec * 0.9, (np.minimum(LUM * 0.5, 30 + 0.22 * LUM))[..., None] * cool)   # dark housing
        cx, cy, r = F["core"]; k = blob(cx, cy, r, soft=0.3)
        gl = 46 + 26 * blob(cx - r * 0.25, cy - r * 0.3, r * 0.6, soft=1.0)             # a little shine on the glass
        apply(k, gl[..., None] * cool)
    return np.clip(out, 0, 255), changed

def turn_lit(points, bulb):
    """An indicator section switched on: the lamp's own pixels turned amber, hottest at the bulb. Returns rgb, alpha."""
    m = poly(points, 1.2)
    xs = [p[0] for p in points]; ys = [p[1] for p in points]
    size = max(max(xs) - min(xs), max(ys) - min(ys))
    amber = np.array([255, 150, 16], np.float32); hot = np.array([255, 232, 150], np.float32)
    lum = np.clip(LUM / 255.0, 0, 1)
    lit = amber * (0.50 + 0.75 * lum[..., None])
    d = np.sqrt((XX - bulb[0]) ** 2 + (YY - bulb[1]) ** 2)
    core = np.exp(-(d / (size * 0.26)) ** 2)
    lit = lit * (1 - 0.9 * core[..., None]) + hot * 0.9 * core[..., None]
    halo = cv2.GaussianBlur(m, (0, 0), size * 0.30) * 0.75 + cv2.GaussianBlur(m, (0, 0), size * 0.85) * 0.35
    halo = np.clip(halo, 0, 0.8) * np.clip(cv2.GaussianBlur(ALPHA, (0, 0), 2.0) * 1.2, 0, 1)
    a = np.maximum(m * 0.97, halo)
    rgb = lit * m[..., None] + np.array([255, 166, 40], np.float32) * (1 - m[..., None])
    return np.clip(rgb, 0, 255), np.clip(a, 0, 1)

def rgba(rgb, a):
    return Image.fromarray(np.dstack([rgb, a * 255.0]).round().astype(np.uint8), "RGBA")
