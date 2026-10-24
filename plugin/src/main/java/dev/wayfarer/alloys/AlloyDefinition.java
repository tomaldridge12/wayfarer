package dev.wayfarer.alloys;

import java.util.List;
import java.util.Map;

/**
 * One alloy from alloys.yml: its crucible recipe, the vanilla material its gear is built on and the tier it
 * upgrades, its stats and identity, how it repairs, and where its template comes from. No server types.
 *
 * @param recipe     item key -> count for one batch; one batch gives {@code yield} ingots
 * @param material   vanilla tier its items are made of (iron_pickaxe), which sets mining tier and base attack values
 * @param upgrades   vanilla tier of the gear its template upgrades (iron gear for steel)
 * @param colour     name colour as 0xRRGGBB
 */
public record AlloyDefinition(String id, String name, int colour, Heat heat, int meltSeconds, Map<String, Integer> recipe,
        int yield, String material, String upgrades, Tools tools, Armour armour, List<Bonus> bonuses, Repair repair,
        String moltenBlock, Template template, Luck luck, boolean fireproof) {

    /** Held gear: durability, mining speed (mining tools), extra attack damage, enchantability. */
    public record Tools(int durability, double speed, double damageBonus, int enchantability) {
    }

    /** Armour points per piece (helmet, chest, legs, boots), toughness, knockback resistance, durability multiplier. */
    public record Armour(List<Integer> points, double toughness, double knockbackResistance, int durability,
            int enchantability) {
    }

    /** Where an identity attribute goes. */
    public enum Applies {
        HELD, TOOLS, WEAPONS, ARMOUR, ALL;

        boolean covers(GearShape shape) {
            return switch (this) {
                case HELD -> shape.held();
                case TOOLS -> shape.miningTool();
                case WEAPONS -> shape == GearShape.SWORD || shape == GearShape.AXE || shape == GearShape.SPEAR;
                case ARMOUR -> shape.armour();
                case ALL -> true;
            };
        }
    }

    /**
     * An identity attribute on the gear (luck +1 per armour piece). {@code multiply} adds a share of the base value
     * (movement speed -0.025 is 2.5% slower); otherwise the amount is added.
     */
    public record Bonus(String attribute, double amount, boolean multiply, Applies on) {
    }

    /**
     * What repairs this alloy on an anvil and how much of max durability one item restores. "ingot" is the alloy's
     * own ingot; anything else is a vanilla item key (bronze: copper_ingot).
     */
    public record Repair(List<String> items, double share) {
        public static final String OWN_INGOT = "ingot";
    }

    /** The guardian (boss id) whose defeat gives this template, the chance per damager, and the duplication block. */
    public record Template(String boss, double chance, String duplicateBlock) {
    }

    /** Shakudo's edge: Fortune counted this much higher on tools; chance of one more of each drop on weapon kills. */
    public record Luck(int fortuneBonus, double extraLootChance) {
        public static final Luck NONE = new Luck(0, 0);
    }
}
