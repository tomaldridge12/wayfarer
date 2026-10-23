package dev.wayfarer;

import dev.wayfarer.banners.BannersModule;
import dev.wayfarer.core.ConfigService;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.ModuleManager;
import dev.wayfarer.core.PaperConfigService;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class WayfarerPlugin extends JavaPlugin implements Core {
    private final ModuleManager modules = new ModuleManager();
    private PaperConfigService config;

    @Override
    public void onEnable() {
        config = new PaperConfigService(this);
        modules.register(new BannersModule());
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
}
