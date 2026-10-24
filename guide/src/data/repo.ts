// The real Guide, loaded from the snapshot scripts/prepare.mjs writes before dev and build.
import { loadGuide, type GuidePaths } from './guide';
import { snapshotFiles, type Snapshot } from './files';
import snapshot from '../generated/snapshot.json';

/** Where things sit in the snapshot (and, relative to the Guide's folder, on disk). */
export const SNAPSHOT_PATHS: GuidePaths = {
  guideData: 'data',
  chapters: 'chapters',
  vanillaTextures: 'textures/minecraft',
  packTextures: 'textures/wayfarer',
  config: 'config',
};

let cached: ReturnType<typeof loadGuide> | undefined;
export const guide = () => (cached ??= loadGuide(SNAPSHOT_PATHS, snapshotFiles(snapshot as Snapshot)));
