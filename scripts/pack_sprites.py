"""Wayfarer's hand-drawn 16x16 sprites for curios, supplies, treasures, the Journal and keepsakes.

Each sprite is (rows, palette): rows of palette keys, '.' transparent. Shared keys: 'o' outline. Drawn for Wayfarer
(CC BY-NC-SA 4.0 with the rest of the pack); pack_art.py writes them and credits them.
"""

O = (30, 24, 28, 255)  # the outline every sprite shares, a warm near-black


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


SPRITES = {}


def sprite(name, rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), f"{name}: rows must be 16 x 16"
    pal = {"o": O}
    pal.update({k: rgb(v) if isinstance(v, int) else v for k, v in palette.items()})
    missing = {c for r in rows for c in r} - set(pal) - {"."}
    assert not missing, f"{name}: no colour for {missing}"
    SPRITES[name] = (rows, pal)


# --- curios ---

sprite("curio/lantern_of_the_deep", [
    "......oooo......",
    ".....o....o.....",
    "......oooo......",
    ".....oMMMMo.....",
    "....oMmmmmmo....",
    "....oGgLLgGo....",
    "...ooGLWWLGoo...",
    "...omGLWWLGmo...",
    "...omGgLLgGmo...",
    "...omGggggGmo...",
    "...ooGGGGGGoo...",
    "....oMmmmmmo....",
    "...oMMMMMMMMo...",
    "....oooooooo....",
    ".....g....g.....",
    "................",
], {"M": 0x5A6E78, "m": 0x34444C, "G": 0x2E7F78, "g": 0x52B5A6, "L": 0x7FD4C1, "W": 0xE6FFF8})

sprite("curio/flint_tooth", [
    "................",
    ".......oo.......",
    "......oWWo......",
    "......oWLo......",
    ".....oWLLGo.....",
    ".....oWLGGo.....",
    "....oWLLGGDo....",
    "....oWLGGDDo....",
    "....oLLGGDDo....",
    "...oWLGGDDDDo...",
    "...oLGGGDDDDo...",
    "...oLGGDDDDDo...",
    "..ooccccccccoo..",
    "..occCCCCCCcco..",
    "...oooooooooo...",
    "................",
], {"W": 0xE8E4DE, "L": 0xB9B2AA, "G": 0x7E7670, "D": 0x4A4440, "c": 0x8C6A43, "C": 0xB8915E})

sprite("curio/debris_dowser", [
    ".oo..........oo.",
    "oWLo........oLWo",
    "oLLGo......oGLLo",
    ".oLGGo....oGGLo.",
    "..oLGGo..oGGLo..",
    "...oLGGooGGLo...",
    "....oLGGGGLo....",
    ".....oLGGLo.....",
    ".....oLGGLo.....",
    "......oLGo......",
    "......oLGo......",
    "......oLGo......",
    "......oDDo......",
    ".....oDRRDo.....",
    "......oDDo......",
    ".......oo.......",
], {"W": 0xF0D9B5, "L": 0xC9A27A, "G": 0x8C6A43, "D": 0x5C3A2E, "R": 0xB5724F})

sprite("curio/jacks_ember", [
    "................",
    "......Y.........",
    ".....YF..Y......",
    "......FY.F......",
    ".....oFFYFo.....",
    "....oFRRFRFo....",
    "...oRRrRRrRRo...",
    "...oRrDDrrDRo...",
    "..oRrDooDDoDRo..",
    "..oRrDDDDDDDRo..",
    "..oRRrDooDDrRo..",
    "...oRRrDDDrRo...",
    "...ooRRRRRRoo...",
    ".....oooooo.....",
    "................",
    "................",
], {"Y": 0xFFE38A, "F": 0xFFB347, "R": 0xF29B38, "r": 0xC4561E, "D": 0x6B2A14})

sprite("curio/reed_pipe", [
    "................",
    "..oooooooooooo..",
    "..oLoLoLoLoLoo..",
    "..oGoGoGoGoGoo..",
    "..oGoGoGoGoGo...",
    "..oGoGoGoGoGo...",
    "..oGoGoGoGoo....",
    "..oGoGoGoGo.....",
    "..oCCCCCCCo.....",
    "..occccccco.....",
    "..oGoGoGoo......",
    "..oGoGoGo.......",
    "..oDoDoo........",
    "..oDoo..........",
    "..oo............",
    "................",
], {"L": 0xE6E8A0, "G": 0xB8C46A, "D": 0x7A8A3A, "C": 0xD9A55B, "c": 0x8C6A43})

