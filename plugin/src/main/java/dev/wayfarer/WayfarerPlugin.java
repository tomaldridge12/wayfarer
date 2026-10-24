package dev.wayfarer;

import dev.wayfarer.banners.BannersModule;
import dev.wayfarer.bosses.BossesModule;
import dev.wayfarer.core.ChunkDataService;
import dev.wayfarer.core.CommandTree;
import dev.wayfarer.core.ConfigService;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemTagService;
import dev.wayfarer.core.ModuleManager;
import dev.wayfarer.core.PaperConfigService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class WayfarerPlugin extends JavaPlugin implements Core {
    private final ModuleManager modules = new ModuleManager();
    private PaperConfigService config;
    private ItemTagService itemTags;
    private ChunkDataService chunkData;
    private final CommandTree<CommandSourceStack> commands = new CommandTree<>("wayfarer");

    @Override
    public void onEnable() {
        config = new PaperConfigService(this);
        itemTags = new ItemTagService(this);
        chunkData = new ChunkDataService(this);
        modules.register(new BannersModule(this));
        modules.register(new BossesModule(this));
        modules.enableAll(this);
        // Fires after every module has enabled (and again on a datapack reload), so the tree is complete.
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(commands.build(), "Wayfarer commands");
            commands.aliases().forEach(alias -> event.registrar().register(alias));
            getLogger().info("Command /wayfarer registered: " + commands.describe());
        });
        getLogger().info("Wayfarer enabled");
    }

    @Override
    public void onDisable() {
        modules.disableAll(this);
    }

    @Override
    public Logger logger() {
        return getLogger();
    }

    @Override
    public ConfigService config() {
        return config;
    }

    @Override
    public ItemTagService itemTags() {
        return itemTags;
    }

    @Override
    public ChunkDataService chunkData() {
        return chunkData;
    }

    @Override
    public CommandTree<CommandSourceStack> commands() {
        return commands;
    }
}
