package dev.wayfarer.foods;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Builds forage items (ADR 0004): their own model, the shared tooltip and the Wayfarer id {@code forage/<id>}. They are
 * built on a brick, which cannot be eaten, placed or planted; the recipe guard keeps them out of vanilla recipes.
 */
final class ForageItems {
    static final String KIND = "forage";
    static final Material BASE = Material.BRICK;

    private final ItemTagService tags;
    private final Supplier<List<ForageDefinition>> forage;

    ForageItems(ItemTagService tags, Supplier<List<ForageDefinition>> forage) {
        this.tags = tags;
        this.forage = forage;
    }

    Optional<ItemStack> make(String id) {
        return forage.get().stream().filter(d -> d.id().equals(id)).findFirst().map(d -> build(d, 1));
    }

    ItemStack build(ForageDefinition d, int amount) {
        ItemStack item = ItemStack.of(BASE, amount);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", d.model()));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name(d.name(), d.colour()));
        item.lore(ItemLook.lore("Forage · Ingredient", d.description(), d.colour(), d.flavour(), List.of("For cooking")));
        tags.writeId(item, KIND, d.id());
        return item;
    }

    /** The forage id of an item, or empty. */
    Optional<String> idOf(ItemStack item) {
        String id = tags.readId(item);
        return id != null && id.startsWith(KIND + "/") ? Optional.of(id.substring(KIND.length() + 1)) : Optional.empty();
    }
}
