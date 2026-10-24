# Wayfarer Guide

The players' website: Astro, static, private behind Cloudflare Access. Numbers come from the plugin's default config at build time.

- `npm run dev` / `npm run build` / `npm test` (the proof: Guide data tests plus a full build checked for config values).
- `src/data/chapters.yml` chapter order and `live`/`planned` status; `src/data/stages.yml` places every reward and boss on a stage (the build fails on a missing or stale entry).
- `src/chapters/*.mdx` the prose; components in `src/components/` render the generated sections.
- Vanilla textures: `../scripts/guide-textures.sh` after a Minecraft version bump. Wayfarer's own art is read from `../resourcepack/`. `scripts/prepare.mjs` (run before dev, build and test) snapshots the files Guide data reads, so pages never read the disk while rendering.

Cloudflare: root directory `guide`; `wrangler.jsonc` builds with `npm run build` and serves `dist` as static files (its `name` must match the Worker). Build watch paths `guide/**`, `plugin/src/main/resources/**`, `resourcepack/**`. Put Cloudflare Access in front of the production domain and the preview deployments.
