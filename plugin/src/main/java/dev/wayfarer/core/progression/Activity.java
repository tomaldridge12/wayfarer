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
