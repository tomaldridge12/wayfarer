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
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # for pack_sprites, run with -I


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


# --- objectives and collections: curios, supplies, special gear, fish, treasures, the Journal, keepsakes ---

FISH = ["alaska_blackfish", "humpback_whitefish", "northern_pike", "walleye", "muskellunge", "siberian_sturgeon", "lamprey",
        "bass", "bluegill", "carp", "catfish", "crappie", "striped_perch", "shad", "european_eel", "gar", "sturgeon",
        "arapaima", "armoured_catfish", "piranha", "guppy", "freshwater_pufferfish", "tunisian_barb", "bujurqui",
        "mediterranean_killifish", "herring", "wolffish", "skate", "spoonhead_sculpin", "gurnard", "black_seabass",
        "flounder", "anchovy", "monkfish", "opah", "mahi_mahi", "flying_fish", "rainbow_wrasse", "painted_moray",
        "swordfish", "oarfish", "echo_fish", "pale_fish", "pride_fish"]
# Klei's file (in Matcha's textures/item) -> Wayfarer's texture under textures/item, used as it is.
MATCHA_ITEMS = {**{f: f"fish/{f}" for f in FISH if f != "pride_fish"}, "gay_fish": "fish/pride_fish",
                "amber": "treasure/amber", "opal": "treasure/opal", "ruby": "treasure/ruby", "topaz": "treasure/topaz",
                "cheerful_clay_statue": "treasure/cheerful_clay_statue", "mournful_clay_statue": "treasure/mournful_clay_statue",
                "nazar": "treasure/nazar", "ofuda": "treasure/ofuda", "shipwreck_map": "treasure/shipwreck_chart",
                "trail_ruins_map": "treasure/trail_ruins_chart",
                "amethyst_earrings": "curio/amethyst_earrings", "lapis_earrings": "curio/lapis_earrings",
                "blessing_glaucus": "curio/glaucus_blessing", "blessing_aeolus": "curio/aeolus_blessing",
                "blessing_demeter": "curio/demeters_blessing", "blessing_apollo": "curio/apollos_blessing",
                "blessing_talos": "curio/talos_blessing", "blessing_hyacinthus": "curio/hyacinthus_blessing",
                "blessing_artemis": "curio/artemis_blessing", "blessing_icarus": "curio/icarus_blessing",
                "blessing_midas": "curio/midas_blessing", "blessing_prometheus": "curio/prometheus_blessing",
                "blessing_daedalus": "curio/daedalus_blessing", "blessing_arachnae": "curio/arachne_blessing",
                "blessing_lu_ban": "curio/lu_ban_blessing", "archaeologists_brush": "curio/archaeologists_brush",
                "brown_feather": "curio/owl_feather", "ruby_circlet": "curio/ruby_circlet",
                "hepatizon_laurel": "curio/hepatizon_laurel", "golden_compass17": "curio/explorers_compass",
                "seed_bundle": "supply/seed_bundle", "floral_bundle": "supply/floral_bundle",
                "mushy_bundle": "supply/mushy_bundle", "crate_empty": "supply/animal_crate",
                "beacon_kindling": "beacon_kindling", "heart": "source/heart"}
# Matcha's 32 compass frames (1-based) become vanilla-style 0-based frames for the Wayfinder and the Guardian's Chart.
MATCHA_COMPASSES = {"golden_compass": "wayfinder_compass", "titanium_compass": "guardians_chart"}
# Klei's art recoloured, so a curio does not look like the treasure it is named after: (source, target, hue shift).
RECOLOURED = [("source/heart", "curio/moss_heart", 125), ("treasure/nazar", "curio/pharaohs_nazar", 175),
              ("treasure/ofuda", "curio/ofuda_charm", 200)]


def import_collections(src):
    """Copies Klei's textures, fetched flat into src under Matcha's own file names, into the pack."""
    import shutil
    for name, target in MATCHA_ITEMS.items():
        os.makedirs(os.path.dirname(os.path.join(ROOT, f"{TEX}/item/{target}.png")), exist_ok=True)
        shutil.copyfile(os.path.join(src, f"{name}.png"), os.path.join(ROOT, f"{TEX}/item/{target}.png"))
        print("imported", target)
    for source, target in MATCHA_COMPASSES.items():
        os.makedirs(os.path.join(ROOT, f"{TEX}/item/{target}"), exist_ok=True)
        for n in range(1, 33):
            shutil.copyfile(os.path.join(src, f"{source}{n}.png"), os.path.join(ROOT, f"{TEX}/item/{target}/{n - 1:02d}.png"))
        print("imported", target, "frames")


