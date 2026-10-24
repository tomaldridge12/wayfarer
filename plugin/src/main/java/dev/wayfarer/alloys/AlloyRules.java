package dev.wayfarer.alloys;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Every decision the alloys module makes, from plain facts: what a crucible's contents melt into, what the smithing
 * table makes, an alloy piece's stats, what repairs it, the anvil's cost, and which guardian gives which template.
 * Built from alloys.yml by {@link AlloyConfigParser}; no server types.
 */
public final class AlloyRules {
    /** Crucible limits and which block below a cauldron gives which heat (block key -> heat). */
    public record Crucible(Map<String, Heat> heatBlocks, int maxContents, int maxIngots) {
        public static Crucible defaults() {
            return new Crucible(Map.of("campfire", Heat.LOW, "lava", Heat.HIGH, "magma_block", Heat.HIGH,
                    "soul_campfire", Heat.SOUL, "soul_fire", Heat.SOUL), 64, 8);
        }
    }

    /** Endless repair: the anvil's cost stops growing at the cap instead of becoming "Too Expensive!". */
    public record Anvil(boolean endless, int cap) {
        public static Anvil defaults() {
            return new Anvil(true, 30);
        }
    }

    /** What a crucible is doing with its contents. */
    public enum Status { EMPTY, NO_RECIPE, NO_HEAT, TOO_COLD, MELTS }

    /** A crucible's verdict: for MELTS, the alloy, batches, ingots and melt time; the alloy also for TOO_COLD. */
    public record Melt(Status status, AlloyDefinition alloy, int batches, int ingots, int ticks) {
        static Melt of(Status status) {
            return new Melt(status, null, 0, 0, 0);
        }
    }

    /** One alloy piece: which alloy, which shape. Written "steel:pickaxe" on items. */
    public record Gear(String alloy, GearShape shape) {
        public String key() {
            return alloy + ":" + shape.id();
        }

        public String model() {
            return alloy + "_" + shape.id();
        }

        public static Optional<Gear> parse(String text) {
            int cut = text.indexOf(':');
            return cut <= 0 ? Optional.empty()
                    : GearShape.parse(text.substring(cut + 1)).map(s -> new Gear(text.substring(0, cut), s));
        }
    }

    /**
     * The stats of one alloy piece. {@code item} is the vanilla item it is built on (iron_pickaxe). Mining speed is
     * 0 for shapes that do not mine; armour fields are 0 for held items.
     */
    public record Stats(String item, int durability, double miningSpeed, double damageBonus, int armour,
            double toughness, double knockbackResistance, int enchantability, List<AlloyDefinition.Bonus> bonuses,
            boolean fireproof) {
    }

    /** The base slot of the smithing table: a vanilla item, or alloy gear. */
    public record SmithBase(String itemKey, Gear gear) {
        public static SmithBase vanilla(String itemKey) {
            return new SmithBase(itemKey, null);
        }

        public static SmithBase alloy(Gear gear) {
            return new SmithBase(null, gear);
        }
    }

    private final Map<String, AlloyDefinition> alloys;
    private final Crucible crucible;
    private final Anvil anvil;

