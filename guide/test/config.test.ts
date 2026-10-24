import { describe, expect, it } from 'vitest';
import { loadGuide } from '../src/data/guide';
import { pityKills } from '../src/data/config';
import { STAGES, fixture, loadFixture } from './fixture';
import { diskFiles, repoDiskPaths } from './disk';

const BANNERS = `
families:
  zombie: [zombie, husk, drowned]
banners:
  zombie: { name: Zombie Banner, colour: green, patterns: ["creeper:lime"], family: zombie, kills: 100, radius: 16 }
heads:
  zombie: { name: Zombie Trophy, family: zombie, kills: 250 }
  spider: { name: Spider Trophy, family: spider, kills: 250 }
relics:
  zombie_weapon: { name: Zombie Cleaver, family: zombie, kills: 400, tier: 1, slot: weapon, item: iron_axe, perks: [{type: hunger_immunity}] }
  zombie_armour_3: { name: Rotguard, family: zombie, drop-only: true, tier: 3, slot: chest, item: iron_axe }
`;
const PLACED = `
  banner:zombie: iron
  head:zombie: diamond
  head:spider: diamond
  relic:zombie_weapon: diamond
  relic:zombie_armour_3: nether`;
const withRewards = (extra = '') => STAGES.replace('rewards: {}', `rewards:${PLACED}${extra}`);
const base = (stages = withRewards()) => ({
  'config/banners.yml': BANNERS, 'data/stages.yml': stages, 'textures/minecraft/item/iron_axe.png': '',
});

describe('ladders from config', () => {
  it('give families, banners, heads and relics with thresholds and stages', () => {
    const g = loadFixture(fixture(base()));
    expect(g.config.families).toEqual([{ id: 'zombie', mobs: ['zombie', 'husk', 'drowned'] }]);
    const banner = g.config.banners[0];
    expect([banner.name, banner.kills, banner.bonus, banner.reduction, banner.radius]).toEqual(['Zombie Banner', 100, 10, 10, 16]);
    expect(g.stageOf(banner.reward)).toBe('iron');
    expect(g.config.heads.map((h) => [h.item, h.skin])).toEqual([['zombie_head', null], ['player_head', 'MHF_Spider']]);
    const [weapon, ascended] = g.config.relics;
    expect([weapon.edge, weapon.perks]).toEqual([30, ['Immune to Hunger']]);
    expect([ascended.edge, ascended.dropOnly, ascended.kills]).toEqual([35, true, null]);
  });

  it('fail when a reward is not on the stage map', () => {
    expect(() => loadFixture(fixture(base(STAGES.replace('rewards: {}', 'rewards:\n  banner:zombie: iron')))))
      .toThrow(/'head:zombie' is not placed on a stage/);
  });

  it('fail when the stage map names a reward no longer in config', () => {
    expect(() => loadFixture(fixture(base(withRewards('\n  banner:husk: iron')))))
      .toThrow(/'banner:husk' is not in the plugin config any more/);
  });

  it('fail when a relic item has no texture', () => {
    const files = base();
    delete (files as any)['textures/minecraft/item/iron_axe.png'];
    expect(() => loadFixture(fixture(files))).toThrow(/item 'iron_axe' has no texture/);
  });

  it('yield empty sections for config that does not exist yet', () => {
    const g = loadFixture(fixture({}));
    expect([g.config.banners, g.config.bosses, g.config.pity, g.config.bossRules]).toEqual([[], [], [], null]);
  });
});

