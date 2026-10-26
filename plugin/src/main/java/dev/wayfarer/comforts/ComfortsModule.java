package dev.wayfarer.comforts;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Comforts: small vanilla rule tweaks, each with its own switch. So far: rotten flesh crafts into leather (plain vanilla
 * items either way, so no item tagging and no recipe guard) and the hotbar, armour and offhand survive death. Main thread.
 */
public final class ComfortsModule implements Module, Listener {
    private static final NamespacedKey LEATHER_KEY = new NamespacedKey("wayfarer", "leather_from_flesh");

    private final JavaPlugin plugin;
    private boolean recipeRegistered;
    private KeepOnDeath keep;

    public ComfortsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "comforts";
    }

    @Override
    public void enable(Core core) throws IOException {
        ComfortsConfigParser.Result r = ComfortsConfigParser.parse(core.config().load(id()));
        r.problems().forEach(p -> core.logger().warning("[comforts] " + p));
        ComfortsConfigParser.Settings s = r.settings();
        if (s.leatherFromFlesh()) {
            ShapelessRecipe recipe = new ShapelessRecipe(LEATHER_KEY, new ItemStack(Material.LEATHER, s.leather()));
            for (int i = 0; i < s.flesh(); i++) {
                recipe.addIngredient(new RecipeChoice.MaterialChoice(Material.ROTTEN_FLESH));
            }
            recipe.setCategory(CraftingBookCategory.MISC);
            plugin.getServer().removeRecipe(LEATHER_KEY); // a leftover from a reload
            recipeRegistered = plugin.getServer().addRecipe(recipe);
            if (recipeRegistered) {
                plugin.getServer().getOnlinePlayers().forEach(p -> p.discoverRecipe(LEATHER_KEY));
            } else {
                core.logger().warning("[comforts] the leather recipe was not registered");
            }
        }
        List<String> kept = new ArrayList<>();
        if (s.keepOnDeath()) {
            if (s.hotbar()) {
                kept.add("hotbar");
            }
            if (s.armour()) {
                kept.add("armour");
            }
            if (s.offhand()) {
                kept.add("offhand");
            }
        }
        if (!kept.isEmpty()) {
            keep = new KeepOnDeath(plugin, core.logger(), s);
            plugin.getServer().getPluginManager().registerEvents(keep, plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        core.logger().info("Comforts loaded: leather from flesh " + (recipeRegistered
                ? "on (" + s.flesh() + " rotten flesh -> " + s.leather() + " leather, recipe registered)" : "off")
                + "; keep on death " + (kept.isEmpty() ? "off" : "on (" + String.join(", ", kept) + ")") + "; "
                + r.problems().size() + " problems");
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (keep != null) {
            HandlerList.unregisterAll(keep);
            keep.clear();
            keep = null;
        }
        if (recipeRegistered) {
            plugin.getServer().removeRecipe(LEATHER_KEY);
            recipeRegistered = false;
        }
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        if (recipeRegistered) {
            event.getPlayer().discoverRecipe(LEATHER_KEY);
        }
    }
}
