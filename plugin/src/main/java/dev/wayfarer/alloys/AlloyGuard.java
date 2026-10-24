package dev.wayfarer.alloys;

import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingTrimRecipe;

/**
 * Alloy items are never the vanilla item they are built on: a recipe that is not Wayfarer's gives nothing when an
 * alloy item is in it (crafting, the grid's repair-by-combining, crafters, furnaces, vanilla smithing upgrades). Armour trims
 * still work on alloy armour.
 */
final class AlloyGuard implements Listener {
    private final AlloyItems items;

    AlloyGuard(AlloyItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onCraft(PrepareItemCraftEvent event) {
        if (!ours(event.getRecipe()) && any(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onCrafter(CrafterCraftEvent event) {
        if (!ours(event.getRecipe())) {
            var block = event.getBlock().getState();
            if (block instanceof org.bukkit.block.Crafter crafter && any(crafter.getInventory().getContents())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    void onSmithing(PrepareSmithingEvent event) {
        var inv = event.getInventory();
        if (inv.getRecipe() instanceof SmithingTrimRecipe) {
            return;
        }
        if (!ours(inv.getRecipe()) && any(new ItemStack[] {inv.getInputTemplate(), inv.getInputEquipment(), inv.getInputMineral()})) {
            event.setResult(null);
        }
    }

    /** Alloy gear built on iron would otherwise smelt down to nuggets. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onSmelt(org.bukkit.event.inventory.FurnaceSmeltEvent event) {
        if (items.tag(event.getSource()).isPresent()) {
            event.setCancelled(true);
        }
    }

    static boolean ours(Recipe recipe) {
        return recipe instanceof Keyed k && k.getKey().getNamespace().equals("wayfarer");
    }

    private boolean any(ItemStack[] stacks) {
        for (ItemStack s : stacks) {
            if (items.tag(s).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
