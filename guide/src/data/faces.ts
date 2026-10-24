// Mob heads are 3D models in the game, so the Guide cuts their face from the mob's skin instead:
// [skin under entity/, face x, y, width, height]. Keyed by head item, or by MHF_ skin for player heads.
export const FACES: Record<string, [string, number, number, number, number]> = {
  zombie_head: ['zombie/zombie', 8, 8, 8, 8],
  skeleton_skull: ['skeleton/skeleton', 8, 8, 8, 8],
  wither_skeleton_skull: ['skeleton/wither_skeleton', 8, 8, 8, 8],
  creeper_head: ['creeper/creeper', 8, 8, 8, 8],
  piglin_head: ['piglin/piglin', 8, 8, 10, 8],
  MHF_Spider: ['spider/spider', 40, 12, 8, 8],
  MHF_CaveSpider: ['spider/cave_spider', 40, 12, 8, 8],
  MHF_Enderman: ['enderman/enderman', 8, 8, 8, 8],
  MHF_Witch: ['witch/witch', 8, 8, 8, 10],
};
