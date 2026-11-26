# parts.py <res> <javaDir>: the pieces of each theme's car that move or light up, cut from that theme's
# picture (cut_<picture>.png with geo_<picture>.json, see carlib.py), and CarParts.java telling CarView
# where they sit. Run cars.py first (it writes meta.json).
#   wheel_f/r   rim, un-squashed to a flat circle so it can be turned
#   mirror      the door mirror head;  mirrorlit = the same with its indicator strip on
#   mirrorbg    the door and glass that show once the head has folded away
#   blink       the indicator sections of both headlamps switched on
import sys, json, math, numpy as np, cv2
from PIL import Image, ImageDraw, ImageFilter
import carlib as C
RAD = 72

def rim(P, off, wd):
    """The rim is an ellipse leaning a little; map it onto a circle."""
    (cx, cy), rx, ry, t = wd["c"], wd["rx"], wd["ry"], math.radians(wd["tilt"])
    # circle point (u,v) in -1..1 -> picture: rotate(tilt) * (u*rx, v*ry) + centre
    A = np.array([[math.cos(t) * rx, -math.sin(t) * ry, cx], [math.sin(t) * rx, math.cos(t) * ry, cy]], np.float32)
    S = np.array([[1 / RAD, 0, -1], [0, 1 / RAD, -1], [0, 0, 1]], np.float32)       # disc pixel -> -1..1
    Mx = A @ S
    disc = cv2.warpAffine(off.astype(np.uint8), Mx, (RAD * 2, RAD * 2), flags=cv2.INTER_CUBIC | cv2.WARP_INVERSE_MAP)
    a = Image.new("L", (RAD * 2, RAD * 2), 0); ImageDraw.Draw(a).ellipse((3, 3, RAD * 2 - 3, RAD * 2 - 3), fill=255)
    solid = cv2.warpAffine(P.ALPHA, Mx, (RAD * 2, RAD * 2), flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP)   # a rim at the picture's edge
    a = np.asarray(a.filter(ImageFilter.GaussianBlur(2))).astype(np.float32) * np.clip(solid, 0, 1)
    im = Image.fromarray(disc).convert("RGBA"); im.putalpha(Image.fromarray(a.round().astype(np.uint8))); return im

def behind(P, off, mi):
    """What is behind the mirror head: carry the door's glass, trim and paint along the lines they run in into the gap."""
    hole = cv2.dilate((P.poly(mi["head"], 0) > 0.5).astype(np.uint8), np.ones((7, 7), np.uint8)) > 0
    def dirs(s): return [s] if isinstance(s[0], (int, float)) else s           # one direction, or several to blend
    which = np.zeros((P.H, P.W), np.int32); sets = [dirs(mi["sill"])]
    for f in mi.get("fills", []):
        which[P.poly(f["area"], 0) > 0.5] = len(sets); sets.append(dirs(f["sill"]))
    bg = off.copy(); solid = P.ALPHA > 0.6
    ys_, xs_ = np.nonzero(hole)
    for y, x in zip(ys_, xs_):
        tot = np.zeros(3, np.float32); wsum = 0.0
        for dx, dy in sets[which[y, x]]:
            t = 1.0
            while True:
                px, py = x - dx * t, y - dy * t
                if not hole[int(round(py)), int(round(px))]: break
                t += 1.0
            acc = np.zeros(3, np.float32); n = 0
            for e in (1.5, 3.0, 4.5):                           # a few steps past the edge, so one odd pixel cannot streak
                qx, qy = int(round(px - dx * e)), int(round(py - dy * e))
                if not hole[qy, qx] and solid[qy, qx]: acc += off[qy, qx]; n += 1
            if n: tot += acc / n / (t * t); wsum += 1.0 / (t * t)                # the nearer edge counts for more
        if wsum: bg[y, x] = tot / wsum
    soft = cv2.GaussianBlur(bg, (0, 0), 1.1); bg[hole] = soft[hole]
    return bg, np.clip(cv2.GaussianBlur(hole.astype(np.float32), (0, 0), 1.0) * 1.5, 0, 1) * P.ALPHA