def hue_shift(rows, degrees):
    """Turns every coloured pixel's hue by the given degrees, keeping its lightness and saturation."""
    import colorsys
    out = []
    for row in rows:
        line = []
        for r, g, b, a in row:
            if a == 0 or max(r, g, b) - min(r, g, b) < 20:
                line.append((r, g, b, a))
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hls_to_rgb((h + degrees / 360) % 1, l, s)
            line.append((round(nr * 255), round(ng * 255), round(nb * 255), a))
        out.append(line)
    return out


def compass_json(name):
    """Like vanilla's compass: points at its lodestone target, else spins (Matcha's frames, renamed)."""
    def dispatch(target):
        entries = [{"threshold": 0.0, "model": {"type": "minecraft:model", "model": f"wayfarer:item/{name}/16"}}]
        entries += [{"threshold": k + 0.5, "model": {"type": "minecraft:model", "model": f"wayfarer:item/{name}/{(17 + k) % 32:02d}"}}
                    for k in range(31)]
        entries.append({"threshold": 31.5, "model": {"type": "minecraft:model", "model": f"wayfarer:item/{name}/16"}})
        return {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": target, "scale": 32.0,
                "entries": entries}
    return {"model": {"type": "minecraft:condition", "property": "minecraft:has_component",
                      "component": "minecraft:lodestone_tracker", "on_true": dispatch("lodestone"), "on_false": dispatch("none")}}


def collection_models():
    """Every model name the curios, collections and objectives modules use, with its parent."""
    import pack_sprites
    names = [t for t in MATCHA_ITEMS.values() if not t.startswith("source/")]
    names += [t for _, t, _ in RECOLOURED]
    names += [n for n in pack_sprites.SPRITES if n != "gear/tidecaller_cast"]
    return sorted(set(names))


def generate_collections():
    import pack_sprites
    for name, (rows, palette) in pack_sprites.SPRITES.items():
        write(f"{TEX}/item/{name}.png", png(rows, palette))
    for source, target, shift in RECOLOURED:
        write(f"{TEX}/item/{target}.png", png_pixels(hue_shift(read(f"{TEX}/item/{source}.png"), shift)))
    for name in collection_models():
        if name == "gear/tidecaller":
            write_json(f"assets/wayfarer/items/{name}.json", {"model": {"type": "minecraft:condition",
                "property": "minecraft:fishing_rod/cast",
                "on_true": {"type": "minecraft:model", "model": "wayfarer:item/gear/tidecaller_cast"},
                "on_false": {"type": "minecraft:model", "model": "wayfarer:item/gear/tidecaller"}}})
            for model in ["gear/tidecaller", "gear/tidecaller_cast"]:
                write_json(f"assets/wayfarer/models/item/{model}.json", model_json(model, "minecraft:item/handheld_rod"))
            continue
        write_json(f"assets/wayfarer/items/{name}.json", item_json(name))
        write_json(f"assets/wayfarer/models/item/{name}.json", model_json(name))
    for compass in MATCHA_COMPASSES.values():
        write_json(f"assets/wayfarer/items/{compass}.json", compass_json(compass))
        for n in range(32):
            write_json(f"assets/wayfarer/models/item/{compass}/{n:02d}.json", model_json(f"{compass}/{n:02d}"))


def collection_credits():
    """(Klei's files as they are, Klei's recoloured, Wayfarer's drawn) for CREDITS.md."""
    import pack_sprites
    klei = [f"{TEX}/item/{t}.png" for t in MATCHA_ITEMS.values() if not t.startswith("source/")]
    klei += [f"{TEX}/item/source/heart.png"]
    klei += [f"{TEX}/item/{c}/{n:02d}.png" for c in MATCHA_COMPASSES.values() for n in range(32)]
    recoloured = [f"{TEX}/item/{t}.png" for _, t, _ in RECOLOURED]
    drawn = [f"{TEX}/item/{n}.png" for n in pack_sprites.SPRITES]
    return sorted(klei), recoloured, sorted(drawn)


