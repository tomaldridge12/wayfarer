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

export interface MarkRow {
  id: string; name: string; colour: string; unit: string; counts: string[]; ranksAt: number[];
  tool: string; materials: string[];
  /** What each rank does, as players read it. */
  ranks: string[];
}

export interface BookRow { id: string; name: string; enchant: string; unit: string; counts: string[]; levelsAt: number[] }

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

export interface CurioRow { id: string; name: string; colour: string; description: string[]; flavour: string; tool: boolean; cooldown: number; icon: string }
export interface GearRow { id: string; name: string; colour: string; base: string; enchants: { id: string; level: number; excellent: boolean }[]; flavour: string; icon: string }
export interface SupplyRow { id: string; name: string; colour: string; description: string[]; flavour: string; icon: string }
export interface CollectionRow { id: string; title: string; colour: string; blurb: string; unit: string; milestones: { at: string; rewards: string[] }[] }
export interface FishRow {
  id: string; name: string; rarity: string; water: string; temps: string[]; time: string; weather: string; place: string;
  minGrams: number; maxGrams: number; flavour: string; hint: string | null; icon: string;
}
export interface TreasureRow { id: string; name: string; colour: string; kind: string; flavour: string; brush: string[]; chests: Record<string, number>; chart: string | null; icon: string }
export interface TreasureRules { brushChance: number; ceiling: number; sites: Record<string, string[]> }
export interface FeatRow { id: string; name: string; flavour: string; counts: string[]; distinct: boolean; at: number | 'all'; hidden: boolean; rewards: string[] }
export interface CosmeticRow {
  id: string; kind: 'title' | 'trail' | 'hat' | 'pet'; name: string; colour: string;
  /** Hats: the vanilla item or pack model shown on the head, and the hat-ability id if it has one. */
  item?: string; ability?: string;
  /** Pets: the baby mob it is, or the pack model it floats as. */
  entity?: string; model?: string;
}
/** A small perk a worn hat gives (curios.yml, hat-abilities). */
export interface HatAbilityRow { id: string; name: string; colour: string; description: string[] }
export interface ObjectiveRules {
  dailies: number; dailyReset: string; zone: string; weeklyDay: string; eventStart: string; eventEnd: string;
  freeRerolls: number; rerollPoints: number; rerollStepPoints: number; streakEvery: number; streakTokens: number; streakReward: string | null;
  duplicateTokens: number; stages: string[]; dailyCount: number; weeklyCount: number; goalCount: number; tables: string[];
}
export interface EventRow { id: string; name: string; blurb: string; goal: string; target: number }
export interface ShopRules { cosmetics: Record<string, number>; supplies: Record<string, number>; extraReroll: number; templateCopy: number; wayfinder: number; kindling: number; enchantPerLevel: number }
export interface EnchantingRules { reshuffle: boolean; levels: number; trigger: string }
export interface ForageRow { id: string; name: string; colour: string; description: string[]; flavour: string; biomes: string[]; plants: string[]; chance: number; pity: number; icon: string }
export interface DishRow {
  id: string; name: string; colour: string; family: string; base: string; nutrition: number; saturation: number;
  effects: { type: string; amplifier: number; seconds: number }[]; rare: boolean; ingredients: string[]; flavour: string; hint: string; icon: string;
}
export interface ComfortsRules { leather: boolean; flesh: number; leatherOut: number; keep: boolean; hotbar: boolean; armour: boolean; offhand: boolean }
export interface ExplorationRules { range: number; cooldown: number; structurePoints: number; biomePoints: number; chartPoints: number; unfoundMultiplier: number; rare: Record<string, number>; structures: Record<string, string>; kindlingSeconds: number }

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
  /** Which earnings the whole server hears about (progression.yml); a missing switch is on. */
  announce: Record<string, boolean>;
  alloys: AlloyRow[];
  alloyRules: AlloyRules | null;
  marks: MarkRow[];
  books: BookRow[];
  curios: CurioRow[];
  gear: GearRow[];
  supplies: SupplyRow[];
  collections: CollectionRow[];
  fish: FishRow[];
  fishChance: number;
  treasures: TreasureRow[];
  treasureRules: TreasureRules | null;
  feats: FeatRow[];
  cosmetics: CosmeticRow[];
  hatAbilities: HatAbilityRow[];
  /** Cosmetic ids that some reward table can roll. */
  tableCosmetics: string[];
  /** How many things a player can track at once (objectives.yml tracker.max). */
  trackerMax: number;
  objectives: ObjectiveRules | null;
  events: EventRow[];
  eventRotation: string[];
  shop: ShopRules | null;
  exploration: ExplorationRules | null;
  enchanting: EnchantingRules | null;
  comforts: ComfortsRules | null;
  forage: ForageRow[];
  dishes: DishRow[];
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

