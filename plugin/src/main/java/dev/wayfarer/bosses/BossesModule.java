package dev.wayfarer.bosses;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Bosses: stars, variants, abilities and boss bars, built on the Paper API only. Talks to others via the core. */
public final class BossesModule implements Module {
    static final String ADMIN_PERMISSION = "wayfarer.bosses.admin";

    private final JavaPlugin plugin;
    private Core core;
    private volatile BossRoster roster = BossRoster.empty();
    private BossSpawner spawner;
    private BossController controller;
    private BukkitTask tick;

    public BossesModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "bosses";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        load();
        spawner = new BossSpawner(plugin.getServer().getScoreboardManager().getMainScoreboard());
        controller = new BossController(plugin.getServer(), () -> roster, spawner, core.logger());
        plugin.getServer().getPluginManager().registerEvents(controller, plugin);
        controller.attachLoaded();
        tick = plugin.getServer().getScheduler().runTaskTimer(plugin, controller::tick, BossController.PERIOD, BossController.PERIOD);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(commands()));
    }

    @Override
    public void disable() {
        if (tick != null) {
            tick.cancel();
        }
        if (controller != null) {
            HandlerList.unregisterAll(controller);
            controller.clear();
        }
    }

    private void load() throws IOException {
        BossRoster r = BossConfigParser.parse(core.config().load(id()), new PaperBossVocabulary());
        r.problems().forEach(p -> core.logger().warning("[bosses] " + p));
        roster = r;
        int variants = r.bosses().values().stream().mapToInt(b -> b.variants().size()).sum();
        core.logger().info("Bosses loaded: " + r.bosses().size() + " bosses, " + variants + " variants, "
                + r.problems().size() + " problems");
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> commands() {
        return Commands.literal("wayfarer")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("boss")
                        .then(Commands.literal("list").executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(BossText.list(roster));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("killall").executes(ctx -> {
                            ctx.getSource().getSender().sendMessage("Removed " + controller.removeAll() + " bosses");
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("reload").executes(ctx -> {
                            reload(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("spawnall")
                                .then(Commands.argument("stars", IntegerArgumentType.integer(1, StarScale.MAX))
                                        .executes(ctx -> {
                                            spawnAll(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "stars"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("boss", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            roster.bosses().keySet().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> spawn(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "boss"), "random", 0))
                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                .suggests((ctx, b) -> {
                                                    roster.find(StringArgumentType.getString(ctx, "boss"))
                                                            .ifPresent(boss -> boss.variants().forEach(v -> b.suggest(v.id())));
                                                    b.suggest("random");
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> spawn(ctx.getSource().getSender(),
                                                        StringArgumentType.getString(ctx, "boss"),
                                                        StringArgumentType.getString(ctx, "variant"), 0))
                                                .then(Commands.argument("stars", IntegerArgumentType.integer(1, StarScale.MAX))
                                                        .executes(ctx -> spawn(ctx.getSource().getSender(),
                                                                StringArgumentType.getString(ctx, "boss"),
                                                                StringArgumentType.getString(ctx, "variant"),
                                                                IntegerArgumentType.getInteger(ctx, "stars"))))))))
                .build();
    }

    private void reload(CommandSender sender) {
        try {
            load();
            sender.sendMessage("Bosses reloaded: " + roster.bosses().size() + " bosses");
        } catch (IOException e) {
            sender.sendMessage("Bosses reload failed, previous bosses kept: " + e.getMessage());
        }
    }

    /** Where an operator's spawn lands: a few blocks in front of a player, or above world spawn from the console. */
    private Location spawnPoint(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(5));
        }
        World world = plugin.getServer().getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        return world.getHighestBlockAt(spawn).getLocation().add(0.5, 1, 0.5);
    }

    private int spawn(CommandSender sender, String bossId, String variantId, int stars) {
        BossDefinition boss = roster.find(bossId).orElse(null);
        if (boss == null) {
            sender.sendMessage("Unknown boss '" + bossId + "'");
            return 0;
        }
        BossVariant variant = "random".equals(variantId)
                ? BossRoster.pickVariant(boss, ThreadLocalRandom.current()::nextDouble)
                : boss.variant(variantId).orElse(null);
        if (variant == null) {
            sender.sendMessage("Unknown variant '" + variantId + "' for " + bossId);
            return 0;
        }
        int s = stars == 0 ? 1 + ThreadLocalRandom.current().nextInt(StarScale.MAX) : stars;
        Location at = spawnPoint(sender);
        var entity = spawner.spawn(boss, variant, s, roster.stars(), at);
        controller.attach(entity);
        String text = "Spawned " + boss.id() + "/" + variant.id() + " ★" + s + " at "
                + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
        core.logger().info("[bosses] " + text);
        if (sender instanceof Player) {
            sender.sendMessage(text);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Operator test tool: every variant of every boss at one star level, in a row. */
    private void spawnAll(CommandSender sender, int stars) {
        Location base = spawnPoint(sender);
        int n = 0;
        for (BossDefinition boss : roster.bosses().values()) {
            for (BossVariant variant : boss.variants()) {
                Location at = base.clone().add(n * 4, 0, 0);
                at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
                controller.attach(spawner.spawn(boss, variant, stars, roster.stars(), at));
                n++;
            }
        }
        String text = "Spawned " + n + " boss variants at ★" + stars;
        core.logger().info("[bosses] " + text);
        sender.sendMessage(text);
    }
}
