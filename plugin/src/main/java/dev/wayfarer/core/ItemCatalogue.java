package dev.wayfarer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Every item Wayfarer adds, grouped by kind for the {@link ItemBrowser}: each module registers its categories while
 * enabling, as lists built on demand so a reload shows the new config. Main thread.
 */
public final class ItemCatalogue {
    /**
     * One kind of item: its id, the name and icon on the browser's cover, and the items for a viewer (some, like trophy
     * heads, are made for whoever opens the browser).
     */
    public record Category(String owner, String id, Component label, Material icon, Function<Player, List<ItemStack>> items) {
    }

    private final Map<String, Category> byId = new LinkedHashMap<>();

    /** Adds a category, or replaces the one with this id (keeping its place on the cover). */
    public void add(String owner, String id, Component label, Material icon, Function<Player, List<ItemStack>> items) {
        Category old = byId.get(id);
        if (old != null && !old.owner().equals(owner)) {
            throw new IllegalArgumentException("Item category '" + id + "' is already registered by " + old.owner());
        }
        byId.put(id, new Category(owner, id, label, icon, items));
    }

    /** A category's items from a kind modules register ids and makers for: every id of {@code kind}, made. */
    public static Function<Player, List<ItemStack>> madeFrom(ItemIds ids, ItemMakers<ItemStack> makers, String kind) {
        return viewer -> ids.ids(kind).stream().map(id -> makers.make(kind + ":" + id)).flatMap(java.util.Optional::stream).toList();
    }

    /** The categories in the order modules registered them. */
    public List<Category> categories() {
        return new ArrayList<>(byId.values());
    }

    /** Forgets the owner's categories, for a module that failed to start or was disabled. */
    public void drop(String owner) {
        byId.values().removeIf(c -> c.owner().equals(owner));
    }
}
