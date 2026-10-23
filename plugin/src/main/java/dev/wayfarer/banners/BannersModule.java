package dev.wayfarer.banners;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Trophy banners. Holds the loaded definitions; rules, items and listeners arrive in later tickets. */
public final class BannersModule implements Module {
    static final String RELOAD_PERMISSION = "wayfarer.banners.reload";
    static final String GIVE_PERMISSION = "wayfarer.banners.give";

    private final JavaPlugin plugin;
    private final Vocabulary vocabulary = new PaperVocabulary();
    private Core core;
    private BannerItems items;
    private volatile BannerSet loaded = BannerSet.empty();

    public BannersModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "banners";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        this.items = new BannerItems(core.itemTags());
        load(); // throws if the file is unreadable, which skips only this module
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        Commands.literal("mobbanners")
                                .then(Commands.literal("reload")
                                        .requires(s -> s.getSender().hasPermission(RELOAD_PERMISSION))
                                        .executes(ctx -> {
                                            reload(ctx.getSource().getSender());
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("give")
                                        .requires(s -> s.getSender().hasPermission(GIVE_PERMISSION))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    plugin.getServer().getOnlinePlayers()
                                                            .forEach(p -> builder.suggest(p.getName()));
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("banner", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            loaded.banners().keySet().forEach(builder::suggest);
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            give(ctx.getSource().getSender(),
                                                                    StringArgumentType.getString(ctx, "player"),
                                                                    StringArgumentType.getString(ctx, "banner"));
                                                            return Command.SINGLE_SUCCESS;
                                                        }))))
                                .build()));
    }

    @Override
    public void disable() {
        loaded = BannerSet.empty();
    }

    public BannerSet banners() {
        return loaded;
    }

    private void load() throws IOException {
        BannerSet set = BannerConfigParser.parse(core.config().load(id()), vocabulary);
        set.problems().forEach(p -> core.logger().warning("[banners] " + p));
        loaded = set;
        int built = 0;
        for (BannerDefinition def : set.banners().values()) {
            try {
                items.create(def); // proves the colour and patterns build on this server
                built++;
            } catch (RuntimeException e) {
                core.logger().warning("[banners] Banner " + def.id() + " item could not be built: " + e);
            }
        }
        core.logger().info("Banner items built: " + built + " of " + set.banners().size());
        core.logger().info("Banners loaded: " + set.banners().size() + " banners, "
                + set.families().size() + " families, " + set.problems().size() + " problems");
    }

    private void reload(CommandSender sender) {
        try {
            load();
            sender.sendMessage("Banners reloaded: " + loaded.banners().size() + " banners, "
                    + loaded.problems().size() + " problems (see log)");
        } catch (IOException e) {
            core.logger().severe("[banners] Reload failed, keeping previous banners: " + e.getMessage());
            sender.sendMessage("Banners reload failed, previous banners kept: " + e.getMessage());
        }
    }

    private void give(CommandSender sender, String playerName, String bannerId) {
        BannerDefinition def = loaded.find(bannerId).orElse(null);
        if (def == null) {
            sender.sendMessage("Unknown banner '" + bannerId + "'");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("No online player named '" + playerName + "'");
            return;
        }
        // Full inventory: addItem returns what did not fit, which then drops at the player's feet.
        target.getInventory().addItem(items.create(def)).values()
                .forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
        sender.sendMessage("Gave " + target.getName() + " the " + def.id() + " banner");
    }
}
