package dev.wayfarer.core.progression;

import dev.wayfarer.core.BossMark;
import dev.wayfarer.core.MinionMark;
import io.papermc.paper.entity.Leashable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

/** Turns server events into activities with their facts for the progression engine. */
final class ProgressionListener implements Listener {
    private final ProgressionService service;

    ProgressionListener(ProgressionService service) {
        this.service = service;
    }

    @EventHandler
    void onPreLogin(AsyncPlayerPreLoginEvent event) {
        service.loadBlocking(event.getUniqueId());
    }

    @EventHandler
    void onBossDefeated(dev.wayfarer.core.BossDefeatedEvent event) {
        service.defeated(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        service.advancement(event.getPlayer(), event.getAdvancement().getKey());
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        service.quit(event.getPlayer().getUniqueId());
    }

    /** HIGHEST, not MONITOR: a due pity drop is added to this death's drops. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        // Bosses are counted from their defeat (every damager); boss minions never count.
        if (victim instanceof Player || MinionMark.is(victim) || BossMark.read(victim).isPresent()) {
            return;
        }
        Entity cause = event.getDamageSource().getCausingEntity(); // a projectile's cause is its shooter
        if (!(cause instanceof Player killer)) {
            return;
        }
        ItemStack held = victim.getEquipment() == null ? null : victim.getEquipment().getItemInMainHand();
        Set<String> dropped = new HashSet<>();
        event.getDrops().forEach(drop -> dropped.add(target(drop.getType())));
        List<String> pity = service.kill(killer, new KillFacts(
                KillFacts.Killer.PLAYER,
                target(victim.getType()),
                victim.getEntitySpawnReason().name(),
                victim instanceof Tameable t && t.isTamed(),
                victim instanceof Leashable l && l.isLeashed(),
                victim.getTicksLived()),
                held == null || held.getType().isAir() ? null : target(held.getType()), dropped);
        for (String item : pity) {
            Material material = Material.matchMaterial(item);
            if (material != null) {
                event.getDrops().add(new ItemStack(material));
            }
        }
    }

    /** Only a player breaking a block fires this; explosions, pistons and the like never count. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean grown = !(block.getBlockData() instanceof org.bukkit.block.data.Ageable crop)
                || crop.getAge() == crop.getMaximumAge();
        service.act(event.getPlayer(), (engine, data) -> engine.mine(data, target(block.getType()), grown));
    }

    /** A Wayfarer item caught (an Almanac fish) counts under its own id: {@code catch:wayfarer:fish/arapaima}. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item item) {
            service.target(item.getItemStack()).ifPresent(target -> {
                service.act(event.getPlayer(), (engine, data) -> engine.record(data, Activity.CATCH + ":" + target));
                service.act(event.getPlayer(), (engine, data) -> engine.find(data, List.of(target)));
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            service.target(event.getItem().getItemStack())
                    .ifPresent(target -> service.act(player, (engine, data) -> engine.find(data, List.of(target))));
        }
    }

    /**
     * Brushing a suspicious block whose loot is not rolled yet: counted once, then modules hear of it (treasures). Vanilla
     * rolls the loot at the first brush tick, after this, so a later brush of the same block never counts again.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBrush(org.bukkit.event.player.PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || block == null
                || event.getItem() == null || event.getItem().getType() != Material.BRUSH
                || !(block.getState(false) instanceof org.bukkit.block.BrushableBlock brushable)
                || !brushable.hasLootTable()) {
            return;
        }
        if (service.act(event.getPlayer(), (engine, data) -> engine.brush(data, target(block.getType()))) != null) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new dev.wayfarer.core.SuspiciousBrushEvent(event.getPlayer(), block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onShear(PlayerShearEntityEvent event) {
        String shorn = Activity.SHEAR + ":" + target(event.getEntity().getType());
        service.act(event.getPlayer(), (engine, data) -> engine.record(data, shorn));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player breeder) {
            String bred = Activity.BREED + ":" + target(event.getEntity().getType());
            service.act(breeder, (engine, data) -> engine.record(data, bred));
        }
    }

    /**
     * Items crafted by hand, counted by what actually arrived: the matching items in the inventory and on the cursor
     * a tick later, against before the click. A click that takes nothing counts nothing; the real result (which a
     * prepare step may have changed) is what is compared.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (!(event.getWhoClicked() instanceof Player player) || result == null || result.isEmpty()
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.NOTHING) {
            return;
        }
        ItemStack like = result.clone();
        String item = service.target(like).orElse(null);
        if (item == null) {
            return;
        }
        int before = held(player, like);
        service.later(() -> {
            int made = held(player, like) - before;
            service.act(player, (engine, data) -> engine.craft(data, item, made));
            if (made > 0) {
                service.act(player, (engine, data) -> engine.find(data, List.of(item)));
            }
        });
    }

    /** How many items like this the player holds: inventory and cursor. */
    private static int held(Player player, ItemStack like) {
        int n = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && s.isSimilar(like)) {
                n += s.getAmount();
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        return n + (cursor.isSimilar(like) ? cursor.getAmount() : 0);
    }

    private static String target(Keyed type) {
        NamespacedKey key = type.getKey();
        return Activity.target(key.getNamespace(), key.getKey());
    }
}
