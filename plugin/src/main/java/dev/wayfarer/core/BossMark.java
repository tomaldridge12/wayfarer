package dev.wayfarer.core;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The core contract that says "this entity is a boss": which boss, its family, variant, stars and home. The
 * bosses module writes it; any module may read it (banners uses it to count the kill toward the boss family).
 */
public record BossMark(String bossId, String family, String variant, int stars, int homeX, int homeY, int homeZ) {
    private static final NamespacedKey KEY = new NamespacedKey("wayfarer", "boss");

    public void write(Entity entity) {
        entity.getPersistentDataContainer().set(KEY, PersistentDataType.STRING,
                String.join(";", bossId, family, variant, Integer.toString(stars),
                        homeX + "," + homeY + "," + homeZ));
    }

    public static Optional<BossMark> read(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String text = pdc.get(KEY, PersistentDataType.STRING);
        return text == null ? Optional.empty() : parse(text);
    }

    static Optional<BossMark> parse(String text) {
        String[] parts = text.split(";");
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            String[] home = parts[4].split(",");
            return Optional.of(new BossMark(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]),
                    Integer.parseInt(home[0]), Integer.parseInt(home[1]), Integer.parseInt(home[2])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
