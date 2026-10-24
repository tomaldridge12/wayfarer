#!/usr/bin/env python3
"""Draws Wayfarer's own pack art as PNGs (standard library only). Run: python3 -I scripts/pack_art.py

Each picture is a list of rows; each character is a palette key, '.' is transparent. Output goes under
resourcepack/, and every file written here must be credited to Wayfarer in resourcepack/CREDITS.md.
"""
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")


def png(rows, palette, scale=1):
    """PNG bytes for a picture of palette keys, each pixel repeated scale times."""
    width, height = len(rows[0]) * scale, len(rows) * scale
    raw = bytearray()
    for row in rows:
        line = bytearray([0])  # filter: none
        for key in row:
            line += bytes(palette.get(key, (0, 0, 0, 0))) * scale
        raw += line * scale
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def read_png(path):
    """(width, height, rows of (r, g, b, a)) for an 8-bit RGB, RGBA, grey or palette PNG."""
    with open(path, "rb") as f:
        data = f.read()
    pos, idat, palette, alpha = 8, b"", None, None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            alpha = list(body)
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    if depth != 8:
        raise ValueError(f"{path}: only 8-bit PNGs are read")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    raw = zlib.decompress(idat)
    stride = width * channels
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(height):
        kind, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if kind == 1:
                line[x] = (line[x] + a) & 255
            elif kind == 2:
                line[x] = (line[x] + b) & 255
            elif kind == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        pixels = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if colour == 6:
                pixels.append(tuple(px))
            elif colour == 2:
                pixels.append(tuple(px) + (255,))
            elif colour == 0:
                pixels.append((px[0],) * 3 + (255,))
            elif colour == 4:
                pixels.append((px[0],) * 3 + (px[1],))
            else:
                k = px[0]
                pixels.append(palette[k] + ((alpha[k] if alpha and k < len(alpha) else 255),))
        rows.append(pixels)
    return width, height, rows


