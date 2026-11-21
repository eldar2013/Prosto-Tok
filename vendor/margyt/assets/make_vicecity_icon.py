"""The Vice City icon for the GTA VI release: MargyT's note glowing white over a sunset, palms at the edges.

Writes icons/vicecity/{background,foreground,full}.png at 1024 px, in the 108-unit adaptive-icon square:
the background fills it all, the note and the star stay inside the middle 72 units a launcher never cuts.

python3 assets/make_vicecity_icon.py
"""
import math
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
from margyt import artwork  # noqa: E402

S = 1024
K = S / artwork.VIEWPORT
OUT = os.path.join(ROOT, "icons", "vicecity")


def gradient():
    """Blue in the top left corner to hot pink in the bottom right, through violet."""
    y, x = np.mgrid[0:S, 0:S].astype(np.float32) / S
    t = np.clip((x * 0.45 + y * 0.75) / 1.2, 0, 1)[..., None]
    stops = [(0.0, (30, 52, 214)), (0.45, (120, 70, 200)), (0.75, (214, 76, 178)), (1.0, (255, 84, 160))]
    out = np.zeros((S, S, 3), np.float32)
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        k = np.clip((t - a) / (b - a), 0, 1)
        sel = (t >= a) & (t <= b)
        out = np.where(sel, np.array(ca) * (1 - k) + np.array(cb) * k, out)
    return Image.fromarray(out.astype(np.uint8)).convert("RGBA")


