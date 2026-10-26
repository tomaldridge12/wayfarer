package dev.wayfarer.comforts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keep on death: the hotbar, armour and offhand stay with the player and everything else drops as vanilla does (into
 * a grave, with the graves datapack). Items are kept through the death event's own keep list, which Paper restores on
 * respawn; the hotbar is then put back in its old slots. Curse of Vanishing items are never kept. XP is untouched.
 * Main thread.
 */
final class KeepOnDeath implements Listener {
    /** A dead player's kept hotbar stacks with the slots they were in, until they respawn. */
    private record Kept(List<Integer> slots, List<ItemStack> stacks) {
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ComfortsConfigParser.Settings settings;
    private final Map<UUID, Kept> hotbars = new HashMap<>();

    KeepOnDeath(JavaPlugin plugin, Logger logger, ComfortsConfigParser.Settings settings) {
        this.plugin = plugin;
        this.logger = logger;
        this.settings = settings;
    }

    void clear() {
        hotbars.clear();
    }

    /** HIGH, so plugins that already decided to keep the inventory run first and are respected. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Player player = event.getEntity();
        PlayerInventory inventory = player.getInventory();
        List<Integer> slots = DeathRules.keptSlots(settings.hotbar(), settings.armour(), settings.offhand(),
                slot -> keepable(inventory.getItem(slot)));
        if (slots.isEmpty()) {
            return;
        }
        List<Integer> hotbarSlots = new ArrayList<>();
        List<ItemStack> hotbarStacks = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = inventory.getItem(slot).clone();
            event.getItemsToKeep().add(stack);
            event.getDrops().remove(stack); // Paper: a kept item must leave the drops too, or it duplicates
            if (slot < DeathRules.HOTBAR_SLOTS) {
                hotbarSlots.add(slot);
                hotbarStacks.add(stack);
            }
        }
        if (!hotbarSlots.isEmpty()) {
            hotbars.put(player.getUniqueId(), new Kept(hotbarSlots, hotbarStacks));
        }
        logger.info("[comforts] " + player.getName() + " died and keeps " + slots.size() + (slots.size() == 1 ? " stack" : " stacks"));
    }

    @EventHandler
    void onRespawn(PlayerRespawnEvent event) {
        Kept kept = hotbars.remove(event.getPlayer().getUniqueId());
        if (kept != null) {
            UUID id = event.getPlayer().getUniqueId();
            // After the respawn has put the kept items back
            plugin.getServer().getScheduler().runTask(plugin, () -> arrange(id, kept));
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        hotbars.remove(event.getPlayer().getUniqueId());
    }

    private void arrange(UUID id, Kept kept) {
        Player player = plugin.getServer().getPlayer(id);
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> now = new ArrayList<>(DeathRules.HOTBAR_SLOTS);
        for (int slot = 0; slot < DeathRules.HOTBAR_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            now.add(stack == null || stack.isEmpty() ? null : stack);
        }
        Optional<List<ItemStack>> layout = DeathRules.hotbarLayout(kept.slots(), kept.stacks(), now);
        layout.ifPresent(stacks -> {
            for (int slot = 0; slot < DeathRules.HOTBAR_SLOTS; slot++) {
                inventory.setItem(slot, stacks.get(slot));
            }
        });
    }

    private static boolean keepable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getEnchantmentLevel(Enchantment.VANISHING_CURSE) == 0;
    }
}
