package dev.wayfarer.core.progression;

/** Activity keys: {@code verb:target}, the target being a vanilla key without its namespace or a full datapack key. */
public final class Activity {
    public static final String KILL = "kill";
    public static final String DEFEAT = "defeat";
    public static final String MINE = "mine";
    public static final String HARVEST = "harvest";
    public static final String CATCH = "catch";
    public static final String SHEAR = "shear";
    public static final String BREED = "breed";
    public static final String DISCOVER = "discover";
    public static final String CRAFT = "craft";
    /** A sheep dyed a colour: {@code dye:sheep/red}. */
    public static final String DYE = "dye";
    /** The Ender Dragon brought back after its first defeat: {@code respawn:ender_dragon}. */
    public static final String RESPAWN = "respawn";
    /** An item held at least once; stays at 1. */
    public static final String FIND = "find";
    /** Brushing started on a suspicious block whose loot was not rolled yet. */
    public static final String BRUSH = "brush";
    /** A food finished eating: {@code eat:cooked_beef}, {@code eat:wayfarer:dish/miners_stew}. */
    public static final String EAT = "eat";
    /** A forage ingredient found by breaking a plant: {@code forage:wild_rice}. */
    public static final String FORAGE = "forage";

    /**
     * Blocks whose fully grown break is also a harvest. Melons and pumpkins only exist grown; stems and
     * right-click crops (sweet berries) are left out.
     */
    static final java.util.Set<String> CROPS = java.util.Set.of("wheat", "carrots", "potatoes", "beetroots",
            "nether_wart", "cocoa", "torchflower_crop", "pitcher_crop", "melon", "pumpkin");

    private Activity() {
    }

    public static String key(String verb, String namespace, String path) {
        return verb + ":" + target(namespace, path);
    }

    /** "husk" for minecraft:husk, "incendium:inferno_knight" for a datapack type. */
    public static String target(String namespace, String path) {
        return "minecraft".equals(namespace) ? path : namespace + ":" + path;
    }
}
