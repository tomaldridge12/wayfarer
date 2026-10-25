package dev.wayfarer.curios;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The curio rules: which carried curios work now (each curio once, however many copies are carried), what their
 * effects add up to, drop odds and tool cooldowns. No server types.
 */
public final class CurioRules {
    private final Map<String, CurioDefinition> curios;
    private final Map<String, CurioDefinition> hatAbilities;

    public CurioRules(Map<String, CurioDefinition> curios) {
        this(curios, Map.of());
    }

    /** @param hatAbilities charm-like perks worn hats give, by ability id; they are not items and not collectible */
    public CurioRules(Map<String, CurioDefinition> curios, Map<String, CurioDefinition> hatAbilities) {
        this.curios = Map.copyOf(curios);
        this.hatAbilities = Map.copyOf(hatAbilities);
    }

    public static CurioRules empty() {
        return new CurioRules(Map.of());
    }

    public Optional<CurioDefinition> curio(String id) {
        return Optional.ofNullable(curios.get(id));
    }

    public Optional<CurioDefinition> hatAbility(String id) {
        return Optional.ofNullable(hatAbilities.get(id));
    }

    /** The hat ability if it exists and its conditions hold right now. */
    public Optional<CurioDefinition> hatWorking(String id, CurioFacts facts) {
        return hatAbility(id).filter(c -> Condition.all(c.conditions(), facts));
    }

    /** Every curio, in config order is not kept by Map.copyOf, so sorted by id for stable output. */
    public List<CurioDefinition> curios() {
        return curios.values().stream().sorted(java.util.Comparator.comparing(CurioDefinition::id)).toList();
    }

    /**
     * The charms that work now among the carried curio ids: known, not tools, conditions met, each once. Order follows
     * the carried order, so the first copy found wins.
     */
    public List<CurioDefinition> working(Collection<String> carried, CurioFacts facts) {
        Set<String> seen = new LinkedHashSet<>();
        List<CurioDefinition> out = new ArrayList<>();
        for (String id : carried) {
            CurioDefinition c = curios.get(id);
            if (c != null && !c.tool() && seen.add(id) && Condition.all(c.conditions(), facts)) {
                out.add(c);
            }
        }
        return out;
    }

    /** The effects of one type among working curios. */
    public static <E extends Effect> List<E> effects(Collection<CurioDefinition> working, Class<E> type) {
        return working.stream().map(CurioDefinition::effect).filter(type::isInstance).map(type::cast).toList();
    }

    /** How many extra items breaking this block gives with a {@code roll} in [0, 1): the effect's amount, or 0. */
    public static int dropBonus(Effect.DropBonus bonus, String block, double roll) {
        return bonus.blocks().contains(block) && roll < bonus.chance() ? bonus.amount() : 0;
    }

    /** Experience from a source after the strongest bonus that covers it (bonuses never multiply each other). */
    public static int xp(int base, Collection<Effect.XpBonus> bonuses, String source) {
        double best = bonuses.stream().filter(b -> b.sources().contains(source))
                .mapToDouble(Effect.XpBonus::multiplier).max().orElse(1);
        return (int) Math.round(base * best);
    }

    /** The strongest boost by name among working curios, or 1. */
    public static double boost(Collection<CurioDefinition> working, String what) {
        return effects(working, Effect.Boost.class).stream().filter(b -> b.what().equals(what))
                .mapToDouble(Effect.Boost::factor).max().orElse(1);
    }

    /** Tool cooldowns per player and curio, in memory (a restart forgives them). */
    public static final class Cooldowns {
        private final Map<String, Long> until = new LinkedHashMap<>();

        /** Milliseconds left on the player's cooldown for this curio; 0 when ready. */
        public long remaining(UUID player, String curio, long now) {
            return Math.max(0, until.getOrDefault(player + "/" + curio, 0L) - now);
        }

        /** Starts the cooldown if it is ready; false (and nothing changes) if it is still cooling. */
        public boolean tryStart(UUID player, String curio, int seconds, long now) {
            if (remaining(player, curio, now) > 0) {
                return false;
            }
            until.put(player + "/" + curio, now + seconds * 1000L);
            return true;
        }

        /** Gives back a cooldown taken for a use that did not happen (nothing to sense, no Wayfinder). */
        public void cancel(UUID player, String curio) {
            until.remove(player + "/" + curio);
        }
    }
}
