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


    @Override
    public boolean isParticle(String name) {
        try {
            org.bukkit.Particle.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean isEffect(String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        return key != null && Registry.MOB_EFFECT.get(key) != null;
    }

    @Override
    public boolean isItem(String name) {
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(name);
        return m != null && m.isItem();
    }

    @Override
    public int enchantMax(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        org.bukkit.enchantments.Enchantment e = k == null ? null : Registry.ENCHANTMENT.get(k);
        return e == null ? -1 : e.getMaxLevel();
    }

    @Override
    public boolean isTrimPattern(String name) {
        NamespacedKey k = NamespacedKey.fromString(name);
        return k != null && Registry.TRIM_PATTERN.get(k) != null;
    }

    @Override
    public boolean isTrimMaterial(String name) {
        NamespacedKey k = NamespacedKey.fromString(name);
        return k != null && Registry.TRIM_MATERIAL.get(k) != null;
    }

    @Override
    public boolean hasExcellentEnchants() {
        return org.bukkit.Bukkit.getPluginManager().getPlugin("ExcellentEnchants") != null;
    }

    @Override
    public java.util.Set<String> mobTypes(boolean hostileOnly) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (EntityType type : Registry.ENTITY_TYPE) {
            Class<?> cls = type.getEntityClass();
            if (type.isAlive() && type != EntityType.PLAYER && cls != null
                    && (!hostileOnly || org.bukkit.entity.Enemy.class.isAssignableFrom(cls))) {
                NamespacedKey key = type.getKey();
                out.add(dev.wayfarer.core.progression.Activity.target(key.getNamespace(), key.getKey()));
            }
        }
        return out;
    }
}