def generate_cosmetics():
    """Hat and pet models from pack_models.py: a colour sheet, a box model and an item definition each."""
    import pack_models
    for name, (palette, _) in pack_models.MODELS.items():
        rows, colours = pack_models.swatch_rows(palette)
        write(f"{TEX}/item/{name}.png", png(rows, colours))
        write_json(f"assets/wayfarer/models/item/{name}.json", pack_models.model_json(name, f"wayfarer:item/{name}"))
        write_json(f"assets/wayfarer/items/{name}.json", item_json(name))


def cosmetic_credits():
    import pack_models
    return sorted(f"{TEX}/item/{n}.png" for n in pack_models.MODELS)



# Farmer's Delight (vectorwing, MIT): icons that fit a dish or forage item as they are, and some recoloured. Fetched
# flat into a folder from its assets/farmersdelight/textures/item and copied in under Wayfarer's names.
FD_ITEMS = {  # target under textures/item -> Farmer's Delight file name
    "forage/tomato": "tomato", "forage/onion": "onion", "forage/cabbage": "cabbage", "forage/wild_rice": "rice_panicle",
    "dish/miners_stew": "beef_stew", "dish/delvers_pasty": "mutton_wrap", "dish/fishers_chowder": "fish_stew",
    "dish/pike_pie": "shepherds_pie", "dish/sushi_bowl": "salmon_roll", "dish/salt_baked_salmon": "grilled_salmon",
    "dish/fish_tacos": "cod_roll", "dish/travellers_sandwich": "chicken_sandwich", "dish/campfire_kebab": "barbecue_stick",
    "dish/cactus_salsa": "tomato_sauce", "dish/farmers_plate": "bacon_and_eggs", "dish/pumpkin_soup": "pumpkin_soup",
    "dish/cabbage_rolls": "cabbage_rolls", "dish/fried_rice": "fried_rice", "dish/vegetable_soup": "vegetable_soup",
    "dish/truffle_risotto": "mushroom_rice", "dish/harvest_feast": "stuffed_pumpkin",
}
# (Farmer's Delight file, target, hue shift in degrees): the original is kept under source/ and credited too.
FD_RECOLOURED = [("apple_pie", "dish/glowberry_tart", 20), ("beef_stew", "dish/smiths_chilli", -18),
                 ("barbecue_stick", "dish/hellfire_skewer", 10), ("dumplings", "dish/chorus_dumplings", 235),
                 ("pumpkin_soup", "dish/tomato_soup", -28)]


def import_foods(src):
    """Copies the Farmer's Delight icons listed above, fetched flat into src, into the pack."""
    import shutil
    for target, name in FD_ITEMS.items():
        os.makedirs(os.path.dirname(os.path.join(ROOT, f"{TEX}/item/{target}.png")), exist_ok=True)
        shutil.copyfile(os.path.join(src, f"{name}.png"), os.path.join(ROOT, f"{TEX}/item/{target}.png"))
        print("imported", target)
    os.makedirs(os.path.join(ROOT, f"{TEX}/item/source"), exist_ok=True)
    for name in sorted({n for n, _, _ in FD_RECOLOURED}):
        shutil.copyfile(os.path.join(src, f"{name}.png"), os.path.join(ROOT, f"{TEX}/item/source/fd_{name}.png"))
        print("imported source/fd_" + name)


def food_models():
    """Every model name the foods module uses that is not drawn in pack_sprites: Farmer's Delight's, as is and recoloured."""
    return sorted(set(FD_ITEMS) | {t for _, t, _ in FD_RECOLOURED})


def generate_foods():
    for name, target, shift in FD_RECOLOURED:
        write(f"{TEX}/item/{target}.png", png_pixels(hue_shift(read(f"{TEX}/item/source/fd_{name}.png"), shift)))
    for name in food_models():
        write_json(f"assets/wayfarer/items/{name}.json", item_json(name))
        write_json(f"assets/wayfarer/models/item/{name}.json", model_json(name))


