package dev.wayfarer.banners;

import java.util.Collection;
import java.util.Map;

/**
 * The Wayfarer set: relic armour (head, chest, legs, feet, off-hand charm) from enough different mob families
 * at once. Any tier counts. No server types.
 */
public final class WayfarerSet {
    /** How many families are needed, the damage reduction from hostile mobs, and the extra hearts. */
    public record Settings(int families, int reductionPercent, int hearts) {
        public static Settings defaults() {
            return new Settings(4, 10, 1);
        }

        static Settings parse(Object raw, java.util.List<String> problems) {
            Settings d = defaults();
            if (!(raw instanceof Map<?, ?> m)) {
                return d;
            }
            return new Settings(whole(m, "families", d.families(), 2, 5, problems),
                    whole(m, "damage-reduction", d.reductionPercent(), 0, 100, problems),
                    whole(m, "hearts", d.hearts(), 0, 10, problems));
        }

        private static int whole(Map<?, ?> m, String name, int fallback, int min, int max, java.util.List<String> problems) {
            Object v = m.get(name);
            if (v == null) {
                return fallback;
            }
            if (v instanceof Number n && n.doubleValue() == n.intValue() && n.intValue() >= min && n.intValue() <= max) {
                return n.intValue();
            }
            problems.add("wayfarer-set." + name + " must be a whole number from " + min + " to " + max + "; using " + fallback);
            return fallback;
        }
    }

    private WayfarerSet() {
    }

    /** Whether worn relic armour (already checked to be in its own slot) completes the set. */
    public static boolean complete(Collection<RelicDefinition> wornArmour, Settings settings) {
        return wornArmour.stream().filter(r -> r.slot().armour() && !MobFamily.ALL.equals(r.family()))
                .map(RelicDefinition::family).distinct().count() >= settings.families();
    }

    /** The set's reduction as an armour edge against every hostile mob, so it adds up like other edges. */
    public static BoostRules.Edge edge(Settings settings) {
        return new BoostRules.Edge("wayfarer_set", MobFamily.ALL, settings.reductionPercent());
    }
}
