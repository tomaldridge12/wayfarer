package dev.wayfarer.objectives;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;

/** The server's own tags, records and particles. */
final class PaperObjectiveVocabulary implements ObjectiveVocabulary {
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

    @Override
    public Set<String> group(String group) {
        Set<String> out = new LinkedHashSet<>();
        if (group.equals("music_discs")) {
            for (Material m : Material.values()) {
                if (!m.isLegacy() && m.isItem() && m.asItemType() != null
                        && m.asItemType().hasDefaultData(DataComponentTypes.JUKEBOX_PLAYABLE)) {
                    out.add(m.getKey().getKey());
                }
            }
        }
        return out;
    }

    @Override
    public boolean isItem(String key) {
        Material m = Material.matchMaterial(key);
        return m != null && m.isItem();
    }

    @Override
    public boolean isMob(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        org.bukkit.entity.EntityType type = k == null ? null : org.bukkit.Registry.ENTITY_TYPE.get(k);
        Class<?> cls = type == null ? null : type.getEntityClass();
        // Peaceful living mobs only: a hostile pet would attack its owner's friends.
        return cls != null && org.bukkit.entity.LivingEntity.class.isAssignableFrom(cls) && !org.bukkit.entity.Enemy.class.isAssignableFrom(cls)
                && !org.bukkit.entity.Player.class.isAssignableFrom(cls);
    }

    @Override
    public boolean isParticle(String key) {
        try {
            Particle.valueOf(key.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
