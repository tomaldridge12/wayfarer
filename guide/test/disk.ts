// Tests read the real repo and fixture folders straight from disk.
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { GuideFiles } from '../src/data/files';
import type { GuidePaths } from '../src/data/guide';

export const diskFiles: GuideFiles = {
  text: (p) => (existsSync(p) ? readFileSync(p, 'utf8') : undefined),
  has: (p) => existsSync(p),
};

const guideRoot = process.cwd();
export const repoDiskPaths: GuidePaths = {
  guideData: join(guideRoot, 'src/data'),
  chapters: join(guideRoot, 'src/chapters'),
  vanillaTextures: join(guideRoot, 'public/textures/minecraft'),
  packTextures: join(guideRoot, '../resourcepack/assets/wayfarer/textures'),
  config: join(guideRoot, '../plugin/src/main/resources'),
};
