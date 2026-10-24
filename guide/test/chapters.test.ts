import { describe, expect, it } from 'vitest';
import { fixture, loadFixture } from './fixture';

describe('chapters', () => {
  it('come out in timeline order with their status', () => {
    const g = loadFixture(fixture({
      'data/chapters.yml': `chapters:
  - { slug: bosses, title: Bosses, stage: nether, status: live }
  - { slug: alloys, title: Alloys, stage: iron, status: planned }
  - { slug: welcome, title: Welcome, stage: none, status: live }`,
      'chapters/bosses.mdx': '', 'chapters/alloys.mdx': '', 'chapters/welcome.mdx': '',
    }));
    expect(g.chapters.map((c) => [c.slug, c.status])).toEqual([
      ['welcome', 'live'], ['alloys', 'planned'], ['bosses', 'live'],
    ]);
  });

  it('fail the build when a listed chapter has no file', () => {
    expect(() => loadFixture(fixture({
      'data/chapters.yml': 'chapters:\n  - { slug: relics, title: Relics, stage: diamond, status: live }',
    }))).toThrow(/chapter 'relics' has no file relics.mdx/);
  });

  it('reject a status other than live or planned', () => {
    expect(() => loadFixture(fixture({
      'data/chapters.yml': 'chapters:\n  - { slug: x, title: X, stage: iron, status: soon }',
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
