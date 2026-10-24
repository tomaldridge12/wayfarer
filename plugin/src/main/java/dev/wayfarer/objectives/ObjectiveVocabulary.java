package dev.wayfarer.objectives;

import java.util.Set;

/** What the server knows by name, so objectives.yml can be checked and expanded without server types. */
public interface ObjectiveVocabulary {
    /** The keys in a block or item tag ({@code minecraft:hoes}), empty when unknown. */
    Set<String> tag(String tag);

    /** A named group of items Wayfarer works out itself ({@code music_discs}: every item a jukebox plays). */
    Set<String> group(String group);

    boolean isParticle(String key);
}
