package dev.wayfarer.curios;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;

/** The server's own potions, attributes, blocks, items, mobs, causes and tags. */
final class PaperCurioVocabulary implements CurioVocabulary {
    @Override
    public boolean isPotion(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k != null && Registry.EFFECT.get(k) != null;
    }

    @Override
    public boolean isAttribute(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k != null && Registry.ATTRIBUTE.get(k) != null;
    }

    @Override
    public boolean isBlock(String key) {
        Material m = Material.matchMaterial(key);
        return m != null && m.isBlock();
    }

    @Override
    public boolean isItem(String key) {
        Material m = Material.matchMaterial(key);
        return m != null && m.isItem();
    }

    @Override
    public boolean isMob(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        org.bukkit.entity.EntityType type = k == null ? null : Registry.ENTITY_TYPE.get(k);
        return type != null && type.isAlive();
    }

    @Override
    public boolean isDamageCause(String key) {
        try {
            EntityDamageEvent.DamageCause.valueOf(key.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean isPotionCause(String key) {
        try {
            EntityPotionEffectEvent.Cause.valueOf(key.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
