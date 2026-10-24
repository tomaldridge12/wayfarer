package dev.wayfarer.marks;

import java.util.Set;

/** What the server knows by name, so marks.yml can be checked and tags expanded without server types. */
public interface MarkVocabulary {
    /** A lower-case vanilla item key such as sand. */
    boolean isItem(String key);

    /** An enchantment by namespaced key (excellentenchants:veinminer); false when that plugin is missing. */
    boolean isEnchant(String key);

    /**
     * The keys (without the minecraft: namespace) in a block or item tag such as {@code minecraft:logs}; empty
     * when the server has no such tag.
     */
    Set<String> tag(String tag);
}
