// Reads the plugin's default config into the Guide's own shapes. Defaults mirror the plugin's parsers
// (RelicDefinition.defaultEdge, Perk.describe); a missing file or section yields nothing, not an error.
import { parse } from 'yaml';
import type { GuideFiles } from './files';

export interface Family { id: string; mobs: string[] }

export interface BannerLook { colour: string; patterns: { pattern: string; colour: string }[] }

export interface BannerRow {
  reward: string; id: string; name: string; family: string; kills: number | null;
  bonus: number; reduction: number; radius: number; look: BannerLook; drops: Record<string, number>;
}

export interface HeadRow { reward: string; id: string; name: string; family: string; kills: number; item: string; skin: string | null }

export type Slot = 'weapon' | 'head' | 'chest' | 'legs' | 'feet' | 'offhand';

export interface RelicRow {
  reward: string; id: string; name: string; family: string; kills: number | null; dropOnly: boolean;
  tier: number; slot: Slot; item: string; edge: number; dye: string | null; trim: string | null;
  enchants: { id: string; level: number; excellent: boolean }[]; perks: string[];
  /** The alloy it is made of (alloy-shaped relics at tiers I, II and Ascended), and the art to draw. */
  alloy: string | null; art: string;
}

export type Heat = 'low' | 'high' | 'soul';

export interface AlloyRow {
  id: string; name: string; colour: string; heat: Heat; meltSeconds: number;
  recipe: { item: string; count: number }[]; yield: number;
  /** Vanilla tier of the gear its template upgrades (any other alloy's gear works too). */
  upgrades: string;
  template: { boss: string | null; duplicateBlock: string };
  /** What it is good at, as players read it: durability, armour, identity attributes, repair. */
  identity: string[];
}

export interface AlloyRules { heatBlocks: Record<string, Heat>; maxIngots: number; endless: boolean; cap: number }

export interface WayfarerSet { families: number; reduction: number; hearts: number }

export interface BossVariant { id: string; name: string; entity: string; colours: string[]; abilities: string[]; equipment: Record<string, string> }

export interface BossRow {
  reward: string; id: string; name: string; health: number; damage: number; cooldown: number;
  entity: string | null; biomes: string[] | 'any'; chance: number | null; nightOnly: boolean; structures: string[];
  roams: boolean; base: string | null; legendary: string | null; variants: BossVariant[];
}

export interface BossRules {
  health: number[]; damage: number[]; starDistances: number[]; guardianMinStars: number;
  extraStarChance: number; roamingMaxStars: number; roamingCooldown: number;
  ascendedChance: number[]; legendaryChance: number[];
}

export interface PityRow { id: string; item: string; mobs: string[]; heldItem: string | null; chance: number; kills: number }

export interface Config {
  families: Family[];
  allMobs: string | null;
  banners: BannerRow[];
  heads: HeadRow[];
  relics: RelicRow[];
  wayfarerSet: WayfarerSet | null;
  bosses: BossRow[];
  bossRules: BossRules | null;
  pity: PityRow[];
  excludedSpawns: string[];
  alloys: AlloyRow[];
  alloyRules: AlloyRules | null;
}

function read(files: GuideFiles, dir: string, file: string): any {
  const text = files.text(`${dir}/${file}`);
  return text === undefined ? {} : parse(text) ?? {};
}

const entries = (o: any): [string, any][] => Object.entries(o ?? {});

// Mirrors TrophyHeads: mobs with a vanilla head use it; the rest are player heads wearing Mojang's MHF_ skin.
const VANILLA_HEADS: Record<string, string> = {
  zombie: 'zombie_head', skeleton: 'skeleton_skull', creeper: 'creeper_head',
  wither_skeleton: 'wither_skeleton_skull', piglin: 'piglin_head', ender_dragon: 'dragon_head',
};
const mhf = (family: string) => 'MHF_' + family.split('_').map((w) => w[0].toUpperCase() + w.slice(1)).join('');

export const TIER_NAMES = ['Regalia', 'Tier I', 'Tier II', 'Ascended', 'Legendary'];

export function defaultEdge(slot: Slot, tier: number, family: string): number {
  if (tier === 0) return 0;
  if (family === 'all') return tier >= 4 ? 15 : 10;
  if (tier >= 3) return slot === 'weapon' ? 50 : 35;
  return slot === 'weapon' ? (tier >= 2 ? 40 : 30) : (tier >= 2 ? 30 : 25);
}

