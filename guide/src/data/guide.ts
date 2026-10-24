// Guide data: turns the repo's files (plugin config, stage map, chapter list, textures) into what pages render.
// It sees files only through GuideFiles, so pages read a build-time snapshot (no disk at render time, which
// Cloudflare's runtime lacks) and tests read fixture folders.
import { parse } from 'yaml';
import { readConfig, type Config } from './config';
import type { GuideFiles } from './files';
import { FACES } from './faces';

export type StageId = 'iron' | 'diamond' | 'nether' | 'post_end';
export type Status = 'live' | 'planned';

export interface Stage { id: StageId; name: string; marker: string; item: string; blurb: string }
export interface Chapter { slug: string; title: string; stage: StageId | 'none'; status: Status }

export interface GuidePaths {
  /** Folder holding stages.yml, chapters.yml and chapters/. */
  guideData: string;
  /** Folder holding chapter files (<slug>.mdx). */
  chapters: string;
  /** Vanilla textures (item/, block/, entity/ ...), served at /textures/minecraft/. */
  vanillaTextures: string;
  /** Wayfarer's resource pack textures, served at /textures/wayfarer/. */
  packTextures: string;
  /** The plugin's default config folder (banners.yml, bosses.yml, progression.yml, alloys.yml, marks.yml). */
  config: string;
}

export interface Guide {
  stages: Stage[];
  chapters: Chapter[];
  config: Config;
  /** The stage a reward or boss id (`banner:zombie`, `boss:void_stalker`) sits on. */
  stageOf(reward: string): StageId;
  /** The art for an item id (`diamond`, `wayfarer:steel_ingot`); null when there is none. */
  icon(item: string): string | null;
}

export class GuideError extends Error {}

const STAGE_IDS: StageId[] = ['iron', 'diamond', 'nether', 'post_end'];

const join = (...parts: string[]) => parts.join('/');

export function loadGuide(paths: GuidePaths, files: GuideFiles): Guide {
  const readYaml = (file: string): any => {
    const text = files.text(file);
    if (text === undefined) throw new GuideError(`missing file ${file}`);
    return parse(text) ?? {};
  };
  const stagesFile = join(paths.guideData, 'stages.yml');
  const chaptersFile = join(paths.guideData, 'chapters.yml');
  const stageDoc = readYaml(stagesFile);
  const stages: Stage[] = (stageDoc.stages ?? []).map((s: any) => {
    if (!STAGE_IDS.includes(s.id)) throw new GuideError(`${stagesFile}: unknown stage '${s.id}'`);
    return { id: s.id, name: s.name, marker: s.marker, item: s.item, blurb: s.blurb };
  });

  const chapters: Chapter[] = (readYaml(chaptersFile).chapters ?? []).map((c: any) => {
    if (c.stage !== 'none' && !STAGE_IDS.includes(c.stage))
      throw new GuideError(`${chaptersFile}: chapter '${c.slug}' has unknown stage '${c.stage}'`);
    if (c.status !== 'live' && c.status !== 'planned')
      throw new GuideError(`${chaptersFile}: chapter '${c.slug}' has status '${c.status}' (live or planned)`);
    if (!files.has(join(paths.chapters, `${c.slug}.mdx`)))
      throw new GuideError(`${chaptersFile}: chapter '${c.slug}' has no file ${c.slug}.mdx`);
    return { slug: c.slug, title: c.title, stage: c.stage, status: c.status };
  });

  // Reading order follows the timeline: unstaged chapters first, then by stage, list order within a stage.
  const rank = (c: Chapter) => (c.stage === 'none' ? -1 : STAGE_IDS.indexOf(c.stage));
  chapters.sort((a, b) => rank(a) - rank(b));

  const icon = (item: string) => iconFor(paths, files, item);
  for (const stage of stages)
    if (!icon(stage.item)) throw new GuideError(`${stagesFile}: stage '${stage.id}' item '${stage.item}' has no texture`);

  const config = readConfig(paths.config, files);
  const placed: Record<string, StageId> = stageDoc.rewards ?? {};
  const known = new Set([...config.banners, ...config.heads, ...config.relics, ...config.bosses].map((r) => r.reward));
  for (const reward of known)
    if (!placed[reward]) throw new GuideError(`${stagesFile}: '${reward}' is not placed on a stage (add it under rewards)`);
  for (const [reward, stage] of Object.entries(placed)) {
    if (!known.has(reward)) throw new GuideError(`${stagesFile}: '${reward}' is not in the plugin config any more (remove it)`);
    if (!STAGE_IDS.includes(stage)) throw new GuideError(`${stagesFile}: '${reward}' has unknown stage '${stage}'`);
  }

  // Items shown by a live chapter must have art; planned chapters may show an empty slot.
  const liveItems = [
    ...config.relics.map((r) => r.item), ...config.relics.map((r) => r.art), ...config.pity.map((p) => p.item),
    ...config.alloys.flatMap((a) => [`wayfarer:${a.id}_ingot`, `wayfarer:${a.id}_template`, ...a.recipe.map((r) => r.item)]),
    ...config.marks.map((m) => `wayfarer:${m.id}_mark_template`),
  ];
  for (const item of liveItems)
    if (!icon(item) && !FACES[item]) throw new GuideError(`item '${item}' has no texture (vanilla or wayfarer:)`);

  return { stages, chapters, config, icon, stageOf: (reward) => placed[reward] };
}

// Items drawn from animation frames: one fixed frame stands for the item.
const ANIMATED: Record<string, string> = { compass: 'compass_16', recovery_compass: 'recovery_compass_16', clock: 'clock_00' };

/** Vanilla items look in item/ then block/; `wayfarer:` items look in the pack's item/. */
export function iconFor(paths: GuidePaths, files: GuideFiles, item: string): string | null {
  const [ns, id] = item.includes(':') ? item.split(':', 2) : ['minecraft', item];
  const name = ANIMATED[id] ?? id;
  const root = ns === 'wayfarer' ? paths.packTextures : ns === 'minecraft' ? paths.vanillaTextures : null;
  if (!root) return null;
  for (const folder of ['item', 'block']) {
    if (files.has(join(root, folder, `${name}.png`))) return `/textures/${ns}/${folder}/${name}.png`;
  }
  return null;
}
