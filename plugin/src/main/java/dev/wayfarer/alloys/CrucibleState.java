package dev.wayfarer.alloys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One crucible's contents and melt progress, kept in its chunk's data. {@code progress} is melt ticks done so far;
 * {@code last} is the world game time it was last advanced, so time passes while the chunk is unloaded. No server
 * types.
 */
public record CrucibleState(int x, int y, int z, Map<String, Integer> contents, int progress, UUID starter, long last) {
    public CrucibleState {
        contents = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(contents));
    }

    public static CrucibleState empty(int x, int y, int z, long now) {
        return new CrucibleState(x, y, z, Map.of(), 0, null, now);
    }

    public int total() {
        return contents.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** What loading some items did: the new state and how many were taken (the rest stay in the hand). */
    public record Added(CrucibleState state, int taken) {
    }

    /**
     * Takes up to {@code offered} of an item at game time {@code now}, never past {@code max} in total; any change
     * starts the melt over.
     */
    public Added add(String item, int offered, int max, UUID who, long now) {
        int taken = Math.max(0, Math.min(offered, max - total()));
        if (taken == 0) {
            return new Added(this, 0);
        }
        Map<String, Integer> next = new LinkedHashMap<>(contents);
        next.merge(item, taken, Integer::sum);
        return new Added(new CrucibleState(x, y, z, next, 0, who, now), taken);
    }

    /** One advance of the clock: the new state, and the finished melt if it just completed. */
    public record Step(CrucibleState state, Optional<AlloyRules.Melt> finished) {
    }

    /**
     * Advances to game time {@code now} under this verdict: a melt gains the ticks since the last advance; no heat or
     * too little heat pauses it; contents that make no recipe have no progress. A finished melt leaves it empty.
     */
    public Step step(AlloyRules.Melt melt, long now) {
        long elapsed = Math.max(0, now - last);
        return switch (melt.status()) {
            case MELTS -> {
                long done = progress + elapsed;
                if (done >= melt.ticks()) {
                    yield new Step(new CrucibleState(x, y, z, Map.of(), 0, null, now), Optional.of(melt));
                }
                yield new Step(new CrucibleState(x, y, z, contents, (int) done, starter, now), Optional.empty());
            }
            case NO_HEAT, TOO_COLD -> new Step(new CrucibleState(x, y, z, contents, progress, starter, now), Optional.empty());
            case EMPTY, NO_RECIPE -> new Step(new CrucibleState(x, y, z, contents, 0, starter, now), Optional.empty());
        };
    }

    /** Prefix of the chunk data format; data without one is the same format (written before it was versioned). */
    static final String VERSION = "v1";

    /**
     * Chunk data: {@code v1} then crucibles, joined by '|', each {@code x,y,z;item=n,item=n;progress;starter or -;last}.
     */
    public static String encode(Collection<CrucibleState> crucibles) {
        List<String> parts = new ArrayList<>();
        for (CrucibleState c : crucibles) {
            List<String> items = new ArrayList<>();
            c.contents().forEach((k, v) -> items.add(k + "=" + v));
            parts.add(c.x() + "," + c.y() + "," + c.z() + ";" + String.join(",", items) + ";" + c.progress() + ";"
                    + (c.starter() == null ? "-" : c.starter()) + ";" + c.last());
        }
        parts.addFirst(VERSION);
        return String.join("|", parts);
    }

    public record Decoded(List<CrucibleState> crucibles, List<String> problems) {
    }

    public static Decoded decode(String text) {
        List<CrucibleState> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return new Decoded(out, problems);
        }
        String[] all = text.split("\\|");
        int first = 0;
        if (all[0].matches("v\\d+")) {
            if (!all[0].equals(VERSION)) {
                problems.add("crucible data is format " + all[0] + ", newer than this plugin (" + VERSION + "); not read");
                return new Decoded(out, problems);
            }
            first = 1;
        }
        for (String part : java.util.Arrays.copyOfRange(all, first, all.length)) {
            try {
                String[] f = part.split(";", -1);
                String[] pos = f[0].split(",");
                Map<String, Integer> contents = new LinkedHashMap<>();
                if (!f[1].isEmpty()) {
                    for (String entry : f[1].split(",")) {
                        String[] kv = entry.split("=");
                        contents.put(kv[0], Integer.parseInt(kv[1]));
                    }
                }
                UUID starter = f[3].equals("-") ? null : UUID.fromString(f[3]);
                out.add(new CrucibleState(Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2]),
                        contents, Integer.parseInt(f[2]), starter, Long.parseLong(f[4])));
            } catch (RuntimeException e) {
                problems.add("unreadable crucible record '" + part + "' skipped");
            }
        }
        return new Decoded(out, problems);
    }
}