const PERKS: Record<string, { amount: number; text: (a: number, family: string) => string }> = {
  hunger_immunity: { amount: 1, text: () => 'Immune to Hunger' },
  poison_immunity: { amount: 1, text: () => 'Immune to Poison' },
  no_knockback: { amount: 1, text: (_, f) => `No knockback from the ${f} family` },
  arrow_saver: { amount: 0.3, text: (a) => `${Math.round(a * 100)}% chance an arrow is not used up` },
  projectile_ward: { amount: 30, text: (a, f) => `−${a}% damage from ${f} arrows` },
  cobweb_walker: { amount: 1, text: () => 'Cobwebs dissolve as you walk into them' },
  fall_cushion: { amount: 5, text: (a) => `No fall damage from falls of ${a} blocks or less` },
  knock_away: { amount: 1.5, text: (_, f) => `Hits knock the ${f} family far back` },
  explosion_ward: { amount: 50, text: (a) => `−${a}% explosion damage` },
  enderman_calm: { amount: 1, text: () => 'Looking at endermen does not anger them' },
  pearl_mastery: { amount: 50, text: (a) => `Ender pearls do no damage; ${a}% shorter cooldown` },
  kill_heal: { amount: 1, text: (a) => `Each kill heals ${a} ❤` },
  lightning_strike: { amount: 0.15, text: (a) => `${Math.round(a * 100)}% chance to call lightning on hit` },
};

export function describePerk(p: { type: string; amount?: number; family?: string }): string {
  const perk = PERKS[p.type];
  if (!perk) return p.type.replace(/_/g, ' ');
  return perk.text(p.amount ?? perk.amount, (p.family ?? '').replace(/_/g, ' '));
}

const ALLOY_SHAPES = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'spear', 'helmet', 'chestplate', 'leggings', 'boots'];
/** The alloy shape of a vanilla item (diamond_axe -> axe), or null; mirrors RelicDefinition.alloyShape. */
export const alloyShape = (item: string) => { const s = item.split('_').pop() ?? ''; return ALLOY_SHAPES.includes(s) ? s : null; };

const percent = (x: number) => `${+(Math.abs(x) * 100).toFixed(1)}%`;
const per = (to: string) => (to === 'armour' ? ' per armour piece' : '');
// Mirrors the attributes alloys.yml uses; anything else reads as its own name.
const BONUSES: Record<string, (a: number, multiply: boolean, to: string) => string> = {
  luck: (a, _, to) => `+${a} luck${per(to)} (better fishing and chest loot)`,
  oxygen_bonus: (a, _, to) => `+${a} breath${per(to)}`,
  water_movement_efficiency: (a, _, to) => `${percent(a)} faster swimming${per(to)}`,
  submerged_mining_speed: () => 'Tools mine at full speed underwater',
  burning_time: (a, _, to) => `${percent(a)} shorter burning${per(to)}`,
  movement_speed: (a, _, to) => `${percent(a)} ${a < 0 ? 'slower' : 'faster'}${per(to)}`,
};

function identity(a: any): string[] {
  const t = a.tools ?? {}; const ar = a.armour ?? {};
  const points: number[] = ar.points ?? [2, 6, 5, 2];
  const out = [`Tools: ${t.durability ?? 250} durability, mining speed ${t.speed ?? 6}`
    + (t['damage-bonus'] ? `, +${t['damage-bonus']} damage` : '')];
  out.push(`Armour: ${points.reduce((x, y) => x + y, 0)} points, toughness ${ar.toughness ?? 0}`
    + (ar['knockback-resistance'] ? `, knockback resistance ${ar['knockback-resistance']}` : ''));
  for (const b of a.bonuses ?? []) {
    const text = BONUSES[b.attribute];
    out.push(text ? text(b.amount, b.operation === 'multiply', b.to ?? 'all') : `${b.attribute} ${b.amount}`);
  }
  if (a.luck?.['fortune-bonus']) out.push(`Tools mine as if Fortune were ${a.luck['fortune-bonus']} higher`);
  if (a.luck?.['extra-loot-chance']) out.push(`${percent(a.luck['extra-loot-chance'])} chance of extra loot on weapon kills`);
  if (a.fireproof) out.push('Survives fire and lava');
  const repair = a.repair ?? {};
  const items: string[] = (repair.items ?? ['ingot']).map((i: string) => (i === 'ingot' ? `${a.name ?? 'its'} ingots` : i.replace(/_/g, ' ') + 's'));
  out.push(`Repaired with ${items.join(' or ')}, ${percent(repair.share ?? 0.25)} each`);
  return out;
}

/** The pity rule from core: the ceil(1 / chance)-th counted kill in a row without the item drops it. */
export const pityKills = (chance: number) => Math.ceil(1 / chance - 1e-9);