sprite("curio/queens_comb", [
    "....Y..Y..Y.....",
    "....YoYYYoY.....",
    "....oYYYYYo.....",
    ".....ooooo......",
    "...oHHoHHoHHo...",
    "..oHhhHhhHhhHo..",
    "..oHhhoHhoHhHo..",
    "..ooHHoHHoHHoo..",
    "..oHHoHhhoHHHo..",
    "..oHhhHhhHhhHo..",
    "..oHhhoHhoHhHo..",
    "..ooHHoHHoHHoo..",
    "...oHhhHhhHho...",
    "....oHHoHHoo....",
    ".....oooooo.....",
    "................",
], {"Y": 0xFFD84A, "H": 0xF2C744, "h": 0xC98A1E})

sprite("curio/lucky_bobber", [
    "........oo......",
    ".......o..o.....",
    "........oo......",
    "........o.......",
    "......oooo......",
    ".....oRRRRo.....",
    "....oRWRRRRo....",
    "....oRRRRRRo....",
    "....ooooooooo...",
    "....oWWWWWWo....",
    "....oWWWWWGo....",
    ".....oWWWGo.....",
    "......oooo......",
    ".......oo.......",
    "................",
    "................",
], {"R": 0xE8524A, "W": 0xF5F1E8, "G": 0xBDB6AA})

sprite("curio/puffer_charm", [
    "................",
    ".....o.o.o......",
    "....oYoYoYo.....",
    "...oYYYYYYYo.o..",
    "..oYYWoYYYYYoYo.",
    "..oYYooYYYYYYo..",
    ".oYYYYYYYYYYYo..",
    ".oYYYYYYYYYYYYo.",
    ".oyYYYYYYYYYYo..",
    "..oyyYYYYYYYYYo.",
    "..oyyyyYYYYYo...",
    "...oyyyyyyyo....",
    "....ooooooo.....",
    "....s.s.s.......",
    "................",
    "................",
], {"Y": 0xE3C84E, "y": 0xB8932C, "W": 0xFFFFFF, "s": 0x7A5A1E})

sprite("curio/insomniacs_charm", [
    "................",
    ".....ooooo......",
    "....oMMMMMo.....",
    "...oMMmmmMMo....",
    "...oMmo.omMo....",
    "...oMmo..oMo....",
    "...oMmo...oo....",
    "...oMmmo........",
    "...oMMmmo....oo.",
    "....oMMmmooooMo.",
    ".....oMMmmmmMo..",
    "......ooMMMMo...",
    "........oooo....",
    "....s.....s.....",
    "...sSs...sSs....",
    "....s.....s.....",
], {"M": 0xB8C0F0, "m": 0x7A86C9, "s": 0x4A548C, "S": 0xE6EAFF})

sprite("curio/stormglass", [
    "......oooo......",
    "......oCCo......",
    ".....oooooo.....",
    ".....oGWGGo.....",
    "....oGWggGGo....",
    "....oGgggGGo....",
    "....oGgSSgGo....",
    "....oGSSSSGo....",
    "....oGSsSSGo....",
    "....oGSSsSGo....",
    "....oGSSSSGo....",
    "....oGgSSgGo....",
    ".....oGGGGo.....",
    "....oCCCCCCo....",
    ".....oooooo.....",
    "................",
], {"C": 0x8C6A43, "G": 0x5F7F92, "g": 0x8FB3C9, "W": 0xE6F2FA, "S": 0xDDE6EE, "s": 0xA9B8C4})

sprite("curio/lodestone_pendant", [
    "....oo....oo....",
    "...o..o..o..o...",
    "...o...oo...o...",
    "....o......o....",
    ".....o....o.....",
    "......oooo......",
    ".....oLLLLo.....",
    "....oLsSSsLo....",
    "....oLSRRSLo....",
    "....oLSRRSLo....",
    "....oLsSSsLo....",
    "....oLLLLLLo....",
    ".....oLLLLo.....",
    "......oooo......",
    "................",
    "................",
], {"L": 0x9FA4AD, "s": 0x5E6268, "S": 0x7E838C, "R": 0xD9474A})

