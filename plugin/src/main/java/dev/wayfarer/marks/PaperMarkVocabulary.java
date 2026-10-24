package dev.wayfarer.marks;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;

/** The server's own items, enchantments and block or item tags. */
final class PaperMarkVocabulary implements MarkVocabulary {
    @Override
    public boolean isItem(String key) {
        Material m = Material.matchMaterial(key);
        return m != null && m.isItem();
    }

    @Override
    public boolean isEnchant(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k != null && Registry.ENCHANTMENT.get(k) != null;
    }

    @Override
    public Set<String> tag(String tag) {
        NamespacedKey key = NamespacedKey.fromString(tag);
        if (key == null) {
            return Set.of();
        }
        Tag<Material> found = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
        if (found == null) {
            found = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        }
        Set<String> out = new LinkedHashSet<>();
        if (found != null) {
            found.getValues().forEach(m -> out.add(m.getKey().getNamespace().equals("minecraft") ? m.getKey().getKey()
                    : m.getKey().toString()));
        }
        return out;
    }
}