    public AlloyRules(Map<String, AlloyDefinition> alloys, Crucible crucible, Anvil anvil) {
        this.alloys = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(alloys));
        this.crucible = crucible;
        this.anvil = anvil;
    }

    public static AlloyRules empty() {
        return new AlloyRules(Map.of(), Crucible.defaults(), Anvil.defaults());
    }

    /** Every alloy, in config order. */
    public List<AlloyDefinition> alloys() {
        return List.copyOf(alloys.values());
    }

    public Optional<AlloyDefinition> alloy(String id) {
        return Optional.ofNullable(alloys.get(id));
    }

    public Crucible crucible() {
        return crucible;
    }

    public Anvil anvil() {
        return anvil;
    }

    // --- Crucible -------------------------------------------------------------------------------------------------

    /** Whether any recipe uses this item key, so a right-click with it loads a crucible. */
    public boolean isIngredient(String itemKey) {
        return alloys.values().stream().anyMatch(a -> a.recipe().containsKey(itemKey));
    }

    /** The heat a block under a cauldron gives; campfires only while lit. */
    public Optional<Heat> heatOf(String blockKey, boolean lit) {
        if ((blockKey.equals("campfire") || blockKey.equals("soul_campfire")) && !lit) {
            return Optional.empty();
        }
        return Optional.ofNullable(crucible.heatBlocks().get(blockKey));
    }

    /**
     * What these contents (item key -> count, order does not matter) do at this heat (empty: no heat). Only an exact
     * multiple of one recipe melts, as that many batches, and only while the ingots fit in one melt.
     */
    public Melt melt(Map<String, Integer> contents, Optional<Heat> heat) {
        Map<String, Integer> present = new java.util.HashMap<>();
        contents.forEach((k, v) -> {
            if (v != null && v > 0) {
                present.put(k, v);
            }
        });
        if (present.isEmpty()) {
            return Melt.of(Status.EMPTY);
        }
        for (AlloyDefinition a : alloys()) {
            int batches = batches(a.recipe(), present);
            if (batches <= 0 || batches * a.yield() > crucible.maxIngots()) {
                continue;
            }
            if (heat.isEmpty()) {
                return new Melt(Status.NO_HEAT, a, batches, batches * a.yield(), 0);
            }
            if (!heat.get().reaches(a.heat())) {
                return new Melt(Status.TOO_COLD, a, batches, batches * a.yield(), 0);
            }
            return new Melt(Status.MELTS, a, batches, batches * a.yield(), a.meltSeconds() * 20);
        }
        return Melt.of(Status.NO_RECIPE);
    }

    /** How many whole batches of the recipe the contents are exactly; 0 if they are not an exact multiple. */
    private static int batches(Map<String, Integer> recipe, Map<String, Integer> contents) {
        if (!recipe.keySet().equals(contents.keySet())) {
            return 0;
        }
        int batches = -1;
        for (var e : recipe.entrySet()) {
            int have = contents.get(e.getKey());
            if (have % e.getValue() != 0) {
                return 0;
            }
            int k = have / e.getValue();
            if (batches == -1) {
                batches = k;
            } else if (batches != k) {
                return 0;
            }
        }
        return Math.max(0, batches);
    }

    // --- Gear -----------------------------------------------------------------------------------------------------

    /** The stats of an alloy piece, or empty for an alloy config no longer has. */
    public Optional<Stats> stats(Gear gear) {
        return alloy(gear.alloy()).map(a -> stats(a, gear.shape()));
    }

    static Stats stats(AlloyDefinition a, GearShape shape) {
        List<AlloyDefinition.Bonus> bonuses = a.bonuses().stream().filter(b -> b.on().covers(shape)).toList();
        String item = shape.vanillaItem(a.material());
        if (shape.armour()) {
            AlloyDefinition.Armour ar = a.armour();
            return new Stats(item, shape.armourDurability(ar.durability()), 0, 0, ar.points().get(shape.armourIndex()),
                    ar.toughness(), ar.knockbackResistance(), ar.enchantability(), bonuses, a.fireproof());
        }
        AlloyDefinition.Tools t = a.tools();
        return new Stats(item, t.durability(), shape.miningTool() ? t.speed() : 0, t.damageBonus(), 0, 0, 0,
                t.enchantability(), bonuses, a.fireproof());
    }

    // --- Smithing -------------------------------------------------------------------------------------------------

    /**
     * What template + base + ingot make: the template's alloy in the base's shape, when the ingot is the same alloy
     * and the base is vanilla gear of the tier that alloy upgrades, or another alloy's gear. (Relics are refused by
     * the banners module, which owns them.)
     */
    public Optional<Gear> smith(String templateAlloy, SmithBase base, String ingotAlloy) {
        if (templateAlloy == null || !templateAlloy.equals(ingotAlloy) || base == null) {
            return Optional.empty();
        }
        Optional<AlloyDefinition> alloy = alloy(templateAlloy);
        if (alloy.isEmpty()) {
            return Optional.empty();
        }
        if (base.gear() != null) {
            return base.gear().alloy().equals(templateAlloy) ? Optional.empty()
                    : Optional.of(new Gear(templateAlloy, base.gear().shape()));
        }
        return base.itemKey() == null ? Optional.empty() : GearShape.ofVanilla(base.itemKey()).filter(v -> v.tier().equals(alloy.get().upgrades()))
                .map(v -> new Gear(templateAlloy, v.shape()));
    }

    // --- Repair ---------------------------------------------------------------------------------------------------

    /**
     * The share of max durability one offered item restores on alloy gear: an alloy ingot ({@code ingotAlloy}) or a
     * plain vanilla item ({@code vanillaKey}, only when the item carries no alloy tag). Empty when it does not repair.
     */
    public OptionalDouble repairShare(String gearAlloy, String ingotAlloy, String vanillaKey) {
        Optional<AlloyDefinition> alloy = alloy(gearAlloy);
        if (alloy.isEmpty()) {
            return OptionalDouble.empty();
        }
        List<String> items = alloy.get().repair().items();
        boolean ok = ingotAlloy != null
                ? ingotAlloy.equals(gearAlloy) && items.contains(AlloyDefinition.Repair.OWN_INGOT)
                : vanillaKey != null && items.contains(vanillaKey);
        return ok ? OptionalDouble.of(alloy.get().repair().share()) : OptionalDouble.empty();
    }

    /** A material repair: how many offered items it uses and the damage left after. */
    public record RepairPlan(int itemsUsed, int damageLeft) {
    }

    /**
     * Repairs {@code damage} of {@code maxDamage} with up to {@code offered} items restoring {@code share} each, as
     * vanilla does: each item restores the share (rounded up), and no more items are used than the damage needs.
     */
    public static RepairPlan repair(int maxDamage, int damage, int offered, double share) {
        int per = Math.max(1, (int) Math.ceil(maxDamage * share));
        int used = Math.min(offered, (damage + per - 1) / per);
        return new RepairPlan(used, Math.max(0, damage - used * per));
    }

    /** Vanilla's anvil cost for a material repair: the item's prior work, one level per item, one for a rename. */
    public static int repairCost(int priorWork, int itemsUsed, boolean renamed) {
        return priorWork + itemsUsed + (renamed ? 1 : 0);
    }

    /** The level cost an anvil charges: vanilla's own, capped while endless repair is on. */
    public int anvilCost(int vanillaCost) {
        return anvil.endless() ? Math.min(vanillaCost, anvil.cap()) : vanillaCost;
    }

    // --- Luck (shakudo) -------------------------------------------------------------------------------------------

    /** The Fortune level a tool of this alloy mines with, given its enchanted level. */
    public int fortune(String gearAlloy, int enchanted) {
        return enchanted + alloy(gearAlloy).map(a -> a.luck().fortuneBonus()).orElse(0);
    }

    /** Whether a kill with this alloy's weapon adds one more of each drop, for a roll in [0, 1). */
    public boolean extraLoot(String gearAlloy, double roll) {
        return alloy(gearAlloy).map(a -> roll < a.luck().extraLootChance()).orElse(false);
    }

    // --- Templates ------------------------------------------------------------------------------------------------

    /** The alloy whose template this boss gives each damager, only when it was a structure guardian. */
    public Optional<AlloyDefinition> templateFrom(String bossId, boolean guardian) {
        if (!guardian) {
            return Optional.empty();
        }
        return alloys().stream().filter(a -> a.template() != null && bossId.equals(a.template().boss())).findFirst();
    }
}
