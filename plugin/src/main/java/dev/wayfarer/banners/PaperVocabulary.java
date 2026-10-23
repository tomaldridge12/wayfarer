package dev.wayfarer.banners;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.DyeColor;
import org.bukkit.entity.EntityType;

/** The server's own mob types, banner patterns and dye colours. */
final class PaperVocabulary implements Vocabulary {
    @Override
    public boolean isMob(String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        return type != null && type.isAlive();
    }

    @Override
    public boolean isPattern(String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        return key != null && Registry.BANNER_PATTERN.get(key) != null;
    }

    @Override
    public boolean isColour(String name) {
        try {
            DyeColor.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
