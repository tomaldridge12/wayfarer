package dev.wayfarer.objectives;

import java.util.Set;

/** What the server knows by name, so objectives.yml can be checked and expanded without server types. */
public interface ObjectiveVocabulary {
    /** The keys in a block or item tag ({@code minecraft:hoes}), empty when unknown. */
    Set<String> tag(String tag);

    /** A named group of items Wayfarer works out itself ({@code music_discs}: every item a jukebox plays). */
    Set<String> group(String group);

    boolean isParticle(String key);

    /** A vanilla item such as {@code carved_pumpkin}; tests accept any. */
    default boolean isItem(String key) {
        return true;
    }

    /** A hat ability the curios module defines under hat-abilities; tests accept any. */
    default boolean isHatAbility(String id) {
        return true;
    }

    /** A peaceful living mob type such as {@code sheep}; tests accept any. */
    default boolean isMob(String key) {
        return true;
    }
}
