// The real repo's paths, for pages and the real-config test. Builds and tests run from the Guide's folder.
import { join } from 'node:path';
import { loadGuide, type GuidePaths } from './guide';

const guideRoot = process.cwd();
export const repoRoot = join(guideRoot, '..');

export const repoPaths: GuidePaths = {
  guideData: join(guideRoot, 'src/data'),
  chapters: join(guideRoot, 'src/chapters'),
  vanillaTextures: join(guideRoot, 'public/textures/minecraft'),
  packTextures: join(repoRoot, 'resourcepack/assets/wayfarer/textures'),
  config: join(repoRoot, 'plugin/src/main/resources'),
};

let cached: ReturnType<typeof loadGuide> | undefined;
export const guide = () => (cached ??= loadGuide(repoPaths));
