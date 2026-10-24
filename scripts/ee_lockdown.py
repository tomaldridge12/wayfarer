#!/usr/bin/env python3
"""Make every ExcellentEnchants enchant a reward only, and set the Veinminer and Treefeller books' levels.

Usage: scripts/ee_lockdown.py <plugins/ExcellentEnchants/enchants folder> [--check]

Edits each <enchant>.yml in place, keeping its comments: under Distribution, Treasure true and Tradeable,
On_Mob_Spawn_Equipment, On_Traded_Equipment, On_Random_Loot and Discoverable false, so no table, librarian, mob, trader,
chest or fishing loot ever gives one; Wayfarer hands them out (relics, mark books, objectives). Veinminer and Treefeller
get three levels with capped block limits (docs/wayfarer-relic-enchants.md). Idempotent; prints what it changed.
--check changes nothing and exits 1 if anything would change. ExcellentEnchants reads these files only on boot.
"""
import os
import re
import sys

DISTRIBUTION = {
    "Treasure": "true",
    "Tradeable": "false",
    "On_Mob_Spawn_Equipment": "false",
    "On_Traded_Equipment": "false",
    "On_Random_Loot": "false",
    "Discoverable": "false",
}
# enchant file -> (MaxLevel, {Block_Limit key: value})
BOOKS = {
    "veinminer.yml": (3, {"Base": "4.0", "Per_Level": "4.0", "Capacity": "12.0"}),
    "treefeller.yml": (3, {"Base": "32.0", "Per_Level": "32.0", "Capacity": "96.0"}),
}


def set_key(lines, key, value, start=0, end=None, indent=None):
    """Sets `key: value` on the first matching line in lines[start:end]; returns True if it changed."""
    end = len(lines) if end is None else end
    pattern = re.compile(r"^(\s*)" + re.escape(key) + r":\s*(\S+)\s*$")
    for i in range(start, end):
        m = pattern.match(lines[i])
        if m and (indent is None or len(m.group(1)) == indent):
            if m.group(2) == value:
                return False
            lines[i] = f"{m.group(1)}{key}: {value}\n"
            return True
    return False


def section(lines, name):
    """(start, end) line range of a top-level or nested `name:` block, by indentation; (0, 0) if absent."""
    for i, line in enumerate(lines):
        m = re.match(r"^(\s*)" + re.escape(name) + r":\s*$", line)
        if m:
            depth = len(m.group(1))
            j = i + 1
            while j < len(lines) and (not lines[j].strip() or lines[j].lstrip().startswith("#")
                                      or len(lines[j]) - len(lines[j].lstrip()) > depth):
                j += 1
            return i + 1, j
    return 0, 0


def lock(path):
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    changed = []
    start, end = section(lines, "Distribution")
    if end:
        for key, value in DISTRIBUTION.items():
            if set_key(lines, key, value, start, end):
                changed.append(f"{key}: {value}")
    name = os.path.basename(path)
    if name in BOOKS:
        max_level, limits = BOOKS[name]
        d_start, d_end = section(lines, "Definition")
        if set_key(lines, "MaxLevel", str(max_level), d_start, d_end):
            changed.append(f"MaxLevel: {max_level}")
        b_start, b_end = section(lines, "Block_Limit")
        for key, value in limits.items():
            if b_end and set_key(lines, key, value, b_start, b_end):
                changed.append(f"Block_Limit.{key}: {value}")
    return lines, changed


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    check = "--check" in sys.argv
    if len(args) != 1 or not os.path.isdir(args[0]):
        print(__doc__)
        return 2
    files = sorted(f for f in os.listdir(args[0]) if f.endswith(".yml"))
    pending = 0
    for f in files:
        path = os.path.join(args[0], f)
        lines, changed = lock(path)
        if changed:
            pending += 1
            print(f"{f}: {', '.join(changed)}")
            if not check:
                with open(path, "w", encoding="utf-8") as out:
                    out.writelines(lines)
    verb = "would change" if check else "changed"
    print(f"{len(files)} enchant files, {pending} {verb}")
    return 1 if check and pending else 0


if __name__ == "__main__":
    sys.exit(main())
