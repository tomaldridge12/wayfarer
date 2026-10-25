// Names, colours and art for reward keys (curio:moss_heart, supply:seed_bundle*2, enchant:excellentenchants:haste_2),
// and the where-and-when words for fish and treasures, mirroring the plugin's FishRules.where and TreasureRules.where.
import type { Config, FishRow, TreasureRow } from './config';
import { title } from './text';

export interface RewardLook { name: string; colour: string; icon: string }

const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];

export function rewardLook(config: Config, reward: string): RewardLook {
  const star = reward.match(/^(.*)\*(\d+)$/);
  const key = star ? star[1] : reward;
  const times = star ? `${star[2]} × ` : '';
  const [type, ...rest] = key.split(':');
  const id = rest.join(':');
  switch (type) {
    case 'curio': {
      const c = config.curios.find((x) => x.id === id);
      return { name: c?.name ?? title(id), colour: c?.colour ?? '#e8d9a8', icon: `wayfarer:curio/${id}` };
    }
    case 'gear': {
      const g = config.gear.find((x) => x.id === id);
      return { name: g?.name ?? title(id), colour: g?.colour ?? '#e8d9a8', icon: g?.icon ?? 'iron_pickaxe' };
    }
    case 'supply': {
      const s = config.supplies.find((x) => x.id === id);
      return { name: times + (s?.name ?? title(id)), colour: s?.colour ?? '#e8d9a8', icon: `wayfarer:supply/${id}` };
    }
    case 'enchant': {
      const m = id.match(/^(?:[a-z]+:)?(.*)_(\d+)$/);
      const level = m ? Number(m[2]) : 1;
      return { name: `${title(m ? m[1] : id)} ${ROMAN[level] ?? level} Book`, colour: id.startsWith('excellentenchants:') ? '#279cf5' : '#c9a0ff',
        icon: 'enchanted_book' };
    }
    case 'cosmetic': {
      const c = config.cosmetics.find((x) => x.id === id);
      const label = c?.kind === 'title' ? 'Title' : c?.kind === 'hat' ? 'Hat' : c?.kind === 'pet' ? 'Pet' : null;
      return { name: c ? (label ? `${label}: ${c.name}` : c.name) : title(id), colour: c?.kind === 'title' ? c.colour : '#c9b8e8',
        icon: c?.kind === 'title' ? 'name_tag' : c?.kind === 'hat' ? 'leather_helmet' : c?.kind === 'pet' ? 'egg' : 'blaze_powder' };
    }
    case 'fish':
      return { name: config.fish.find((x) => x.id === id)?.name ?? title(id), colour: '#d8d2c4', icon: `wayfarer:fish/${id}` };
    case 'treasure': {
      const t = config.treasures.find((x) => x.id === id);
      return { name: t?.name ?? title(id), colour: t?.colour ?? '#d9a55b', icon: `wayfarer:treasure/${id}` };
    }
    default:
      return { name: title(id || key), colour: '#e8d9a8', icon: 'paper' };
  }
}

export const RARITY_COLOURS: Record<string, string> = { common: '#d8d2c4', uncommon: '#7fd46b', rare: '#5ba8f2', legendary: '#f2b84b' };
const TEMP_ORDER = ['cold', 'cool', 'temperate', 'warm', 'hot'];
const cap = (s: string) => (s ? s[0].toUpperCase() + s.slice(1) : s);

export function fishWhere(f: FishRow): string {
  let out: string;
  if (f.place) {
    out = ({ deep_dark: 'Any water in the deep dark', pale_garden: 'Water in the pale garden', lush_caves: 'The pools of lush caves',
      deep_ocean: 'Far out over the deep ocean', mangrove: 'Among mangrove roots' } as Record<string, string>)[f.place] ?? `${title(f.place)} water`;
  } else {
    const temps = TEMP_ORDER.filter((t) => f.temps.includes(t));
    const climate = temps.length ? `${temps.join(' or ')} ` : '';
    out = f.water === 'fresh' ? cap(`${climate}fresh water`) : f.water === 'salt' ? cap(`${climate}seas`)
      : f.water === 'brackish' ? cap(`${climate}brackish water, where rivers meet the sea`) : cap(climate ? `${climate}water` : 'Any water');
  }
  if (f.time === 'night') out += ', at night';
  else if (f.time === 'day') out += ', by day';
  if (f.weather === 'rain') out += ', in the rain';
  else if (f.weather === 'thunder') out += ', in a thunderstorm';
  return f.hint ?? out;
}

export function weight(grams: number): string {
  if (grams < 1000) return `${grams} g`;
  const kg = grams / 1000;
  return kg >= 100 ? `${Math.round(kg)} kg` : `${kg.toFixed(kg >= 10 ? 0 : 1)} kg`;
}

const SITES: Record<string, string> = { trail_ruins: 'trail ruins', desert_well: 'desert wells', desert_pyramid: 'desert pyramids',
  ocean_ruin_warm: 'warm ocean ruins', ocean_ruin_cold: 'cold ocean ruins' };
const CHESTS: Record<string, string> = { shipwreck_map: 'shipwreck map', simple_dungeon: 'dungeon', village_desert_house: 'desert village',
  village_cartographer: 'cartographer', corridor: 'trial chamber', bastion_treasure: 'bastion treasure', bastion_other: 'bastion',
  bastion_bridge: 'bastion', end_city_treasure: 'End city' };
const list = (items: string[]) => {
  const d = [...new Set(items)];
  return d.length <= 1 ? d.join('') : `${d.slice(0, -1).join(', ')} and ${d[d.length - 1]}`;
};

export function treasureWhere(t: TreasureRow): string {
  const parts: string[] = [];
  if (t.brush.length) parts.push(`Brushed at ${list(t.brush.map((b) => SITES[b] ?? b.replace(/_/g, ' ')))}`);
  const chests = Object.keys(t.chests).sort().map((c) => { const p = c.split('/').pop()!; return CHESTS[p] ?? p.replace(/_/g, ' '); });
  if (chests.length) parts.push(`${parts.length ? 'in' : 'In'} ${list(chests)} chests`);
  return parts.join('; ');
}

/** Where a curio, gear piece or supply comes from: the feats and collection milestones that pay it. */
export function sources(config: Config, reward: string): string[] {
  const out: string[] = [];
  for (const f of config.feats) if (!f.hidden && f.rewards.some((r) => r === reward)) out.push(`the ${f.name} feat`);
  for (const c of config.collections) for (const m of c.milestones)
    if (m.rewards.includes(reward)) out.push(`the ${c.title} at ${m.at}`);
  return out;
}
