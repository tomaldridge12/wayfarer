package dev.wayfarer.curios;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Builds curio items (ADR 0004): one per stack, their own model, the shared reward tooltip, and the Wayfarer id
 * {@code curio/<id>} that activities and this module read back. Tools carry a cooldown group so the vanilla cooldown
 * overlay shows on the item.
 */
final class CurioItems {
    static final String KIND = "curio";
    /** What curios are built on: no right-click use, and the recipe guard keeps them out of recipes. */
    static final Material BASE = Material.BRICK;

    private final ItemTagService tags;
    private final Supplier<CurioRules> rules;

    CurioItems(ItemTagService tags, Supplier<CurioRules> rules) {
        this.tags = tags;
        this.rules = rules;
    }

    Optional<ItemStack> make(String id) {
        return rules.get().curio(id).map(this::build);
    }

    ItemStack build(CurioDefinition c) {
        ItemStack item = ItemStack.of(BASE);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", c.model()));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name(c.name(), c.colour()));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        if (c.tool()) {
            item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(c.cooldownSeconds())
                    .cooldownGroup(group(c)).build());
        }
        List<String> footers = c.tool() ? List.of("Right-click · rests " + ItemLook.duration(c.cooldownSeconds()) + " after")
                : List.of("Works from anywhere in your pack");
        item.lore(ItemLook.lore("Curio · " + (c.tool() ? "Tool" : "Charm"), c.description(), c.colour(), c.flavour(), footers));
        tags.writeId(item, KIND, c.id());
        return item;
    }

    /** The cooldown group, so every copy of this curio shows the same cooldown. */
    static Key group(CurioDefinition c) {
        return Key.key("wayfarer", "curio_" + c.id());
    }

    /** The curio id of an item, or empty. */
    Optional<String> idOf(ItemStack item) {
        String id = tags.readId(item);
        return id != null && id.startsWith(KIND + "/") ? Optional.of(id.substring(KIND.length() + 1)) : Optional.empty();
    }
}
