// Small formatting helpers shared by chapter components.
export const title = (id: string) => id.split(/[_:]/).filter(Boolean).map((w) => w[0].toUpperCase() + w.slice(1)).join(' ');
export const plural = (n: number, one: string, many = `${one}s`) => `${n.toLocaleString('en-GB')} ${n === 1 ? one : many}`;
export const num = (n: number) => n.toLocaleString('en-GB');
/** An XP price as the Guide writes it: points, never levels. */
export const xp = (points: number) => `${num(points)} XP`;
export const pct = (x: number) => `${+(x * 100).toFixed(1)}%`;
const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V'];
export const roman = (n: number) => ROMAN[n] ?? String(n);
/** Biome and structure ids as players read them: `#minecraft:is_forest` → forests. */
export function place(id: string): string {
  const bare = id.replace(/^#?(minecraft|c):/, '');
  if (bare.startsWith('has_structure/')) return `near ${title(bare.slice('has_structure/'.length))}s`;
  if (bare.startsWith('is_')) return `any ${title(bare.slice(3)).toLowerCase()} biome`;
  return title(bare);
}
