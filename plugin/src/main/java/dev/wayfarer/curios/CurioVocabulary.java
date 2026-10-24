package dev.wayfarer.curios;

import java.util.Set;

/** What the server knows by name, so curios.yml can be checked without server types. Keys are lower-case. */
public interface CurioVocabulary {
    boolean isPotion(String key);

    boolean isAttribute(String key);

    boolean isBlock(String key);

    boolean isItem(String key);

    boolean isMob(String key);

    /** A damage cause such as {@code fall} or {@code hot_floor}. */
    boolean isDamageCause(String key);

    /** A potion effect cause such as {@code attack}. */
    boolean isPotionCause(String key);

    /** The blocks or items in a tag ({@code minecraft:logs}), empty when unknown. */
    Set<String> tag(String tag);
}
