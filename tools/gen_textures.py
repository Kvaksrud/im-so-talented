#!/usr/bin/env python3
"""Draws the mod icon as pixel art, using only the standard library.

Run from anywhere:  python3 tools/gen_textures.py
Writes icon.png (128 x 128) into src/main/resources/assets/imsotalented/, and a 512 x 512 project logo
(for CurseForge and Modrinth) and an enlarged preview into build/texture-preview/.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/imsotalented"
PREVIEW = ROOT / "build/texture-preview"

CLEAR = (0, 0, 0, 0)


def hex_color(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x]

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def scaled(self, k):
        out = Canvas(self.w * k, self.h * k)
        for y in range(out.h):
            for x in range(out.w):
                out.px[y][x] = self.px[y // k][x // k]
        return out

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = b"".join(b"\x00" + bytes(v for c in row for v in c) for row in self.px)

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(raw, 9))
        png += chunk(b"IEND", b"")
        path.write_bytes(png)


# ---------------------------------------------------------------- palettes (dark -> light)

GOLD = [hex_color(c) for c in ("#6B430A", "#9C6610", "#CF961A", "#F2C83E", "#FFEE9A")]
XP = [hex_color(c) for c in ("#245A08", "#3F8E10", "#69C11E", "#A6EC3A", "#DDFF80", "#FBFFD8")]
# The four talent trees' colors, as in the game's tabs and XP bars.
GATHERER = [hex_color(c) for c in ("#6B3F0A", "#A8650F", "#E0A440", "#F5C76A", "#FFE7A8")]
WARRIOR = [hex_color(c) for c in ("#5E1410", "#9E2A20", "#E0503E", "#F28A78", "#FFC8BC")]
GUARDIAN = [hex_color(c) for c in ("#13305E", "#1F5296", "#3F86D8", "#7FB6F2", "#C9E2FF")]
EXPLORER = [hex_color(c) for c in ("#0B4A48", "#137A76", "#2FB5AE", "#6EDDD5", "#C2FFF9")]


def pick(palette, t):
    """A palette colour for brightness t in 0..1."""
    return palette[max(0, min(len(palette) - 1, int(t * len(palette))))]


def orb(canvas, cx, cy, r, palette, highlight=(-0.4, -0.45)):
    """A shaded sphere: brightest towards the upper left, a dark rim, a hot spot."""
    hx, hy = cx + highlight[0] * r, cy + highlight[1] * r
    for y in range(canvas.h):
        for x in range(canvas.w):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d > r:
                continue
            light = 1.0 - math.hypot(px - hx, py - hy) / (r * 1.75)
            rim = d / r
            t = light * 0.95 - max(0.0, rim - 0.78) * 1.2
            canvas.set(x, y, pick(palette, max(0.0, t)))


def ring(canvas, cx, cy, r_in, r_out, palette, spread=0.45):
    """A shaded ring (a gold setting around a gem or orb), lit from the upper left."""
    for y in range(canvas.h):
        for x in range(canvas.w):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if r_in < d <= r_out:
                # -1 at the upper left, +1 at the lower right
                facing = ((px - cx) + (py - cy)) / (d * math.sqrt(2)) if d else 0.0
                canvas.set(x, y, pick(palette, 0.5 - facing * spread))


# ---------------------------------------------------------------- mod icon

def star_points(cx, cy, r_out, r_in, n=5, rot=-math.pi / 2):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = rot + i * math.pi / n
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def inside(poly, x, y):
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def line(canvas, x0, y0, x1, y1, width, color):
    """A thick line: every pixel whose centre lies within width/2 of the segment."""
    for y in range(canvas.h):
        for x in range(canvas.w):
            px, py = x + 0.5, y + 0.5
            vx, vy = x1 - x0, y1 - y0
            t = max(0.0, min(1.0, ((px - x0) * vx + (py - y0) * vy) / (vx * vx + vy * vy)))
            if math.hypot(px - (x0 + t * vx), py - (y0 + t * vy)) <= width / 2:
                canvas.set(x, y, color)


def outline(canvas, color):
    """Draws color on every clear pixel next to a filled one."""
    filled = {(x, y) for y in range(canvas.h) for x in range(canvas.w) if canvas.get(x, y)[3]}
    for x, y in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in filled:
                canvas.set(x + dx, y + dy, color)


def icon():
    """A talent tree: an XP orb with a star as the root, branching down to one node per talent tree, in its color.
    32 x 32 pixels; the mod icon and the logo are whole-number scales of it."""
    n = 32
    c = Canvas(n, n)
    ink = hex_color("#140C22")
    root, root_r = (16, 10.5), 7.4
    bar_y, node_y = 21, 27
    xs = [4.5, 12.17, 19.83, 27.5]
    # branches first, so the nodes sit on top of them: a stem, a bar, and a drop to each node
    line(c, root[0], root[1], root[0], bar_y, 2.2, GOLD[2])
    line(c, xs[0], bar_y, xs[-1], bar_y, 2.2, GOLD[2])
    for x in xs:
        line(c, x, bar_y, x, node_y, 2.2, GOLD[2])
    for y in range(n):  # light the top edge of the branches
        for x in range(n):
            if c.get(x, y) == GOLD[2] and (y == 0 or c.get(x, y - 1) != GOLD[2]):
                c.set(x, y, GOLD[3])
    for x, palette in zip(xs, (GATHERER, WARRIOR, GUARDIAN, EXPLORER)):
        ring(c, x, node_y, 2.4, 3.5, GOLD)
        orb(c, x, node_y, 2.4, palette)
    ring(c, root[0], root[1], root_r, root_r + 1.3, GOLD)
    orb(c, root[0], root[1], root_r, XP[:5])  # no near-white, so the star stands out
    star = star_points(root[0], root[1] + 0.5, root_r * 0.73, root_r * 0.3)
    shape = {(x, y) for y in range(n) for x in range(n) if inside(star, x + 0.5, y + 0.5)}
    for x, y in shape:
        # lit upper half, deeper gold lower half
        c.set(x, y, GOLD[4] if y + 0.5 < root[1] else GOLD[3])
    # a dark gold edge around the star so it reads on the green
    for x, y in shape:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in shape:
                c.set(x + dx, y + dy, GOLD[0])
    outline(c, ink)
    return c


def main():
    art = icon()
    art.scaled(4).save(ASSETS / "icon.png")
    print("wrote", ASSETS / "icon.png")
    # The project logo for CurseForge and Modrinth, which want a square of at least 400 x 400.
    art.scaled(16).save(PREVIEW / "logo-512.png")
    print("wrote", PREVIEW / "logo-512.png")
    art.scaled(8).save(PREVIEW / "icon.png")
    print("preview in", PREVIEW)


if __name__ == "__main__":
    main()
