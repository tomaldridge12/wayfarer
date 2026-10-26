package dev.wayfarer.foods;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ForageMultiplier;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.PlayerStore;
import dev.wayfarer.core.progression.Activity;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Forage: a player breaking a configured plant in a configured biome may find an ingredient. Only a player's own
 * break fires the event, so pistons, fire and explosions give nothing; creative gives nothing either. Main thread.
 */
final class Forage implements Listener {
    private static final int HERB = 0x9BC47A;

    /** Biome tag membership never changes while the server runs. */
    private static final Map<String, Boolean> TAGS = new ConcurrentHashMap<>();

    private final Core core;
    private final Supplier<List<ForageDefinition>> forage;
    private final ForageItems items;
    private final PlayerStore<ForageData> store;

    Forage(Core core, Supplier<List<ForageDefinition>> forage, ForageItems items, PlayerStore<ForageData> store) {
        this.core = core;
        this.forage = forage;
        this.items = items;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block block = event.getBlock();
        List<ForageDefinition> candidates = ForageRules.forPlant(forage.get(), block.getType().getKey().getKey());
        ForageData data = candidates.isEmpty() ? null : store.cached(player.getUniqueId());
        if (data == null) {
            return;
        }
        double multiplier = core.services().get(ForageMultiplier.class).map(ForageMultiplier::current).orElse(1.0);
        for (ForageDefinition d : candidates) {
            if (!inBiome(d, block)) {
                continue;
            }
            int streak = data.streak(d.id());
            boolean found = ForageRules.drops(streak, d.pity(), ForageRules.chance(d.chance(), multiplier),
                    ThreadLocalRandom.current().nextDouble());
            int next = ForageRules.nextStreak(streak, found);
            if (next == 0) {
                data.streaks.remove(d.id());
            } else {
                data.streaks.put(d.id(), next);
            }
            store.markDirty(player.getUniqueId());
            if (found) {
                give(player, block, d, streak >= d.pity());
            }
        }
    }

    private void give(Player player, Block block, ForageDefinition d, boolean pity) {
        UUID id = player.getUniqueId();
        String activity = Activity.FORAGE + ":" + d.id();
        boolean first = core.progression().snapshot(id).map(s -> s.count(activity) == 0).orElse(false);
        ItemStack stack = items.build(d, 1);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack);
        core.progression().record(id, activity, "forage " + d.id());
        if (first) {
            player.sendActionBar(ItemLook.text("✦ ", HERB).append(ItemLook.text("Found " + d.name().toLowerCase(java.util.Locale.ROOT), d.colour())));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.5f);
        }
        core.logger().info("[foods] " + player.getName() + " found " + d.id() + (pity ? " (pity)" : "") + (first ? " (first time)" : ""));
    }

    private static boolean inBiome(ForageDefinition d, Block block) {
        Biome biome = block.getBiome();
        NamespacedKey biomeKey = biome.getKey();
        String plain = "minecraft".equals(biomeKey.getNamespace()) ? biomeKey.getKey() : biomeKey.asString();
        for (String entry : d.biomes()) {
            if (entry.startsWith("#") ? inTag(entry.substring(1), biomeKey) : entry.equals(plain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inTag(String tag, NamespacedKey biomeKey) {
        return TAGS.computeIfAbsent(tag + "|" + biomeKey, k -> tagContains(tag, biomeKey));
    }

    static boolean tagContains(String tag, NamespacedKey biomeKey) {
        NamespacedKey tagKey = NamespacedKey.fromString(tag);
        if (tagKey == null) {
            return false;
        }
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        TagKey<Biome> key = TagKey.create(RegistryKey.BIOME, tagKey);
        return registry.hasTag(key) && registry.getTag(key).contains(TypedKey.create(RegistryKey.BIOME, biomeKey));
    }
}
