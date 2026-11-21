#!/usr/bin/env python3
"""The avatar frames, drawn from height maps and lit like metal.

    python3 assets/frames/make_frames.py   ->  server/icons/frame_supporter.png, frame_owner.png

Each picture is square. The avatar sits in its centre at a diameter of 1/scale of the picture's side
(`scale` is the number in server/badges.py FRAMES, and it has to match).
"""
import os
import numpy as np
from PIL import Image, ImageDraw

SIZE = 1024
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "server", "icons")
yy, xx = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def blur(a, r):
    """A Gaussian blur through the Fourier transform: floats stay floats, so slopes have no steps."""
    fy = np.fft.fftfreq(a.shape[0])[:, None]
    fx = np.fft.rfftfreq(a.shape[1])[None, :]
    kernel = np.exp(-2 * (np.pi * r) ** 2 * (fx * fx + fy * fy))
    return np.fft.irfft2(np.fft.rfft2(a) * kernel, s=a.shape).astype(np.float32)


def light(height, base, strength=7.0, shine=26, sun=(-0.45, -0.6, 0.66), ramp=None):
    """Colour for a height map: gold ramp by how the surface faces the light, and a sharp highlight."""
    gy, gx = np.gradient(height)
    nx, ny, nz = -gx * strength, -gy * strength, np.ones_like(height)
    n = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny, nz = nx / n, ny / n, nz / n
    lx, ly, lz = sun
    d = np.clip(nx * lx + ny * ly + nz * lz, 0, 1)
    # the half vector towards a viewer straight above
    hx, hy, hz = lx, ly, lz + 1
    hn = (hx * hx + hy * hy + hz * hz) ** 0.5
    spec = np.clip((nx * hx + ny * hy + nz * hz) / hn, 0, 1) ** shine
    # metal is mostly reflection: dark brown in shade, deep gold, pale gold in the light
    stops = ramp or [(0.0, (46, 24, 6)), (0.35, (122, 74, 16)), (0.62, (206, 150, 44)), (0.85, (246, 208, 104)), (1.0, (255, 240, 186))]
    xs = np.array([s[0] for s in stops], dtype=np.float32)
    colour = np.stack([np.interp(d, xs, [s[1][i] for s in stops]) for i in range(3)], -1)
    colour = colour + spec[..., None] * np.array([255, 244, 214]) * 0.75
    return np.clip(colour * base[..., None], 0, 255)


def rgba(colour, alpha):
    return np.dstack([colour, np.clip(alpha, 0, 1) * 255]).astype(np.uint8)


def noise(amount, r=2.0, seed=4):
    rng = np.random.default_rng(seed)
    n = rng.random((SIZE, SIZE), dtype=np.float32)
    n = blur(n, r)
    return (n - n.mean()) / (n.std() + 1e-6) * amount


# ------------------------------------------------------------ the museum frame

