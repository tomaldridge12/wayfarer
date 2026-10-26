package dev.wayfarer.foods;

import dev.wayfarer.core.ItemTagService;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Puts a dish's recipe in the recipe book the first time a player holds an ingredient of it: on pickup, on joining, and
 * on a scan of online players' packs (for items that arrive by chest, trade or crafting). Main thread.
 */
final class DishDiscoveryListener implements Listener {
    private final Supplier<List<DishDefinition>> dishes;
    private final ItemTagService tags;

    DishDiscoveryListener(Supplier<List<DishDefinition>> dishes, ItemTagService tags) {
        this.dishes = dishes;
        this.tags = tags;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            discover(player, event.getItem().getItemStack());
        }
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        scan(event.getPlayer());
    }

    /** Every item in the player's pack. */
    void scan(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            discover(player, stack);
        }
    }

    private void discover(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = tags.readId(stack);
        String forage = id != null && id.startsWith(ForageItems.KIND + "/") ? id.substring(ForageItems.KIND.length() + 1) : null;
        boolean fish = id != null && id.startsWith("fish/");
        if (id != null && forage == null && !fish) {
            return; // some other Wayfarer item (a dish, a curio) is not an ingredient, whatever it is built on
        }
        for (String dish : DishDiscovery.unlocks(dishes.get(), stack.getType().getKey().getKey(), forage, fish)) {
            NamespacedKey key = new NamespacedKey("wayfarer", DishRecipes.PREFIX + dish);
            if (!player.hasDiscoveredRecipe(key)) {
                player.discoverRecipe(key);
            }
        }
    }
}
