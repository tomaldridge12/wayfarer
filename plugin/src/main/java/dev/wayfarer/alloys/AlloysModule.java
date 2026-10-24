package dev.wayfarer.alloys;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Alloys: the crucible, alloy ingots, templates and gear, smithing upgrades, repair and endless repair. */
public final class AlloysModule implements Module {
    static final String ADMIN_PERMISSION = "wayfarer.alloys.admin";

    private final JavaPlugin plugin;
    private final AlloyVocabulary vocabulary = new PaperAlloyVocabulary();
    private final List<Listener> listeners = new ArrayList<>();
    private Core core;
    private volatile AlloyRules rules = AlloyRules.empty();
    private AlloyItems items;
    private Crucibles crucibles;
    private AlloyRecipes recipes;
    private org.bukkit.scheduler.BukkitTask clock;

    public AlloysModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "alloys";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        items = new AlloyItems(core.itemTags(), () -> rules);
        recipes = new AlloyRecipes(plugin.getServer(), () -> rules, items);
        load();
        listen(recipes);
        listen(new dev.wayfarer.core.RecipeGuard(i -> items.tag(i).isPresent()));
        listen(new AlloyAnvil(() -> rules, items));
        listen(new ShakudoLuck(() -> rules, items));
        listen(new GuardianTemplates(plugin.getServer(), () -> rules, items, core.logger()));
        listen(new AlloyRefreshListener(items));
        crucibles = new Crucibles(plugin.getServer(), core.chunkData(), () -> rules, items,
                (player, activity) -> core.progression().record(player, activity, "crucible"), core.logger());
        listen(crucibles);
        core.logger().info("Crucibles loaded: " + crucibles.loadAll() + " in loaded chunks");
        clock = plugin.getServer().getScheduler().runTaskTimer(plugin, crucibles::tick, Crucibles.PERIOD_TICKS, Crucibles.PERIOD_TICKS);
        core.commands().register(id(), commands());
        // Relics are built on alloy gear through core: "alloy:steel_axe".
        core.itemMakers().register(id(), "alloy", name -> name.contains(":") ? java.util.Optional.empty() : items.byName(name, 1));
    }

    @Override
    public void disable() {
        if (clock != null) {
            clock.cancel();
        }
        if (crucibles != null) {
            crucibles.clear();
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

    /** Reads alloys.yml; throws if the file is unreadable, which skips only this module at start. */
    private int load() throws IOException {
        AlloyConfigParser.Result r = AlloyConfigParser.parse(core.config().load(id()), vocabulary);
        r.problems().forEach(p -> core.logger().warning("[alloys] " + p));
        rules = r.rules();
        core.logger().info("Alloys loaded: " + rules.alloys().size() + " alloys ("
                + String.join(", ", rules.alloys().stream().map(AlloyDefinition::id).toList()) + "), "
                + r.problems().size() + " problems; endless repair " + (rules.anvil().endless()
                ? "on (cap " + rules.anvil().cap() + " levels)" : "off"));
        int built = 0;
        int all = 0;
        for (AlloyDefinition a : rules.alloys()) {
            for (GearShape shape : GearShape.values()) {
                all++;
                try {
                    if (items.gear(new AlloyRules.Gear(a.id(), shape)).isPresent()) {
                        built++;
                    }
                } catch (RuntimeException e) {
                    core.logger().warning("[alloys] " + a.id() + " " + shape.id() + " could not be built: " + e);
                }
            }
        }
        core.logger().info("Alloy gear built: " + built + " of " + all);
        int[] made = recipes.register();
        core.logger().info("Alloy recipes registered: " + made[0] + " smithing upgrades, " + made[1]
                + " template duplications (" + made[2] + " live)");
        for (String sample : List.of("steel_pickaxe", "adamant_chestplate")) {
            items.byName(sample, 1).ifPresent(i -> core.logger().info("Alloy gear check: " + AlloyItems.describe(i)));
        }
        return r.problems().size();
    }

    AlloyRules rules() {
        return rules;
    }

    private LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("alloys")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("list").executes(ctx -> {
                    list(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    try {
                        int problems = load();
                        sender.sendMessage("Alloys reloaded: " + rules.alloys().size() + " alloys, " + problems + " problems");
                    } catch (IOException e) {
                        sender.sendMessage("Alloys reload failed, keeping the old config: " + e.getMessage());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("load")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                        .then(Commands.argument("world", StringArgumentType.word())
                        .then(Commands.argument("item", StringArgumentType.word())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .then(Commands.argument("starter", StringArgumentType.word()).executes(ctx -> {
                            load(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "x"),
                                    IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"),
                                    StringArgumentType.getString(ctx, "world"), StringArgumentType.getString(ctx, "item"),
                                    IntegerArgumentType.getInteger(ctx, "count"), StringArgumentType.getString(ctx, "starter"));
                            return Command.SINGLE_SUCCESS;
                        })))))))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests((ctx, b) -> {
                            plugin.getServer().getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        }).then(Commands.argument("item", StringArgumentType.greedyString()).suggests((ctx, b) -> {
                            items.names().stream().filter(n -> n.startsWith(b.getRemaining())).forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(ctx -> {
                            give(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "item").trim());
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    /** Testing tool: loads a crucible as the starter's right-click would (the starter may be offline, by UUID). */
    private void load(CommandSender sender, int x, int y, int z, String worldName, String item, int count, String starter) {
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        java.util.UUID who;
        try {
            who = java.util.UUID.fromString(starter);
        } catch (IllegalArgumentException e) {
            Player p = plugin.getServer().getPlayerExact(starter);
            if (p == null) {
                sender.sendMessage("No online player named '" + starter + "' (a UUID works for offline players)");
                return;
            }
            who = p.getUniqueId();
        }
        if (world == null) {
            sender.sendMessage("No world named '" + worldName + "'");
            return;
        }
        sender.sendMessage(crucibles.load(world.getBlockAt(x, y, z), item.toLowerCase(java.util.Locale.ROOT), count, who));
    }

    private void list(CommandSender sender) {
        for (AlloyDefinition a : rules.alloys()) {
            List<String> parts = new ArrayList<>();
            a.recipe().forEach((item, n) -> parts.add(n + " " + item));
            sender.sendMessage(a.name() + ": " + String.join(" + ", parts) + " at " + a.heat().label() + " heat, "
                    + a.meltSeconds() + "s -> " + a.yield() + " ingot; upgrades " + a.upgrades() + " gear; template from "
                    + a.template().boss());
        }
    }

    private void give(CommandSender sender, String playerName, String name) {
        Player player = plugin.getServer().getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage("No online player named '" + playerName + "'");
            return;
        }
        ItemStack item = items.byName(name, 1).orElse(null);
        if (item == null) {
            sender.sendMessage("Unknown alloy item '" + name + "' (try ingot:steel, template:steel or steel_pickaxe)");
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        sender.sendMessage("Gave " + name + " to " + player.getName());
    }
}
