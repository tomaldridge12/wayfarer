// Runs in Node before dev and build. Copies Wayfarer's pack textures into public/textures/wayfarer (gitignored)
// and snapshots every file Guide data reads into src/generated/snapshot.json, so pages never touch the disk
// while rendering (Cloudflare can render them in its own runtime, which has no repo on disk).
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const pack = '../resourcepack/assets/wayfarer/textures';
rmSync('public/textures/wayfarer', { recursive: true, force: true });
if (existsSync(pack)) cpSync(pack, 'public/textures/wayfarer', { recursive: true, filter: (f) => !f.endsWith('.mcmeta') });

const walk = (dir) => (existsSync(dir) ? readdirSync(dir).flatMap((f) => {
  const p = join(dir, f);
  return statSync(p).isDirectory() ? walk(p) : [p];
}) : []);

// Snapshot key prefix -> folder on disk (matches SNAPSHOT_PATHS in src/data/repo.ts).
const texts = {};
const files = [];
const read = (prefix, dir, pattern) => {
  for (const f of walk(dir)) if (pattern.test(f)) texts[`${prefix}/${relative(dir, f)}`] = readFileSync(f, 'utf8');
};
const list = (prefix, dir) => { for (const f of walk(dir)) files.push(`${prefix}/${relative(dir, f)}`); };

read('data', 'src/data', /\.yml$/);
read('config', '../plugin/src/main/resources', /\.yml$/);
list('chapters', 'src/chapters');
list('textures/minecraft', 'public/textures/minecraft');
list('textures/wayfarer', 'public/textures/wayfarer');

mkdirSync('src/generated', { recursive: true });
writeFileSync('src/generated/snapshot.json', JSON.stringify({ texts, files }));
console.log(`Guide snapshot: ${Object.keys(texts).length} texts, ${files.length} files`);
