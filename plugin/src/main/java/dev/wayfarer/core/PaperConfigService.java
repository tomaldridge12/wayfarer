package dev.wayfarer.core;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** One `<module id>.yml` per module in the plugin folder, seeded from the jar's default. */
public final class PaperConfigService implements ConfigService {
    private final JavaPlugin plugin;

    public PaperConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private File file(String moduleId) {
        String name = moduleId + ".yml";
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        return file;
    }

    @Override
    public boolean enabled(String moduleId) {
        // Lenient: an unreadable file reads as enabled, so the module's own strict load reports the error.
        return YamlConfiguration.loadConfiguration(file(moduleId)).getBoolean("enabled", true);
    }

    @Override
    public Map<String, Object> load(String moduleId) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file(moduleId));
        } catch (InvalidConfigurationException e) {
            throw new IOException(moduleId + ".yml is not valid YAML: " + e.getMessage(), e);
        }
        return plain(yaml);
    }

    private static Map<String, Object> plain(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key, plainValue(section.get(key)));
        }
        return out;
    }

    private static Object plainValue(Object value) {
        if (value instanceof ConfigurationSection s) {
            return plain(s);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PaperConfigService::plainValue).toList();
        }
        return value;
    }
}
