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
    private static final String ID = "item_id";
    private final Plugin plugin;

    public ItemTagService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The same tagging for entities and blocks with persistent data (e.g. marking an arrow). */
    public void write(org.bukkit.persistence.PersistentDataHolder holder, String module, String value) {
        ItemTags.write(holder(holder.getPersistentDataContainer()), module, value);
    }

    public Optional<String> read(org.bukkit.persistence.PersistentDataHolder holder, String module) {
        return ItemTags.read(holder(holder.getPersistentDataContainer()), module);
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

    /** Gives an item its Wayfarer id ({@code fish}, {@code arapaima}), which activities count it under (see {@link ItemIds}). */
    public void writeId(ItemStack item, String kind, String id) {
        item.editPersistentDataContainer(pdc -> pdc.set(new NamespacedKey(plugin, ID), PersistentDataType.STRING, kind + "/" + id));
    }

    /** The item's Wayfarer id ({@code fish/arapaima}), or null. Cheap: reads the data without copying the meta. */
    public String readId(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        return item.getPersistentDataContainer().get(new NamespacedKey(plugin, ID), PersistentDataType.STRING);
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