sprite("curio/hunters_tooth", [
    "....oo....oo....",
    "...o..o..o..o...",
    "...o...oo...o...",
    "....o..cc..o....",
    ".....ooccoo.....",
    ".....oWWWLo.....",
    ".....oWWWLo.....",
    "....oWWWWLLo....",
    "....oWWWLLLo....",
    ".....oWWLLo.....",
    ".....oWWLLo.....",
    "......oWLo......",
    "......oWLo......",
    ".......oo.......",
    "................",
    "................",
], {"W": 0xF2EEE2, "L": 0xC9BFA8, "c": 0x8C3A2E})

sprite("curio/mermaids_comb", [
    "................",
    "..oooooooooooo..",
    ".oSSSSSSSSSSSSo.",
    ".oSsPPsSSsPPsSo.",
    ".oSSSSSSSSSSSSo.",
    ".oooooooooooooo.",
    ".oToToToToToToo.",
    ".oToToToToToTo..",
    ".oToToToToToTo..",
    ".oToToToToToTo..",
    "..o.o.o.o.o.o...",
    "................",
    "................",
    "................",
    "................",
    "................",
], {"S": 0x6FD0C5, "s": 0x3FA7A0, "P": 0xF2D8E6, "T": 0xBFEFEA})

sprite("curio/prospectors_pan", [
    "................",
    "................",
    "................",
    "...oooooooooo...",
    "..oMLLLLLLLLMo..",
    ".oMLggGggYgLLMo.",
    ".oMLgGGgYYgggMo.",
    ".oMLggGgYggGgMo.",
    "..oMMLgggggLMo..",
    "...oMMMMMMMMo...",
    "....oooooooo....",
    "................",
    "................",
    "................",
    "................",
    "................",
], {"M": 0x6E6A64, "L": 0xA8A29A, "g": 0x8C7A5C, "G": 0x5C4E38, "Y": 0xF2C744})

sprite("curio/bone_dice", [
    "................",
    "................",
    "..ooooooo.......",
    ".oWWWWWWWo......",
    ".oWDWWWWWoooooo.",
    ".oWWWWWWoWWWWWWo",
    ".oWWWDWWoWDWWDWo",
    ".oWWWWWWoWWWWWWo",
    ".oWWWWWDoWWWWWWo",
    ".oGWWWWWoWDWWDWo",
    "..ooooooGWWWWWWo",
    ".........oGGGGo.",
    "..........oooo..",
    "................",
    "................",
    "................",
], {"W": 0xEDE6D3, "G": 0xBDB39A, "D": 0x3A3028})

sprite("curio/hearthstone", [
    "................",
    "................",
    "......oooo......",
    "....ooSSSSoo....",
    "...oSSsssSSSo...",
    "..oSssFFFsssSo..",
    "..oSsFYYFFsSSo..",
    ".oSsFYWWYFFsSo..",
    ".oSsFYYYYFFssSo.",
    ".oSssFFFFFsssSo.",
    "..oSsssssssSSo..",
    "..ooSSSSSSSSoo..",
    "....oooooooo....",
    "................",
    "................",
    "................",
], {"S": 0x6E6662, "s": 0x4C4642, "F": 0xE07A4F, "Y": 0xFFC27A, "W": 0xFFF2D8})

sprite("curio/glow_jar", [
    "................",
    ".....oooooo.....",
    ".....oCCCCo.....",
    "....oooooooo....",
    "....oGGGGGGo....",
    "...oGgTTgggGo...",
    "...oGTWTggTGo...",
    "...oGgTggTWGo...",
    "...oGggTggTGo...",
    "...oGTggTggGo...",
    "...oGWTgggTGo...",
    "...oGgggTggGo...",
    "....oGGGGGGo....",
    ".....oooooo.....",
    "................",
    "................",
], {"C": 0x8C6A43, "G": 0x3E7F8C, "g": 0x2A5560, "T": 0x5FE0D0, "W": 0xDFFFFA})

sprite("curio/strider_sole", [
    "................",
    "................",
    "......oooo......",
    ".....oRRRRo.....",
    "....oRrRRrRo....",
    "....oRRRRRRo....",
    "....oKKKKKKo....",
    "....oRrRRrRo....",
    "....oRRRRRRo....",
    "....oKKKKKKo....",
    "....oRrRRrRo....",
    "....oRRRRRRo....",
    "....oKKKKKKo....",
    ".....oRrrRo.....",
    "......oooo......",
    "................",
], {"R": 0xB5463C, "r": 0xE07A5F, "K": 0x5C2420})