def supporter():
    """A round gilt frame, the kind an old portrait hangs in. It comes in a little over the avatar's edge."""
    scale = 1.30
    c = SIZE / 2
    x, y = xx - c, yy - c
    r = np.sqrt(x * x + y * y)
    theta = np.arctan2(y, x)
    avatar = SIZE / scale / 2                       # the avatar's own radius
    inner = avatar * 0.91                           # where the frame starts: inside the avatar
    outer = avatar * 1.15
    band = outer - inner
    t = (r - inner) / band                          # 0 inner edge .. 1 outer edge

    # a carved profile across the band: bead, cove, big torus, fine edge
    profile = (
        0.22 * np.exp(-((t - 0.08) / 0.07) ** 2)
        + 0.05 * smooth(0.14, 0.26, t)
        + 0.62 * np.exp(-((t - 0.58) / 0.26) ** 2)
        + 0.24 * np.exp(-((t - 0.95) / 0.06) ** 2)
    )
    # leaves round the torus and beads round the inner edge
    leaves = 44
    leaf = np.cos(theta * leaves / 2) ** 2 * np.exp(-((t - 0.58) / 0.20) ** 2)
    tips = 0.5 + 0.5 * np.cos(theta * leaves + t * 12)
    carved = 0.24 * leaf * tips
    beads = 0.11 * np.cos(theta * 56) ** 4 * np.exp(-((t - 0.08) / 0.06) ** 2)

    # shells standing out past the edge: big ones on the diagonals, small ones between
    height_shell = np.zeros_like(r)
    ribs_all = np.zeros_like(r)
    for k in range(8):
        a = k * np.pi / 4 + np.pi / 4
        big = k % 2 == 0
        reach = outer + (10 if big else 2)
        cx, cy = np.cos(a) * reach, np.sin(a) * reach
        d = np.hypot(x - cx, y - cy)
        size = 56 if big else 36
        lobe = np.exp(-(d / size) ** 2) * (1.0 if big else 0.8)
        spokes = 0.5 + 0.5 * np.cos(np.arctan2(y - cy, x - cx) * (11 if big else 9))
        use = lobe > height_shell
        ribs_all = np.where(use, spokes, ribs_all)
        height_shell = np.maximum(height_shell, lobe)
    shell = height_shell * (0.62 + 0.38 * ribs_all)

    ring = smooth(-0.03, 0.03, t) * (1 - smooth(0.98, 1.04, t))
    silhouette = np.maximum(ring, smooth(0.20, 0.34, shell) * smooth(-0.03, 0.03, t))
    height = (profile + carved + beads) * ring
    height = np.maximum(height, shell * 1.15 * silhouette)
    height = blur(height, 1.6)
    grain = noise(0.006, 1.2)

    cavity = np.clip((height - blur(height, 9)) * 7 + 1, 0.55, 1.2)
    gold = light(height + grain * silhouette, cavity, strength=8.5, shine=24)

    # a shadow on the page behind it, and one on the avatar under its inner edge
    shadow = blur(silhouette, 11) * 0.42
    colour = gold * silhouette[..., None]
    alpha = np.maximum(silhouette, shadow)
    return rgba(colour, alpha)


# ------------------------------------------------------------------- the crown

def polygon_mask(points, blur_r=0.0):
    k = 3
    im = Image.new("L", (SIZE * k, SIZE * k), 0)
    ImageDraw.Draw(im).polygon([(px * k, py * k) for px, py in points], fill=255)
    im = im.resize((SIZE, SIZE), Image.LANCZOS)
    out = np.asarray(im, dtype=np.float32) / 255
    return blur(out, blur_r) if blur_r else out


