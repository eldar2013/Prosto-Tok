#!/usr/bin/env python3
"""The tilcayo's skins, made from its own picture so every move and the cheeks stay where they are.

    python3 assets/make_tilcayo_skins.py   ->  assets/tilcayo_run_alien.png, _gold.png, _crown.png

alien: green all over, two antennas with glowing tips; gold: a yellow-to-orange gradient map, for
those who supported MargyT; crown: the owner's crown (server/icons/frame_owner.png) on its head.
Same size as tilcayo_run.png; drawn four times larger and brought down, for clean edges.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = Image.open(os.path.join(HERE, "tilcayo_run.png")).convert("RGBA")
W, H = BASE.size
K = 4                                   # supersampling
HEAD = (165, 50)                        # the top of the head, between the ears, in the picture's pixels


def gradient_map(image, stops):
    """Colours by brightness: each pixel takes the colour at its luminance along the stops."""
    a = np.asarray(image).astype(np.float32)
    lum = (0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]) / 255
    xs = np.array([s[0] for s in stops], dtype=np.float32)
    out = np.stack([np.interp(lum, xs, [s[1][i] for s in stops]) for i in range(3)], -1)
    return Image.fromarray(np.dstack([out, a[..., 3]]).astype(np.uint8), "RGBA")


def big(image):
    return image.resize((W * K, H * K), Image.LANCZOS)


def small(image):
    return image.resize((W, H), Image.LANCZOS)


def alien():
    body = gradient_map(BASE, [(0.0, (6, 30, 10)), (0.3, (34, 110, 30)), (0.6, (110, 205, 70)), (0.85, (190, 245, 140)), (1.0, (235, 255, 215))])
    canvas = big(body)
    draw = ImageDraw.Draw(canvas)
    x, y = HEAD[0] * K, HEAD[1] * K
    for side, lean in ((-1, -22), (1, 18)):
        root = (x + side * 11 * K, y + 4 * K)
        tip = (root[0] + lean * K * 0.55, 12 * K)
        mid = ((root[0] + tip[0]) / 2 + side * 6 * K, (root[1] + tip[1]) / 2)
        points = [tuple(np.array(root) * (1 - t) ** 2 + np.array(mid) * 2 * t * (1 - t) + np.array(tip) * t * t) for t in np.linspace(0, 1, 24)]
        draw.line(points, fill=(24, 90, 30, 255), width=int(3.2 * K), joint="curve")
        draw.line(points, fill=(80, 170, 60, 255), width=int(1.4 * K), joint="curve")
        glow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse((tip[0] - 9 * K, tip[1] - 9 * K, tip[0] + 9 * K, tip[1] + 9 * K), fill=(170, 255, 90, 150))
        canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(4 * K)))
        draw.ellipse((tip[0] - 5 * K, tip[1] - 5 * K, tip[0] + 5 * K, tip[1] + 5 * K), fill=(150, 255, 70, 255), outline=(30, 110, 30, 255), width=K)
        draw.ellipse((tip[0] - 2.5 * K, tip[1] - 3 * K, tip[0] - 0.5 * K, tip[1] - 1 * K), fill=(240, 255, 220, 255))
    return small(canvas)


def gold():
    """Yellow at the top, orange at the bottom: two gradient maps blended down the picture."""
    yellow = np.asarray(gradient_map(BASE, [(0.0, (90, 52, 0)), (0.3, (200, 140, 10)), (0.55, (255, 205, 30)), (0.8, (255, 236, 110)), (1.0, (255, 252, 210))])).astype(np.float32)
    orange = np.asarray(gradient_map(BASE, [(0.0, (70, 20, 0)), (0.3, (175, 62, 4)), (0.55, (245, 120, 16)), (0.8, (255, 170, 60)), (1.0, (255, 225, 160))])).astype(np.float32)
    t = np.clip((np.arange(H, dtype=np.float32)[:, None, None] / H - 0.15) / 0.7, 0, 1)
    out = yellow * (1 - t) + orange * t
    out[..., 3] = np.asarray(BASE)[..., 3]
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def crown():
    owner = Image.open(os.path.join(HERE, "..", "server", "icons", "frame_owner.png")).convert("RGBA")
    piece = owner.crop(owner.getchannel("A").getbbox())
    width = 84 * K
    piece = piece.resize((width, round(piece.height * width / piece.width)), Image.LANCZOS).rotate(-6, resample=Image.BICUBIC, expand=True)
    canvas = big(BASE)
    x = HEAD[0] * K - piece.width // 2
    y = HEAD[1] * K + 13 * K - piece.height
    canvas.alpha_composite(piece, (x, max(y, 0)), (0, max(-y, 0)))
    return small(canvas)


if __name__ == "__main__":
    for name, make in (("alien", alien), ("gold", gold), ("crown", crown)):
        path = os.path.join(HERE, "tilcayo_run_%s.png" % name)
        make().save(path, optimize=True)
        print(path, os.path.getsize(path))
