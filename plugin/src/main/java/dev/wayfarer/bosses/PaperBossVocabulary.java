package dev.wayfarer.bosses;

import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

/** The server's own entity types, items and colours. */
final class PaperBossVocabulary implements BossVocabulary {
    @Override
    public boolean isBossEntity(String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        return type != null && type.isSpawnable() && type.getEntityClass() != null
                && Mob.class.isAssignableFrom(type.getEntityClass());
    }

    @Override
    public boolean isItem(String name) {
        Material m = Material.matchMaterial(name);
        return m != null && m.isItem();
    }

    @Override
    public boolean isGlowColour(String name) {
        return NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT)) != null;
    }
}
