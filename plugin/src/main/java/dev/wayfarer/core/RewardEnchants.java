package dev.wayfarer.core;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.enchantments.Enchantment;

/**
 * The ExcellentEnchants enchants modules hand out as rewards (relic presets, mark books), and the check that they
 * come from nowhere else: asks ExcellentEnchants how each is distributed and warns about any it still gives out from
 * tables, villagers, mobs or loot, or whose max level is below what a reward gives. Uses reflection on its public API
 * (EnchantRegistry.getById, CustomEnchantment.getDistribution), so the plugin stays optional. Never edits its files.
 * Main thread.
 */
public final class RewardEnchants {
    private final Server server;
    private final Logger log;
    private final Map<String, Map<String, Integer>> byOwner = new LinkedHashMap<>();

    public RewardEnchants(Server server, Logger log) {
        this.server = server;
        this.log = log;
    }

    /** Replaces the owner's reward enchants: namespaced key -> the highest level it gives. */
    public void register(String owner, Map<String, Integer> enchants) {
        Map<String, Integer> excellent = new LinkedHashMap<>();
        enchants.forEach((k, v) -> {
            if (k.startsWith("excellentenchants:")) {
                excellent.put(k, v);
            }
        });
        byOwner.put(owner, excellent);
    }

    public void drop(String owner) {
        byOwner.remove(owner);
    }

    /** Every registered reward enchant at the highest level given. */
    public Map<String, Integer> all() {
        return EnchantExclusivity.merge(byOwner.values());
    }

    /** Logs whether every reward enchant is exclusive to Wayfarer; call after start and after reloads. */
    public void check() {
        org.bukkit.plugin.Plugin excellent = server.getPluginManager().getPlugin("ExcellentEnchants");
        Map<String, Integer> given = all();
        if (excellent == null || given.isEmpty()) {
            return;
        }
        Map<String, EnchantExclusivity.Distribution> reported = new LinkedHashMap<>();
        Map<String, Integer> max = new LinkedHashMap<>();
        try {
            // Paper keeps each plugin's classes apart, so look the API up through ExcellentEnchants' own loader.
            ClassLoader loader = excellent.getClass().getClassLoader();
            Class<?> api = Class.forName("su.nightexpress.excellentenchants.enchantment.EnchantRegistry", true, loader);
            Class<?> custom = Class.forName("su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment", true, loader);
            Method byId = api.getMethod("getById", String.class);
            Method distribution = custom.getMethod("getDistribution");
            for (String key : given.keySet()) {
                Object enchant = byId.invoke(null, key.substring(key.indexOf(':') + 1));
                if (enchant == null) {
                    continue; // the module's own config check reports unknown enchants
                }
                Object d = distribution.invoke(enchant);
                reported.put(key, new EnchantExclusivity.Distribution(flag(d, "isTreasure"), flag(d, "isTradable"),
                        flag(d, "isOnMobSpawnEquipment"), flag(d, "isOnTradedEquipment"), flag(d, "isOnRandomLoot"),
                        flag(d, "isDiscoverable")));
                Enchantment bukkit = Registry.ENCHANTMENT.get(NamespacedKey.fromString(key));
                if (bukkit != null) {
                    max.put(key, bukkit.getMaxLevel());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.info("Could not ask ExcellentEnchants about enchant distribution (" + e
                    + "); see docs/wayfarer-relic-enchants.md to check by hand");
            return;
        }
        Map<String, List<String>> problems = new LinkedHashMap<>(EnchantExclusivity.problems(reported));
        EnchantExclusivity.levelProblems(given, max).forEach((key, fix) ->
                problems.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(fix));
        if (problems.isEmpty()) {
            log.info("All " + reported.size() + " reward enchants are exclusive to Wayfarer");
            return;
        }
        log.warning(problems.size() + " reward enchants can still be found outside Wayfarer or are capped too low. In "
                + "plugins/ExcellentEnchants/enchants/<name>.yml set these, then restart (see docs/wayfarer-relic-enchants.md):");
        problems.forEach((key, fixes) -> log.warning("  " + key + ": " + String.join(", ", fixes)));
    }

    private static boolean flag(Object distribution, String method) throws ReflectiveOperationException {
        return (boolean) distribution.getClass().getMethod(method).invoke(distribution);
    }
}
