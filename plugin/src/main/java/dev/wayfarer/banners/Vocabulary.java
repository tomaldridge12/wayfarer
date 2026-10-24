package dev.wayfarer.banners;

/** What the server knows by name; lets the config rules stay free of server types. */
public interface Vocabulary {
    boolean isMob(String name);

    boolean isPattern(String name);

    boolean isColour(String name);

    /** An upper-case particle name such as HAPPY_VILLAGER. */
    boolean isParticle(String name);

    /** A lower-case potion effect key such as luck. */
    boolean isEffect(String name);

    /** A lower-case item name such as iron_axe. */
    default boolean isItem(String name) {
        return true;
    }

    /** An enchant's highest level by namespaced key, or -1 when the server has no such enchant. */
    default int enchantMax(String key) {
        return 10;
    }

    /** Whether the optional ExcellentEnchants plugin is installed. */
    default boolean hasExcellentEnchants() {
        return true;
    }

    default boolean isTrimPattern(String name) {
        return true;
    }

    default boolean isTrimMaterial(String name) {
        return true;
    }

    /** Every living mob type as an activity target ("husk"), or only the hostile ones. */
    default java.util.Set<String> mobTypes(boolean hostileOnly) {
        return java.util.Set.of();
    }
}
