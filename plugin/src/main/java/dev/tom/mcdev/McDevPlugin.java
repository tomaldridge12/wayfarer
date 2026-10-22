package dev.tom.mcdev;

import org.bukkit.plugin.java.JavaPlugin;

public final class McDevPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("McDev enabled");
    }
}
