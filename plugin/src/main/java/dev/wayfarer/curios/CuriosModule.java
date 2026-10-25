package dev.wayfarer.curios;

import dev.wayfarer.core.ItemCatalogue;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.wayfarer.core.Bait;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.PlayerStore;
import org.bukkit.scheduler.BukkitTask;
import dev.wayfarer.core.CurioBoosts;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.RecipeGuard;
import dev.wayfarer.core.Wayfinding;
import dev.wayfarer.core.progression.RewardType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Curios: reward items with one effect each, charms while carried and tools on right-click. */
public final class CuriosModule implements Module {
    static final String ADMIN_PERMISSION = "wayfarer.curios.admin";

    private final JavaPlugin plugin;
    private final CurioVocabulary vocabulary = new PaperCurioVocabulary();
    private final List<Listener> listeners = new ArrayList<>();
    private Core core;
    private volatile CurioRules rules = CurioRules.empty();
    private CurioItems items;
    private CurioEffects effects;
    private CurioTools tools;
    private volatile LootItems.Loot loot = LootItems.Loot.empty();
    private LootItems lootItems;
    private Supplies supplies;
    private PlayerStore<CurioPlayerData> store;
    private BukkitTask saveTask;

    public CuriosModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "curios";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        items = new CurioItems(core.itemTags(), () -> rules);
        lootItems = new LootItems(core.itemTags(), core.itemMakers(), () -> loot);
        store = new PlayerStore<>(plugin.getDataFolder().toPath().resolve("data").resolve(id()), CurioPlayerData.VERSION,
                new CurioPlayerData.Codec(), core.logger(), uuid -> plugin.getServer().getPlayer(uuid) != null);
        plugin.getServer().getOnlinePlayers().forEach(p -> store.load(p.getUniqueId()));
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, store::flush, 1200, 1200);
        load();
        effects = new CurioEffects(plugin, () -> rules, items,
                i -> core.itemTags().read(i, "alloys").filter(t -> t.startsWith("gear:")).isPresent(),
                () -> core.services().get(dev.wayfarer.core.HatAbilities.Worn.class));
        core.services().register(id(), dev.wayfarer.core.HatAbilities.Catalogue.class, new HatCatalogue());
        tools = new CurioTools(plugin, () -> rules, items, effects::working,
                () -> core.services().get(Wayfinding.class), core.logger());
        supplies = new Supplies(() -> loot, lootItems, store, core.logger());
        listen(new RecipeGuard(i -> items.idOf(i).isPresent() || lootItems.supplyOf(i).isPresent()));
        listen(supplies);
        listen(new Joins());
        listen(effects);
        listen(tools);
        effects.start();
        core.itemMakers().register(id(), CurioItems.KIND, name -> items.make(name));
        core.itemMakers().register(id(), LootItems.GEAR, lootItems::gear);
        core.itemMakers().register(id(), LootItems.SUPPLY, name -> lootItems.supply(name, 1));
        core.itemIds().register(id(), CurioItems.KIND, () -> rules.curios().stream().map(CurioDefinition::id).toList());
        core.itemIds().register(id(), LootItems.GEAR, () -> loot.gear().keySet());
        core.itemIds().register(id(), LootItems.SUPPLY, () -> loot.supplies().keySet());
        core.progression().registerRewardType(id(), CurioItems.KIND, new Curios());
        core.progression().registerRewardType(id(), LootItems.GEAR, new Gear());
        core.progression().registerRewardType(id(), LootItems.SUPPLY, new Supply());
        core.progression().registerRewardType(id(), "enchant", new EnchantBook());
        core.services().register(id(), Bait.class, supplies::useCast);
        core.services().register(id(), CurioBoosts.class, (player, what) -> CurioRules.boost(effects.working(player), what));
        core.commands().register(id(), commands());
        core.catalogue().add(id(), "curios", Component.text("Curios", NamedTextColor.WHITE), Material.AMETHYST_SHARD,
                ItemCatalogue.madeFrom(core.itemIds(), core.itemMakers(), CurioItems.KIND));
        core.catalogue().add(id(), "gear", Component.text("Special gear", NamedTextColor.WHITE), Material.DIAMOND_PICKAXE,
                ItemCatalogue.madeFrom(core.itemIds(), core.itemMakers(), LootItems.GEAR));
        core.catalogue().add(id(), "supplies", Component.text("Supplies", NamedTextColor.WHITE), Material.WHEAT_SEEDS,
                ItemCatalogue.madeFrom(core.itemIds(), core.itemMakers(), LootItems.SUPPLY));
    }

    @Override
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (store != null) {
            store.close();
        }
        if (effects != null) {
            effects.stop();
        }
        if (tools != null) {
            tools.stop();
        }
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    /** What hats may ask: which abilities exist and how to describe them. */
    private final class HatCatalogue implements dev.wayfarer.core.HatAbilities.Catalogue {
        @Override
        public boolean exists(String ability) {
            return rules.hatAbility(ability).isPresent();
        }

        @Override
        public java.util.List<String> describe(String ability) {
            return rules.hatAbility(ability).map(c -> {
                java.util.List<String> lines = new java.util.ArrayList<>();
                lines.add(c.name());
                lines.addAll(c.description());
                return java.util.List.copyOf(lines);
            }).orElse(java.util.List.of());
        }
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    /** Reads curios.yml; throws if the file is unreadable. */
    private int load() throws IOException {
        Map<String, Object> root = core.config().load(id());
        CurioConfigParser.Result r = CurioConfigParser.parse(root.get("curios"), vocabulary);
        r.problems().forEach(p -> core.logger().warning("[curios] " + p));
        CurioConfigParser.Result hatAbilities = CurioConfigParser.parseHatAbilities(root.get("hat-abilities"), vocabulary);
        hatAbilities.problems().forEach(p -> core.logger().warning("[curios] " + p));
        Map<String, CurioDefinition> byAbility = new java.util.LinkedHashMap<>();
        hatAbilities.curios().forEach((abilityId, c) -> byAbility.put(abilityId, c));
        rules = new CurioRules(r.curios(), byAbility);
        core.logger().info("Hat abilities loaded: " + byAbility.size() + " (" + String.join(", ", byAbility.keySet()) + ")");
        int built = 0;
        for (CurioDefinition c : rules.curios()) {
            try {
                items.build(c);
                built++;
            } catch (RuntimeException e) {
                core.logger().warning("[curios] Curio " + c.id() + " could not be built: " + e.getMessage());
            }
        }
        GearSupplyParser.Result g = GearSupplyParser.parse(root.get("gear"), root.get("supplies"), new PaperNames());
        g.problems().forEach(p -> core.logger().warning("[curios] " + p));
        g.notices().forEach(n -> core.logger().info("[curios] " + n));
        loot = new LootItems.Loot(g.gear(), g.supplies());
        Map<String, Integer> enchants = new java.util.LinkedHashMap<>();
        g.gear().values().forEach(p -> p.enchants().forEach((k, v) -> enchants.merge(k, v, Math::max)));
        core.rewardEnchants().register(id(), enchants);
        int gearBuilt = 0;
        int alloyGear = 0;
        for (GearPreset p : g.gear().values()) {
            try {
                ItemStack item = lootItems.build(p);
                gearBuilt++;
                if (core.itemTags().read(item, "alloys").isPresent()) {
                    alloyGear++;
                }
            } catch (RuntimeException e) {
                core.logger().warning("[curios] Gear " + p.id() + " could not be built: " + e.getMessage());
            }
        }
        int suppliesBuilt = 0;
        for (SupplyDefinition sd : g.supplies().values()) {
            try {
                lootItems.build(sd, 1);
                suppliesBuilt++;
            } catch (RuntimeException e) {
                core.logger().warning("[curios] Supply " + sd.id() + " could not be built: " + e.getMessage());
            }
        }
        core.logger().info("Special gear built: " + gearBuilt + " of " + g.gear().size() + " (" + alloyGear + " on alloys); "
                + "supplies built: " + suppliesBuilt + " of " + g.supplies().size() + "; " + g.problems().size() + " problems");
        long tools = rules.curios().stream().filter(CurioDefinition::tool).count();
        core.logger().info("Curios loaded: " + rules.curios().size() + " curios (" + (rules.curios().size() - tools)
                + " charms, " + tools + " tools), " + r.problems().size() + " problems");
        core.logger().info("Curio items built: " + built + " of " + rules.curios().size());
        return r.problems().size() + g.problems().size();
    }

    /** The server's items, enchantments and mob variants, for gear and supplies. */
    private static final class PaperNames implements GearSupplyParser.Names {
        @Override
        public boolean isItem(String key) {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(key);
            return m != null && m.isItem();
        }

        @Override
        public boolean isEnchant(String key) {
            org.bukkit.NamespacedKey k = org.bukkit.NamespacedKey.fromString(key);
            return k != null && org.bukkit.Registry.ENCHANTMENT.get(k) != null;
        }

        @Override
        public boolean isVariant(String mob, String variant) {
            org.bukkit.NamespacedKey k = org.bukkit.NamespacedKey.minecraft(variant);
            var access = io.papermc.paper.registry.RegistryAccess.registryAccess();
            return switch (mob) {
                case "cow" -> access.getRegistry(io.papermc.paper.registry.RegistryKey.COW_VARIANT).get(k) != null;
                case "pig" -> access.getRegistry(io.papermc.paper.registry.RegistryKey.PIG_VARIANT).get(k) != null;
                case "chicken" -> access.getRegistry(io.papermc.paper.registry.RegistryKey.CHICKEN_VARIANT).get(k) != null;
                case "sheep" -> java.util.Arrays.stream(org.bukkit.DyeColor.values())
                        .anyMatch(c -> c.name().equalsIgnoreCase(variant));
                default -> false;
            };
        }
    }

    /** Loads bait on login (the pre-login thread may block on files) and saves it on quit. */
    private final class Joins implements Listener {
        @org.bukkit.event.EventHandler
        void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
            try {
                store.load(event.getUniqueId()).join();
            } catch (RuntimeException e) {
                core.logger().severe("[curios] Could not load data for " + event.getUniqueId() + ": " + e.getMessage());
            }
        }

        @org.bukkit.event.EventHandler
        void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
            store.save(event.getPlayer().getUniqueId());
        }
    }

    private final class Gear implements RewardType {
        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            return lootItems.gear(id).orElse(null);
        }

        @Override
        public Component title(String id) {
            GearPreset p = loot.gear().get(id);
            return p == null ? null : ItemLook.name(p.name(), p.colour());
        }

        @Override
        public Component describe(String id) {
            return lootItems.gear(id).map(i -> title(id).hoverEvent(i.asHoverEvent())).orElse(null);
        }
    }

    /**
     * An enchanted book as a reward: {@code enchant:excellentenchants:double_catch_3}. Earning one also unlocks the enchant
     * for buying again in the shop.
     */
    private final class EnchantBook implements RewardType {
        private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

        /** The enchantment and level, or null when the id is malformed or the server lacks the enchant. */
        private org.bukkit.enchantments.Enchantment enchant(String id) {
            int cut = id.lastIndexOf('_');
            org.bukkit.NamespacedKey key = cut <= 0 ? null : org.bukkit.NamespacedKey.fromString(id.substring(0, cut));
            return key == null ? null : org.bukkit.Registry.ENCHANTMENT.get(key);
        }

        private int level(String id) {
            String n = id.substring(id.lastIndexOf('_') + 1);
            return n.matches("\\d{1,2}") ? Integer.parseInt(n) : 1;
        }

        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            org.bukkit.enchantments.Enchantment e = enchant(id);
            if (e == null) {
                core.logger().warning("[curios] Enchant reward " + id + " cannot be made (is its plugin installed?); it stays"
                        + " earned and can be claimed later");
                return null;
            }
            ItemStack book = ItemStack.of(org.bukkit.Material.ENCHANTED_BOOK);
            book.editMeta(org.bukkit.inventory.meta.EnchantmentStorageMeta.class, m -> m.addStoredEnchant(e, level(id), true));
            return book;
        }

        @Override
        public Component title(String id) {
            int cut = id.lastIndexOf('_');
            if (cut <= 0 || !id.contains(":")) {
                return null;
            }
            String key = id.substring(0, cut);
            int level = level(id);
            String name = CurioConfigParser.title(key.substring(key.indexOf(':') + 1));
            return ItemLook.name(name + " " + (level < ROMAN.length ? ROMAN[level] : level) + " Book",
                    key.startsWith("excellentenchants:") ? 0x279CF5 : 0xC9A0FF);
        }
    }

    /** A supply reward; {@code seed_bundle} gives one, {@code animal_crate*3} three. */
    private final class Supply implements RewardType {
        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            String[] parts = id.split("\\*");
            int amount = parts.length > 1 && parts[1].matches("\\d+") ? Integer.parseInt(parts[1]) : 1;
            return lootItems.supply(parts[0], amount).orElse(null);
        }

        @Override
        public Component title(String id) {
            String[] parts = id.split("\\*");
            SupplyDefinition sd = loot.supplies().get(parts[0]);
            return sd == null ? null : ItemLook.name((parts.length > 1 ? parts[1] + " × " : "") + sd.name(), sd.colour());
        }
    }

    private final class Curios implements RewardType {
        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            return items.make(id).orElse(null);
        }

        @Override
        public Component title(String id) {
            return rules.curio(id).map(c -> ItemLook.name(c.name(), c.colour())).orElse(null);
        }

        @Override
        public Component describe(String id) {
            return items.make(id).map(i -> title(id).hoverEvent(i.asHoverEvent())).orElse(null);
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("curios")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    loot.gear().values().forEach(p -> sender.sendMessage("Gear " + p.name() + " (" + p.id() + "): " + p.base()
                            + ", " + p.enchants()));
                    loot.supplies().values().forEach(sd -> sender.sendMessage("Supply " + sd.name() + " (" + sd.id() + "): "
                            + sd.opens()));
                    for (CurioDefinition c : rules.curios()) {
                        sender.sendMessage(c.name() + " (" + c.id() + "): " + (c.tool() ? "tool, " + c.cooldownSeconds() + "s"
                                : "charm") + ", " + c.effect().getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    try {
                        int problems = load();
                        sender.sendMessage("Curios reloaded: " + rules.curios().size() + " curios, " + problems + " problems");
                    } catch (IOException e) {
                        sender.sendMessage("Curios reload failed, keeping the old config: " + e.getMessage());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests((ctx, b) -> {
                            plugin.getServer().getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        }).then(Commands.argument("item", StringArgumentType.greedyString()).suggests((ctx, b) -> {
                            rules.curios().forEach(c -> b.suggest("curio:" + c.id()));
                            loot.gear().keySet().forEach(id -> b.suggest("gear:" + id));
                            loot.supplies().keySet().forEach(id -> b.suggest("supply:" + id));
                            return b.buildFuture();
                        }).executes(ctx -> {
                            give(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "item").trim());
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    /** Gives any item a Wayfarer module makes by key (curio:moss_heart); a bare id is read as a curio. */
    private void give(CommandSender sender, String playerName, String key) {
        Player player = plugin.getServer().getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage("No online player named '" + playerName + "'");
            return;
        }
        String full = key.contains(":") ? key : CurioItems.KIND + ":" + key;
        ItemStack item = core.itemMakers().make(full).orElse(null);
        if (item == null) {
            sender.sendMessage("Nothing is made as '" + full + "' (try curio:moss_heart)");
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        sender.sendMessage("Gave " + full + " to " + player.getName());
    }
}