def bezier(p0, p1, p2, t):
    return ((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
            (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])


def frond(draw, p0, p1, p2, body, spikes, colour, seed):
    """A frond as Rockstar draws one: a solid curved body, widest past its middle and narrowing to the tip, its
    edges cut into long pointed teeth that all lean towards the tip. `spikes` is per edge: (count, longest, angle
    off the stalk in radians, how much they vary); an edge with no teeth stays a smooth curve."""
    import random
    rnd = random.Random(seed)
    n = 90
    spine = [bezier(p0, p1, p2, i / n) for i in range(n + 1)]

    def frame(i):
        (ax, ay), (bx, by) = spine[max(0, i - 1)], spine[min(n, i + 1)]
        dx, dy = bx - ax, by - ay
        m = math.hypot(dx, dy) or 1.0
        return dx / m, dy / m

    def width(u):
        return body * math.sin(min(1.0, u * 1.15) * math.pi * 0.5) ** 0.5 * (1 - u) ** 0.55

    left, right = [], []
    for i in range(n + 1):
        u = i / n
        (x, y), (dx, dy) = spine[i], frame(i)
        w = width(u)
        left.append((x - dy * w, y + dx * w))
        right.append((x + dy * w, y - dx * w))
    draw.polygon(left + right[::-1], fill=colour)
    for side, (count, longest, angle, vary) in zip((1, -1), spikes):
        if count <= 0:
            continue
        for k in range(count):
            u = 0.08 + 0.9 * (k + rnd.uniform(-0.3, 0.3)) / count
            u = min(0.97, max(0.04, u))
            i = int(u * n)
            (x, y), (dx, dy) = spine[i], frame(i)
            w = width(u)
            nx, ny = -dy * side, dx * side
            ex, ey = x + nx * w * 0.85, y + ny * w * 0.85
            length = longest * (0.7 + 0.3 * math.sin(min(1.0, u * 1.3) * math.pi * 0.5)) * (1.1 - 0.45 * u)
            length *= rnd.uniform(1 - vary, 1 + vary * 0.6)
            a = angle * rnd.uniform(0.8, 1.2)
            tx = ex + (dx * math.cos(a) + nx * math.sin(a)) * length
            ty = ey + (dy * math.cos(a) + ny * math.sin(a)) * length
            # slender: a narrow base, long to the point
            base = min(max(7.0, w * rnd.uniform(0.25, 0.4)), body * 0.3)
            # the tooth: a long thin triangle from the edge, its base along the stalk
            draw.polygon([(ex - dx * base, ey - dy * base), (ex + dx * base, ey + dy * base), (tx, ty)], fill=colour)


def palms(sky):
    """Laid out as on Rockstar's picture: two fronds hang in from the top right corner, three arch up out of the
    bottom left one. Not one flat colour: each frond is the sky behind it darkened, deepest at the stalk's base
    and lighter towards its tip, so the leaves carry the same sunset as the background."""
    sky_rgb = np.asarray(sky.convert("RGB"), np.float32)
    y, x = np.mgrid[0:S, 0:S].astype(np.float32)
    out = np.zeros((S, S, 4), np.float32)
    # edges blend into the sky, not into black: black here drew a dark outline round every frond
    out[..., :3] = sky_rgb
    # in pixels of the 1024 picture, read off Rockstar's: base off the edge, a control point, the tip
    fronds = [
        # top right: along the top, teeth hanging under it, swept towards the tip
        ((1120, -30), (860, 20), (600, 118), 70, ((0, 0, 0, 0), (30, 150, 0.95, 0.3)), 0.50, 0.74),
        # top right: down the right side, a thick crescent, teeth down its inner edge
        ((1110, 110), (860, 210), (795, 540), 95, ((30, 175, 0.6, 0.3), (0, 0, 0, 0)), 0.48, 0.74),
        # bottom left: the top one a feather with teeth both sides, the lower two arches with teeth beneath
        # drawn back to front: the lowest arch, the feather, then the middle arch over both
        ((-50, 1070), (240, 830), (500, 930), 78, ((28, 160, 0.9, 0.3), (0, 0, 0, 0)), 0.74, 0.92),
        ((-50, 770), (90, 640), (235, 540), 52, ((18, 100, 0.5, 0.35), (18, 110, 0.5, 0.35)), 0.52, 0.76),
        ((-50, 900), (220, 680), (470, 750), 72, ((28, 150, 0.9, 0.3), (0, 0, 0, 0)), 0.52, 0.74),
    ]
    for p0, p1, p2, body, spikes, dark_base, dark_tip in fronds:
        mask = Image.new("L", (S, S), 0)
        k = S / 1024
        sc = lambda q: (q[0] * k, q[1] * k)
        frond(ImageDraw.Draw(mask), sc(p0), sc(p1), sc(p2), body * k,
              tuple((c, l * k, ang, v) for c, l, ang, v in spikes), 255, seed=int(p0[1] * 13 + p2[0]))
        m = np.asarray(mask.filter(ImageFilter.GaussianBlur(1.2)), np.float32)[..., None] / 255.0
        bx, by = sc(p0)
        tx, ty = sc(p2)
        length = math.hypot(tx - bx, ty - by) or 1.0
        along = np.clip(np.hypot(x - bx, y - by) / length, 0, 1)[..., None]
        shade = dark_base + (dark_tip - dark_base) * along
        colour = sky_rgb * shade
        colour[..., 2] = np.minimum(255, colour[..., 2] * 1.08)
        out[..., :3] = out[..., :3] * (1 - m) + colour * m
        out[..., 3:] = np.maximum(out[..., 3:], m * 255)
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")


def note_polygons():
    """The note's outline from artwork, as polygons in pixels."""
    polys, cur = [], []
    for cmd, xs, ys in re.findall(r"([MLZ])\s*(?:([-\d.]+),([-\d.]+))?", artwork._NOTE):
        if cmd == "M":
            if cur:
                polys.append(cur)
            cur = [(float(xs), float(ys))]
        elif cmd == "L":
            cur.append((float(xs), float(ys)))
        elif cmd == "Z" and cur:
            polys.append(cur)
            cur = []
    if cur:
        polys.append(cur)
    return polys


def glyph_layer():
    """The note where Rockstar has its R, a little left of centre, and a star outline at its foot."""
    k = 4
    big = Image.new("L", (S * k, S * k), 0)
    d = ImageDraw.Draw(big)
    # the note sits in 26..76 of the 108 square; move it up-left and grow it a little
    cx, cy, grow = 50.0, 50.0, 1.12
    dx, dy = -6.0, -4.0
    for poly in note_polygons():
        pts = [((cx + (x - cx) * grow + dx) * K * k, (cy + (y - cy) * grow + dy) * K * k) for x, y in poly]
        d.polygon(pts, fill=255)
    # a five-pointed star outline, as Rockstar's, at the note's lower right
    sx, sy, r_out, r_in = 79.0 * K * k, 74.0 * K * k, 13.5 * K * k, 5.8 * K * k
    star = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = r_out if i % 2 == 0 else r_in
        star.append((sx + math.cos(a) * r, sy + math.sin(a) * r))
    # the outline starts at an inner corner: where a line starts and ends there is no join, and the top tip came out cut
    star = star[1:] + star[:1]
    d.line(star + star[:2], fill=255, width=int(2.4 * K * k), joint="curve")
    mask = big.resize((S, S), Image.LANCZOS)
    white = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    white.putalpha(mask)
    # the neon glow: the same shapes blurred wide, pink-white, under the sharp ones
    glow = Image.new("RGBA", (S, S), (255, 214, 245, 0))
    glow.putalpha(mask.filter(ImageFilter.GaussianBlur(S * 0.03)).point(lambda v: min(255, int(v * 1.6))))
    halo = Image.new("RGBA", (S, S), (255, 160, 230, 0))
    halo.putalpha(mask.filter(ImageFilter.GaussianBlur(S * 0.07)).point(lambda v: min(255, int(v * 1.1))))
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.alpha_composite(halo)
    out.alpha_composite(glow)
    out.alpha_composite(white)
    # all of it inside the circle no launcher mask cuts: 34 of the 54 units from the centre
    f = 0.78
    small = out.resize((round(S * f), round(S * f)), Image.LANCZOS)
    fitted = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fitted.alpha_composite(small, ((S - small.width) // 2, (S - small.height) // 2))
    return fitted


def main():
    os.makedirs(OUT, exist_ok=True)
    background = gradient()
    background.alpha_composite(palms(background))
    foreground = glyph_layer()
    full = background.copy()
    full.alpha_composite(foreground)
    background.save(os.path.join(OUT, "background.png"))
    foreground.save(os.path.join(OUT, "foreground.png"))
    full.save(os.path.join(OUT, "full.png"))
    print("written", OUT)


if __name__ == "__main__":
    main()
