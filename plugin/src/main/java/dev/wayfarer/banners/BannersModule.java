package dev.wayfarer.banners;

import com.mojang.brigadier.Command;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Trophy banners. Holds the loaded definitions; rules, items and listeners arrive in later tickets. */
public final class BannersModule implements Module {
    static final String RELOAD_PERMISSION = "wayfarer.banners.reload";

    private final JavaPlugin plugin;
    private final Vocabulary vocabulary = new PaperVocabulary();
    private Core core;
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
}
