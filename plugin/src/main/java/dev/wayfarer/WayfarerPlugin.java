package dev.wayfarer;

import dev.wayfarer.banners.BannersModule;
import dev.wayfarer.bosses.BossesModule;
import dev.wayfarer.core.ChunkDataService;
import dev.wayfarer.core.ConfigService;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemTagService;
import dev.wayfarer.core.ModuleManager;
import dev.wayfarer.core.PaperConfigService;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class WayfarerPlugin extends JavaPlugin implements Core {
    private final ModuleManager modules = new ModuleManager();
    private PaperConfigService config;
    private ItemTagService itemTags;
    private ChunkDataService chunkData;

    @Override
    public void onEnable() {
        config = new PaperConfigService(this);
        itemTags = new ItemTagService(this);
        chunkData = new ChunkDataService(this);
        modules.register(new BannersModule(this));
        modules.register(new BossesModule(this));
        modules.enableAll(this);
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
}
