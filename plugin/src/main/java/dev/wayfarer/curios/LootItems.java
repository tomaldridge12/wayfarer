package dev.wayfarer.curios;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemMakers;
import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Builds special gear (on alloy gear through core's item makers, or vanilla) and supplies, tagged with the Wayfarer
 * ids {@code gear/<id>} and {@code supply/<id>}. Gear names use the custom name, which alloy refreshes leave alone.
 */
final class LootItems {
    static final String GEAR = "gear";
    static final String SUPPLY = "supply";
    /** Supplies are built on this: no use of its own, kept out of recipes by the guard. */
    static final Material SUPPLY_BASE = Material.BRICK;
    static final int SUPPLY_STACK = 16;

    /** The current gear and supply definitions. */
    record Loot(Map<String, GearPreset> gear, Map<String, SupplyDefinition> supplies) {
        static Loot empty() {
            return new Loot(Map.of(), Map.of());
        }
    }

    private final ItemTagService tags;
    private final ItemMakers<ItemStack> makers;
    private final Supplier<Loot> loot;

    LootItems(ItemTagService tags, ItemMakers<ItemStack> makers, Supplier<Loot> loot) {
        this.tags = tags;
        this.makers = makers;
        this.loot = loot;
    }

    Optional<ItemStack> gear(String id) {
        return Optional.ofNullable(loot.get().gear().get(id)).map(this::build);
    }

    Optional<ItemStack> supply(String id, int amount) {
        return Optional.ofNullable(loot.get().supplies().get(id)).map(s -> build(s, amount));
    }

    ItemStack build(GearPreset g) {
        ItemStack item = g.onAlloy() ? makers.make(g.base()).orElse(null) : null;
        if (item == null) {
            item = ItemStack.of(Material.matchMaterial(g.fallback()));
        }
        item.setData(DataComponentTypes.CUSTOM_NAME, ItemLook.name(g.name(), g.colour()));
        if (g.model() != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", g.model()));
        }
        if (g.unbreakable()) {
            item.setData(DataComponentTypes.UNBREAKABLE);
        }
        item.editMeta(meta -> {
            g.enchants().forEach((key, level) -> {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.fromString(key));
                if (e != null) {
                    meta.addEnchant(e, level, true); // a preset may go past what a table would offer
                }
            });
        });
        List<Component> lore = new ArrayList<>(ItemLook.lore("Special Gear", List.of(), g.colour(), g.flavour(), List.of()));
        List<Component> old = item.lore();
        if (old != null && !old.isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(old);
        }
        item.lore(lore);
        tags.writeId(item, GEAR, g.id());
        return item;
    }

    ItemStack build(SupplyDefinition s, int amount) {
        ItemStack item = ItemStack.of(SUPPLY_BASE, Math.max(1, Math.min(SUPPLY_STACK, amount)));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", s.model()));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name(s.name(), s.colour()));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, SUPPLY_STACK);
        item.lore(ItemLook.lore("Supply", s.description(), s.colour(), s.flavour(), List.of("Right-click to open")));
        tags.writeId(item, SUPPLY, s.id());
        return item;
    }

    /** The supply id of an item, or empty. */
    Optional<String> supplyOf(ItemStack item) {
        String id = tags.readId(item);
        return id != null && id.startsWith(SUPPLY + "/") ? Optional.of(id.substring(SUPPLY.length() + 1)) : Optional.empty();
    }
}
