package dev.wayfarer.foods;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.DishGuide;
import dev.wayfarer.core.FishWaters;
import dev.wayfarer.core.ItemCatalogue;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.PlayerStore;
import dev.wayfarer.core.RecipeGuard;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.TagKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Foods: forage, dishes and the Cookbook (`.scratch/foods/spec.md`). The counters `eat:` and `forage:` are recorded by
 * core's progression listener from the start; this module grows the rest on top of them. Main thread.
 */
public final class FoodsModule implements Module {
    private final JavaPlugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private volatile List<ForageDefinition> forage = List.of();
    private volatile List<DishDefinition> dishes = List.of();
    private DishRecipes recipes;
    private PlayerStore<ForageData> store;
    private BukkitTask saveTask;
    private BukkitTask discoveryTask;

    public FoodsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "foods";
    }

    @Override
    public void enable(Core core) throws Exception {
        FoodsConfigParser.Result r = FoodsConfigParser.parse(core.config().load(id()),
                key -> {
                    Material m = Material.matchMaterial(key);
                    return m != null && m.isBlock();
                }, FoodsModule::knownBiome);
        r.problems().forEach(p -> core.logger().warning("[foods] " + p));
        forage = r.forage();
        Map<String, Object> config = core.config().load(id());
        DishConfigParser.Result dr = DishConfigParser.parse(config, new Vocabulary(core));
        dr.problems().forEach(p -> core.logger().warning("[foods] " + p));
        dishes = dr.dishes();
        ForageItems items = new ForageItems(core.itemTags(), () -> forage);
        DishItems dishItems = new DishItems(core.itemTags(), () -> dishes);
        recipes = new DishRecipes(plugin.getServer(), () -> dishes, dishItems, core.itemTags(), () -> core.services().get(FishWaters.class));
        store = new PlayerStore<>(plugin.getDataFolder().toPath().resolve("data").resolve(id()), ForageData.VERSION,
                new ForageData.Codec(), core.logger(), uuid -> plugin.getServer().getPlayer(uuid) != null);
        plugin.getServer().getOnlinePlayers().forEach(p -> store.load(p.getUniqueId()));
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, store::flush, 1200, 1200);
        listen(new RecipeGuard(i -> items.idOf(i).isPresent() || dishItems.idOf(i).isPresent()));
        listen(recipes);
        DishDiscoveryListener discovery = new DishDiscoveryListener(() -> dishes, core.itemTags());
        listen(discovery);
        discoveryTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(discovery::scan), 200, 200);
        listen(new Forage(core, () -> forage, items, store));
        listen(new Joins());
        core.itemMakers().register(id(), ForageItems.KIND, items::make);
        core.itemMakers().register(id(), DishItems.KIND, dishItems::make);
        core.itemIds().register(id(), ForageItems.KIND, () -> forage.stream().map(ForageDefinition::id).toList());
        core.itemIds().register(id(), DishItems.KIND, () -> dishes.stream().map(DishDefinition::id).toList());
        core.services().register(id(), DishGuide.class, dish -> dishes.stream().filter(d -> d.id().equals(dish)).findFirst()
                .map(d -> new DishGuide.Info(d.family(), d.hint())));
        // Collections is not up yet, so a named species cannot be checked now; say so once every module has started.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            List<String> known = core.itemIds().ids("fish");
            for (DishDefinition d : dishes) {
                for (DishDefinition.Ingredient ing : d.recipe().ingredients()) {
                    if (ing.kind() == DishDefinition.Kind.FISH && !Set.of("any", "fresh", "salt").contains(ing.what())
                            && !known.contains(ing.what())) {
                        core.logger().warning("[foods] dishes." + d.id() + " asks for fish '" + ing.what() + "', which no species has; "
                                + "it can never be crafted");
                    }
                }
            }
        });
        core.progression().registerRewardType(id(), DishItems.KIND, new DishReward(dishItems));
        core.catalogue().add(id(), "forage", Component.text("Forage", NamedTextColor.WHITE), Material.SWEET_BERRIES,
                ItemCatalogue.madeFrom(core.itemIds(), core.itemMakers(), ForageItems.KIND));
        core.catalogue().add(id(), "dishes", Component.text("Dishes", NamedTextColor.WHITE), Material.MUSHROOM_STEW,
                ItemCatalogue.madeFrom(core.itemIds(), core.itemMakers(), DishItems.KIND));
        int registered = recipes.register();
        core.logger().info("Foods loaded: " + dishes.size() + " dishes, " + forage.size() + " forage ingredients; "
                + (r.problems().size() + dr.problems().size()) + " problems; " + registered + " recipes registered; eat and forage counters recording");
    }

    @Override
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (store != null) {
            store.close();
        }
        if (discoveryTask != null) {
            discoveryTask.cancel();
        }
        if (recipes != null) {
            recipes.unregister();
        }
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    /** A biome key or a #tag this server's registry has. */
    private static boolean knownBiome(String entry) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        if (entry.startsWith("#")) {
            NamespacedKey tag = NamespacedKey.fromString(entry.substring(1));
            return tag != null && registry.hasTag(TagKey.create(RegistryKey.BIOME, tag));
        }
        NamespacedKey key = NamespacedKey.fromString(entry);
        return key != null && registry.get(key) != null;
    }

    /** What this server has, for the dish parser. */
    private final class Vocabulary implements DishConfigParser.Vocabulary {
        private final Core core;

        Vocabulary(Core core) {
            this.core = core;
        }

        @Override
        public boolean isFoodItem(String name) {
            Material m = Material.matchMaterial(name);
            return m != null && m.isItem() && ItemStack.of(m).hasData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD);
        }

        @Override
        public boolean isItem(String name) {
            Material m = Material.matchMaterial(name);
            return m != null && m.isItem();
        }

        @Override
        public boolean isEffect(String name) {
            return org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(name)) != null;
        }

        @Override
        public boolean isForage(String id) {
            return forage.stream().anyMatch(d -> d.id().equals(id));
        }

        @Override
        public boolean isFishSpecies(String id) {
            return true; // checked once every module is up: collections, which owns the species, starts after foods
        }
    }

    private final class Joins implements Listener {
        @EventHandler
        void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
            try {
                store.load(event.getUniqueId()).join();
            } catch (RuntimeException e) {
                plugin.getLogger().severe("[foods] Could not load data for " + event.getUniqueId() + ": " + e.getMessage());
            }
        }

        @EventHandler
        void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
            store.save(event.getPlayer().getUniqueId());
        }
    }
}
