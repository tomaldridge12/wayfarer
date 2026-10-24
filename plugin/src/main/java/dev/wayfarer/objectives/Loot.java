package dev.wayfarer.objectives;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/**
 * Hands out loot keys from reward tables, streaks and goals: tokens and cosmetics go straight onto the player's
 * record, books also unlock their enchant for the shop, and items go into their inventory, or wait in their record
 * until they next join. Main thread.
 */
final class Loot {
    private final Core core;
    private final Supplier<ObjectivesService> service;

    Loot(Core core, Supplier<ObjectivesService> service) {
        this.core = core;
        this.service = service;
    }

    /** Gives every loot key and says what was given ("20 tokens, Tome of Experience"). */
    Component give(UUID id, ObjectivesPlayerData data, List<String> loot, String why) {
        Player online = Bukkit.getPlayer(id);
        List<Component> said = new ArrayList<>();
        for (String one : loot) {
            said.add(name(one));
            if (one.startsWith("tokens:")) {
                service.get().tokens(id, data, Long.parseLong(one.substring(7)), why);
                continue;
            }
            if (one.startsWith("cosmetic:")) {
                data.cosmetics.add(one.substring(9));
                continue;
            }
            if (one.startsWith("enchant:")) {
                unlock(data, one);
            }
            if (online != null) {
                item(one).ifPresentOrElse(item -> online.getInventory().addItem(item).values()
                        .forEach(left -> online.getWorld().dropItem(online.getLocation(), left)),
                        () -> core.logger().warning("[objectives] Loot " + one + " could not be made (is its module on?)"));
            } else {
                data.pending.add(one);
            }
        }
        service.get().store().markDirty(id);
        return ObjectivesService.list(said);
    }

    /** A book's enchant becomes buyable in the shop, up to the highest level earned. */
    static void unlock(ObjectivesPlayerData data, String book) {
        String id = book.substring("enchant:".length());
        int cut = id.lastIndexOf('_');
        if (cut > 0 && id.substring(cut + 1).matches("\\d+")) {
            data.unlockedEnchants.merge(id.substring(0, cut), Integer.parseInt(id.substring(cut + 1)), Math::max);
        }
    }

    /** The item for a loot key, or empty when no module makes it. */
    Optional<ItemStack> item(String text) {
        RewardTables.Loot l = RewardTables.Loot.of(text);
        String what = l.what();
        if (what.startsWith("item:")) {
            Material m = Material.matchMaterial(what.substring(5));
            return m == null ? Optional.empty() : Optional.of(ItemStack.of(m, Math.min(m.getMaxStackSize(), l.amount())));
        }
        if (what.startsWith("enchant:")) {
            String id = what.substring(8);
            int cut = id.lastIndexOf('_');
            Enchantment e = cut <= 0 ? null : Registry.ENCHANTMENT.get(NamespacedKey.fromString(id.substring(0, cut)));
            if (e == null) {
                return Optional.empty();
            }
            ItemStack book = ItemStack.of(Material.ENCHANTED_BOOK);
            book.editMeta(EnchantmentStorageMeta.class, m -> m.addStoredEnchant(e, Integer.parseInt(id.substring(cut + 1)), true));
            return Optional.of(book);
        }
        return core.itemMakers().make(what).map(item -> {
            item.setAmount(Math.min(item.getMaxStackSize(), l.amount()));
            return item;
        });
    }

    /** A loot key's name for messages. */
    Component name(String text) {
        if (text.startsWith("tokens:")) {
            return ItemLook.text(text.substring(7) + " tokens", ObjectivesService.GOLD);
        }
        if (text.startsWith("item:")) {
            RewardTables.Loot l = RewardTables.Loot.of(text);
            String words = l.what().substring(5).replace('_', ' ');
            return ItemLook.text((l.amount() > 1 ? l.amount() + " " : "") + Character.toUpperCase(words.charAt(0)) + words.substring(1),
                    ObjectivesService.PAPER);
        }
        return core.progression().rewardTitle(text);
    }
}
