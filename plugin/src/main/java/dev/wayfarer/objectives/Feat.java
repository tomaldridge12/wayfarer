package dev.wayfarer.objectives;

import java.util.List;
import java.util.Set;

/**
 * A feat: a one-off milestone on any count (a sum, or how many different targets), its story, whether it hides until
 * earned, and the themed rewards it pays.
 */
public record Feat(String id, String name, String flavour, Set<String> selector, boolean distinct, long at, boolean hidden,
        List<String> rewards) {
    public Feat {
        selector = Set.copyOf(selector);
        rewards = List.copyOf(rewards);
    }

    /** The reward that marks the feat itself as earned (its title card and announcement). */
    public String marker() {
        return FeatRules.FEAT + ":" + id;
    }
}
