package dev.wayfarer.core;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Marks items with a module-owned value in their persistent data; invisible and needs no resource pack. */
public final class ItemTagService {
    private final Plugin plugin;

    public ItemTagService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** A second named value for the same module, e.g. a trophy's earner alongside its reward. */
    public void write(ItemStack item, String module, String field, String value) {
        write(item, module + "_" + field, value);
    }

    public Optional<String> read(ItemStack item, String module, String field) {
        return read(item, module + "_" + field);
    }

    public void write(ItemStack item, String module, String value) {
        item.editMeta(meta -> ItemTags.write(holder(meta.getPersistentDataContainer()), module, value));
    }

    public Optional<String> read(ItemStack item, String module) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        return ItemTags.read(holder(meta.getPersistentDataContainer()), module);
    }

    private ItemTags.Holder holder(PersistentDataContainer pdc) {
        return new ItemTags.Holder() {
            @Override
            public String get(String key) {
                return pdc.get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
            }

            @Override
            public void set(String key, String value) {
                pdc.set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
            }
        };
    }
}
