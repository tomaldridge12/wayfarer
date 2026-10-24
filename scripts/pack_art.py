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

if __name__ == "__main__":
    write("pack.png", png(ROSE, ROSE_PALETTE, scale=8))
    write("assets/wayfarer/textures/item/placeholder.png", png(ROSE, ROSE_PALETTE))
    sys.exit(0)
