package dev.wayfarer.alloys;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

/**
 * Anvils: alloy gear repairs with its own ingot (bronze with copper too), never with vanilla material or by
 * combining with another alloy or plain gear; and endless repair caps every anvil's cost instead of "Too Expensive!".
 */
final class AlloyAnvil implements Listener {
    /** High enough that vanilla never empties the result for cost; the real cap is applied in the prepare step. */
    private static final int NO_LIMIT = 1_000_000;

    private final Supplier<AlloyRules> rules;
    private final AlloyItems items;

    AlloyAnvil(Supplier<AlloyRules> rules, AlloyItems items) {
        this.rules = rules;
        this.items = items;
    }

    @EventHandler
    void onOpen(InventoryOpenEvent event) {
        if (event.getView() instanceof AnvilView view && rules.get().anvil().endless()) {
            view.setMaximumRepairCost(NO_LIMIT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onPrepare(PrepareAnvilEvent event) {
        AnvilView view = event.getView();
        ItemStack left = view.getTopInventory().getFirstItem();
        ItemStack right = view.getTopInventory().getSecondItem();
        if (left == null || left.isEmpty()) {
            return;
        }
        Optional<AlloyRules.Gear> gear = items.gearOf(left);
        boolean rightEmpty = right == null || right.isEmpty();
        if (gear.isEmpty()) {
            if (!rightEmpty && items.gearOf(right).isPresent()) {
                event.setResult(null); // plain gear never takes an alloy's durability or stats
            }
            return;
        }
        if (rightEmpty || right.getType() == Material.ENCHANTED_BOOK) {
            return; // renaming and books work as vanilla
        }
        Optional<AlloyRules.Gear> other = items.gearOf(right);
        if (other.isPresent() || right.getType() == left.getType()) {
            if (!other.equals(gear)) {
                event.setResult(null); // only the same alloy and shape combine
            }
            return;
        }
        String ingot = items.ingotAlloy(right).orElse(null);
        String vanilla = items.tag(right).isPresent() ? null : right.getType().getKey().getKey();
        OptionalDouble share = rules.get().repairShare(gear.get().alloy(), ingot, vanilla);
        if (share.isEmpty()) {
            event.setResult(null);
            return;
        }
        Integer max = left.getData(DataComponentTypes.MAX_DAMAGE);
        int damage = left.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        if (max == null || damage <= 0) {
            event.setResult(null);
            return;
        }
        AlloyRules.RepairPlan plan = AlloyRules.repair(max, damage, right.getAmount(), share.getAsDouble());
        ItemStack result = left.clone();
        result.setData(DataComponentTypes.DAMAGE, plan.damageLeft());
        String rename = view.getRenameText();
        boolean renamed = rename != null && !rename.isBlank() && !rename.equals(plainName(left));
        if (renamed) {
            result.setData(DataComponentTypes.CUSTOM_NAME, Component.text(rename));
        }
        int prior = left.getDataOrDefault(DataComponentTypes.REPAIR_COST, 0);
        result.setData(DataComponentTypes.REPAIR_COST, prior * 2 + 1);
        event.setResult(result);
        view.setRepairItemCountCost(plan.itemsUsed());
        view.setRepairCost(AlloyRules.repairCost(prior, plan.itemsUsed(), renamed));
    }

    /** Endless repair, for every anvil result: the cost stops at the cap. */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onCost(PrepareAnvilEvent event) {
        AnvilView view = event.getView();
        if (event.getResult() != null && !event.getResult().isEmpty()) {
            if (rules.get().anvil().endless()) {
                view.setMaximumRepairCost(NO_LIMIT);
            }
            view.setRepairCost(rules.get().anvilCost(view.getRepairCost()));
        }
    }

    private static String plainName(ItemStack item) {
        Component name = item.getData(DataComponentTypes.CUSTOM_NAME);
        return name == null ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
    }
}
