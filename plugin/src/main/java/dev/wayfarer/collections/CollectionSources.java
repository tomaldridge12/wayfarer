package dev.wayfarer.collections;

import dev.wayfarer.core.ItemIds;
import dev.wayfarer.core.ItemMakers;
import dev.wayfarer.core.progression.Activity;
import dev.wayfarer.core.progression.Ladder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Builds each built-in collection's entries from what the server knows: every mob type, every biome each dimension
 * can generate (Terralith's included), every structure, vanilla's collectibles and every curio. Main thread.
 */
final class CollectionSources {
    static final String OVERWORLD = "Overworld";
    static final String NETHER = "Nether";
    static final String END = "The End";

    private final Server server;
    private final ItemIds ids;
    private final ItemMakers<ItemStack> makers;

    private final java.util.function.Supplier<List<FishSpecies>> fish;

    private final java.util.function.Supplier<List<TreasureDefinition>> treasures;

    CollectionSources(Server server, ItemIds ids, ItemMakers<ItemStack> makers, java.util.function.Supplier<List<FishSpecies>> fish,
            java.util.function.Supplier<List<TreasureDefinition>> treasures) {
        this.server = server;
        this.ids = ids;
        this.makers = makers;
        this.fish = fish;
        this.treasures = treasures;
    }

    /** Entries for a built-in collection by id; empty for one this class does not know (other tickets add theirs). */
    List<CollectionEntry> entries(CollectionDefinition def, List<Ladder> ladders) {
        return switch (def.id()) {
            case "bestiary" -> bestiary(def, ladders);
            case "atlas" -> atlas(def);
            case "cabinet" -> cabinet(def);
            case "curios" -> curios(def);
            case "almanac" -> almanac(def);
            case "treasures" -> treasures(def);
            default -> List.of();
        };
    }

    private List<CollectionEntry> bestiary(CollectionDefinition def, List<Ladder> ladders) {
        Set<String> exclude = Set.copyOf(CollectionConfigParser.strings(def.settings().get("exclude")));
        List<CollectionEntry> hostile = new ArrayList<>();
        List<CollectionEntry> creatures = new ArrayList<>();
        for (EntityType type : Registry.ENTITY_TYPE) {
            NamespacedKey key = type.getKey();
            String target = Activity.target(key.getNamespace(), key.getKey());
            if (!type.isAlive() || !type.isSpawnable() || exclude.contains(target) || type.getEntityClass() == null) {
                continue;
            }
            boolean enemy = Enemy.class.isAssignableFrom(type.getEntityClass());
            String egg = key.getKey() + "_spawn_egg";
            CollectionEntry e = new CollectionEntry(target, Activity.KILL + ":" + target, type.translationKey(),
                    Material.matchMaterial(egg) != null ? egg : "name_tag",
                    def.hints().getOrDefault(target, enemy ? "Hostile, and somewhere out there." : "Somewhere out there."),
                    enemy ? "Hostile" : "Creatures", "slain");
            (enemy ? hostile : creatures).add(e);
        }
        List<CollectionEntry> bosses = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Ladder ladder : ladders) {
            for (String entry : ladder.selector()) {
                if (entry.startsWith(Activity.DEFEAT + ":") && seen.add(entry)) {
                    String head = ladder.rungs().stream().map(Ladder.Rung::reward).filter(r -> r.startsWith("head:"))
                            .findFirst().orElse(null);
                    bosses.add(new CollectionEntry(entry.substring(entry.indexOf(':') + 1), entry, "#" + ladder.title(),
                            head != null ? "reward:" + head : "wither_skeleton_skull",
                            def.hints().getOrDefault("boss", "A boss."), "Bosses", "beaten"));
                }
            }
        }
        List<CollectionEntry> out = new ArrayList<>(hostile);
        out.addAll(creatures);
        out.addAll(bosses);
        return out;
    }

    private List<CollectionEntry> atlas(CollectionDefinition def) {
        Map<String, CollectionEntry> biomes = new LinkedHashMap<>();
        for (World world : server.getWorlds()) {
            String tab = tab(world);
            List<Biome> possible = world.vanillaBiomeProvider().getBiomes(world).stream()
                    .sorted(java.util.Comparator.comparing(b -> b.getKey().toString())).toList();
            for (Biome biome : possible) {
                NamespacedKey key = biome.getKey();
                if (key.getKey().equals("the_void") || key.getKey().equals("custom")) {
                    continue;
                }
                String target = Activity.target(key.getNamespace(), key.getKey());
                biomes.putIfAbsent(target, new CollectionEntry("biome/" + target, Activity.DISCOVER + ":biome/" + target,
                        "%" + biome.translationKey() + "|" + CollectionRules.title(target), Icons.biome(target),
                        def.hints().getOrDefault("biome/" + target, Icons.biomeHint(target)), tab, "visited"));
            }
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        CollectionConfigParser.asMap(def.settings().get("groups")).forEach((g, v) -> groups.put(g, CollectionConfigParser.strings(v)));
        Map<String, String> dimension = new LinkedHashMap<>();
        CollectionConfigParser.asMap(def.settings().get("structure-dimensions")).forEach((dim, v) ->
                CollectionConfigParser.strings(v).forEach(s -> dimension.put(s, dim.equals("nether") ? NETHER : dim.equals("end") ? END : OVERWORLD)));
        Map<String, List<String>> members = new LinkedHashMap<>();
        for (var structure : Registry.STRUCTURE) {
            NamespacedKey key = Registry.STRUCTURE.getKey(structure);
            if (key == null) {
                continue;
            }
            String target = Activity.target(key.getNamespace(), key.getKey());
            String group = groups.entrySet().stream().filter(g -> g.getValue().stream().anyMatch(p -> p.endsWith("*")
                    ? target.startsWith(p.substring(0, p.length() - 1)) : target.equals(p))).map(Map.Entry::getKey)
                    .findFirst().orElse(target);
            members.computeIfAbsent(group, k -> new ArrayList<>()).add(Activity.DISCOVER + ":structure/" + target);
        }
        List<CollectionEntry> out = new ArrayList<>(biomes.values());
        members.forEach((group, activities) -> out.add(new CollectionEntry("structure/" + group, String.join("|", activities),
                CollectionRules.title(group), Icons.structure(group),
                def.hints().getOrDefault("structure/" + group, "A structure you have not stepped inside yet."),
                dimension.getOrDefault(group, OVERWORLD), "entered")));
        return out;
    }

    private static String tab(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> NETHER;
            case THE_END -> END;
            default -> OVERWORLD;
        };
    }

    private List<CollectionEntry> cabinet(CollectionDefinition def) {
        List<CollectionEntry> discs = new ArrayList<>();
        List<CollectionEntry> sherds = new ArrayList<>();
        List<CollectionEntry> templates = new ArrayList<>();
        List<CollectionEntry> patterns = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir()) {
                continue;
            }
            String key = m.getKey().getKey();
            if (m.asItemType() != null && m.asItemType().hasDefaultData(DataComponentTypes.JUKEBOX_PLAYABLE)) {
                discs.add(entry(def, key, "Music Disc: " + CollectionRules.title(key.replace("music_disc_", "")), "music_disc", "Records"));
            } else if (Tag.ITEMS_DECORATED_POT_SHERDS.isTagged(m)) {
                sherds.add(entry(def, key, CollectionRules.title(key), "pottery_sherd", "Sherds"));
            } else if (key.endsWith("_smithing_template")) {
                templates.add(entry(def, key, CollectionRules.title(key.replace("_smithing_template", "")) + " Template",
                        "trim_template", "Templates"));
            } else if (key.endsWith("_banner_pattern")) {
                patterns.add(entry(def, key, CollectionRules.title(key), "banner_pattern", "Patterns"));
            }
        }
        List<CollectionEntry> out = new ArrayList<>(discs);
        out.addAll(sherds);
        out.addAll(templates);
        out.addAll(patterns);
        return out;
    }

    private static CollectionEntry entry(CollectionDefinition def, String key, String name, String kind, String tab) {
        return new CollectionEntry(key, Activity.FIND + ":" + key, "#" + name, key,
                def.hints().getOrDefault(key, def.hints().getOrDefault(kind, "Somewhere out there.")), tab, "found");
    }

    private List<CollectionEntry> almanac(CollectionDefinition def) {
        List<CollectionEntry> out = new ArrayList<>();
        for (String water : List.of("fresh", "brackish", "salt", "any")) {
            for (FishSpecies s : fish.get()) {
                if (!s.water().equals(water)) {
                    continue;
                }
                String hint = (s.hint() != null ? s.hint() : FishRules.where(s)) + " · " + s.rarity();
                out.add(new CollectionEntry(s.id(), Activity.CATCH + ":" + ItemIds.NAMESPACE + ":fish/" + s.id(), "#" + s.name(),
                        "fish:" + s.id(), hint, switch (water) {
                            case "fresh" -> "Fresh water";
                            case "brackish" -> "Brackish";
                            case "salt" -> "Seas";
                            default -> "Strange waters";
                        }, "caught"));
            }
        }
        return out;
    }

    private List<CollectionEntry> treasures(CollectionDefinition def) {
        List<CollectionEntry> out = new ArrayList<>();
        for (String kind : List.of("gem", "statue", "charm", "chart", "fossil", "coin")) {
            for (TreasureDefinition t : treasures.get()) {
                if (t.kind().equals(kind)) {
                    out.add(new CollectionEntry(t.id(), Activity.FIND + ":" + ItemIds.NAMESPACE + ":treasure/" + t.id(), "#" + t.name(),
                            "treasure:" + t.id(), TreasureRules.where(t), switch (kind) {
                                case "gem" -> "Gems";
                                case "statue", "charm" -> "Statues & charms";
                                case "chart" -> "Charts";
                                case "fossil" -> "Fossils";
                                default -> "Coins";
                            }, "found"));
                }
            }
        }
        return out;
    }

    private List<CollectionEntry> curios(CollectionDefinition def) {
        List<CollectionEntry> out = new ArrayList<>();
        for (String id : ids.ids("curio")) {
            ItemStack item = makers.make("curio:" + id).orElse(null);
            String name = item == null || !item.hasData(DataComponentTypes.ITEM_NAME) ? CollectionRules.title(id)
                    : PlainTextComponentSerializer.plainText().serialize(item.getData(DataComponentTypes.ITEM_NAME));
            out.add(new CollectionEntry(id, Activity.FIND + ":" + ItemIds.NAMESPACE + ":curio/" + id, "#" + name, "curio:" + id,
                    def.hints().getOrDefault(id, def.hints().getOrDefault("curio", "A curio.")), "", "found"));
        }
        return out;
    }
}
