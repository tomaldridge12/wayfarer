# Roadmap

A first capture of where Wayfarer could go next, from brainstorming on the server. Nothing here is decided: it is a list to
refine, cut and reorder. The aim is a reason to keep playing after elytra and netherite, with less of the boring grind and more
use for the items nobody wants.

## Principles

1. **Reward going out into the world.** Like the kill tracker, mob farms, spawners and place-and-break loops earn nothing.
2. **New gear is a sidegrade to netherite, not an upgrade.** A reason to swap gear, not to throw netherite away.
3. **No end goal.** Build frameworks that new content slots into through config: quests, loot, events, bosses.
4. **Group goals matter as much as solo ones.**
5. **Every quest and milestone gives a reason to do it.** Each pays out something you can't get any other way: a unique
   enchant, loot or item, not just a number.

## Owner notes (2026-10-24)

- **ExcellentEnchants are rewards only.** None of its enchants should come from enchanting tables, villager trades, loot
  chests, fishing or mob gear. Today only the relic enchants are locked this way (`docs/wayfarer-relic-enchants.md`); extend
  that to every ExcellentEnchants enchant, and hand them out through ladders, quests, boss loot and the shop.
- **Lots of fun, odd milestones.** Beyond the big ladders, add many small one-off milestones with their own unique items,
  such as "mine 10,000 moss" or "craft 100 hoes". They should be playful and surprising, and each should pay out a unique
  enchant, item, cosmetic or loot.
- **Loads of quests and milestones overall.** Variety matters more than size: every activity should have something to chase.
- Crafting isn't counted yet. "Craft 100 hoes" needs a `craft:<item>` activity in the progression core; it's a small
  listener, so it's worth adding early, since counters only start once recording does.

## Foundation: one activity tracker

Skills, milestones, dailies, pity drops and group goals all start with "player X did Y". Build that once: a counter per player
for blocks mined, sheep sheared, fish caught, crops harvested, mobs killed, structures found and so on, with anti-cheese applied
in one place. Every module below reads from it.

- Vanilla's "blocks mined" stat also counts blocks a player placed and broke again, so Wayfarer keeps its own counts and marks
  player-placed blocks in chunk data, the way placed banners are stored.
- No credit for spawner mobs, matching the kill tracker.

## Modules

Sizes are rough: S is a small feature, M a module, L a large system.

### 1. Quests

| Feature | Spec | Size |
|---|---|---|
| Milestone ladders | Tiered lifetime counts (sand, wool, logs, fish, crops, deepslate). Rewards are tools that make that activity faster, so the grind pays off once and is lighter after. Same shape as the trophy ladders. | M |
| Dailies and weekly | Three dailies and one weekly per player, drawn from a YAML quest pool with a date-based seed. Reroll one for XP. Streaks pause rather than reset when someone misses a day. | M |
| Quest tokens and shop | Every quest pays tokens; a shop sells cosmetics and single-use items, so unlucky rolls still add up. | S |
| Group objectives | Server-wide counters, such as "catch 500 salmon between everyone" or "complete 5 ominous trial chambers". Everyone who contributed gets a trophy and their own loot, the way boss loot already works. | M |
| Daily events | A theme with a modifier and a group goal, e.g. "The Drowned Tide: double fishing loot today, and a server quest to catch 500 salmon". | S |

Milestone reward ideas:

| Milestone | Tiers | Reward |
|---|---|---|
| Mine sand | 1k / 10k / 50k | Dune Shovel: digs a 3×3 area, sand only |
| Shear wool | 500 / 5k | Shepherd's Shears: shears all sheep within a few blocks at once |
| Catch fish | 200 / 2k | Angler's Rod: faster bites, better treasure chance |
| Cut logs | 5k / 25k | Feller's Axe: fells the whole tree |
| Harvest crops | 5k | Reaper's Hoe: harvests and replants a 3×3 area |
| Mine deepslate | 10k | Delver's Pick: digs a 3×3 area through deepslate and tuff |

Other quest-only rewards: titles, armour trims, banner patterns, particle trails, named player heads, biome compass charges,
waystone scrolls, boss summon keys, relic reforges.

### 2. Skills

Mining, Woodcutting, Farming, Fishing, Combat and Exploration, each levelled by doing it.

- Perks are small and passive: about +5% double drops, faster bites, less hunger drain. The big rewards stay with milestones and
  loot, so skills don't take over the game.
- Every 10 levels unlocks something: a cosmetic title, a recipe or a quest line.
- Skills go up continuously and give small bonuses; milestones are fixed thresholds that give items. Both read the same counters.

Size: L

### 3. Loot with rarities

Extend the relic, Ascended and Legendary items into a general system.

- Five rarity tiers, Common to Legendary, each with a set number of special-ability slots ("affixes") drawn from a pool in config.
- Drop sources: bosses, structure guardians, quest rewards, trial chamber vaults, rare world drops.
- Reforge station: reroll one affix for XP plus materials, each reroll costing more than the last.
- Item sets, like the four-family Wayfarer set bonus.

