# Making relic enchants exclusive (ExcellentEnchants)

Wayfarer's relics come with ExcellentEnchants enchants. Out of the box, ExcellentEnchants also gives these enchants out through
enchanting tables, librarians, mob equipment and loot, so a relic's enchant would not be special. Wayfarer never edits
ExcellentEnchants' files; this page says what to change, and Wayfarer warns at startup (and on `/wayfarer reload`) about any
reward enchant that is still obtainable elsewhere.

## For each enchant below

Open `plugins/ExcellentEnchants/enchants/<name>.yml` and, under `Distribution:`, set:

| Setting | Value | Stops it coming from |
|---|---|---|
| `Treasure` | `true` | enchanting tables (and random mob or villager gear) |
| `Tradeable` | `false` | librarian trades |
| `On_Mob_Spawn_Equipment` | `false` | gear mobs spawn with |
| `On_Traded_Equipment` | `false` | enchanted gear villagers sell |
| `On_Random_Loot` | `false` | chest and fishing loot |
| `Discoverable` | `false` | other discovery (e.g. loot tables that pick any enchant) |

Then restart the server (ExcellentEnchants reads these only on boot). The startup log line
`[banners] All N relic enchants are exclusive to Wayfarer` confirms it.

## Reward enchants in the default kit

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

If you change relic presets in `banners.yml`, the startup check follows your config, not this list.

## Without ExcellentEnchants

Relics still work: they keep their vanilla enchants, family edge and perks, and the log says which enchants were left out.
