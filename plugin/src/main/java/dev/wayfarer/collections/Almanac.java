package dev.wayfarer.collections;

import dev.wayfarer.core.Bait;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemTagService;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The Almanac on the server: Wayfarer fish built on cod (so they eat, cook and feed like cod), the catch that turns
 * a vanilla fish into a species that could bite there, personal records and legendary announcements. Main thread.
 */
final class Almanac implements Listener {
    static final String KIND = "fish";
    static final Set<Material> VANILLA_FISH = Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(ZoneId.of("Europe/London"));

    /** The Almanac's settings and species as last loaded. */
    record State(FishConfigParser.Settings settings, List<FishSpecies> species) {
        static State empty() {
            return new State(new FishConfigParser.Settings(0, java.util.Map.of()), List.of());
        }

        Optional<FishSpecies> species(String id) {
            return species.stream().filter(s -> s.id().equals(id)).findFirst();
        }
    }

    private final Server server;
    private final ItemTagService tags;
    private final Supplier<State> state;
    private final PlayerStore<CollectionPlayerData> store;
    private final Supplier<Optional<Bait>> bait;
    private final Logger log;

    Almanac(Server server, ItemTagService tags, Supplier<State> state, PlayerStore<CollectionPlayerData> store,
            Supplier<Optional<Bait>> bait, Logger log) {
        this.server = server;
        this.tags = tags;
        this.state = state;
        this.store = store;
        this.bait = bait;
        this.log = log;
    }

    static int colour(String rarity) {
        return switch (rarity) {
            case "uncommon" -> 0x7FD46B;
            case "rare" -> 0x5BA8F2;
            case "legendary" -> 0xF2B84B;
            default -> 0xD8D2C4;
        };
    }

    /** A fish of the species: with a catch's weight, catcher, date and place, or a plain specimen (grams 0). */
    ItemStack fish(FishSpecies s, long grams, String catcher, long at, String place) {
        ItemStack item = ItemStack.of(Material.COD);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", s.model()));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name(s.name(), colour(s.rarity())));
        List<String> description = new ArrayList<>();
        if (grams > 0) {
            description.add(FishRules.weight(grams));
            description.add("Caught by " + catcher + ", " + DATE.format(Instant.ofEpochMilli(at)));
            if (place != null) {
                description.add("in " + place);
            }
        }
        item.lore(ItemLook.lore("Almanac · " + CollectionRules.title(s.rarity()), description, colour(s.rarity()), s.flavour(),
                List.of()));
        if (s.rarity().equals("legendary")) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        tags.writeId(item, KIND, s.id());
        return item;
    }

    /** A specimen with a fresh weight, for the give command and fish rewards. */
    Optional<ItemStack> make(String id, String catcher) {
        return state.get().species(id).map(s -> fish(s, FishRules.grams(s, ThreadLocalRandom.current().nextDouble()), catcher,
                System.currentTimeMillis(), null));
    }

    /** HIGH, so core's catch counting (MONITOR) sees the Wayfarer fish and counts it under its own id. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onCatch(PlayerFishEvent event) {
        State s = state.get();
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)
                || !VANILLA_FISH.contains(caught.getItemStack().getType()) || tags.readId(caught.getItemStack()) != null
                || ThreadLocalRandom.current().nextDouble() >= s.settings().chance()) {
            return;
        }
        Player player = event.getPlayer();
        Location at = event.getHook().getLocation();
        double baitFactor = bait.get().map(b -> b.useCast(player.getUniqueId())).orElse(1.0);
        FishRules.Facts facts = facts(at);
        FishSpecies species = FishRules.pick(s.species(), facts, s.settings().rarityWeights(), baitFactor, ThreadLocalRandom.current());
        if (species == null) {
            return;
        }
        long grams = FishRules.grams(species, ThreadLocalRandom.current().nextDouble());
        long now = System.currentTimeMillis();
        ItemStack fish = fish(species, grams, player.getName(), now, biomeName(at));
        caught.setItemStack(fish);
        record(player, species, grams, now, fish);
        log.info("[collections] " + player.getName() + " caught a " + species.id() + " of " + grams + " g (" + facts.water()
                + ", " + facts.temp() + (baitFactor > 1 ? ", baited" : "") + ")");
    }

    private void record(Player player, FishSpecies s, long grams, long now, ItemStack fish) {
        CollectionPlayerData data = store.cached(player.getUniqueId());
        CollectionPlayerData.Catch old = data == null ? null : data.records.get(s.id());
        Component name = ItemLook.name(s.name(), colour(s.rarity()));
        if (data != null && FishRules.isRecord(grams, old == null ? 0 : old.grams())) {
            data.records.put(s.id(), new CollectionPlayerData.Catch(grams, now));
            store.markDirty(player.getUniqueId());
            if (old == null) {
                player.sendActionBar(text("New in your Almanac: ", Journal.INK).append(name).append(text(" · " + FishRules.weight(grams), Journal.PAPER)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
            } else {
                player.sendActionBar(text("Personal best! ", Journal.DONE).append(name).append(text(" · " + FishRules.weight(grams)
                        + " (was " + FishRules.weight(old.grams()) + ")", Journal.PAPER)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            }
        }
        if (s.rarity().equals("legendary")) {
            server.broadcast(text("✦ ", 0xF2B84B).append(text(player.getName() + " landed a legendary ", Journal.PAPER))
                    .append(name.hoverEvent(fish.asHoverEvent())).append(text(" · " + FishRules.weight(grams), Journal.PAPER)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
    }

    /** Heaviest of a species the player has landed, in grams, or 0. */
    long best(java.util.UUID player, String species) {
        CollectionPlayerData data = store.cached(player);
        CollectionPlayerData.Catch c = data == null ? null : data.records.get(species);
        return c == null ? 0 : c.grams();
    }