sprite("curio/north_mitten", [
    "................",
    "....oooo........",
    "...oWWWWoo......",
    "...oWBWWWWo.....",
    "...oWWWWBWo.oo..",
    "...oWBWWWWooWWo.",
    "...oWWWWBWoWWWo.",
    "...oWBWWWWWWWo..",
    "...oWWWWBWWWo...",
    "...oWBWWWWWo....",
    "...oRRRRRRRo....",
    "...oRrRrRrRo....",
    "...ooooooooo....",
    "................",
    "................",
    "................",
], {"W": 0xEEF3F7, "B": 0x9FB8CC, "R": 0xC94A4A, "r": 0x8C2E2E})

# --- supplies ---

sprite("supply/wayfarers_bait", [
    "................",
    "................",
    "................",
    "..oooooooooooo..",
    ".oTTTTTTTTTTTTo.",
    ".otttttttttttto.",
    ".oooooooooooooo.",
    ".oTPpPTTTPpPTTo.",
    ".oTpPpTPpPpTTTo.",
    ".oTTpPpPTTpPpTo.",
    ".oTTTTTTTTTTTTo.",
    ".otLLLLLLLLLLto.",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
], {"T": 0x9CA3A8, "t": 0x6A7176, "P": 0xE89A9A, "p": 0xB55C5C, "L": 0xD97A5B})

sprite("supply/tome_of_experience", [
    "................",
    "...oooooooooo...",
    "..oGGGGGGGGGWo..",
    "..oGgggggggGWo..",
    "..oGgYYYYYgGWo..",
    "..oGgYLLLYgGWo..",
    "..oGgYLWLYgGWo..",
    "..oGgYLLLYgGWo..",
    "..oGgYYYYYgGWo..",
    "..oGgggggggGWo..",
    "..oGGGGGGGGGWo..",
    "..oDDDDDDDDDWo..",
    "..oDPPPPPPPPPo..",
    "...ooooooooooo..",
    "................",
    "................",
], {"G": 0x5FA83A, "g": 0x3E7A24, "Y": 0x9BE05A, "L": 0xC8F59A, "W": 0xF0F0E0, "D": 0x2A5418, "P": 0xD8D2BC})

# --- treasures: fossils and coins ---

sprite("treasure/ammonite", [
    "................",
    "................",
    ".....oooooo.....",
    "...ooSSSSSSoo...",
    "..oSSsooooSSSo..",
    "..oSsoSSSSosSo..",
    ".oSsoSsooSSosSo.",
    ".oSsoSoSSoSosSo.",
    ".oSsoSoSoSSosSo.",
    ".oSsoSsooSsosSo.",
    "..oSsoSSSSosSo..",
    "..oSSsooooSsSo..",
    "...ooSSSSSSoo...",
    ".....oooooo.....",
    "................",
    "................",
], {"S": 0xC2A27A, "s": 0x8C7052})

sprite("treasure/trilobite", [
    "................",
    "......oooo......",
    ".....oSSSSo.....",
    "....oSsSSsSo....",
    "...oSsoSSosSo...",
    "...oSSSSSSSSo...",
    "..oSsSSsSsSSso..",
    "..oSoSSsSsSSoo..",
    "..oSsSSsSsSSso..",
    "..oSoSSsSsSSoo..",
    "...oSsSsSsSso...",
    "...ooSSsSsSoo...",
    ".....oSsSso.....",
    "......oSSo......",
    ".......oo.......",
    "................",
], {"S": 0x8C7A66, "s": 0x5C4E40})

sprite("treasure/fern_fossil", [
    "................",
    "..oooooooooooo..",
    ".oRRRRRRRRRRRRo.",
    ".oRRRRRRdRRRRRo.",
    ".oRRRdRRdRRdRRo.",
    ".oRRRRddddRRRRo.",
    ".oRRdRRRdRRRdRo.",
    ".oRRRdddddddRRo.",
    ".oRRRRRRdRRRRRo.",
    ".oRRdRRRdRRRdRo.",
    ".oRRRddddddRRRo.",
    ".oRRRRRRdRRRRRo.",
    ".orRRRRRdRRRRro.",
    ".orrrrrrrrrrrro.",
    "..oooooooooooo..",
    "................",
], {"R": 0x9A9488, "r": 0x6E685E, "d": 0x5B6B3A})

