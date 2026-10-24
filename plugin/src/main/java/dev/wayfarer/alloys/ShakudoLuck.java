package dev.wayfarer.alloys;

import dev.wayfarer.core.BossMark;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Shakudo, the lucky metal: its tools drop as if Fortune were higher, and kills with its weapons sometimes drop one
 * more of each item. Its armour's luck is a plain attribute.
 */
final class ShakudoLuck implements Listener {
    private final Supplier<AlloyRules> rules;
    private final AlloyItems items;

    ShakudoLuck(Supplier<AlloyRules> rules, AlloyItems items) {
        this.rules = rules;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBlockDrops(BlockDropItemEvent event) {
        BlockState state = event.getBlockState();
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        Optional<AlloyRules.Gear> gear = items.gearOf(tool).filter(g -> g.shape().miningTool());
        if (gear.isEmpty() || state instanceof Container || event.getItems().isEmpty()
                || tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }
        int enchanted = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        int fortune = rules.get().fortune(gear.get().alloy(), enchanted);
        if (fortune == enchanted) {
            return;
        }
        ItemStack lucky = tool.clone();
        lucky.addUnsafeEnchantment(Enchantment.FORTUNE, fortune);
        List<ItemStack> drops = new ArrayList<>(state.getDrops(lucky, event.getPlayer()));
        List<Item> entities = event.getItems();
        for (int i = entities.size() - 1; i >= drops.size(); i--) {
            entities.remove(i);
        }
        for (int i = 0; i < drops.size(); i++) {
            if (i < entities.size()) {
                entities.get(i).setItemStack(drops.get(i));
            } else {
                state.getWorld().dropItemNaturally(state.getLocation().add(0.5, 0.5, 0.5), drops.get(i));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onKill(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        Player killer = mob.getKiller();
        if (killer == null || mob instanceof Player || mob.fromMobSpawner() || BossMark.read(mob).isPresent()) {
            return;
        }
        Optional<AlloyRules.Gear> gear = items.gearOf(killer.getInventory().getItemInMainHand())
                .filter(g -> g.shape() == GearShape.SWORD || g.shape() == GearShape.AXE || g.shape() == GearShape.SPEAR);
        if (gear.isEmpty() || !rules.get().extraLoot(gear.get().alloy(), ThreadLocalRandom.current().nextDouble())) {
            return;
        }
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            // Equipment the mob wore or held is not loot.
            if (drop != null && !drop.isEmpty() && drop.getType().getMaxDurability() == 0 && items.tag(drop).isEmpty()) {
                extra.add(drop.asOne());
            }
        }
        event.getDrops().addAll(extra);
    }
}
