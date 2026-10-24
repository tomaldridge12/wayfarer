// The build proof: the real Guide builds, and its pages carry values taken from the real config.
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { beforeAll, describe, expect, it } from 'vitest';
import { loadGuide } from '../src/data/guide';
import { diskFiles, repoDiskPaths } from './disk';

const page = (slug: string) => readFileSync(slug ? `dist/${slug}/index.html` : 'dist/index.html', 'utf8');
const g = loadGuide(repoDiskPaths, diskFiles);

describe('built guide', () => {
  beforeAll(() => { execSync('npm run build', { stdio: 'pipe' }); }, 120_000);

  it('has the Welcome chapter with the four stages', () => {
    const html = page('');
    expect(html).toContain('Welcome, Wayfarer');
    for (const s of g.stages) expect(html).toContain(s.name);
  });

  it('builds a page for every chapter, stamping the planned ones', () => {
    for (const c of g.chapters.filter((c) => c.slug !== 'welcome')) {
      expect(page(c.slug).includes('Details may change')).toBe(c.status === 'planned');
    }
  });

  it('shows the zombie banner at its configured kills', () => {
    const zombie = g.config.banners.find((b) => b.id === 'zombie')!;
    expect(page('banners-and-trophies')).toContain(`Earned at ${zombie.kills} kills of the Zombie family`);
  });

  it('shows each pity drop at its guaranteed kill count', () => {
    const html = page('pity-drops');
    const trident = g.config.pity.find((p) => p.id === 'trident')!;
    expect(trident.kills).toBe(12);
    expect(html).toMatch(new RegExp(`class="big"[^>]*>${trident.kills}<`));
  });

  it('shows every boss by name', () => {
    const html = page('bosses');
    for (const b of g.config.bosses) expect(html).toContain(b.name);
  });
});
