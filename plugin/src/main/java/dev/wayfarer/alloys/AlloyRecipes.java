package dev.wayfarer.alloys;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;

/**
 * The smithing upgrades (template + gear + ingot) and template duplication (template + 7 ingots + theme block -> 2
 * templates). Recipes match on base materials only; the prepare steps decide the result from the items' tags with
 * {@link AlloyRules}, and give nothing for any wrong combination.
 */
final class AlloyRecipes implements Listener {
    private static final String UPGRADE = "alloy_upgrade_";
    private static final String DUPLICATE = "alloy_template_";

    private final Server server;
    private final Supplier<AlloyRules> rules;
    private final AlloyItems items;
    private final List<NamespacedKey> registered = new ArrayList<>();

    AlloyRecipes(Server server, Supplier<AlloyRules> rules, AlloyItems items) {
        this.server = server;
        this.rules = rules;
        this.items = items;
    }

    /** Registers one upgrade recipe per shape and one duplication recipe per alloy; returns both counts and how many the server holds. */
    int[] register() {
        unregister();
        int upgrades = 0;
        for (GearShape shape : GearShape.values()) {
            List<Material> bases = new ArrayList<>();
            for (String tier : List.of("iron", "diamond", "netherite")) {
                Optional.ofNullable(Material.matchMaterial(shape.vanillaItem(tier))).ifPresent(bases::add);
            }
            AlloyDefinition shown = rules.get().alloys().isEmpty() ? null : rules.get().alloys().getFirst();
            ItemStack result = shown == null ? ItemStack.of(bases.getFirst())
                    : items.gear(new AlloyRules.Gear(shown.id(), shape)).orElse(ItemStack.of(bases.getFirst()));
            NamespacedKey key = new NamespacedKey("wayfarer", UPGRADE + shape.id());
            server.addRecipe(new SmithingTransformRecipe(key, result, new RecipeChoice.MaterialChoice(AlloyItems.BASE),
                    new RecipeChoice.MaterialChoice(bases), new RecipeChoice.MaterialChoice(AlloyItems.BASE), false), false);
            registered.add(key);
            upgrades++;
        }
        int duplicates = 0;
        for (AlloyDefinition a : rules.get().alloys()) {
            Material block = Material.matchMaterial(a.template().duplicateBlock());
            if (block == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey("wayfarer", DUPLICATE + a.id());
            ShapedRecipe recipe = new ShapedRecipe(key, items.template(a, 2));
            recipe.shape("ITI", "IBI", "III");
            recipe.setIngredient('I', new RecipeChoice.MaterialChoice(AlloyItems.BASE));
            recipe.setIngredient('T', new RecipeChoice.MaterialChoice(AlloyItems.BASE));
            recipe.setIngredient('B', block);
            server.addRecipe(recipe, false);
            registered.add(key);
            duplicates++;
        }
        if (!server.getOnlinePlayers().isEmpty()) {
            server.updateRecipes();
        }
        int live = (int) registered.stream().filter(server::hasRecipe).count();
        return new int[] {upgrades, duplicates, live};
    }

    void unregister() {
        registered.forEach(k -> server.removeRecipe(k, false));
        registered.clear();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        if (!(inv.getRecipe() instanceof SmithingTransformRecipe r) || !r.getKey().getNamespace().equals("wayfarer")
                || !r.getKey().getKey().startsWith(UPGRADE)) {
            return;
        }
        ItemStack equipment = inv.getInputEquipment();
        String template = items.templateAlloy(inv.getInputTemplate()).orElse(null);
        String ingot = items.ingotAlloy(inv.getInputMineral()).orElse(null);
        if (equipment == null || equipment.isEmpty()) {
            event.setResult(null);
            return;
        }
        Optional<AlloyRules.Gear> gear = items.gearOf(equipment);
        AlloyRules.SmithBase base = gear.isPresent() ? AlloyRules.SmithBase.alloy(gear.get())
                : items.tag(equipment).isPresent() ? null : AlloyRules.SmithBase.vanilla(equipment.getType().getKey().getKey());
        event.setResult(rules.get().smith(template, base, ingot).flatMap(g -> items.convert(equipment, g)).orElse(null));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    void onCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof ShapedRecipe shaped) || !shaped.getKey().getNamespace().equals("wayfarer")
                || !shaped.getKey().getKey().startsWith(DUPLICATE)) {
            return;
        }
        String alloy = shaped.getKey().getKey().substring(DUPLICATE.length());
        ItemStack[] m = event.getInventory().getMatrix();
        boolean ok = m.length == 9 && items.templateAlloy(m[1]).filter(alloy::equals).isPresent();
        for (int i : new int[] {0, 2, 3, 5, 6, 7, 8}) {
            ok &= m.length == 9 && items.ingotAlloy(m[i]).filter(alloy::equals).isPresent();
        }
        event.getInventory().setResult(ok ? rules.get().alloy(alloy).map(a -> items.template(a, 2)).orElse(null) : null);
    }
}
