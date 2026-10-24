package dev.wayfarer.objectives;

import java.util.Set;

/**
 * One objective from a pool: its name and what to do ({@code text} with {n} for the target), what it counts (a sum, or
 * new distinct targets), how many, the earliest stage it is drawn at, the reward table it rolls from and its tokens.
 */
public record ObjectiveDefinition(String id, String name, String text, Set<String> selector, boolean distinct, long target,
        String stage, String table, int tokens) {
    public ObjectiveDefinition {
        selector = Set.copyOf(selector);
    }

    /** "Mine 48 coal ore". */
    public String task() {
        return text.replace("{n}", FeatRules.number(target));
    }
}
