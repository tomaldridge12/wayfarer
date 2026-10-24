# Wayfarer Guide

The players' website: Astro, static, private behind Cloudflare Access. Numbers come from the plugin's default config at build time.

- `npm run dev` / `npm run build` / `npm test` (the proof: Guide data tests plus a full build checked for config values).
- `src/data/chapters.yml` chapter order and `live`/`planned` status; `src/data/stages.yml` places every reward and boss on a stage (the build fails on a missing or stale entry).
- `src/chapters/*.mdx` the prose; components in `src/components/` render the generated sections.
- Vanilla textures: `../scripts/guide-textures.sh` after a Minecraft version bump. Wayfarer's own art is read from `../resourcepack/`.

Cloudflare Pages: root directory `guide`, build command `npm run build`, output `dist`. Build watch paths `guide/**`, `plugin/src/main/resources/**`, `resourcepack/**`. Put Cloudflare Access in front of the production domain and the preview deployments.
