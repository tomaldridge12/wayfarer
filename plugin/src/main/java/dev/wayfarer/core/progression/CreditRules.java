package dev.wayfarer.core.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Which kills count. Spawn reasons are upper-case names; "custom" is deliberately not excluded by default. */
public record CreditRules(Set<String> excludedSpawnReasons, long minLifetimeTicks) {
    static final List<String> DEFAULT_EXCLUDED = List.of("SPAWNER", "TRIAL_SPAWNER", "OMINOUS_ITEM_SPAWNER",
            "SPAWNER_EGG", "SLIME_SPLIT", "BREEDING", "EGG", "DISPENSE_EGG");

    public static CreditRules defaults() {
        return new CreditRules(Set.copyOf(DEFAULT_EXCLUDED), 0);
    }

    /** Whether a kill counts for its killer. */
    boolean counts(KillFacts kill) {
        return kill.killer() == KillFacts.Killer.PLAYER && !kill.tamed() && !kill.leashed()
                && !excludedSpawnReasons.contains(kill.spawnReason().toUpperCase(Locale.ROOT))
                && kill.ageTicks() >= minLifetimeTicks;
    }

    /** The {@code counting} section; {@code isSpawnReason} takes upper-case names. */
    public static CreditRules parse(Object raw, Predicate<String> isSpawnReason, List<String> problems) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        Set<String> excluded = Set.copyOf(DEFAULT_EXCLUDED);
        if (map.get("exclude-spawn-reasons") instanceof List<?> list) {
            List<String> names = new ArrayList<>();
            for (Object item : list) {
                String name = String.valueOf(item).toUpperCase(Locale.ROOT);
                if (isSpawnReason.test(name)) {
                    names.add(name);
                } else {
                    problems.add("counting.exclude-spawn-reasons: unknown spawn reason '" + item + "' ignored");
                }
            }
            excluded = Set.copyOf(names);
        }
        long min = 0;
        Object value = map.get("min-mob-lifetime-ticks");
        if (value instanceof Number n && n.longValue() >= 0) {
            min = n.longValue();
        } else if (value != null) {
            problems.add("counting.min-mob-lifetime-ticks must be a whole number of 0 or more; using 0");
        }
        return new CreditRules(excluded, min);
    }
}
