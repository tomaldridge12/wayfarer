package dev.wayfarer.alloys;

import java.util.Locale;
import java.util.Optional;

/** The vanilla tool and armour shapes alloy gear comes in. */
public enum GearShape {
    SWORD, PICKAXE, AXE, SHOVEL, HOE, SPEAR, HELMET, CHESTPLATE, LEGGINGS, BOOTS;

    /** Vanilla armour durability per piece before the material's multiplier (helmet 11, chest 16, legs 15, feet 13). */
    private static final int[] ARMOUR_BASE = {11, 16, 15, 13};

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean armour() {
        return ordinal() >= HELMET.ordinal();
    }

    /** 0 helmet .. 3 boots; -1 for tools and weapons. */
    public int armourIndex() {
        return armour() ? ordinal() - HELMET.ordinal() : -1;
    }

    /** Mining tools, whose mining speed the alloy sets. */
    public boolean miningTool() {
        return this == PICKAXE || this == AXE || this == SHOVEL || this == HOE;
    }

    /** Held items that hit things: everything that is not armour. */
    public boolean held() {
        return !armour();
    }

    /** Max durability of an armour piece for a material multiplier. */
    public int armourDurability(int multiplier) {
        return ARMOUR_BASE[armourIndex()] * multiplier;
    }

    /** The vanilla item key of this shape in a vanilla tier: iron + pickaxe -> iron_pickaxe. */
    public String vanillaItem(String tier) {
        return tier + "_" + id();
    }

    public String title() {
        return id().substring(0, 1).toUpperCase(Locale.ROOT) + id().substring(1);
    }

    public static Optional<GearShape> parse(String id) {
        for (GearShape s : values()) {
            if (s.id().equals(id)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** A vanilla gear key split into tier and shape: iron_pickaxe -> (iron, pickaxe); anything else is empty. */
    public static Optional<Vanilla> ofVanilla(String itemKey) {
        int cut = itemKey.lastIndexOf('_');
        if (cut <= 0) {
            return Optional.empty();
        }
        return parse(itemKey.substring(cut + 1)).map(shape -> new Vanilla(itemKey.substring(0, cut), shape));
    }

    /** A vanilla gear piece's tier (iron, diamond, golden ...) and shape. */
    public record Vanilla(String tier, GearShape shape) {
    }
}
