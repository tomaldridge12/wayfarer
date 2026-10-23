package dev.wayfarer.bosses;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which ability a boss uses next. Each ability has a base cooldown and a range band to its target; stars and phase
 * two shorten cooldowns, and 3 and 5 stars add an ability. The least recently used ready ability goes first.
 * No server types.
 */
public final class AbilityPlanner {
    /** Base cooldown in ticks and the target distance band in blocks. */
    record Spec(int cooldownTicks, double minRange, double maxRange) {
    }

    static final Map<Ability, Spec> SPECS = new EnumMap<>(Map.of(
            Ability.CHARGE, new Spec(120, 4, 16),
            Ability.GROUND_SLAM, new Spec(140, 0, 5),
            Ability.SUMMON_MINIONS, new Spec(320, 0, 30),
            Ability.POISON_CLOUD, new Spec(200, 0, 12),
            Ability.ARROW_VOLLEY, new Spec(120, 3, 24),
            Ability.BLINK, new Spec(160, 3, 24),
            Ability.FIRE_RING, new Spec(200, 0, 6),
            Ability.PULL, new Spec(180, 6, 20),
            Ability.SHIELD, new Spec(600, 0, 30)));

    /** Extra abilities granted at 3 and 5 stars, the first not already known. */
    private static final List<Ability> BONUS = List.of(Ability.GROUND_SLAM, Ability.CHARGE, Ability.PULL,
            Ability.SUMMON_MINIONS);

    /** Ticks between any two abilities. */
    static final int GLOBAL_COOLDOWN = 60;

    private final List<Ability> abilities;
    private final double cooldownFactor;
    private final Map<Ability, Long> lastUsed = new EnumMap<>(Ability.class);
    private long lastAny = Long.MIN_VALUE / 2;

    public AbilityPlanner(List<Ability> variantAbilities, int stars) {
        List<Ability> known = new ArrayList<>(variantAbilities);
        known.remove(Ability.ENRAGE); // enrage is phase two, not a cast
        int bonus = stars >= 5 ? 2 : stars >= 3 ? 1 : 0;
        for (Ability a : BONUS) {
            if (bonus == 0) {
                break;
            }
            if (!known.contains(a)) {
                known.add(a);
                bonus--;
            }
        }
        this.abilities = List.copyOf(known);
        this.cooldownFactor = 1 - 0.1 * (StarScale.clamp(stars) - 1); // 5 stars: 60% of base cooldowns
    }

    public List<Ability> abilities() {
        return abilities;
    }

    /**
     * The ability to use now, if any.
     * @param distance blocks to the current target, or NaN when the boss has none
     */
    public Optional<Ability> next(long now, double distance, boolean phaseTwo) {
        if (Double.isNaN(distance) || now - lastAny < GLOBAL_COOLDOWN * factor(phaseTwo)) {
            return Optional.empty();
        }
        Ability best = null;
        long bestUsed = Long.MAX_VALUE;
        for (Ability a : abilities) {
            Spec spec = SPECS.get(a);
            long used = lastUsed.getOrDefault(a, Long.MIN_VALUE / 2);
            boolean ready = now - used >= spec.cooldownTicks() * factor(phaseTwo);
            boolean inRange = distance >= spec.minRange() && distance <= spec.maxRange();
            if (ready && inRange && used < bestUsed) {
                best = a;
                bestUsed = used;
            }
        }
        return Optional.ofNullable(best);
    }

    public void used(Ability ability, long now) {
        lastUsed.put(ability, now);
        lastAny = now;
    }

    private double factor(boolean phaseTwo) {
        return cooldownFactor * (phaseTwo ? 0.75 : 1);
    }
}