def owner():
    scale = 1.85
    c = SIZE / 2
    hole = SIZE / scale / 2
    # the crown's own space: it's drawn level, then tipped to one side like it was put on in a hurry
    base_y = c - hole * 0.82
    wide = hole * 0.86
    top_y = base_y - hole * 0.86
    points = 5
    outline = []
    # the band's bottom is a curve so it sits round the head
    for i in range(0, 41):
        u = -1 + 2 * i / 40
        outline.append((c + u * wide, base_y + (1 - u * u) * 34))
    outline.reverse()
    tips = []
    for i in range(points):
        u = -1 + 2 * i / (points - 1)
        reach = 0.86 + 0.14 * (1 - abs(u))
        tip_x = c + u * wide * 1.10
        tip_y = base_y - (base_y - top_y) * reach
        tips.append((tip_x, tip_y))
    shape = [(c - wide, base_y)]
    for i, (tx, ty) in enumerate(tips):
        shape.append((tx, ty))
        if i < points - 1:
            nx, ny = tips[i + 1]
            shape.append(((tx + nx) / 2, base_y - (base_y - top_y) * 0.33))
    shape.append((c + wide, base_y))
    bottom = [(c + u * wide, base_y + (1 - u * u) * 34 + 30) for u in np.linspace(1, -1, 41)]
    body = polygon_mask(shape + bottom)
    body_soft = blur(body, 5)

    # the height: pillowed body, a raised band, ridges up the middle of each point
    height = body_soft * 0.5 + blur(body, 16) * 0.5
    u = (xx - c) / wide
    band_center = base_y + (1 - np.clip(u, -1, 1) ** 2) * 34 + 2
    band = np.exp(-((yy - band_center) / 26) ** 2) * body
    height += band * 0.42
    for (tx, ty) in tips:
        f = np.clip((yy - ty) / (base_y - ty), 0, 1)
        cx = tx + (c - tx) * 0.12 * f + (c + (tx - c) * 0.90 - tx) * f
        ridge = np.exp(-(((xx - cx) / (8 + 40 * f)) ** 2)) * (1 - f) ** 0.6 * body * (yy > ty)
        height += ridge * 0.34
    height *= 1.0
    height = blur(height, 1.2)

    cavity = np.clip((height - blur(height, 12)) * 5 + 1, 0.62, 1.2)
    gold = light(height + noise(0.004, 1.0) * body, cavity, strength=11, shine=26)
    colour = gold * body[..., None]
    alpha = body.copy()

    def add(mask, rgb):
        nonlocal colour, alpha
        colour = rgb * mask[..., None] + colour * (1 - mask[..., None])
        alpha = np.maximum(alpha, mask)

    # balls on the tips
    for (tx, ty) in tips:
        d = np.hypot(xx - tx, yy - ty)
        radius = 21
        m = smooth(radius + 1, radius - 1, d)
        h = np.sqrt(np.clip(1 - (d / radius) ** 2, 0, 1)) * 0.55
        add(m, light(blur(h, 1.0), np.ones_like(h), strength=26, shine=34))

    # jewels on the band: red and green cut stones, with a spark
    jewels = [(-0.70, (172, 20, 38), 21), (-0.35, (24, 118, 84), 19), (0.0, (172, 20, 38), 27), (0.35, (24, 118, 84), 19), (0.70, (172, 20, 38), 21)]
    for ux, rgb, radius in jewels:
        jx = c + ux * wide * 0.95
        jy = base_y + (1 - ux * ux) * 34 + 3
        d = np.hypot(xx - jx, (yy - jy) * 1.08)
        m = smooth(radius + 1, radius - 1, d)
        rim = smooth(radius + 7, radius + 5, d)
        h = np.sqrt(np.clip(1 - (d / (radius + 7)) ** 2, 0, 1)) * 0.3
        add(rim, light(blur(h, 0.8), np.ones_like(h), strength=20, shine=30))
        angle = np.arctan2(yy - jy, xx - jx)
        facets = 0.74 + 0.26 * np.sign(np.sin(angle * 4 + d / radius * 2.4))
        depth = 0.55 + 0.45 * np.clip(1 - (np.hypot(xx - jx + radius * 0.3, yy - jy + radius * 0.35) / radius), 0, 1)
        stone = np.stack([rgb[i] * facets * depth for i in range(3)], -1) * 1.5
        spark = np.exp(-(np.hypot(xx - jx + radius * 0.34, yy - jy + radius * 0.38) / (radius * 0.2)) ** 2)
        stone = stone + spark[..., None] * np.array([255, 255, 255])
        add(m, np.clip(stone, 0, 255))

    # tip it
    picture = Image.fromarray(rgba(colour, alpha), "RGBA").rotate(9, center=(c, base_y), resample=Image.BICUBIC)
    # a shadow it throws on the avatar under it
    a = np.asarray(picture, dtype=np.float32)[..., 3] / 255
    shadow = np.roll(blur(a, 10), 12, axis=0) * 0.45
    out = np.zeros((SIZE, SIZE, 4), dtype=np.float32)
    out[..., 3] = shadow * 255
    rgb = np.asarray(picture, dtype=np.float32)[..., :3]
    out[..., :3] = rgb * a[..., None]
    out[..., 3] = np.maximum(out[..., 3], a * 255)
    return out.astype(np.uint8)


# ---------------------------------------------------------------- the fat tilcayo

def tilcayo():
    """The fat tilcayo from the settings, sitting on the avatar's head. Picture side 768: he stays sharp."""
    scale = 2.05
    side = 768
    here = os.path.dirname(__file__)
    cat = Image.open(os.path.join(here, "..", "tilcayo_run.png")).convert("RGBA")
    avatar = side / scale / 2
    wide = int(avatar * 1.2)
    tall = int(wide * cat.height / cat.width)
    cat = cat.resize((wide, tall), Image.LANCZOS).rotate(-5, resample=Image.BICUBIC, expand=True)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    # he sits low enough to dent the head a little
    feet = side / 2 - avatar * 0.74
    x = int(side / 2 - cat.width / 2 + avatar * 0.04)
    y = int(feet - cat.height)
    # the shadow he throws on what's under him
    alpha = np.zeros((side, side), dtype=np.float32)
    alpha[max(y, 0):y + cat.height, x:x + cat.width] = np.asarray(cat, dtype=np.float32)[max(-y, 0):, :, 3] / 255
    fy = np.fft.fftfreq(side)[:, None]
    fx = np.fft.rfftfreq(side)[None, :]
    soft = np.fft.irfft2(np.fft.rfft2(np.roll(alpha, 7, axis=0)) * np.exp(-2 * (np.pi * 7) ** 2 * (fx * fx + fy * fy)), s=alpha.shape)
    out.putalpha(Image.fromarray((np.clip(soft, 0, 1) * 0.42 * 255).astype(np.uint8)))
    out.alpha_composite(cat, (x, max(y, 0)), (0, max(-y, 0)))
    return np.asarray(out), side


