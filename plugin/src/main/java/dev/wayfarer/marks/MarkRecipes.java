package dev.wayfarer.marks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;

/**
 * Marking tools at the smithing table: template + tool + the mark's material. One recipe per mark matches on
 * materials only; the prepare step decides the result from the items' tags with {@link MarkRules}. The template is
 * not used up: it goes back into its slot once the result is taken.
 */
final class MarkRecipes implements Listener {
    private static final String PREFIX = "mark_";

    private final Plugin plugin;
    private final Supplier<MarkRules> rules;
    private final MarkItems items;
    private final List<NamespacedKey> registered = new ArrayList<>();

    MarkRecipes(Plugin plugin, Supplier<MarkRules> rules, MarkItems items) {
        this.plugin = plugin;
        this.rules = rules;
        this.items = items;
    }

    /** One recipe per mark; returns how many the server holds. */
    int register() {
        unregister();
        for (MarkDefinition m : rules.get().marks()) {
            List<Material> tools = new ArrayList<>();
            List<Material> materials = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isLegacy()) {
                    String key = material.getKey().getKey();
                    if (m.tool().fits(key)) {
                        tools.add(material);
                    }
                    if (m.materials().contains(key)) {
                        materials.add(material);
                    }
                }
            }
            if (tools.isEmpty() || materials.isEmpty()) {
                continue;
            }
            NamespacedKey key = new NamespacedKey("wayfarer", PREFIX + m.id());
            ItemStack shown = items.template(m.id(), 1).orElse(ItemStack.of(tools.getFirst()));
            plugin.getServer().addRecipe(new SmithingTransformRecipe(key, shown, new RecipeChoice.MaterialChoice(MarkItems.BASE),
                    new RecipeChoice.MaterialChoice(tools), new RecipeChoice.MaterialChoice(materials), false), false);
            registered.add(key);
        }
        if (!plugin.getServer().getOnlinePlayers().isEmpty()) {
            plugin.getServer().updateRecipes();
        }
        return (int) registered.stream().filter(plugin.getServer()::hasRecipe).count();
    }

    void unregister() {
        registered.forEach(k -> plugin.getServer().removeRecipe(k, false));
        registered.clear();
    }

    private static boolean ours(SmithingInventory inv) {
        return inv.getRecipe() instanceof SmithingTransformRecipe r && r.getKey().getNamespace().equals("wayfarer")
                && r.getKey().getKey().startsWith(PREFIX);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        if (!ours(inv)) {
            return;
        }
        ItemStack tool = inv.getInputEquipment();
        ItemStack material = inv.getInputMineral();
        Optional<MarkRules.OnTool> template = items.templateOf(inv.getInputTemplate());
        if (tool == null || tool.isEmpty() || material == null || material.isEmpty() || template.isEmpty()) {
            event.setResult(null);
            return;
        }
        event.setResult(rules.get().smith(template.get(), tool.getType().getKey().getKey(), items.markOf(tool).orElse(null),
                material.getType().getKey().getKey()).flatMap(mark -> items.marked(tool, mark)).orElse(null));
    }

    /** The grid's repair-by-combining makes a fresh tool, which would lose the mark: marked tools are never combined there. */
    @EventHandler(priority = EventPriority.HIGH)
    void onCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (!dev.wayfarer.core.RecipeGuard.ours(event.getRecipe())) {
            for (ItemStack s : event.getInventory().getMatrix()) {
                if (items.markOf(s).isPresent()) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
        }
    }

    /** Vanilla smithing uses the template up; a mark template stays, so it goes back once the result is taken. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onTake(SmithItemEvent event) {
        SmithingInventory inv = event.getInventory();
        if (!ours(inv) || items.templateOf(inv.getInputTemplate()).isEmpty()) {
            return;
        }
        ItemStack template = inv.getInputTemplate().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack now = inv.getInputTemplate();
            if (now == null || now.isEmpty()) {
                inv.setInputTemplate(template);
            } else if (now.isSimilar(template) && now.getAmount() < template.getAmount()) {
                now.setAmount(template.getAmount());
            }
        });
    }
}
