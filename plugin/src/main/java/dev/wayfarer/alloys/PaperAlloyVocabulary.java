package dev.wayfarer.alloys;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/** The server's own items, blocks and attributes. */
final class PaperAlloyVocabulary implements AlloyVocabulary {
    @Override
    public boolean isItem(String key) {
        Material m = Material.matchMaterial(key);
        return m != null && m.isItem();
    }

    @Override
    public boolean isBlock(String key) {
        Material m = Material.matchMaterial(key);
        return m != null && m.isBlock();
    }

    @Override
    public boolean isAttribute(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k != null && Registry.ATTRIBUTE.get(k) != null;
    }
}
