// Builds a throwaway repo-shaped folder for Guide data tests.
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { loadGuide, type GuidePaths } from '../src/data/guide';
import { diskFiles } from './disk';

export const STAGES = `
stages:
  - { id: iron, name: Iron, marker: Acquire Hardware, item: iron_ingot, blurb: b }
  - { id: diamond, name: Diamond, marker: Diamonds!, item: diamond, blurb: b }
  - { id: nether, name: Nether, marker: We Need to Go Deeper, item: netherrack, blurb: b }
  - { id: post_end, name: Post-End, marker: Free the End, item: elytra, blurb: b }
rewards: {}
`;

export function fixture(files: Record<string, string>): GuidePaths {
  const root = mkdtempSync(join(tmpdir(), 'guide-'));
  const all: Record<string, string> = {
    'data/stages.yml': STAGES,
    'data/chapters.yml': 'chapters: []',
    'textures/minecraft/item/iron_ingot.png': '',
    'textures/minecraft/item/diamond.png': '',
    'textures/minecraft/block/netherrack.png': '',
    'textures/minecraft/item/elytra.png': '',
    ...files,
  };
  for (const [path, body] of Object.entries(all)) {
    mkdirSync(dirname(join(root, path)), { recursive: true });
    writeFileSync(join(root, path), body);
  }
  mkdirSync(join(root, 'chapters'), { recursive: true });
  mkdirSync(join(root, 'config'), { recursive: true });
  return {
    guideData: join(root, 'data'),
    chapters: join(root, 'chapters'),
    vanillaTextures: join(root, 'textures/minecraft'),
    packTextures: join(root, 'textures/wayfarer'),
    config: join(root, 'config'),
  };
}

export const loadFixture = (paths: GuidePaths) => loadGuide(paths, diskFiles);