sprite("treasure/fossil_tooth", [
    "................",
    "........ooooo...",
    ".......oWWWLLo..",
    "......oWWWWLLo..",
    "......oWWWLLDo..",
    ".....oWWWWLLDo..",
    ".....oWWWLLDo...",
    "....oWWWWLLDo...",
    "....oWWWLLDo....",
    "...oWWWWLLDo....",
    "...oWWWLLDo.....",
    "...oWWLLDo......",
    "...oWLLDo.......",
    "...oLLDo........",
    "....ooo.........",
    "................",
], {"W": 0xE6DCC0, "L": 0xBFAE8A, "D": 0x8C7A5A})

sprite("treasure/bone_fragment", [
    "................",
    "..oo.oo.........",
    ".oWWoWWo........",
    ".oWWWWLo........",
    "..oWWWLo........",
    "...oWWLLo.......",
    "....oWWLLo......",
    ".....oWWLLo.....",
    "......oWWLLo....",
    ".......oWWLLo...",
    "........oWWLoo..",
    ".........oWLLDo.",
    "........oWLLLDo.",
    "........oLDooDo.",
    ".........oo..o..",
    "................",
], {"W": 0xF0E8D8, "L": 0xC9BCA4, "D": 0x8C7E66})

COIN = [
    "................",
    "................",
    ".....oooooo.....",
    "....oLLLLLLo....",
    "...oLMMMMMMLo...",
    "..oLMM......MDo.",
    "..oLM........Do.",
    "..oLM........Do.",
    "..oLM........Do.",
    "..oLM........Do.",
    "..oLMM......MDo.",
    "...oLMMMMMMMDo..",
    "....oDDDDDDDo...",
    ".....ooooooo....",
    "................",
    "................",
]
# 8x6 emblems struck in the middle: a sprig, a crown, a snout, an eye.
EMBLEMS = {
    "copper_coin": ["...EE...", "..EMME..", ".EMEEME.", "...EE...", "...EE...", "..EEEE.."],
    "gold_coin": ["E..EE..E", "EE.EE.EE", "EEEEEEEE", "EMEMMEME", "EEEEEEEE", "........"],
    "piglin_coin": [".EEEEEE.", "EMMMMMME", "EMEMMEME", "EMEMMEME", "EMMMMMME", ".EEEEEE."],
    "end_coin": ["........", "..EEEE..", ".EMMMME.", "EMMEEMME", ".EMMMME.", "..EEEE.."],
}
COINS = {
    "copper_coin": {"L": 0xE8A07A, "M": 0xC9744A, "D": 0x8C4A2E, "E": 0x5FA88C},
    "gold_coin": {"L": 0xFFE89A, "M": 0xF2C744, "D": 0xB8862A, "E": 0x8C5A1E},
    "piglin_coin": {"L": 0xF2C77A, "M": 0xD9A034, "D": 0x8C5A1E, "E": 0x5C3414},
    "end_coin": {"L": 0xEAD9F2, "M": 0xC9A0E0, "D": 0x7A5A9A, "E": 0x2E7A6E},
}
for _name, _pal in COINS.items():
    _rows = [list(r) for r in COIN]
    for _y, _line in enumerate(EMBLEMS[_name]):
        for _x, _c in enumerate(_line):
            _rows[5 + _y][5 + _x] = _c if _c != "." else "M"
    sprite(f"treasure/{_name}", ["".join(r) for r in _rows], _pal)

# --- the Journal, the unfound shape and keepsakes ---

sprite("journal", [
    "................",
    "..ooooooooooo...",
    ".oBBBBBBBBBBBo..",
    ".oBbbbbbbbbbBPo.",
    ".oBbbbGGbbbbBPo.",
    ".oBbbbGYGbbbBPo.",
    ".oBbGGYWYGGbBPo.",
    ".oBbbbGYGbbbBPo.",
    ".oBbbbGGbbbbBPo.",
    ".oBbbbbbbbbbBPo.",
    ".oBBBBBBBBBBBPo.",
    ".oSSSSSSSSSSSPo.",
    ".oBBBBBBBBBBBBo.",
    "..oooooooooooRo.",
    ".............R..",
    "................",
], {"B": 0x7A4A2E, "b": 0x5C3420, "G": 0xA67426, "Y": 0xFFC94A, "W": 0xFFF7E1, "P": 0xE8D9A8, "S": 0xC9B88A,
    "R": 0xB5463C})

