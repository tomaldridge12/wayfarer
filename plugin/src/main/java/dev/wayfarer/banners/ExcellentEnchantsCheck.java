package dev.wayfarer.banners;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Server;

/**
 * Asks ExcellentEnchants how each reward enchant is distributed and warns about any it still hands out from
 * tables, villagers, mobs or loot. Uses reflection on its public API (EnchantRegistry.getById, CustomEnchantment.getDistribution):
 * the plugin stays optional and its jar is not on any repository this build uses. Never edits its files.
 */
final class ExcellentEnchantsCheck {
    private ExcellentEnchantsCheck() {
    }

    static void run(Server server, BannerSet set, Logger log) {
        org.bukkit.plugin.Plugin excellent = server.getPluginManager().getPlugin("ExcellentEnchants");
        if (excellent == null) {
            if (!set.missingOptionalEnchants().isEmpty()) {
                log.warning("[banners] ExcellentEnchants is not installed; relics are given without its enchants "
                        + set.missingOptionalEnchants());
            }
            return;
        }
        List<String> keys = EnchantExclusivity.rewardEnchants(set);
        if (keys.isEmpty()) {
            return;
        }
        Map<String, EnchantExclusivity.Distribution> reported = new LinkedHashMap<>();
        try {
            // Paper keeps each plugin's classes apart, so look the API up through ExcellentEnchants' own loader.
            ClassLoader loader = excellent.getClass().getClassLoader();
            Class<?> api = Class.forName("su.nightexpress.excellentenchants.enchantment.EnchantRegistry", true, loader);
            Class<?> custom = Class.forName("su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment", true, loader);
            Method byId = api.getMethod("getById", String.class);
            Method distribution = custom.getMethod("getDistribution");
            for (String key : keys) {
                Object enchant = byId.invoke(null, key.substring(key.indexOf(':') + 1));
                if (enchant == null) {
                    continue; // the config check already reported unknown enchants
                }
                Object d = distribution.invoke(enchant);
                reported.put(key, new EnchantExclusivity.Distribution(flag(d, "isTreasure"), flag(d, "isTradable"),
                        flag(d, "isOnMobSpawnEquipment"), flag(d, "isOnTradedEquipment"), flag(d, "isOnRandomLoot"),
                        flag(d, "isDiscoverable")));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.info("[banners] Could not ask ExcellentEnchants about enchant distribution (" + e
                    + "); see docs/wayfarer-relic-enchants.md to check by hand");
            return;
        }
        Map<String, List<String>> problems = EnchantExclusivity.problems(reported);
        if (problems.isEmpty()) {
            log.info("[banners] All " + reported.size() + " relic enchants are exclusive to Wayfarer");
            return;
        }
        log.warning("[banners] " + problems.size() + " relic enchants can still be found outside Wayfarer. In "
                + "plugins/ExcellentEnchants/enchants/<name>.yml set these, then restart (see docs/wayfarer-relic-enchants.md):");
        problems.forEach((key, fixes) -> log.warning("[banners]   " + key + ": " + String.join(", ", fixes)));
    }

    private static boolean flag(Object distribution, String method) throws ReflectiveOperationException {
        return (boolean) distribution.getClass().getMethod(method).invoke(distribution);
    }
}
