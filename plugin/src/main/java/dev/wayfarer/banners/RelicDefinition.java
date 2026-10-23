package dev.wayfarer.banners;

import java.util.Locale;
import java.util.Map;

/**
 * A relic on a family's ladder: one weapon or armour piece at one tier. {@code enchants} maps namespaced keys
 * ("minecraft:sharpness", "excellentenchants:vampire") to levels. {@code edgePercent} is the family edge: extra
 * damage dealt (weapons) or damage taken away (armour).
 */
public record RelicDefinition(String id, String name, String family, int kills, int tier, Slot slot,
        String item, Map<String, Integer> enchants, String trimPattern, String trimMaterial, int edgePercent,
        java.util.List<Perk> perks, Integer dye) {

    /** Tiers: 0 cosmetic regalia, 1 Rare (iron-level), 2 Epic (diamond-level), 3 Ascended, 4 Legendary. */
    public static final int REGALIA = 0;
    public static final int ASCENDED = 3;
    public static final int LEGENDARY = 4;

    /** Drop-only relics (Ascended, Legendary) can never be reached by kill count. */
    public static final int DROP_ONLY = Integer.MAX_VALUE;

    public RelicDefinition(String id, String name, String family, int kills, int tier, Slot slot,
            String item, Map<String, Integer> enchants, String trimPattern, String trimMaterial, int edgePercent,
            java.util.List<Perk> perks) {
        this(id, name, family, kills, tier, slot, item, enchants, trimPattern, trimMaterial, edgePercent, perks, null);
    }

    public boolean dropOnly() {
        return kills == DROP_ONLY;
    }

    /** A relic with no item details, for rules that only need its place on the ladder. */
    public RelicDefinition(String id, String name, String family, int kills, int tier, Slot slot) {
        this(id, name, family, kills, tier, slot, null, Map.of(), null, null, defaultEdge(slot, tier, family), java.util.List.of(), null);
    }

    /** Weapons +30% / +40%; armour −25% / −30%; the all-mobs relic +10%. */
    static int defaultEdge(Slot slot, int tier, String family) {
        if (tier == REGALIA) {
            return 0;
        }
        if (MobFamily.ALL.equals(family)) {
            return tier >= LEGENDARY ? 15 : 10;
        }
        if (tier >= ASCENDED) {
            return slot == Slot.WEAPON ? 50 : 35;
        }
        return slot == Slot.WEAPON ? (tier >= 2 ? 40 : 30) : (tier >= 2 ? 30 : 25);
    }

    /** Where a relic is used: weapons from the main hand, armour worn, charms from the off-hand. */
    public enum Slot {
        WEAPON, HEAD, CHEST, LEGS, FEET, OFFHAND;

        static Slot parse(String text) {
            return Slot.valueOf(text.toUpperCase(Locale.ROOT));
        }

        public boolean armour() {
            return this != WEAPON;
        }
    }
}
