package dev.wayfarer.core.progression;

/** Activity keys: {@code verb:target}, the target being a vanilla key without its namespace or a full datapack key. */
public final class Activity {
    public static final String KILL = "kill";

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
