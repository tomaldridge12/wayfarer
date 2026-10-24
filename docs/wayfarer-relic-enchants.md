# ExcellentEnchants is rewards only

On this server, no ExcellentEnchants enchant comes from enchanting tables, librarians, mob gear, traders, chests or fishing:
they come only from Wayfarer's rewards (relics, the Veinminer and Treefeller books from the mark ladders, and later quests
and the shop). Out of the box ExcellentEnchants gives them out everywhere, so its files need changing once.

## The quick way

```
python3 scripts/ee_lockdown.py server/plugins/ExcellentEnchants/enchants
```

then restart. `scripts/remote.sh sync` does the same on the remote host (once ExcellentEnchants has booted there and
written its files). The script is idempotent and keeps the files' comments. The Wayfarer plugin itself never edits
ExcellentEnchants' files; it warns at startup (and on `/wayfarer reload` or `/wayfarer marks reload`) about any ExcellentEnchants enchant
that is still obtainable elsewhere, and any reward whose max level is below the level it gives.

## What the script sets (by hand, if you prefer)

For every enchant, open `plugins/ExcellentEnchants/enchants/<name>.yml` and, under `Distribution:`, set:

| Setting | Value | Stops it coming from |
|---|---|---|
| `Treasure` | `true` | enchanting tables (and random mob or villager gear) |
| `Tradeable` | `false` | librarian trades |
| `On_Mob_Spawn_Equipment` | `false` | gear mobs spawn with |
| `On_Traded_Equipment` | `false` | enchanted gear villagers sell |
| `On_Random_Loot` | `false` | chest and fishing loot |
| `Discoverable` | `false` | other discovery (e.g. loot tables that pick any enchant) |

Then restart the server (ExcellentEnchants reads these only on boot). The startup log line
`All N ExcellentEnchants enchants are reward-only (M given by Wayfarer)` confirms it.

## Veinminer and Treefeller (the mark books)

Besides the distribution settings above, both books come in three levels, so the script also sets, in `veinminer.yml` and
`treefeller.yml`:

| File | Setting | Value | Why |
|---|---|---|---|
| `veinminer.yml` | `MaxLevel` | `3` | already 3 by default |
| `veinminer.yml` | `Veinminer.Block_Limit` | `Base: 4.0`, `Per_Level: 4.0`, `Capacity: 12.0` | 4 / 8 / 12 blocks at I / II / III |
| `treefeller.yml` | `MaxLevel` | `3` | the default is 1, so II and III books would be capped |
| `treefeller.yml` | `Treefeller.Block_Limit` | `Base: 32.0`, `Per_Level: 32.0`, `Capacity: 96.0` | 32 / 64 / 96 logs at I / II / III |

Check the block-limit formula against ExcellentEnchants' own comments in those files when you change them; the aim is a
small vein at I and a whole tree only at III.

## Relic enchants in the default kit

- `blindness` (enderman_weapon, enderman_weapon_2)
- `double_strike` (enderman_weapon, enderman_weapon_2, wayfarers_blade)
- `elemental_protection` (creeper_armour, creeper_armour_2)
- `hardened` (zombie_armour, zombie_armour_2)
- `jumping` (spider_armour, spider_armour_2)
- `night_vision` (skeleton_armour, skeleton_armour_2)
- `paralyze` (spider_weapon, spider_weapon_2)
- `rage` (zombie_weapon, zombie_weapon_2)
- `regrowth` (zombie_armour, zombie_armour_2)
- `saturation` (skeleton_armour, skeleton_armour_2)
- `sniper` (skeleton_weapon, skeleton_weapon_2)
- `speed` (spider_armour, spider_armour_2)
- `stopping_force` (creeper_armour, creeper_armour_2)
- `thunder` (creeper_weapon, creeper_weapon_2)
- `vampire` (wayfarers_blade, zombie_weapon, zombie_weapon_2)
- `venom` (spider_weapon, spider_weapon_2)
- `withered_arrows` (skeleton_weapon, skeleton_weapon_2)

If you change relic presets in `banners.yml` or the books in `marks.yml`, the startup check follows your config, not
this list. The books add `veinminer` and `treefeller`.

## Without ExcellentEnchants

Relics still work: they keep their vanilla enchants, family edge and perks, and the log says which enchants were left out.
