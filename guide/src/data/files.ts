// The files Guide data reads: their text, or just whether they exist (chapters, textures).
export interface GuideFiles {
  text(path: string): string | undefined;
  has(path: string): boolean;
}

/** The repo as snapshotted by scripts/prepare.mjs: what pages read at build time. */
export interface Snapshot { texts: Record<string, string>; files: string[] }

export function snapshotFiles(snapshot: Snapshot): GuideFiles {
  const all = new Set([...snapshot.files, ...Object.keys(snapshot.texts)]);
  return { text: (p) => snapshot.texts[p], has: (p) => all.has(p) };
}