sprite("journal/unknown", [
    "................",
    "................",
    ".....oooooo.....",
    "....oGGGGGGo....",
    "...oGGggggGGo...",
    "...oGgo..oGGo...",
    "....oo...oGGo...",
    ".........oGGo...",
    "........oGGo....",
    ".......oGGo.....",
    ".......oGGo.....",
    "........oo......",
    ".......oGGo.....",
    ".......oGGo.....",
    "........oo......",
    "................",
], {"G": 0x8C8475, "g": 0x6E675C})

sprite("keepsake", [
    "................",
    ".....oooooo.....",
    "....oYYYYYYo....",
    "...oYGGGGGGYo...",
    "...oYGWWWWGYo...",
    "...oYGWBBWGYo...",
    "...oYGWBBWGYo...",
    "...oYGWWWWGYo...",
    "...oYGGGGGGYo...",
    "....oYYYYYYo....",
    ".....oooooo.....",
    ".....oRooRo.....",
    "....oRRooRRo....",
    "....oRo..oRo....",
    ".....o....o.....",
    "................",
], {"Y": 0xF2C744, "G": 0xB8862A, "W": 0xF5EEDA, "B": 0x5B9BD5, "R": 0xC94A4A})

# --- special gear on vanilla bases ---

ROD = [
    "................",
    "..............oo",
    ".............oWo",
    "............oLo.",
    "...........oLo.s",
    "..........oLo..s",
    ".........oLo...s",
    "........oLo....s",
    ".......oBo.....s",
    "......oBo......s",
    ".....oBo.......s",
    "....oCo.......oR",
    "...oCo........oo",
    "..oCo...........",
    ".oCo............",
    ".oo.............",
]
ROD_PALETTE = {"W": 0xE6F4FA, "L": 0x6FB8E0, "B": 0x3FA7D9, "C": 0x2A6A8C, "s": 0xD8E6EE, "R": 0xE8524A}
sprite("gear/tidecaller", ROD, ROD_PALETTE)
# Cast: the line and bobber are out in the water.
sprite("gear/tidecaller_cast", [r.replace("s", ".").replace("R", ".")[:14] + (".." if i in (11, 12) else r[14:])
                                for i, r in enumerate(ROD)], ROD_PALETTE)


# --- foods: forage and the dishes Farmer's Delight has nothing for ---

def food(name, rows, palette):
    """Like sprite(), for hand-written food art: rows are centred across the top and padded on the right with '.'."""
    width = max(len(r) for r in rows)
    left = (16 - width) // 2
    padded = ["." * left + r + "." * (16 - left - len(r)) for r in rows]
    top = (16 - len(padded)) // 2
    padded = ["." * 16] * top + padded + ["." * 16] * (16 - top - len(padded))
    sprite(name, padded, palette)


food("forage/wild_herbs", [
    "....oo.......",
    "...ogGo..oo..",
    ".oo.oGgoogGo.",
    "ogGooGGoGGgo.",
    "oGgGoGgGoGGo.",
    ".oGGgGGGGgo..",
    "..oGGGGGGo...",
    "...ooGGGoo...",
    "....oBBBo....",
    "....oBbBo....",
    ".....oBo.....",
], {"g": 0xA6D27F, "G": 0x5E9C45, "B": 0xC9A15F, "b": 0x8F6B3A})

food("forage/chilli", [
    "........oo...",
    ".......oGGo..",
    "......oGGo...",
    ".....orrrro..",
    "....orRRRrro.",
    "...orRRrrRrro",
    "...orRrro.orr",
    "..orRrro...oo",
    "..orrro......",
    "..orro.......",
    "..oro........",
    "...oo........",
], {"r": 0xC93A22, "R": 0xEE6A52, "G": 0x3E8E41})

food("forage/samphire", [
    "o....o...o...",
    "ogo..ogo.ogo.",
    "ogo..ogo.ogo.",
    "oGo.oGGo.oGo.",
    "oGooGgGooGGo.",
    ".oGoGGGoGGo..",
    ".oGGGgGGGo...",
    "..oGGGGGo....",
    "...ooGGoo....",
    ".....oo......",
], {"g": 0x9ADBB0, "G": 0x4FA38A})

