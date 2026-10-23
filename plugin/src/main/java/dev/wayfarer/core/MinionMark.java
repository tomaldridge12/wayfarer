package dev.wayfarer.core;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** Marks a mob summoned by a boss, so other modules can ignore it (e.g. it never counts as a kill). */
public final class MinionMark {
    private static final NamespacedKey KEY = new NamespacedKey("wayfarer", "minion");

    private MinionMark() {
    }

    public static void write(Entity minion, Entity boss) {
        minion.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, boss.getUniqueId().toString());
    }

    public static boolean is(Entity entity) {
        return entity.getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
    }
}