export function readConfig(dir: string, files: GuideFiles): Config {
  const banners = read(files, dir, 'banners.yml');
  const bosses = read(files, dir, 'bosses.yml');
  const progression = read(files, dir, 'progression.yml');
  const alloysYml = read(files, dir, 'alloys.yml');

  const families = entries(banners.families).map(([id, mobs]) => ({ id, mobs: mobs as string[] }));

  const bannerRows: BannerRow[] = entries(banners.banners).map(([id, b]) => ({
    reward: `banner:${id}`, id, name: b.name, family: b.family, kills: b.kills ?? null,
    bonus: b['damage-bonus'] ?? 10, reduction: b['damage-reduction'] ?? 10, radius: b.radius ?? 16,
    look: {
      colour: b.colour ?? 'white',
      patterns: (b.patterns ?? []).map((p: string) => { const [pattern, colour] = p.split(':'); return { pattern, colour }; }),
    },
    drops: b.drops ?? {},
  }));

  const heads: HeadRow[] = entries(banners.heads).map(([id, h]) => ({
    reward: `head:${id}`, id, name: h.name, family: h.family, kills: h.kills,
    item: h.item ?? VANILLA_HEADS[h.family] ?? 'player_head',
    skin: h.skin ?? ((h.item ?? VANILLA_HEADS[h.family] ?? 'player_head') === 'player_head' ? mhf(h.family) : null),
  }));

  const relics: RelicRow[] = entries(banners.relics).map(([id, r]) => {
    const tier = r.tier ?? 1;
    const slot = (r.slot ?? 'weapon') as Slot;
    return {
      reward: `relic:${id}`, id, name: r.name, family: r.family, kills: r.kills ?? null, dropOnly: !!r['drop-only'],
      tier, slot, item: r.item, edge: r.edge ?? defaultEdge(slot, tier, r.family), dye: r.dye ?? null, trim: r.trim ?? null,
      enchants: entries(r.enchants).map(([e, level]) => ({
        id: e.replace(/^excellentenchants:/, ''), level: level as number, excellent: e.startsWith('excellentenchants:'),
      })),
      perks: (r.perks ?? []).map((p: any) => describePerk(p)),
      alloy: r.alloy ?? null,
      art: r.alloy && alloyShape(r.item) ? `wayfarer:${r.alloy}_${alloyShape(r.item)}` : r.item,
    };
  });

  const set = banners['wayfarer-set'];
  const bossLoot = banners.bosses ?? {};
  const bossFamilies = bossLoot.families ?? {};
  const legendaries = bossLoot.legendaries ?? {};

  const bossRows: BossRow[] = entries(bosses.bosses).map(([id, b]) => {
    const spawn = b.spawn ?? {};
    return {
      reward: `boss:${id}`, id, name: b.name, health: b.health, damage: b.damage, cooldown: b['cooldown-minutes'] ?? 60,
      entity: spawn.entity ?? null, biomes: spawn.biomes ?? [], chance: spawn.chance ?? null,
      nightOnly: !!spawn['night-only'], structures: spawn.structures ?? [], roams: !!spawn.entity,
      base: bossFamilies[id]?.base || null, legendary: legendaries[id] ?? null,
      variants: entries(b.variants).map(([vid, v]) => ({
        id: vid, name: v.name, entity: v.entity, colours: v.colours ?? [], abilities: v.abilities ?? [], equipment: v.equipment ?? {},
      })),
    };
  });

  const sp = bosses.spawning;
  const bossRules: BossRules | null = bosses.stars && sp ? {
    health: bosses.stars.health, damage: bosses.stars.damage, starDistances: sp['star-distances'],
    guardianMinStars: sp['guardian-min-stars'], extraStarChance: sp['extra-star-chance'],
    roamingMaxStars: sp['natural-max-stars'], roamingCooldown: sp['natural-cooldown-minutes'],
    ascendedChance: bossLoot.loot?.['ascended-chance'] ?? [], legendaryChance: bossLoot.loot?.['legendary-chance'] ?? [],
  } : null;

  const pity: PityRow[] = entries(progression.pity).map(([id, p]) => ({
    id, item: p.item, mobs: p.mobs ?? [], heldItem: p['held-item'] ?? null, chance: p.chance, kills: pityKills(p.chance),
  }));

  const alloys: AlloyRow[] = entries(alloysYml.alloys).map(([id, a]) => ({
    id, name: a.name ?? id, colour: a.colour ?? '#ffffff', heat: a.heat, meltSeconds: a['melt-seconds'] ?? 30,
    recipe: entries(a.recipe).map(([item, count]) => ({ item, count: count as number })), yield: a.yield ?? 1,
    upgrades: a.upgrades ?? a.material ?? 'iron',
    template: { boss: a.template?.boss ?? null, duplicateBlock: a.template?.['duplicate-block'] ?? 'cobblestone' },
    identity: identity(a),
  }));
  const crucible = alloysYml.crucible ?? {};
  const alloyRules: AlloyRules | null = alloysYml.alloys ? {
    heatBlocks: crucible.heat ?? {}, maxIngots: crucible['max-ingots'] ?? 8,
    endless: alloysYml.anvil?.endless ?? true, cap: alloysYml.anvil?.cap ?? 30,
  } : null;

  return {
    alloys, alloyRules,
    families, allMobs: banners.counting?.['all-mobs'] ?? null,
    banners: bannerRows, heads, relics,
    wayfarerSet: set ? { families: set.families, reduction: set['damage-reduction'], hearts: set.hearts } : null,
    bosses: bossRows, bossRules, pity,
    excludedSpawns: progression.counting?.['exclude-spawn-reasons'] ?? [],
  };
}