describe('alloys from config', () => {
  const ALLOYS = `
crucible: {heat: {campfire: low}, max-ingots: 8}
anvil: {endless: true, cap: 30}
alloys:
  steel:
    name: Steel
    heat: low
    recipe: {iron_ingot: 4, coal_block: 1}
    upgrades: iron
    tools: {durability: 1000, speed: 5.5}
    armour: {points: [2, 6, 5, 2], toughness: 1}
    repair: {items: [ingot], share: 0.125}
    template: {boss: bone_marshal, duplicate-block: tuff_bricks}
  electrum:
    name: Electrum
    heat: high
    recipe: {gold_ingot: 4}
    upgrades: diamond
    bonuses: [{attribute: oxygen_bonus, amount: 1, to: armour}]
`;
  const RELICS = `
families:
  zombie: [zombie]
relics:
  cleaver: { name: Cleaver, family: zombie, kills: 400, tier: 1, item: iron_axe, alloy: steel }
  bow: { name: Bow, family: zombie, kills: 500, tier: 1, item: bow }
`;
  const files = () => ({
    'config/alloys.yml': ALLOYS, 'config/banners.yml': RELICS,
    'data/stages.yml': STAGES.replace('rewards: {}', 'rewards:\n  relic:cleaver: iron\n  relic:bow: iron'),
    'textures/minecraft/item/iron_axe.png': '', 'textures/minecraft/item/bow.png': '',
    'textures/minecraft/item/coal_block.png': '', 'textures/minecraft/item/gold_ingot.png': '',
    'textures/wayfarer/item/steel_axe.png': '', 'textures/wayfarer/item/steel_ingot.png': '',
    'textures/wayfarer/item/steel_template.png': '', 'textures/wayfarer/item/electrum_ingot.png': '',
    'textures/wayfarer/item/electrum_template.png': '',
  });

  it('give recipes, heats, template sources and identities', () => {
    const g = loadFixture(fixture(files()));
    const [steel, electrum] = g.config.alloys;
    expect([steel.heat, steel.recipe, steel.upgrades]).toEqual(['low', [{ item: 'iron_ingot', count: 4 }, { item: 'coal_block', count: 1 }], 'iron']);
    expect(steel.template).toEqual({ boss: 'bone_marshal', duplicateBlock: 'tuff_bricks' });
    expect(steel.identity).toContain('Tools: 1000 durability, mining speed 5.5');
    expect(steel.identity).toContain('Repaired with Steel ingots, 12.5% each');
    expect(electrum.identity).toContain('+1 breath per armour piece');
    expect(g.config.alloyRules).toEqual({ heatBlocks: { campfire: 'low' }, maxIngots: 8, endless: true, cap: 30 });
  });

  it('draw relics made of an alloy with the alloy piece', () => {
    const g = loadFixture(fixture(files()));
    expect(g.config.relics.map((r) => [r.alloy, r.art])).toEqual([['steel', 'wayfarer:steel_axe'], [null, 'bow']]);
  });

  it('fail when an alloy ingot has no art', () => {
    const f = files();
    delete (f as any)['textures/wayfarer/item/electrum_ingot.png'];
    expect(() => loadFixture(fixture(f))).toThrow(/wayfarer:electrum_ingot/);
  });
});

describe('marks from config', () => {
  it('give each mark its ladder, tool, material and what each rank does', () => {
    const g = loadFixture(fixture({
      'config/marks.yml': `
marks:
  dune: {name: Dune, counts: [mine:sand], ranks-at: [500, 2500], tool: shovel, material: [sand], ranks: [{area: 3x1}, {area: 3x3, gravel: true}]}
  angler: {name: Angler, unit: "catch|catches", counts: ["catch:*"], ranks-at: [100], tool: fishing_rod, material: string, ranks: [{bite-wait: 0.7, treasure: 0.05}]}
books:
  veinminer: {name: Veinminer, enchant: excellentenchants:veinminer, counts: ["mine:#minecraft:coal_ores"], levels-at: [250]}
`,
      'textures/wayfarer/item/dune_mark_template.png': '', 'textures/wayfarer/item/angler_mark_template.png': '',
    }));
    const [dune, angler] = g.config.marks;
    expect([dune.tool, dune.materials, dune.ranksAt]).toEqual(['shovel', ['sand'], [500, 2500]]);
    expect(dune.ranks).toEqual(['Digs 3x1 of sand at once', 'Digs 3x3 of sand and gravel at once']);
    expect(angler.ranks).toEqual(['Bites come 30% sooner, 5% chance of treasure']);
    expect(angler.materials).toEqual(['string']);
    expect(g.config.books).toEqual([{ id: 'veinminer', name: 'Veinminer', enchant: 'excellentenchants:veinminer', unit: 'block',
      counts: ['mine:#minecraft:coal_ores'], levelsAt: [250] }]);
  });

  it('fail when a mark template has no art', () => {
    expect(() => loadFixture(fixture({ 'config/marks.yml': 'marks:\n  reaper: {name: Reaper, ranks-at: [1], tool: hoe, material: [hay_block], ranks: [{area: 3x1}]}\n' })))
      .toThrow(/wayfarer:reaper_mark_template/);
  });
});

describe('pity drops', () => {
  it('guarantee the drop at ceil(1 / chance) kills', () => {
    expect(pityKills(0.025)).toBe(40);
    expect(pityKills(0.085)).toBe(12);
    expect(pityKills(0.01)).toBe(100);
  });
});

describe('the real repo', () => {
  it('loads: every reward placed, every item drawn, every chapter written', () => {
    const g = loadGuide(repoDiskPaths, diskFiles);
    expect(g.config.banners.length).toBeGreaterThan(0);
    expect(g.config.bosses.length).toBeGreaterThan(0);
    expect(g.chapters[0].slug).toBe('welcome');
  });
});
