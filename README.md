# Wayfarer

A Paper 26.3 plugin for a friends' Minecraft server: Terraria-style trophy progression.

Kill enough of a mob family (zombies, skeletons, spiders, creepers, endermen) and you earn its rewards, one ladder per family:

- **Banners** that, once placed, give everyone nearby bonus damage against that family and less damage from it.
- **Trophy heads** that show your live kill count when placed.
- **Relics**: a themed weapon and armour piece per family in two tiers, with a family edge, perks and exclusive enchants. Wear relic armour from four families for the Wayfarer set bonus. Relics are meant as a trade-off against netherite, not an upgrade.
- An all-mobs ladder topped by the Wayfarer's Blade.
- **Bosses**: nine bosses (23 variants) rated 1 to 5 stars, built only on the Paper API. They appear as rare biome spawns and
  as guardians of structures (pyramids, monuments, bastions, strongholds and more), fight with abilities and a second phase,
  and give everyone who helped their own loot: boss heads, materials, a boss ladder (banner, head, regalia), and rare
  Ascended relics and Legendaries.

Everything is tunable in `plugins/Wayfarer/banners.yml` and `plugins/Wayfarer/bosses.yml`. Commands live under `/mobbanners`
(`progress`, `claim`, and operator tools) and `/wayfarer boss` (operator tools: `spawn`, `spawnall`, `list`, `killall`, `defeat`).

## Optional plugins

- [ExcellentEnchants](https://modrinth.com/plugin/excellentenchants) (with NightCore) adds the relics' custom enchants; see
  [docs/wayfarer-relic-enchants.md](docs/wayfarer-relic-enchants.md) to make them exclusive.
- [packetevents](https://modrinth.com/plugin/packetevents) shows those enchants' descriptions in tooltips.

## Building and running

Needs JDK 25.

```bash
source scripts/env.sh        # puts Homebrew JDK 25 on PATH (edit for your machine)
./gradlew :plugin:build      # build and unit tests
scripts/dev.sh               # dev server in server/ with the plugin loaded
scripts/smoke.sh             # boots a throwaway server and checks the log (about a minute)
```
