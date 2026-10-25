package dev.wayfarer.enchanting;

/** Enchanting rules: when a click reshuffles an enchanting table and whether the player can pay. No server types. */
public final class EnchantingRules {
    /** What a click on an enchanting table amounts to. */
    public enum Verdict {
        /** Not a reshuffle click: vanilla carries on. */
        IGNORE,
        /** A reshuffle click from a player with no level to pay with. */
        NEEDS_LEVEL,
        /** Pay and reshuffle. */
        RESHUFFLE
    }

    private EnchantingRules() {
    }

    /**
     * Whether a click is the reshuffle gesture: a main-hand right-click on an enchanting table while sneaking with the
     * trigger item in that hand. {@code held} and {@code trigger} are item keys without a namespace ({@code lapis_lazuli}).
     */
    public static boolean triggers(boolean mainHandRightClickOnTable, boolean sneaking, String held, String trigger) {
        return mainHandRightClickOnTable && sneaking && held != null && held.equalsIgnoreCase(trigger);
    }

    /** What happens for a click: ignored, refused for want of levels (free in creative), or a reshuffle. */
    public static Verdict verdict(boolean triggered, int level, boolean creative, int cost) {
        if (!triggered) {
            return Verdict.IGNORE;
        }
        return creative || level >= cost ? Verdict.RESHUFFLE : Verdict.NEEDS_LEVEL;
    }

    /** The level after paying: whole levels come off and the progress bar keeps its fill. Creative pays nothing. */
    public static int levelAfter(int level, int cost, boolean creative) {
        return creative ? level : Math.max(0, level - cost);
    }
}
