import { describe, expect, it } from 'vitest';
import { fixture, loadFixture } from './fixture';

describe('chapters', () => {
  it('come out in group order, list order within a group, with their status', () => {
    const g = loadFixture(fixture({
      'data/chapters.yml': `groups:
  - { id: start, name: Start, item: iron_ingot }
  - { id: gear, name: Gear, item: diamond }
chapters:
  - { slug: bosses, title: Bosses, group: gear, stage: nether, status: live }
  - { slug: alloys, title: Alloys, group: gear, stage: iron, status: planned }
  - { slug: welcome, title: Welcome, group: start, stage: none, status: live }`,
      'chapters/bosses.mdx': '', 'chapters/alloys.mdx': '', 'chapters/welcome.mdx': '',
    }));
    expect(g.chapters.map((c) => [c.slug, c.status])).toEqual([
      ['welcome', 'live'], ['bosses', 'live'], ['alloys', 'planned'],
    ]);
  });

  it('fail the build when a listed chapter has no file', () => {
    expect(() => loadFixture(fixture({
      'data/chapters.yml': 'groups:\n  - { id: g, name: G, item: iron_ingot }\nchapters:\n  - { slug: relics, title: Relics, group: g, stage: diamond, status: live }',
    }))).toThrow(/chapter 'relics' has no file relics.mdx/);
  });

  it('reject a status other than live or planned', () => {
    expect(() => loadFixture(fixture({
      'data/chapters.yml': 'groups:\n  - { id: g, name: G, item: iron_ingot }\nchapters:\n  - { slug: x, title: X, group: g, stage: iron, status: soon }',
      'chapters/x.mdx': '',
    }))).toThrow(/status 'soon'/);
  });
});

describe('icons', () => {
  it('resolve vanilla items from item/ or block/, and wayfarer items from the pack', () => {
    const g = loadFixture(fixture({ 'textures/wayfarer/item/steel_ingot.png': '' }));
    expect(g.icon('diamond')).toBe('/textures/minecraft/item/diamond.png');
    expect(g.icon('netherrack')).toBe('/textures/minecraft/block/netherrack.png');
    expect(g.icon('wayfarer:steel_ingot')).toBe('/textures/wayfarer/item/steel_ingot.png');
    expect(g.icon('wayfarer:adamant_ingot')).toBeNull();
  });

  it('fail the build when a stage item has no texture', () => {
    expect(() => loadFixture(fixture({ 'data/stages.yml': 'stages:\n  - { id: iron, name: Iron, marker: m, item: nope, blurb: b }' })))
      .toThrow(/stage 'iron' item 'nope' has no texture/);
  });
});