# ------------------------------------------------------------ the angel's glow

ANDROID_SHAPES = ("circle", "rounded", "square", "cookie", "clover", "burst", "sun", "pentagon", "hexagon", "heart")


def _sided(sides, around):
    slice_ = 2 * np.pi / sides
    within = np.mod(around, slice_)
    flat = np.cos(np.pi / sides) / np.cos(within - slice_ / 2)
    cap, sharp = 0.93, 36.0
    soft = -np.log(np.exp(-sharp * flat) + np.exp(-sharp * cap)) / sharp
    return soft / cap


def _reach(shape, around):
    """MargyT's AvatarShape.reach: how far a shape reaches in a direction, up is zero."""
    if shape == "cookie": return 0.92 + 0.08 * np.cos(9 * around)
    if shape == "clover": return 0.84 + 0.16 * np.cos(4 * around + np.pi)
    if shape == "burst": return 0.90 + 0.10 * np.cos(12 * around)
    if shape == "sun": return 0.87 + 0.13 * np.cos(8 * around)
    if shape == "pentagon": return _sided(5, around)
    if shape == "hexagon": return _sided(6, around)
    return np.ones_like(around)


def _arc(cx, cy, r, start, sweep, n=60):
    t = np.radians(start + sweep * np.linspace(0, 1, n))
    return np.stack([cx + r * np.cos(t), cy + r * np.sin(t)], -1)


def _cubic(p0, p1, p2, p3, n=40):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = (np.array(p, dtype=np.float64) for p in (p0, p1, p2, p3))
    return (1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t * t * p2 + t ** 3 * p3


def shape_outline(shape):
    """A shape's outline in the square from -1 to 1, as AvatarShape.unit draws it (y down)."""
    if shape == "circle":
        return _arc(0, 0, 1, 0, 360, 240)
    if shape in ("rounded", "square"):
        r = 0.44 if shape == "rounded" else 0.0
        if r == 0:
            side = np.linspace(-1, 1, 80)
            return np.concatenate([np.stack([side, -np.ones(80)], -1), np.stack([np.ones(80), side], -1),
                                   np.stack([side[::-1], np.ones(80)], -1), np.stack([-np.ones(80), side[::-1]], -1)])
        c = 1 - r
        return np.concatenate([_arc(c, -c, r, -90, 90), _arc(c, c, r, 0, 90), _arc(-c, c, r, 90, 90), _arc(-c, -c, r, 180, 90)])
    if shape == "heart":
        # arcTo(RectF(-1,-1,.1,.1), -35.1, -174.9), two cubics to the point and back, arcTo(RectF(-.1,-1,1,.1), 30, -174.9)
        left = _arc(-0.45, -0.45, 0.55, -35.1, -174.9)
        down = _cubic(left[-1], (-0.70, 0.25), (-0.28, 0.70), (0, 1))
        up = _cubic((0, 1), (0.28, 0.70), (0.70, 0.25), (0.926, -0.175))
        right = _arc(0.45, -0.45, 0.55, 30, -174.9)
        return np.concatenate([left, down, up, right])
    around = 2 * np.pi * np.arange(288) / 288 - np.pi / 2
    reach = _reach(shape, around + np.pi / 2)
    return np.stack([reach * np.cos(around), reach * np.sin(around)], -1)


def shape_distance(shape, x, y, radius):
    """How far each point is outside the avatar's edge (negative inside), for MargyT's avatar shapes."""
    if shape == "circle":
        return np.sqrt(x * x + y * y) - radius
    outline = shape_outline(shape) * radius
    # inside: the outline filled in
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).polygon([(float(px + SIZE / 2), float(py + SIZE / 2)) for px, py in outline], fill=255)
    inside = np.asarray(mask) > 127
    # the distance to the nearest point of the outline, a band of rows at a time
    dense = np.concatenate([np.linspace(outline[i], outline[(i + 1) % len(outline)], 4, endpoint=False) for i in range(len(outline))])
    best = np.full(x.shape, np.inf, dtype=np.float32)
    for row in range(0, SIZE, 64):
        bx, by = x[row:row + 64, :, None], y[row:row + 64, :, None]
        for start in range(0, len(dense), 256):
            part = dense[start:start + 256]
            d = np.sqrt((bx - part[:, 0]) ** 2 + (by - part[:, 1]) ** 2).min(-1)
            best[row:row + 64] = np.minimum(best[row:row + 64], d)
    return np.where(inside, -best, best).astype(np.float32)


