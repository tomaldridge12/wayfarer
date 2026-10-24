package dev.wayfarer.marks;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The Shepherd and Angler marks: marked shears shear the whole flock around the sheep you shear; a marked rod gets
 * bites sooner and, at higher ranks, sometimes reels in fishing treasure instead.
 */
final class FlockAndRodMarks implements Listener {
    private final Supplier<MarkRules> rules;
    private final MarkItems items;
    private final BiConsumer<UUID, String> record;
    private final Logger log;
    private boolean treasureWarned;

    /** @param record counts one activity for a player (each extra sheep is a shear) */
    FlockAndRodMarks(Supplier<MarkRules> rules, MarkItems items, BiConsumer<UUID, String> record, Logger log) {
        this.rules = rules;
        this.items = items;
        this.record = record;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onShear(PlayerShearEntityEvent event) {
        if (!(event.getEntity() instanceof Sheep first)) {
            return;
        }
        Player player = event.getPlayer();
        Optional<MarkRules.OnTool> mark = items.markOf(event.getItem()).filter(m -> m.mark().equals("shepherd"));
        Optional<MarkDefinition> def = rules.get().mark("shepherd");
        if (mark.isEmpty() || def.isEmpty()) {
            return;
        }
        MarkDefinition.Rank rank = def.get().rank(mark.get().rank());
        extraWool(first, rank.chance());
        int r = rank.radius();
        Collection<Sheep> flock = first.getLocation().getNearbyEntitiesByType(Sheep.class, r, r, r);
        for (Sheep sheep : flock) {
            if (sheep == first || !sheep.isAdult() || !sheep.readyToBeSheared()) {
                continue;
            }
            ItemStack shears = player.getInventory().getItem(event.getHand());
            if (wouldBreak(shears)) {
                return;
            }
            sheep.shear(Sound.Source.PLAYER);
            extraWool(sheep, rank.chance());
            record.accept(player.getUniqueId(), "shear:sheep");
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.damageItemStack(event.getHand(), 1);
            }
        }
    }

    private static void extraWool(Sheep sheep, double chance) {
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
            Material wool = Material.matchMaterial((sheep.getColor() == null ? "white" : sheep.getColor().name().toLowerCase(java.util.Locale.ROOT)) + "_wool");
            if (wool != null) {
                sheep.getWorld().dropItemNaturally(sheep.getLocation(), ItemStack.of(wool));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onFish(PlayerFishEvent event) {
        Optional<MarkRules.OnTool> mark = rod(event);
        Optional<MarkDefinition> def = rules.get().mark("angler");
        if (mark.isEmpty() || def.isEmpty()) {
            return;
        }
        MarkDefinition.Rank rank = def.get().rank(mark.get().rank());
        FishHook hook = event.getHook();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            hook.setMinWaitTime(Math.max(1, (int) Math.round(hook.getMinWaitTime() * rank.biteWait())));
            hook.setMaxWaitTime(Math.max(hook.getMinWaitTime(), (int) Math.round(hook.getMaxWaitTime() * rank.biteWait())));
        } else if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caught
                && rank.chance() > 0 && ThreadLocalRandom.current().nextDouble() < rank.chance()) {
            treasure(hook, event.getPlayer()).ifPresent(caught::setItemStack);
        }
    }

    private Optional<MarkRules.OnTool> rod(PlayerFishEvent event) {
        EquipmentSlot hand = event.getHand();
        Player player = event.getPlayer();
        ItemStack rod = hand == null ? player.getInventory().getItemInMainHand() : player.getInventory().getItem(hand);
        return items.markOf(rod).filter(m -> m.mark().equals("angler"));
    }

    /** One roll of vanilla's fishing treasure; empty (keeping the real catch) if it cannot be made. */
    private Optional<ItemStack> treasure(FishHook hook, Player player) {
        return treasure();
    }

    /**
     * Vanilla's fishing treasure pool, rolled here because the server's loot API cannot pass the rod the table needs:
     * one of six, equally likely; the bow and rod enchanted at level 30 and worn down to a quarter or less, the book
     * enchanted at level 30. Also used once at start to prove it rolls.
     */
    Optional<ItemStack> treasure() {
        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Material pick = TREASURE.get(random.nextInt(TREASURE.size()));
            ItemStack item = ItemStack.of(pick == Material.ENCHANTED_BOOK ? Material.BOOK : pick);
            if (pick == Material.BOW || pick == Material.FISHING_ROD || pick == Material.ENCHANTED_BOOK) {
                item = org.bukkit.Bukkit.getItemFactory().enchantWithLevels(item, 30, true, random);
            }
            if (pick == Material.BOW || pick == Material.FISHING_ROD) {
                int max = pick.getMaxDurability();
                item.setData(DataComponentTypes.DAMAGE, max - 1 - random.nextInt(Math.max(1, max / 4)));
            }
            return Optional.of(item);
        } catch (RuntimeException e) {
            if (!treasureWarned) {
                treasureWarned = true;
                log.warning("[marks] Fishing treasure could not be made (" + e + "); Angler treasure is off");
            }
            return Optional.empty();
        }
    }

    private static final List<Material> TREASURE = List.of(Material.BOW, Material.ENCHANTED_BOOK, Material.FISHING_ROD,
            Material.NAME_TAG, Material.NAUTILUS_SHELL, Material.SADDLE);

    private static boolean wouldBreak(ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return true;
        }
        if (tool.hasData(DataComponentTypes.UNBREAKABLE)) {
            return false;
        }
        Integer max = tool.getData(DataComponentTypes.MAX_DAMAGE);
        int damage = tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        return max != null && damage + 1 >= max;
    }
}
