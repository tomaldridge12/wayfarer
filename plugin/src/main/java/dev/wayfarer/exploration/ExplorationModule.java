package dev.wayfarer.exploration;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.RecipeGuard;
import dev.wayfarer.core.Wayfinding;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Exploration: the Wayfinder compass and Guardian's Chart, safe surface after the first dragon, and beacon kindling. */
public final class ExplorationModule implements Module {
    static final String ADMIN_PERMISSION = "wayfarer.exploration.admin";

    private final JavaPlugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private Core core;
    private volatile ExplorationConfigParser.Settings settings;
    private ExplorationState state;
    private dev.wayfarer.core.StateFile stateFile;
    private Wayfinder wayfinder;
    private BeaconKindling kindling;

    public ExplorationModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "exploration";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        load();
        stateFile = new dev.wayfarer.core.StateFile(plugin.getDataFolder().toPath().resolve("data").resolve("exploration.json"),
                core.logger());
        state = ExplorationState.read(stateFile.path(), core.logger());
        wayfinder = new Wayfinder(plugin, core, () -> settings, state, this::save);
        kindling = new BeaconKindling(plugin, core.itemTags(), () -> settings, core.logger());
        listen(wayfinder);
        listen(kindling);
        listen(new SafeSurface(() -> state.safeSurface, this::dragonDown));
        listen(new RecipeGuard(kindling::is));
        wayfinder.start();
        core.services().register(id(), Wayfinding.class, wayfinder);
        core.itemMakers().register(id(), id(), name -> name.equals("beacon_kindling") ? Optional.of(kindling.item()) : Optional.empty());
        core.commands().register(id(), commands());
        core.logger().info("Safe surface: " + (state.safeSurface ? "on" : "off") + (state.dragonDown ? " (the dragon has fallen)" : ""));
    }

    @Override
    public void disable() {
        if (wayfinder != null) {
            wayfinder.stop();
        }
        if (kindling != null) {
            kindling.stop();
        }
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        if (state != null) {
            stateFile.close(state.json());
        }
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    private void load() throws IOException {
        ExplorationConfigParser.Result r = ExplorationConfigParser.parse(core.config().load(id()));
        r.problems().forEach(p -> core.logger().warning("[exploration] " + p));
        r.notes().forEach(n -> core.logger().info("[exploration] " + n));
        settings = r.settings();
        ExplorationRules.Prices prices = settings.prices();
        core.logger().info("Exploration loaded: Wayfinder offers " + settings.structures().size() + " structures within "
                + settings.rangeBlocks() + " blocks, " + settings.cooldownSeconds() + "s between searches; " + r.problems().size()
                + " problems; XP prices: structure " + prices.structure() + ", biome " + prices.biome() + " (x"
                + prices.multiplierText() + " if unfound), chart " + settings.chartPoints() + ", "
                + prices.rare().size() + " rare");
    }

    /** Taken as JSON here on the main thread, written in order off it. */
    private void save() {
        stateFile.save(state.json());
    }

    /** The first dragon down: safe surface comes on (once; an admin can turn it off again). */
    private void dragonDown(String by) {
        if (state.dragonDown) {
            return;
        }
        state.dragonDown = true;
        state.safeSurface = true;
        save();
        plugin.getServer().broadcast(ItemLook.text("✦ The dragon has fallen to " + by + ". ", 0xB58CE6).append(ItemLook.text(
                "From tonight, nothing hostile walks the open surface.", 0xE8D9A8)));
        core.logger().info("[exploration] The first dragon fell to " + by + "; safe surface on");
    }

    private LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("exploration")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("safesurface")
                        .then(Commands.literal("on").executes(ctx -> safe(ctx.getSource().getSender(), true)))
                        .then(Commands.literal("off").executes(ctx -> safe(ctx.getSource().getSender(), false))))
                .then(Commands.literal("reload").executes(ctx -> {
                    try {
                        load();
                        ctx.getSource().getSender().sendMessage("Exploration reloaded");
                    } catch (IOException e) {
                        ctx.getSource().getSender().sendMessage("Exploration reload failed, keeping the old config: " + e.getMessage());
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private int safe(CommandSender sender, boolean on) {
        state.safeSurface = on;
        save();
        sender.sendMessage("Safe surface " + (on ? "on" : "off"));
        core.logger().info("[exploration] Safe surface turned " + (on ? "on" : "off") + " by " + sender.getName());
        return Command.SINGLE_SUCCESS;
    }
}