food("forage/mountain_garlic", [
    ".....oo.....",
    ".....oWo....",
    "....oWWo....",
    "...oWWWWo...",
    "..oWWwWWWo..",
    ".oWWwWWwWWo.",
    ".oWwWWWWwWo.",
    ".oWWWWpWWWo.",
    ".oWWwWWwWWo.",
    "..oWWWWWWo..",
    "...ooWWoo...",
    ".....oo.....",
], {"W": 0xEDE6D0, "w": 0xC4BAA0, "p": 0xB89BC4})

food("forage/truffle", [
    "....oooooo....",
    "..ooDDddDDoo..",
    ".oDDdDDDdDDDo.",
    ".oDdDDhDDDdDo.",
    "oDDDDdDDdDDDDo",
    "oDhDDDDDDDhDDo",
    "oDDDdDDdDDDDDo",
    ".oDDDDDDDdDDo.",
    "..ooDDDDDDoo..",
    "....oooooo....",
], {"D": 0x56443B, "d": 0x33261F, "h": 0x8A7263})

food("forage/peppercorn", [
    ".....oo.....",
    "....oGGo....",
    "...oGGo.....",
    ".oo.oGo.oo..",
    "oPPoGGooPPo.",
    "oPhPooGoPhPo",
    "oPPPoGoPPPo.",
    ".ooo.oGo.ooo",
    "....oGGoo...",
    "...oPPPo....",
    "..oPhPPPo...",
    "..oPPPPPo...",
    "...ooooo....",
], {"P": 0x4A3C38, "h": 0x8E7E76, "G": 0x4F8F43})

food("dish/caviar_toast", [
    "oooooooooooo",
    "oCTTTTTTTTCo",
    "oTTkkTkkTTTo",
    "oTkwkkkwkTTo",
    "oTkkkTkkkgTo",
    "oTTkTkkkTggo",
    "oTkkkkTkTgTo",
    "oTTkTkkkTTTo",
    "oCTTTTTTTTCo",
    "oooooooooooo",
], {"C": 0xB07A3F, "T": 0xE8CD94, "k": 0x2B3440, "w": 0x7C8EA3, "g": 0x5FA047})

food("dish/trail_mix", [
    "......oo......",
    ".....oBBo.....",
    "....oBBbBo....",
    ".....oRRo.....",
    "....oBBBBo....",
    "...oBBbBBBo...",
    "..oBBBBBBbBo..",
    "..oBbBBRBBBo..",
    "..oBBSBBBSBo..",
    "...oBBBBBBo...",
    "....oooooo....",
], {"B": 0xA8794A, "b": 0x7F5632, "R": 0xC8506A, "S": 0xE8D7A0})

food("dish/sweetberry_toast", [
    "oooooooooooo",
    "oCTTTTTTTTCo",
    "oTHHHHHHHHTo",
    "oTHrHHrHHHTo",
    "oTHHrHHHrHTo",
    "oTHHHhHHHHTo",
    "oTHrHHrHhHTo",
    "oTHHHHHHHHTo",
    "oCTTTTTTTTCo",
    "oooooooooooo",
], {"C": 0xB07A3F, "T": 0xE8CD94, "H": 0xE0A83A, "h": 0xF2D27A, "r": 0xC83A55})

food("dish/honey_bun", [
    "....oooooo....",
    "..ooBBBBBBoo..",
    ".oBBbBBBBBbBo.",
    "oBBBHHHHHBBBBo",
    "oBbHHhHHHHhBBo",
    "oBBBHHHHHHBBBo",
    "oBBbBHHHHBbBBo",
    ".oBBBBBBBBBBo.",
    "..ooBBbBBBoo..",
    "....oooooo....",
], {"B": 0xC98A4A, "b": 0x9E6733, "H": 0xF0B83C, "h": 0xFAD878})

food("dish/fish_and_chips", [
    "..o.o.o.o.......",
    ".oYoYoYoYo......",
    ".oYoYoYoYo..oo..",
    ".oYYYYYYYoooFFo.",
    "..ooooooooFFFFo.",
    "...ooooFFFFFFo..",
    "..oFFFFFFfFFo...",
    ".oFFeoFFFFFoo...",
    "oFFoFFFFfFFFFo..",
    ".oFFFFFFFFFFo...",
    "..ooFFFFFFoo....",
    "....oooooo......",
], {"Y": 0xE8B94A, "F": 0xD8935A, "f": 0xA8683A, "e": 0x2B2420})
