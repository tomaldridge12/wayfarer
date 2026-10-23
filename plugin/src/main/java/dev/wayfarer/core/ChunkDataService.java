package dev.wayfarer.core;

import java.util.Optional;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** One module-owned string per chunk, kept in the chunk's persistent data and saved with the world. */
public final class ChunkDataService {
    private final Plugin plugin;

    public ChunkDataService(Plugin plugin) {
        this.plugin = plugin;
    }

    public Optional<String> read(Chunk chunk, String module) {
        return Optional.ofNullable(chunk.getPersistentDataContainer().get(key(module), PersistentDataType.STRING));
    }

    /** Writes the value, or removes it when null. */
    public void write(Chunk chunk, String module, String value) {
        if (value == null) {
            chunk.getPersistentDataContainer().remove(key(module));
        } else {
            chunk.getPersistentDataContainer().set(key(module), PersistentDataType.STRING, value);
        }
    }

    private NamespacedKey key(String module) {
        return new NamespacedKey(plugin, ItemTags.storageKey(module));
    }
}
