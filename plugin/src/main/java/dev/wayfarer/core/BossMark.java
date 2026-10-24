package dev.wayfarer.core;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The core contract that says "this entity is a boss": which boss, its family, variant, stars, home, and whether it
 * appeared as a structure guardian. The bosses module writes it; any module may read it (banners counts the kill
 * toward the boss family; alloys gives guardian templates).
 */
public record BossMark(String bossId, String family, String variant, int stars, int homeX, int homeY, int homeZ,
        boolean guardian) {
    private static final NamespacedKey KEY = new NamespacedKey("wayfarer", "boss");

    /** A boss that did not appear as a structure guardian. */
    public BossMark(String bossId, String family, String variant, int stars, int homeX, int homeY, int homeZ) {
        this(bossId, family, variant, stars, homeX, homeY, homeZ, false);
    }

    public void write(Entity entity) {
        entity.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, encode());
    }

    String encode() {
        return String.join(";", bossId, family, variant, Integer.toString(stars), homeX + "," + homeY + "," + homeZ,
                guardian ? "guardian" : "roaming");
    }

    public static Optional<BossMark> read(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String text = pdc.get(KEY, PersistentDataType.STRING);
        return text == null ? Optional.empty() : parse(text);
    }

    static Optional<BossMark> parse(String text) {
        String[] parts = text.split(";");
        if (parts.length != 5 && parts.length != 6) { // marks written before the guardian flag have five parts
            return Optional.empty();
        }
        try {
            String[] home = parts[4].split(",");
            return Optional.of(new BossMark(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]),
                    Integer.parseInt(home[0]), Integer.parseInt(home[1]), Integer.parseInt(home[2]),
                    parts.length == 6 && parts[5].equals("guardian")));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
