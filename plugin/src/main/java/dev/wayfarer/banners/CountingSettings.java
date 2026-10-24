package dev.wayfarer.banners;

import java.util.List;
import java.util.Map;

/**
 * Which kills the all-mobs ladder reads: hostile mob types only, unless {@code counting.all-mobs: any}. Which kills
 * count at all is core's credit rules (progression.yml).
 */
public record CountingSettings(boolean allMobsHostileOnly) {
    public static CountingSettings defaults() {
        return new CountingSettings(true);
    }

    static CountingSettings parse(Object raw, List<String> problems) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        for (String moved : List.of("exclude-spawn-reasons", "min-mob-lifetime-ticks")) {
            if (map.containsKey(moved)) {
                problems.add("counting." + moved + " has moved to progression.yml (counting." + moved + "); ignored here");
            }
        }
        boolean hostileOnly = true;
        Object allMobs = map.get("all-mobs");
        if ("any".equals(allMobs)) {
            hostileOnly = false;
        } else if (allMobs != null && !"hostile".equals(allMobs)) {
            problems.add("counting.all-mobs must be hostile or any; using hostile");
        }
        return new CountingSettings(hostileOnly);
    }
}