    // --- where the hook landed ---

    /** The water's kind, its climate (rivers and beaches take the land's around them), day, weather and places. */
    static FishRules.Facts facts(Location at) {
        World world = at.getWorld();
        Block block = at.getBlock();
        String biome = key(block.getBiome());
        Set<String> places = new HashSet<>();
        places(biome, places);
        String water;
        if (has(block.getBiome(), "is_ocean")) {
            water = "salt";
            if (has(block.getBiome(), "is_deep_ocean")) {
                places.add("deep_ocean");
            }
        } else if (biome.contains("beach") || biome.contains("shore") || biome.contains("mangrove")) {
            water = "brackish";
        } else {
            water = "fresh";
        }
        String temp;
        if (water.equals("salt")) {
            temp = oceanTemp(biome);
        } else if (has(block.getBiome(), "is_river") || water.equals("brackish")) {
            temp = landClimate(at, places);
        } else {
            temp = climate(block.getTemperature());
        }
        long time = world.getTime();
        boolean day = world.getEnvironment() == World.Environment.NORMAL && (time < 13000 || time >= 23000);
        return new FishRules.Facts(water, temp, day, world.hasStorm(), world.isThundering(), places);
    }

    private static String oceanTemp(String biome) {
        if (biome.contains("frozen")) {
            return "cold";
        }
        if (biome.contains("cold")) {
            return "cool";
        }
        if (biome.contains("lukewarm")) {
            return "warm";
        }
        return biome.contains("warm") ? "hot" : "temperate";
    }

    /** The climate of the land around a river or beach: the warmest of a ring of samples 24 blocks out. */
    private static String landClimate(Location at, Set<String> places) {
        double warmest = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            Block b = at.getWorld().getBlockAt(at.getBlockX() + (int) Math.round(Math.cos(angle) * 24), at.getBlockY(),
                    at.getBlockZ() + (int) Math.round(Math.sin(angle) * 24));
            Biome biome = b.getBiome();
            if (has(biome, "is_river") || has(biome, "is_ocean")) {
                continue;
            }
            places(key(biome), places);
            warmest = Math.max(warmest, b.getTemperature());
        }
        return climate(warmest == Double.NEGATIVE_INFINITY ? at.getBlock().getTemperature() : warmest);
    }

    static String climate(double temperature) {
        if (temperature < 0.2) {
            return "cold";
        }
        if (temperature < 0.45) {
            return "cool";
        }
        if (temperature < 0.9) {
            return "temperate";
        }
        return temperature < 1.5 ? "warm" : "hot";
    }

    private static void places(String biome, Set<String> into) {
        if (biome.contains("jungle")) {
            into.add("jungle");
        }
        if (biome.contains("swamp") && !biome.contains("mangrove")) {
            into.add("swamp");
        }
        if (biome.contains("mangrove")) {
            into.add("mangrove");
        }
        if (biome.contains("desert") || biome.contains("badlands")) {
            into.add("desert");
        }
        if (biome.equals("deep_dark")) {
            into.add("deep_dark");
        }
        if (biome.equals("pale_garden")) {
            into.add("pale_garden");
        }
        if (biome.equals("lush_caves")) {
            into.add("lush_caves");
        }
    }

    private static String key(Biome biome) {
        NamespacedKey k = biome.getKey();
        return k.getNamespace().equals("minecraft") ? k.getKey() : k.toString();
    }

    private static boolean has(Biome biome, String tag) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        TagKey<Biome> key = TagKey.create(RegistryKey.BIOME, NamespacedKey.minecraft(tag));
        return registry.hasTag(key) && registry.getTag(key).contains(TypedKey.create(RegistryKey.BIOME, biome.getKey()));
    }

    private static String biomeName(Location at) {
        return "the " + CollectionRules.title(key(at.getBlock().getBiome()));
    }

    private static Component text(String s, int rgb) {
        return Component.text(s, TextColor.color(rgb)).decoration(TextDecoration.ITALIC, false);
    }
}
