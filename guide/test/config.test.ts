import { describe, expect, it } from 'vitest';
import { loadGuide } from '../src/data/guide';
import { pityKills } from '../src/data/config';
import { STAGES, fixture } from './fixture';
import { repoPaths } from '../src/data/repo';

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
    const g = loadGuide(fixture(base()));
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
    expect(() => loadGuide(fixture(base(STAGES.replace('rewards: {}', 'rewards:\n  banner:zombie: iron')))))
      .toThrow(/'head:zombie' is not placed on a stage/);
  });

  it('fail when the stage map names a reward no longer in config', () => {
    expect(() => loadGuide(fixture(base(withRewards('\n  banner:husk: iron')))))
      .toThrow(/'banner:husk' is not in the plugin config any more/);
  });

  it('fail when a relic item has no texture', () => {
    const files = base();
    delete (files as any)['textures/minecraft/item/iron_axe.png'];
    expect(() => loadGuide(fixture(files))).toThrow(/item 'iron_axe' has no texture/);
  });

  it('yield empty sections for config that does not exist yet', () => {
    const g = loadGuide(fixture({}));
    expect([g.config.banners, g.config.bosses, g.config.pity, g.config.bossRules]).toEqual([[], [], [], null]);
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
    const g = loadGuide(repoPaths);
    expect(g.config.banners.length).toBeGreaterThan(0);
    expect(g.config.bosses.length).toBeGreaterThan(0);
    expect(g.chapters[0].slug).toBe('welcome');
  });
});
