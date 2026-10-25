"""Wayfarer's hat and pet models: little voxel models built from coloured boxes (CC BY-NC-SA 4.0 with the pack).

Each model is a list of boxes (from, to, colour key) in Minecraft's 16-unit model space, drawn to sit inside the 16-unit
cube: a hat has its base at y 0 and stands in the middle; a pet is centred. The plugin shows hats at 0.55 scale on the
head and pets at the cosmetic's scale. Every face of a box is one flat swatch of the model's own colour sheet (a 16x16
texture of 4x4 swatches), so no UV art is needed. pack_art.py writes the textures, models and item definitions and
credits them.
"""


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


MODELS = {}


def model(name, palette, boxes):
    assert len(palette) <= 16, f"{name}: at most 16 colours"
    for _, _, key in boxes:
        assert key in palette, f"{name}: no colour for {key}"
    MODELS[name] = (palette, boxes)


# --- hats ---

model("hat/top_hat", {"k": 0x1D1B22, "r": 0xB02E3C, "g": 0xE3B341}, [
    ([1, 0, 1], [15, 1, 15], "k"),
    ([4, 1, 4], [12, 10, 12], "k"),
    ([3.9, 1, 3.9], [12.1, 3, 12.1], "r"),
    ([7, 1.4, 3.7], [9, 2.6, 3.95], "g"),
    ([7, 1.4, 12.05], [9, 2.6, 12.3], "g"),
])

model("hat/wizard_hat", {"p": 0x4B2E83, "d": 0x34205C, "g": 0xF2D23C}, [
    ([0, 0, 0], [16, 1, 16], "d"),
    ([3, 1, 3], [13, 5, 13], "p"),
    ([2.9, 1, 2.9], [13.1, 2.4, 13.1], "g"),
    ([4, 5, 4], [12, 8, 12], "d"),
    ([5, 8, 5], [11, 11, 11], "p"),
    ([6.5, 11, 6.5], [9.5, 14, 9.5], "d"),
    ([7.2, 14, 7.2], [8.8, 16, 8.8], "p"),
    ([7, 3, 2.8], [9, 4.5, 3.05], "g"),
    ([7, 3, 12.95], [9, 4.5, 13.2], "g"),
])

model("hat/crown", {"g": 0xF0C030, "d": 0xB88A1A, "r": 0xD8203C, "b": 0x3A6FE0}, [
    ([3, 0, 3], [13, 4, 4], "g"),
    ([3, 0, 12], [13, 4, 13], "g"),
    ([3, 0, 4], [4, 4, 12], "g"),
    ([12, 0, 4], [13, 4, 12], "g"),
    ([3, 4, 3], [5, 9, 5], "g"),
    ([11, 4, 3], [13, 9, 5], "g"),
    ([3, 4, 11], [5, 9, 13], "g"),
    ([11, 4, 11], [13, 9, 13], "g"),
    ([7, 4, 3], [9, 7, 4], "d"),
    ([7, 4, 12], [9, 7, 13], "d"),
    ([3, 4, 7], [4, 7, 9], "d"),
    ([12, 4, 7], [13, 7, 9], "d"),
    ([7, 1, 2.9], [9, 3, 3.1], "r"),
    ([7, 1, 12.9], [9, 3, 13.1], "r"),
    ([2.9, 1, 7], [3.1, 3, 9], "b"),
    ([12.9, 1, 7], [13.1, 3, 9], "b"),
])

model("hat/propeller_beanie", {"r": 0xD23B3B, "b": 0x3B6FD2, "y": 0xF1D040, "s": 0x9AA0AA}, [
    ([3, 0, 3], [13, 4, 8], "r"),
    ([3, 0, 8], [13, 4, 13], "b"),
    ([4, 4, 4], [12, 6, 12], "r"),
    ([6, 6, 6], [10, 7, 10], "y"),
    ([7.5, 7, 7.5], [8.5, 9, 8.5], "s"),
    ([1, 9, 7.5], [15, 9.6, 8.5], "y"),
    ([7.5, 9.6, 1], [8.5, 10.2, 15], "b"),
])

# --- pets (they look toward north, which is -z) ---

model("pet/mini_shulker", {"p": 0x956797, "l": 0xB78AB8, "d": 0x6B4A6C, "c": 0xF2E6B6, "k": 0x1D1B22}, [
    ([3, 0, 3], [13, 6, 13], "p"),
    ([2.9, 6, 2.9], [13.1, 6.8, 13.1], "d"),
    ([3, 6.8, 3], [13, 10.5, 13], "l"),
    ([5.5, 2.5, 1], [10.5, 6, 3.1], "c"),
    ([6.2, 4.2, 0.9], [7.4, 5.4, 1.05], "k"),
    ([8.6, 4.2, 0.9], [9.8, 5.4, 1.05], "k"),
])

model("pet/mini_ender_dragon", {"k": 0x16161D, "d": 0x2B1D3A, "m": 0xD946EF, "s": 0x4A4A55}, [
    ([5, 3, 5], [11, 8, 13], "k"),
    ([7, 8, 6], [9, 9, 12], "d"),
    ([6, 6, 3], [10, 10, 6], "k"),
    ([5, 8, 0], [11, 12, 3.9], "k"),
    ([5.4, 10, -0.1], [7, 11.2, 0.1], "m"),
    ([9, 10, -0.1], [10.6, 11.2, 0.1], "m"),
    ([5, 12, 1], [6, 14, 2], "s"),
    ([10, 12, 1], [11, 14, 2], "s"),
    ([7, 4, 13], [9, 7, 16], "k"),
    ([4, 7, 6], [5, 8, 13], "d"),
    ([0, 8, 6], [4, 9, 13], "d"),
    ([0, 8.9, 6], [4, 9.3, 7], "s"),
    ([11, 7, 6], [12, 8, 13], "d"),
    ([12, 8, 6], [16, 9, 13], "d"),
    ([12, 8.9, 6], [16, 9.3, 7], "s"),
    ([5, 0, 6], [7, 3, 8], "k"),
    ([9, 0, 6], [11, 3, 8], "k"),
])

FACES = ("north", "east", "south", "west", "up", "down")


def swatch_rows(palette):
    """The 16x16 colour sheet: 4x4-pixel swatches, left to right then down, in palette order."""
    keys = list(palette)
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            n = (y // 4) * 4 + x // 4
            row += keys[n] if n < len(keys) else "."
        rows.append(row)
    return rows, {k: rgb(v) for k, v in palette.items()}


def model_json(name, texture):
    """The model JSON: a box per element, each face one swatch, with head, fixed and gui display settings."""
    palette, boxes = MODELS[name]
    slot = {k: n for n, k in enumerate(palette)}
    elements = []
    for lo, hi, key in boxes:
        u, v = (slot[key] % 4) * 4, (slot[key] // 4) * 4
        uv = [u + 1, v + 1, u + 3, v + 3]  # inside the swatch, clear of its neighbours
        elements.append({"from": lo, "to": hi, "faces": {f: {"uv": uv, "texture": "#0"} for f in FACES}})
    return {"textures": {"0": texture, "particle": texture}, "elements": elements,
            "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]},
                        "ground": {"scale": [0.4, 0.4, 0.4]}}}