Size: L (most of the item framework exists)

### 4. Capped vein mining and tree felling

- Vein I/II/III mines 1, 2 or 4 extra connected blocks of the same ore; Felling I/II/III does the same for logs.
- Wayfarer's own enchants rather than ExcellentEnchants' uncapped versions, so the cap is guaranteed.
- Only from milestones and quests; the relic enchant exclusivity check already handles keeping enchants out of tables and trades.
- Extra durability per extra block mined.

Size: M

### 5. Enchanting overhaul

| Feature | Spec | Size |
|---|---|---|
| Reroll | Pay a few levels to reshuffle the enchanting table's options. | S |
| Attune | Pick a target enchant, pay extra XP to raise its odds. Never guaranteed. | M |
| Lectern purchase | Buy specific enchants, including ExcellentEnchants' list, at a lectern. | M |

Mob farms make XP close to free, so purchases need more than XP:

- Each purchase also costs quest tokens or arcane dust from salvaging, which farms don't produce.
- You can only buy an enchant you've already got once by normal means.
- Each purchase of the same enchant costs that player more.

### 6. Exploration

| Feature | Spec | Size |
|---|---|---|
| Biome and structure locator | At the cartography table, pay XP for a compass or map to a chosen biome or structure. The action bar shows the distance. Cost goes up with rarity. A Guardian's Chart variant points to the nearest structure whose guardian you haven't beaten. | M |
| Wayfarer's Journal | Checklists of biomes, structures, fish, bosses and trophy ladders, with titles and rewards at each completion level. | M |
| Waystones | XP-cost teleports between built waystones, priced by distance. | M |

The biome search is slow and runs on the main thread, so it needs a search radius limit and a per-player cooldown.

### 7. XP sinks

| Feature | Spec | Size |
|---|---|---|
| Banner empowerment | Feed XP into a placed banner for a bigger radius or a temporary boost. | S |
| Grindstone extraction | Move one enchant from an item onto a book. | S |
| Anvil fix | Pay XP to clear an item's repair penalty and the "Too Expensive!" cap. | S |
| XP bottling | Store levels in bottles so friends can trade them. | S |
| Return to grave | Pay XP to teleport to your grave, or for a compass to it (VanillaTweaks graves). | S |
| Summoning altar | Boss materials plus XP call a specific boss on demand. | M |
| Shrines | A placed block where XP buys a 10-minute buff (Haste, Luck, Night Vision). | S |
| Villager reroll | Pay XP to reroll a librarian's trades. | S |
| Spawner pickup | Silk touch plus a large XP cost. | S |

Charge XP points, not levels: each level costs more points than the last, so a level charge hurts high-level players more.
Prices can be shown in levels and converted.

### 8. Less grind, more use for junk

- **Pity counters:** wither skulls, tridents, shulker shells and breeze rods drop for certain after N kills without one. Uses
  the existing kill counts. (S)
- **Salvager block:** junk in, XP, dust or tokens out, at rates set in config. (S)
- **Junk as quest turn-ins and reforge materials:** rotten flesh (also cooks into leather in a smoker), spare stone, Bane of
  Arthropods books (break down into dust), horse armour, saddles, gold and chainmail armour (salvage to nuggets), poisonous
  potatoes and spider eyes (poison arrow coating). (content)

### 9. Claude routine for content

Once a week a Claude routine writes new quest pool entries, event themes and loot affix ideas, and opens a pull request for
review. The server pulls the merged pool, by a timed `git pull` or by downloading the file once a day. The plugin rolls the
dailies itself, so a missed routine run never leaves the server without quests, and nothing reaches the live economy unreviewed.

## Build order

1. **Foundation:** the activity tracker with anti-cheese, plus pity counters (small, and proves the tracker works).
2. **Quests:** milestones with the first three tool rewards (Dune Shovel, Shepherd's Shears, Vein I), then dailies and tokens.
3. **Exploration:** the biome and structure locator, then the Journal.
4. **Enchanting:** reroll, then the lectern purchase with token or dust pricing.
5. **Group content:** group objectives, daily events, then the Claude routine.
6. **The big systems:** skills, then loot rarities with reforging.
7. **Ongoing:** waystones, the remaining XP sinks, new sets, new bosses.

Steps 1 and 2 alone give players a reason to keep going after elytra.

## Open questions

- **Plugin or mods?** Everything here works as a Paper plugin. A client mod is only worth it for custom models, UI or new
  blocks, and custom models could come from a resource pack instead.
- **Do skills reset or prestige?**
- **How rare are Legendaries, and can they be traded?**
- **Do existing players get milestone credit from vanilla stats,** or does everyone start at zero?
- **Which ExcellentEnchants enchants** become quest, shop or lectern rewards, and which stay table-only?