def png_pixels(rows):
    """PNG bytes for rows of (r, g, b, a) tuples."""
    width, height = len(rows[0]), len(rows)
    raw = bytearray()
    for row in rows:
        raw += b"\x00" + b"".join(bytes(p) for p in row)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def sheet(paths, out, scale=6, columns=11, background=(198, 198, 198, 255)):
    """A preview sheet: each texture scaled up on a light square, in a grid, for the owner to approve."""
    cell = 16 * scale + 8
    count = len(paths)
    width, height = cell * min(columns, count), cell * ((count + columns - 1) // columns)
    canvas = [[background for _ in range(width)] for _ in range(height)]
    for n, path in enumerate(paths):
        w, h, rows = read_png(path)
        s = max(1, (16 * scale) // max(w, h))
        ox, oy = (n % columns) * cell + 4, (n // columns) * cell + 4
        for y in range(h):
            for x in range(w):
                r, g, b, a = rows[y][x]
                if a == 0:
                    continue
                for dy in range(s):
                    for dx in range(s):
                        bg = canvas[oy + y * s + dy][ox + x * s + dx]
                        t = a / 255
                        canvas[oy + y * s + dy][ox + x * s + dx] = (
                            round(r * t + bg[0] * (1 - t)), round(g * t + bg[1] * (1 - t)),
                            round(b * t + bg[2] * (1 - t)), 255)
    with open(out, "wb") as f:
        f.write(png_pixels(canvas))


def write(relative, data):
    path = os.path.join(ROOT, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    print("wrote", relative)


# A compass rose: the pack icon and the placeholder item that proves the wayfarer:<name> model scheme.
ROSE = [
    "................",
    ".......gg.......",
    "......gGGg......",
    "......gGGg......",
    ".....gGwwGg.....",
    "..d..gGwwGg..d..",
    ".dDddgGwwGgddDd.",
    "dDDDDDwwwwDDDDDd",
    "dDDDDDwwwwDDDDDd",
    ".dDddgGwwGgddDd.",
    "..d..gGwwGg..d..",
    ".....gGwwGg.....",
    "......gGGg......",
    "......gGGg......",
    ".......gg.......",
    "................",
]
ROSE_PALETTE = {
    "g": (166, 116, 38, 255), "G": (255, 201, 74, 255),
    "d": (60, 64, 86, 255), "D": (122, 130, 168, 255),
    "w": (255, 247, 225, 255),
}

# --- alloys ---

ALLOYS = ["steel", "bronze", "shakudo", "electrum", "hepatizon", "adamant"]
# Klei's Matcha Flavoured draws these four in every vanilla shape; bronze and adamant are recoloured from its steel.
FROM_MATCHA = ["steel", "electrum", "shakudo", "hepatizon"]
SHAPES = ["sword", "pickaxe", "axe", "shovel", "hoe", "spear", "helmet", "chestplate", "leggings", "boots"]
TEX = "assets/wayfarer/textures"

# Dark to light ramps for the metals Matcha does not have.
RAMPS = {
    "bronze": [(74, 44, 16), (140, 84, 28), (200, 128, 44), (236, 172, 72), (252, 220, 140)],
    "adamant": [(22, 26, 48), (44, 56, 96), (78, 98, 150), (126, 152, 206), (196, 214, 246)],
}


def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def is_grey(p):
    return max(p[:3]) - min(p[:3]) < 30


def ramp_from(rows):
    """Five colours, dark to light, sampled from a texture's opaque pixels (a metal's own palette)."""
    colours = sorted({p[:3] for row in rows for p in row if p[3] == 255}, key=luminance)
    return [colours[round(i * (len(colours) - 1) / 4)] for i in range(5)]


def shade(ramp, t):
    t = min(1.0, max(0.0, t)) * (len(ramp) - 1)
    i = min(int(t), len(ramp) - 2)
    f = t - i
    return tuple(round(ramp[i][c] * (1 - f) + ramp[i + 1][c] * f) for c in range(3))


def recolour(rows, ramp):
    """Repaints the grey (metal) pixels along the ramp by brightness, keeping handles and wraps as they are."""
    greys = [luminance(p) for row in rows for p in row if p[3] and is_grey(p)]
    lo, hi = min(greys), max(greys)
    return [[shade(ramp, (luminance(p) - lo) / max(1, hi - lo)) + (p[3],) if p[3] and is_grey(p) else p
             for p in row] for row in rows]


TEMPLATE = [
    "................",
    "..oooooooooooo..",
    ".osSSSSSSSSSSso.",
    ".oSssssssssssSo.",
    ".oSss..ee..ssSo.",
    ".oSs..eEEe..sSo.",
    ".oSs.eELLEe.sSo.",
    ".oSseELLLLEesSo.",
    ".oSseELLLLEesSo.",
    ".oSs.eELLEe.sSo.",
    ".oSs..eEEe..sSo.",
    ".oSss..ee..ssSo.",
    ".oSssssssssssSo.",
    ".osSSSSSSSSSSso.",
    "..oooooooooooo..",
    "................",
]


def template(ramp):
    """An alloy template: a slate tablet bearing the alloy's diamond emblem (drawn for Wayfarer)."""
    palette = {"o": (24, 24, 30, 255), "s": (58, 60, 70, 255), "S": (92, 96, 110, 255),
               "e": ramp[0] + (255,), "E": ramp[2] + (255,), "L": ramp[4] + (255,)}
    return png(TEMPLATE, palette)


# --- marks ---

MARKS = ["dune", "shepherd", "angler", "reaper"]
# 8x8 emblems for the centre of a mark template: a sand dune, open shears, a fish, a wheat sheaf.
MARK_EMBLEMS = {
    "dune": [
        "........",
        "........",
        "...ll...",
        "..lLLl..",
        ".lLLLLl.",
        "lLLDDLLl",
        "LLDDDDLL",
        "DDDDDDDD",
    ],
    "shepherd": [
        "L......L",
        ".L....L.",
        "..L..L..",
        "...LL...",
        "...LL...",
        "..D..D..",
        ".DD..DD.",
        ".DD..DD.",
    ],
    "angler": [
        "........",
        "..LLL...",
        ".LLLLL.L",
        "LDLLLLLL",
        "LLLLLLLL",
        ".LLLLL.L",
        "..DDD...",
        "........",
    ],
    "reaper": [
        "L.L.L...",
        ".LLL....",
        "L.L.L...",
        ".LLL.L.L",
        "..D...L.",
        "..D..L.L",
        "..D.....",
        "..D.....",
    ],
}
MARK_COLOURS = {  # dark, light
    "dune": ((176, 128, 60), (240, 214, 150)),
    "shepherd": ((150, 150, 160), (250, 250, 250)),
    "angler": ((40, 96, 160), (120, 190, 240)),
    "reaper": ((150, 104, 40), (236, 200, 96)),
}


def mark_template(mark):
    """A mark template: the alloy templates' slate tablet bearing the mark's emblem (drawn for Wayfarer)."""
    rows = [list(r) for r in TEMPLATE]
    for y in range(4, 12):
        for x in range(4, 12):
            rows[y][x] = "s"
    for y, line in enumerate(MARK_EMBLEMS[mark]):
        for x, c in enumerate(line):
            if c != ".":
                rows[4 + y][4 + x] = c
    dark, light = MARK_COLOURS[mark]
    palette = {"o": (24, 24, 30, 255), "s": (58, 60, 70, 255), "S": (92, 96, 110, 255),
               "D": dark + (255,), "L": light + (255,)}
    return png(["".join(r) for r in rows], palette)


def item_json(name):
    if name.endswith("_spear"):
        # Like vanilla spears: the flat icon in menus and on the ground, the long model in hand.
        return {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                          "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
                                     "model": {"type": "minecraft:model", "model": f"wayfarer:item/{name}"}}],
                          "fallback": {"type": "minecraft:model", "model": f"wayfarer:item/{name}_in_hand"}},
                "swap_animation_scale": 1.95}
    return {"model": {"type": "minecraft:model", "model": f"wayfarer:item/{name}"}}


def model_json(name, parent="minecraft:item/generated"):
    return {"parent": parent, "textures": {"layer0": f"wayfarer:item/{name}"}}


def write_json(relative, value):
    import json
    write(relative, (json.dumps(value, indent=2) + "\n").encode())


def read(relative):
    return read_png(os.path.join(ROOT, relative))[2]


def import_matcha(src):
    """Copies Klei's alloy textures, fetched flat into src as item_<alloy>_<shape>.png, eq_<layer>_<alloy>.png,
    trim_<alloy>[_darker].png and item_steel_alloy.png, into the pack under Wayfarer's names."""
    import shutil
    def copy(name, relative):
        os.makedirs(os.path.dirname(os.path.join(ROOT, relative)), exist_ok=True)
        shutil.copyfile(os.path.join(src, name), os.path.join(ROOT, relative))
        print("imported", relative)
    for alloy in FROM_MATCHA:
        for shape in SHAPES + ["spear_in_hand"]:
            copy(f"item_{alloy}_{shape}.png", f"{TEX}/item/{alloy}_{shape}.png")
        for layer in ["humanoid", "humanoid_leggings"]:
            copy(f"eq_{layer}_{alloy}.png", f"{TEX}/entity/equipment/{layer}/{alloy}.png")
    for alloy in ["electrum", "shakudo", "hepatizon"]:
        for variant in ["", "_darker"]:
            copy(f"trim_{alloy}{variant}.png", f"{TEX}/trims/color_palettes/{alloy}{variant}.png")
    copy("item_steel_alloy.png", f"{TEX}/item/steel_ingot.png")


def generate():
    """Wayfarer's own art and every model file. Bronze and adamant gear, and the other ingots, are recoloured from
    Klei's steel; templates are drawn here."""
    ramps = dict(RAMPS)
    for alloy in FROM_MATCHA:
        ramps[alloy] = ramp_from(read(f"{TEX}/item/{alloy}_chestplate.png"))
    for alloy in RAMPS:
        for shape in SHAPES + ["spear_in_hand"]:
            write(f"{TEX}/item/{alloy}_{shape}.png", png_pixels(recolour(read(f"{TEX}/item/steel_{shape}.png"), ramps[alloy])))
        for layer in ["humanoid", "humanoid_leggings"]:
            write(f"{TEX}/entity/equipment/{layer}/{alloy}.png",
                  png_pixels(recolour(read(f"{TEX}/entity/equipment/{layer}/steel.png"), ramps[alloy])))
    for alloy in ALLOYS:
        if alloy != "steel":
            write(f"{TEX}/item/{alloy}_ingot.png", png_pixels(recolour(read(f"{TEX}/item/steel_ingot.png"), ramps[alloy])))
        write(f"{TEX}/item/{alloy}_template.png", template(ramps[alloy]))
        names = [f"{alloy}_{shape}" for shape in SHAPES] + [f"{alloy}_ingot", f"{alloy}_template"]
        for name in names:
            write_json(f"assets/wayfarer/items/{name}.json", item_json(name))
            parent = "minecraft:item/handheld" if name.split("_", 1)[1] in SHAPES[:5] else "minecraft:item/generated"
            write_json(f"assets/wayfarer/models/item/{name}.json", model_json(name, parent))
        write_json(f"assets/wayfarer/models/item/{alloy}_spear_in_hand.json",
                   model_json(f"{alloy}_spear_in_hand", "minecraft:item/spear_in_hand"))
        write_json(f"assets/wayfarer/equipment/{alloy}.json",
                   {"layers": {layer: [{"texture": f"wayfarer:{alloy}"}] for layer in ["humanoid", "humanoid_leggings"]}})


def generate_marks():
    for mark in MARKS:
        name = f"{mark}_mark_template"
        write(f"{TEX}/item/{name}.png", mark_template(mark))
        write_json(f"assets/wayfarer/items/{name}.json", item_json(name))
        write_json(f"assets/wayfarer/models/item/{name}.json", model_json(name, "minecraft:item/generated"))


def matcha_files():
    files = [f"{TEX}/item/{a}_{s}.png" for a in FROM_MATCHA for s in SHAPES + ["spear_in_hand"]]
    files += [f"{TEX}/entity/equipment/{layer}/{a}.png" for a in FROM_MATCHA for layer in ["humanoid", "humanoid_leggings"]]
    files += [f"{TEX}/trims/color_palettes/{a}{v}.png" for a in ["electrum", "shakudo", "hepatizon"] for v in ["", "_darker"]]
    return files + [f"{TEX}/item/steel_ingot.png"]


def write_credits():
    recoloured = [f"{TEX}/item/{a}_{s}.png" for a in RAMPS for s in SHAPES + ["spear_in_hand"]]
    recoloured += [f"{TEX}/entity/equipment/{layer}/{a}.png" for a in RAMPS for layer in ["humanoid", "humanoid_leggings"]]
    recoloured += [f"{TEX}/item/{a}_ingot.png" for a in ALLOYS if a != "steel"]
    drawn = ["pack.png", f"{TEX}/item/placeholder.png"] + [f"{TEX}/item/{a}_template.png" for a in ALLOYS]
    drawn += [f"{TEX}/item/{m}_mark_template.png" for m in MARKS]
    lines = ["# Credits", "",
             "Every texture in this pack, with its author and source. The pack is CC BY-NC-SA 4.0 (see LICENSE.txt).",
             "Written by `scripts/pack_art.py`; do not edit by hand.", "",
             "## Klei (Matcha Flavoured)", "",
             "From Matcha Flavoured by Klei (kleiwright), https://github.com/kleiwright/matcha-flavoured, licensed",
             "CC BY-NC-SA 4.0. Renamed to Wayfarer's names; otherwise unchanged.", ""]
    lines += [f"- `{f}`" for f in matcha_files()]
    lines += ["", "## Wayfarer, recoloured from Klei's Matcha Flavoured steel", "",
              "Klei's steel textures (above) with the metal recoloured by `scripts/pack_art.py`; CC BY-NC-SA 4.0.", ""]
    lines += [f"- `{f}`" for f in recoloured]
    lines += ["", "## Wayfarer", "", "Drawn for this pack by `scripts/pack_art.py`.", ""]
    lines += [f"- `{f}`" for f in drawn]
    write("CREDITS.md", ("\n".join(lines) + "\n").encode())


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--import-matcha":
        import_matcha(sys.argv[2])
        sys.exit(0)
    if len(sys.argv) == 3 and sys.argv[1] == "--mark-sheet":
        sheet([os.path.join(ROOT, f"{TEX}/item/{m}_mark_template.png") for m in MARKS], sys.argv[2], scale=10, columns=4)
        sys.exit(0)
    if len(sys.argv) == 3 and sys.argv[1] == "--sheet":
        order = SHAPES + ["ingot", "template"]
        sheet([os.path.join(ROOT, f"{TEX}/item/{a}_{s}.png") for a in ALLOYS for s in order], sys.argv[2], columns=12)
        sys.exit(0)
    write("pack.png", png(ROSE, ROSE_PALETTE, scale=8))
    write("assets/wayfarer/textures/item/placeholder.png", png(ROSE, ROSE_PALETTE))
    generate()
    generate_marks()
    write_credits()
    sys.exit(0)