def angel(shape="circle", scale=1.55):
    """Holy light behind the avatar: a warm halo hugging its edge and rays fanning out from it,
    long and short in turn, fading to nothing before the picture's edge. Follows the avatar's shape."""
    c = SIZE / 2
    x, y = xx - c, yy - c
    radius = SIZE / scale / 2
    d = shape_distance(shape, x, y, radius)
    theta = np.arctan2(y, x)
    room = c - radius                                  # from the avatar's edge to the picture's

    # rays: two families at different angles, so they don't look like a cartwheel
    rays = np.zeros_like(d)
    rng = np.random.default_rng(7)
    for count, width, reach, strength, turn in ((16, 0.050, 0.98, 1.0, 0.0), (16, 0.030, 0.62, 0.75, np.pi / 16),
                                                 (32, 0.012, 0.42, 0.45, np.pi / 32)):
        for i in range(count):
            angle = turn + i * 2 * np.pi / count + rng.uniform(-0.03, 0.03)
            gap = np.angle(np.exp(1j * (theta - angle)))
            long_ = reach * rng.uniform(0.82, 1.0) * room
            # narrower further out: a beam of light, not a wedge
            w = width * (1.0 - 0.45 * np.clip(d / long_, 0, 1))
            beam = np.exp(-(gap / w) ** 2) * strength
            fade = smooth(0, 0.08 * room, d) * (1 - smooth(0.25 * long_, long_, d))
            rays = np.maximum(rays, beam * fade)
    rays = blur(rays, 1.6)

    halo = np.exp(-np.maximum(d, 0) / (0.16 * room)) * smooth(-6, 4, d)
    rim = np.exp(-(d / 7.0) ** 2) * 0.9                  # a bright thin line right at the edge
    sparkle = np.zeros_like(d)
    for _ in range(26):
        a = rng.uniform(0, 2 * np.pi)
        dist = radius + rng.uniform(0.15, 0.8) * room
        sx, sy = c + np.cos(a) * dist, c + np.sin(a) * dist
        size = rng.uniform(2.5, 5.5)
        sparkle = np.maximum(sparkle, np.exp(-((xx - sx) ** 2 + (yy - sy) ** 2) / (2 * size * size)) * rng.uniform(0.5, 1.0))

    light_ = np.clip(halo * 1.05 + rays * 0.8 + rim + sparkle, 0, None)
    alpha = np.clip(1 - np.exp(-light_ * 1.6), 0, 1)
    alpha *= smooth(-3, 1.5, d)                            # the avatar itself stays clear
    alpha *= 1 - smooth(0.92 * c, c, np.sqrt(x * x + y * y))
    # white where it's strongest, warm gold as it thins out
    t = np.clip(light_ / 1.4, 0, 1)
    gold = np.array([250, 178, 54], dtype=np.float32)
    white = np.array([255, 251, 236], dtype=np.float32)
    colour = gold[None, None, :] * (1 - t[..., None]) + white[None, None, :] * t[..., None]
    return rgba(colour, alpha)


def save(picture, name, side=384):
    path = os.path.join(OUT, name)
    Image.fromarray(picture, "RGBA").resize((side, side), Image.LANCZOS).save(path, optimize=True)
    print(path, os.path.getsize(path))


if __name__ == "__main__":
    # the supporters' frame is the angel's glow now: one picture per avatar shape, the circle as the default
    save(angel("circle"), "frame_supporter.png", 512)
    for shape in ANDROID_SHAPES:
        save(angel(shape), "frame_supporter_%s.png" % shape, 512)
    save(owner(), "frame_owner.png")
    cat, side = tilcayo()
    save(cat, "frame_tilcayo.png", side)
