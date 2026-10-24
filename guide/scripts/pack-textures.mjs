// Serves Wayfarer's resource pack textures with the Guide: copies them into public/textures/wayfarer
// (gitignored) before dev and build, so the pack's source stays the only copy in the repo.
import { cpSync, rmSync, existsSync } from 'node:fs';

const from = '../resourcepack/assets/wayfarer/textures';
const to = 'public/textures/wayfarer';
rmSync(to, { recursive: true, force: true });
if (existsSync(from)) cpSync(from, to, { recursive: true, filter: (f) => !f.endsWith('.mcmeta') });
