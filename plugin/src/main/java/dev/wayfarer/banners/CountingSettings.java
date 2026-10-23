package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which kills count. Spawn reasons are upper-case names; "custom" is deliberately not excluded by default.
 * The all-mobs family counts hostile mobs only unless {@code counting.all-mobs: any}.
 */
public record CountingSettings(Set<String> excludedSpawnReasons, long minLifetimeTicks, boolean allMobsHostileOnly) {
    public CountingSettings(Set<String> excludedSpawnReasons, long minLifetimeTicks) {
        this(excludedSpawnReasons, minLifetimeTicks, true);
    }

    static final List<String> DEFAULT_EXCLUDED = List.of("SPAWNER", "TRIAL_SPAWNER", "OMINOUS_ITEM_SPAWNER",
            "SPAWNER_EGG", "SLIME_SPLIT", "BREEDING", "EGG", "DISPENSE_EGG");

    public static CountingSettings defaults() {
        return new CountingSettings(Set.copyOf(DEFAULT_EXCLUDED), 0);
    }

    static CountingSettings parse(Object raw, Vocabulary vocabulary, List<String> problems) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        Set<String> excluded = Set.copyOf(DEFAULT_EXCLUDED);
        if (map.get("exclude-spawn-reasons") instanceof List<?> list) {
            List<String> names = new ArrayList<>();
            for (Object item : list) {
                String name = String.valueOf(item).toUpperCase(Locale.ROOT);
                if (vocabulary.isSpawnReason(name)) {
                    names.add(name);
                } else {
                    problems.add("counting.exclude-spawn-reasons: unknown spawn reason '" + item + "' ignored");
                }
            }
            excluded = names.stream().collect(Collectors.toUnmodifiableSet());
        }
        long min = 0;
        Object value = map.get("min-mob-lifetime-ticks");
        if (value instanceof Number n && n.longValue() >= 0) {
            min = n.longValue();
        } else if (value != null) {
            problems.add("counting.min-mob-lifetime-ticks must be a whole number of 0 or more; using 0");
        }
        boolean hostileOnly = true;
        Object allMobs = map.get("all-mobs");
        if ("any".equals(allMobs)) {
            hostileOnly = false;
        } else if (allMobs != null && !"hostile".equals(allMobs)) {
            problems.add("counting.all-mobs must be hostile or any; using hostile");
        }
        return new CountingSettings(excluded, min, hostileOnly);
    }
}