def food_credits():
    """(Farmer's Delight's files as they are, its files recoloured) for CREDITS.md."""
    as_is = [f"{TEX}/item/{t}.png" for t in FD_ITEMS]
    as_is += [f"{TEX}/item/source/fd_{n}.png" for n in sorted({n for n, _, _ in FD_RECOLOURED})]
    return sorted(as_is), [f"{TEX}/item/{t}.png" for _, t, _ in FD_RECOLOURED]


FD_LICENCE = """MIT License

Copyright (c) 2020 vectorwing

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""


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
    klei, klei_recoloured, oc_drawn = collection_credits()
    lines += [f"- `{f}`" for f in matcha_files() + klei]
    fd_as_is, fd_recoloured = food_credits()
    lines += ["", "## vectorwing (Farmer's Delight)", "",
              "From Farmer's Delight by vectorwing, https://github.com/vectorwing/FarmersDelight (the 1.21 branch), whose",
              "LICENSE file is the MIT licence below; the repository has no separate notice for its art. Renamed to",
              "Wayfarer's names; otherwise unchanged. The licence text, as the MIT licence asks:", ""]
    lines += ["    " + line if line else "" for line in FD_LICENCE.split("\n")]
    lines += [""]
    lines += [f"- `{f}`" for f in fd_as_is]
    lines += ["", "## Wayfarer, recoloured from Klei's Matcha Flavoured", "",
              "Klei's textures (above) recoloured by `scripts/pack_art.py`: bronze and adamant from Klei's steel; the",
              "Moss Heart from Klei's heart, the Pharaoh's Nazar and Ofuda Charm from Klei's nazar and ofuda. CC BY-NC-SA 4.0.", ""]
    lines += [f"- `{f}`" for f in recoloured + klei_recoloured]
    lines += ["", "## Wayfarer, recoloured from vectorwing's Farmer's Delight", "",
              "Farmer's Delight's textures (above, under `source/` where the original is not used as it is) recoloured by",
              "`scripts/pack_art.py`: the Glowberry Tart, Smith's Chilli, Hellfire Skewer, Chorus Dumplings and Tomato Soup.",
              "Distributed under the MIT licence above, and CC BY-NC-SA 4.0 as part of this pack.", ""]
    lines += [f"- `{f}`" for f in fd_recoloured]
    lines += ["", "## Wayfarer", "", "Drawn for this pack by `scripts/pack_art.py` (sprites in `scripts/pack_sprites.py`).", ""]
    lines += [f"- `{f}`" for f in drawn + oc_drawn + cosmetic_credits()]
    write("CREDITS.md", ("\n".join(lines) + "\n").encode())


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--import-matcha":
        import_matcha(sys.argv[2])
        sys.exit(0)
    if len(sys.argv) == 3 and sys.argv[1] == "--import-collections":
        import_collections(sys.argv[2])
        sys.exit(0)
    if len(sys.argv) == 3 and sys.argv[1] == "--import-foods":
        import_foods(sys.argv[2])
        sys.exit(0)
    if len(sys.argv) == 3 and sys.argv[1] == "--food-sheet":
        import pack_sprites
        names = sorted({n for n in pack_sprites.SPRITES if n.startswith(("forage/", "dish/"))} | set(food_models()))
        sheet([os.path.join(ROOT, f"{TEX}/item/{n}.png") for n in names], sys.argv[2], scale=8, columns=8)
        print(len(names), "icons:", " ".join(names))
        sys.exit(0)
    if len(sys.argv) == 3 and sys.argv[1] == "--collection-sheets":
        out = sys.argv[2]
        groups = {}
        for name in collection_models():
            groups.setdefault(name.split("/")[0] if "/" in name else "misc", []).append(name)
        for group, names in groups.items():
            sheet([os.path.join(ROOT, f"{TEX}/item/{n}.png") for n in names], os.path.join(out, f"{group}.png"), scale=6,
                  columns=12 if len(names) > 12 else len(names))
        sheet([os.path.join(ROOT, f"{TEX}/item/{c}/{n:02d}.png") for c in MATCHA_COMPASSES.values() for n in range(0, 32, 4)],
              os.path.join(out, "compasses.png"), scale=6, columns=8)
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
    generate_collections()
    generate_foods()
    generate_cosmetics()
    write_credits()
    sys.exit(0)