def pieces(P):
    """Everything cut from one picture, at the cutout's own size."""
    G = P.G; off, _ = P.lights_off(); mi = G["mirror"]
    head = P.poly(mi["head"], 0.8)
    xs = [p[0] for p in mi["head"]]; ys = [p[1] for p in mi["head"]]
    pad = 46                                                    # room for the head to swing up when it folds
    box = (int(min(xs)) - 10, int(min(ys)) - pad, int(max(xs)) + 10, int(max(ys)) + 8)
    bg, bga = behind(P, off, mi)
    lit_rgb, lit_a = P.turn_lit(mi["turn"], mi["bulb"])
    lit = off * (1 - lit_a[..., None]) + lit_rgb * lit_a[..., None]
    bl = np.zeros((P.H, P.W, 4), np.float32)                    # the indicators in the headlamps
    for key in ("lamp_near", "lamp_far"):
        if "turn" not in G[key]: continue
        rgb, a = P.turn_lit(G[key]["turn"], G[key]["bulb"])
        keep = a >= bl[..., 3]
        bl[..., :3] = np.where(keep[..., None], rgb, bl[..., :3]); bl[..., 3] = np.maximum(bl[..., 3], a)
    return dict(wheel_f=rim(P, off, G["wheel_f"]), wheel_r=rim(P, off, G["wheel_r"]), box=box,
                mirror=C.rgba(off, head * P.ALPHA).crop(box), mirrorlit=C.rgba(lit, np.maximum(head, lit_a * 0.9) * P.ALPHA).crop(box),
                mirrorbg=C.rgba(bg, bga).crop(box), blink=P.canvas(C.rgba(bl[..., :3], bl[..., 3])))

def values(P, m, box):
    """One theme's line of CarParts: the picture's geometry in that theme's car_<theme>.png pixels."""
    G = P.G; mi = G["mirror"]; sc = m["sc"]
    def fx(x): return (x - m["bx0"] + m["ms"]) * sc
    def fy(y): return (y - m["by0"] + m["mt"]) * sc
    vals = [m["w"], m["h"]]
    for n in ("f", "r"):
        wd = G["wheel_" + n]
        vals += [fx(wd["c"][0]), fy(wd["c"][1]), wd["rx"] * sc, wd["ry"] * sc, wd["tilt"]]
    vals += [fx(box[0]), fy(box[1]), (box[2] - box[0]) * sc, (box[3] - box[1]) * sc,
             fx(mi["hinge"][0]), fy(mi["hinge"][1]), mi["fold_deg"], mi["fold_scale"]]
    vals += [len(G["fogs"])]
    for F in G["fogs"]: vals += [fx(F["core"][0]), fy(F["core"][1])]
    for x, y in G.get("plate", [[0, 0]] * 4): vals += [fx(x) if x else 0, fy(y) if y else 0]   # blank plate: TL, TR, BR, BL
    return vals

if __name__ == "__main__":
    R, J = sys.argv[1], sys.argv[2]
    M = json.load(open("meta.json")); done = {}; java = []
    for k, m in M.items():
        P = C.picture(m["src"])
        if m["src"] not in done: done[m["src"]] = pieces(P)
        pc = done[m["src"]]; sc = m["sc"]; box = pc["box"]
        for n in ("f", "r"): pc["wheel_" + n].save(f"{R}/drawable-nodpi/wheel_{n}_{k}.png", optimize=True)
        fw, fh = max(2, round((box[2] - box[0]) * sc * 2)), max(2, round((box[3] - box[1]) * sc * 2))   # stored at 2x for sharpness
        for name in ("mirror", "mirrorlit", "mirrorbg"):
            pc[name].resize((fw, fh), Image.LANCZOS).save(f"{R}/drawable-nodpi/{name}_{k}.png", optimize=True)
        pc["blink"].resize((m["w"], m["h"]), Image.LANCZOS).save(f"{R}/drawable-nodpi/blink_{k}.png", optimize=True)
        java.append(f'            case "{k}": return new float[]{{' + ", ".join(f"{v:.2f}f" for v in values(P, m, box)) + "};")
    open(J + "/CarParts.java", "w").write('''package com.harislcd.launcher;

/** Generated by parts.py: where the wheels, the door mirror and the fog lamps sit in each theme's car picture. */
final class CarParts {
    private CarParts() {}

    /**
     * In picture pixels: width, height; front rim (cx, cy, rx, ry, lean in degrees); rear rim (the same five);
     * mirror patch (left, top, width, height); mirror hinge (x, y); how far the mirror swings when it folds
     * (degrees) and how much shorter it looks folded; number of fog lamps (the indicator tap points) and
     * their centres (x, y); then the four corners of the blank number plate (top left, top right, bottom
     * right, bottom left; all zero if the picture has none), where the owner's own plate text is drawn.
     */
    static float[] of(String theme) {
        switch (theme) {
''' + "\n".join(java) + '''
            default: return of("classic");
        }
    }
}
''')
    print("parts for", len(M), "themes")