/** The announcement categories in progression.yml, in the order the Guide lists them. */
export const ANNOUNCE_CATEGORIES = ['feat', 'boss-defeat', 'ascended-drop', 'legendary-drop', 'server-goal', 'streak', 'ladder-rung'];

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

// Mirrors MarkRules: what each mark's rank fields mean.
function rankText(mark: string, r: any): string {
  switch (mark) {
    case 'dune': return `Digs ${r.area} of sand${r.gravel ? ' and gravel' : ''} at once`;
    case 'reaper': return `Harvests and replants ${r.area} of crops`;
    case 'shepherd': return `Shears every sheep within ${r.radius} blocks${r['extra-wool'] ? `, ${percent(r['extra-wool'])} chance of extra wool` : ''}`;
    case 'angler': return `Bites come ${percent(1 - (r['bite-wait'] ?? 1))} sooner${r.treasure ? `, ${percent(r.treasure)} chance of treasure` : ''}`;
    default: return '';
  }
}

/** Curio effects that are right-click tools (the rest are charms that work while carried). */
const TOOL_EFFECTS = ['spread', 'light', 'sense', 'recall', 'locate'];

const title = (id: string) => id.split(/[_:]/).filter(Boolean).map((w) => w[0].toUpperCase() + w.slice(1)).join(' ');
const num = (n: number) => n.toLocaleString('en-GB');

/** The pity rule from core: the ceil(1 / chance)-th counted kill in a row without the item drops it. */
export const pityKills = (chance: number) => Math.ceil(1 / chance - 1e-9);

