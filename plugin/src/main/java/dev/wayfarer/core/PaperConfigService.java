package dev.wayfarer.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** One `<module id>.yml` per module in the plugin folder, seeded from the jar's default. */
public final class PaperConfigService implements ConfigService {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> loaded = new HashMap<>();

    public PaperConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public YamlConfiguration module(String moduleId) {
        return loaded.computeIfAbsent(moduleId, id -> {
            String name = id + ".yml";
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists() && plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            }
            return YamlConfiguration.loadConfiguration(file);
        });
    }

    @Override
    public boolean enabled(String moduleId) {
        return module(moduleId).getBoolean("enabled", true);
    }
}