export function readConfig(dir: string, files: GuideFiles): Config {
  const banners = read(files, dir, 'banners.yml');
  const bosses = read(files, dir, 'bosses.yml');
  const progression = read(files, dir, 'progression.yml');
  const alloysYml = read(files, dir, 'alloys.yml');
  const marksYml = read(files, dir, 'marks.yml');

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

  const asList = (v: any): string[] => (Array.isArray(v) ? v : v == null ? [] : [v]).map(String);
  const marks: MarkRow[] = entries(marksYml.marks).map(([id, m]) => ({
    id, name: m.name ?? id, colour: m.colour ?? '#ffffff', unit: m.unit ?? 'block', counts: asList(m.counts),
    ranksAt: m['ranks-at'] ?? [], tool: m.tool, materials: asList(m.material),
    ranks: (m.ranks ?? []).map((r: any) => rankText(id, r)),
  }));
  const books: BookRow[] = entries(marksYml.books).map(([id, b]) => ({
    id, name: b.name ?? id, enchant: b.enchant, unit: b.unit ?? 'block', counts: asList(b.counts), levelsAt: b['levels-at'] ?? [],
  }));

  const curiosYml = read(files, dir, 'curios.yml');
  const collectionsYml = read(files, dir, 'collections.yml');
  const objectivesYml = read(files, dir, 'objectives.yml');
  const explorationYml = read(files, dir, 'exploration.yml');
  const enchantingYml = read(files, dir, 'enchanting.yml');
  const comfortsYml = read(files, dir, 'comforts.yml');
  const foodsYml = read(files, dir, 'foods.yml');
  const curios: CurioRow[] = entries(curiosYml.curios).map(([id, c]) => ({
    id, name: c.name ?? title(id), colour: c.colour ?? '#e8d9a8', description: asList(c.description), flavour: c.flavour ?? '',
    tool: TOOL_EFFECTS.includes(c.effect), cooldown: c.cooldown ?? 0, icon: `wayfarer:curio/${id}`,
  }));
  const gear: GearRow[] = entries(curiosYml.gear).map(([id, g]) => {
    const base = String(g.base ?? '');
    return {
      id, name: g.name ?? title(id), colour: g.colour ?? '#e8d9a8', base, flavour: g.flavour ?? '',
      enchants: entries(g.enchants).map(([e, level]) => ({ id: e.replace(/^(excellentenchants|minecraft):/, ''), level: level as number,
        excellent: e.startsWith('excellentenchants:') })),
      icon: base.startsWith('alloy:') ? `wayfarer:${base.slice(6)}` : g.model ? `wayfarer:${g.model}` : base,
    };
  });
  const supplies: SupplyRow[] = entries(curiosYml.supplies).map(([id, s2]) => ({
    id, name: s2.name ?? title(id), colour: s2.colour ?? '#e8d9a8', description: asList(s2.description), flavour: s2.flavour ?? '',
    icon: `wayfarer:supply/${id}`,
  }));
  const collectionDefs = collectionsYml.collections ?? {};
  const collections: CollectionRow[] = entries(collectionDefs).map(([id, c]) => ({
    id, title: c.title ?? title(id), colour: c.colour ?? '#e8d9a8', blurb: c.blurb ?? '', unit: c.unit ?? 'entry|entries',
    milestones: entries(c.milestones).map(([at, rewards]) => ({ at, rewards: asList(rewards) })),
  }));
  const almanac = collectionDefs.almanac ?? {};
  const fish: FishRow[] = entries(almanac.species).map(([id, f]) => {
    const [min, max] = String(f.weight ?? '1-1').split('-').map(Number);
    return {
      id, name: f.name ?? title(id), rarity: f.rarity ?? 'common', water: f.water ?? 'any', temps: asList(f.temp), time: f.time ?? 'any',
      weather: f.weather ?? 'any', place: f.place ?? '', minGrams: min, maxGrams: max, flavour: f.flavour ?? '', hint: f.hint ?? null,
      icon: `wayfarer:fish/${id}`,
    };
  });
  const treasureDef = collectionDefs.treasures ?? {};
  const treasures: TreasureRow[] = entries(treasureDef.list).map(([id, t]) => ({
    id, name: t.name ?? title(id), colour: t.colour ?? '#d9a55b', kind: t.kind, flavour: t.flavour ?? '', brush: asList(t.brush),
    chests: Object.fromEntries(entries(t.chests).map(([k, v]) => [k.replace(/^minecraft:/, ''), v as number])), chart: t.chart ?? null,
    icon: `wayfarer:treasure/${id}`,
  }));
  const brushChance = treasureDef['brush-chance'] ?? 0.12;
  const treasureRules: TreasureRules | null = treasureDef.list ? {
    brushChance, ceiling: Math.ceil(1 / brushChance - 1e-9),
    sites: Object.fromEntries(entries(treasureDef.sites).map(([k, v]) => [k, asList(v)])),
  } : null;
  const feats: FeatRow[] = entries(objectivesYml.feats).map(([id, f]) => ({
    id, name: f.name ?? title(id), flavour: f.flavour ?? '', counts: asList(f.counts), distinct: !!f.distinct,
    at: f.at === 'all' ? 'all' : f.at ?? 0, hidden: !!f.hidden, rewards: asList(f.rewards),
  }));
  const cosmetics: CosmeticRow[] = entries(objectivesYml.cosmetics).map(([id, c]) => ({
    id, kind: c.kind, name: c.name ?? title(id), colour: c.colour ?? '#e8d9a8',
    item: c.item, ability: c.ability, entity: c.entity, model: c.model,
  }));
  const hatAbilities: HatAbilityRow[] = entries(curiosYml['hat-abilities']).map(([id, a]) => ({
    id, name: a.name ?? title(id), colour: a.colour ?? '#e8d9a8', description: asList(a.description),
  }));
  const tableCosmetics = [...new Set(JSON.stringify(objectivesYml.tables ?? {}).match(/cosmetic:[a-z0-9_]+/g) ?? [])].map((x) => x.slice(9));
  const sched = objectivesYml.schedule ?? {};
  const dl = objectivesYml.dailies ?? {};
  const objectives: ObjectiveRules | null = objectivesYml['daily-pool'] ? {
    dailies: dl.count ?? 3, dailyReset: sched['daily-reset'] ?? '06:00', zone: sched.zone ?? 'Europe/London',
    weeklyDay: title(String(sched['weekly-reset'] ?? 'MONDAY').toLowerCase()), eventStart: String(sched['event-start'] ?? 'FRIDAY 18:00'),
    eventEnd: String(sched['event-end'] ?? 'MONDAY 00:00'), freeRerolls: dl.rerolls?.free ?? 1, rerollPoints: dl.rerolls?.['xp-points'] ?? 27,
    rerollStepPoints: dl.rerolls?.['xp-step-points'] ?? 16, streakEvery: dl.streak?.every ?? 7, streakTokens: dl.streak?.tokens ?? 50,
    streakReward: dl.streak?.reward ?? null, duplicateTokens: objectivesYml['duplicate-tokens'] ?? 30,
    stages: Object.keys(objectivesYml.stages ?? {}), dailyCount: Object.keys(objectivesYml['daily-pool'] ?? {}).length,
    weeklyCount: Object.keys(objectivesYml['weekly-pool'] ?? {}).length, goalCount: Object.keys(objectivesYml['goal-pool'] ?? {}).length,
    tables: Object.keys(objectivesYml.tables ?? {}),
  } : null;
  const events: EventRow[] = entries(objectivesYml.events?.list).map(([id, e]) => ({
    id, name: e.name ?? title(id), blurb: e.blurb ?? '', goal: String(e.goal?.text ?? '').replace('{n}', num(e.goal?.target ?? 0)),
    target: e.goal?.target ?? 0,
  }));
  const shopYml = objectivesYml.shop;
  const shop: ShopRules | null = shopYml ? {
    cosmetics: shopYml.cosmetics ?? {}, supplies: shopYml.supplies ?? {}, extraReroll: shopYml['extra-reroll'] ?? 40,
    templateCopy: shopYml['template-copy'] ?? 120, wayfinder: shopYml.wayfinder ?? 60, kindling: shopYml['beacon-kindling'] ?? 80,
    enchantPerLevel: shopYml['enchant-per-level'] ?? 60,
  } : null;
  const wf = explorationYml.wayfinder;
  const exploration: ExplorationRules | null = wf ? {
    range: wf.range ?? 3200, cooldown: wf.cooldown ?? 300, structurePoints: wf['structure-points'] ?? 55, biomePoints: wf['biome-points'] ?? 27,
    chartPoints: wf['chart-points'] ?? 112, unfoundMultiplier: wf['unfound-multiplier'] ?? 2, rare: wf.rare ?? {}, structures: wf.structures ?? {},
    kindlingSeconds: explorationYml['beacon-kindling']?.seconds ?? 90,
  } : null;

  const reshuffle = enchantingYml.reshuffle;
  const enchanting: EnchantingRules | null = reshuffle ? {
    reshuffle: reshuffle.enabled ?? true, levels: reshuffle.levels ?? 1, trigger: String(reshuffle.trigger ?? 'lapis_lazuli'),
  } : null;

  const forageDefaults = { chance: foodsYml.forage?.chance ?? 0.04, pity: foodsYml.forage?.pity ?? 40 };
  const forage: ForageRow[] = entries(foodsYml.forage?.ingredients).map(([id, f]) => ({
    id, name: f.name ?? title(id), colour: f.colour ?? '#e8d9a8', description: asList(f.description), flavour: f.flavour ?? '',
    biomes: asList(f.biomes), plants: asList(f.plants), chance: f.chance ?? forageDefaults.chance, pity: f.pity ?? forageDefaults.pity,
    icon: `wayfarer:forage/${id}`,
  }));
  const dishes: DishRow[] = entries(foodsYml.dishes?.items).map(([id, d]) => ({
    id, name: d.name ?? title(id), colour: d.colour ?? '#e8d9a8', family: d.family ?? 'other', base: d.base ?? '',
    nutrition: d.nutrition ?? 0, saturation: d.saturation ?? 0,
    effects: (Array.isArray(d.effects) ? d.effects : []).map((e: any) => ({ type: String(e.type), amplifier: e.amplifier ?? 0, seconds: Math.round((e.minutes ?? 0) * 60) })),
    rare: !!d.rare, ingredients: asList(d.ingredients), flavour: d.flavour ?? '', hint: d.hint ?? '', icon: `wayfarer:dish/${id}`,
  }));
  const lf = comfortsYml['leather-from-flesh'];
  const kd = comfortsYml['keep-on-death'];
  const comforts: ComfortsRules | null = lf || kd ? {
    leather: lf?.enabled ?? true, flesh: lf?.flesh ?? 4, leatherOut: lf?.leather ?? 1,
    keep: kd?.enabled ?? true, hotbar: kd?.hotbar ?? true, armour: kd?.armour ?? true, offhand: kd?.offhand ?? true,
  } : null;

  return {
    curios, gear, supplies, collections, fish, fishChance: almanac.chance ?? 0.5, treasures, treasureRules, feats, cosmetics,
    hatAbilities, tableCosmetics, trackerMax: objectivesYml.tracker?.max ?? 5,
    objectives, events, eventRotation: asList(objectivesYml.events?.rotation), shop, exploration, enchanting, comforts, forage, dishes,
    alloys, alloyRules, marks, books,
    families, allMobs: banners.counting?.['all-mobs'] ?? null,
    banners: bannerRows, heads, relics,
    wayfarerSet: set ? { families: set.families, reduction: set['damage-reduction'], hearts: set.hearts } : null,
    bosses: bossRows, bossRules, pity,
    excludedSpawns: progression.counting?.['exclude-spawn-reasons'] ?? [],
    announce: Object.fromEntries(ANNOUNCE_CATEGORIES.map((c) => [c, progression.announce?.[c] !== false])),
  };
}
