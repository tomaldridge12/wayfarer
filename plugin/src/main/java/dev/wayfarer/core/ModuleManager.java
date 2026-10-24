package dev.wayfarer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Enables registered modules, logging and skipping any that are switched off or fail to start. */
public final class ModuleManager {
    private final List<Module> registered = new ArrayList<>();
    private final List<Module> running = new ArrayList<>();

    public void register(Module module) {
        registered.add(module);
    }

    public void enableAll(Core core) {
        for (Module module : registered) {
            String id = module.id();
            if (!core.config().enabled(id)) {
                core.logger().info("Module " + id + " skipped (disabled in config)");
                continue;
            }
            try {
                module.enable(core);
                running.add(module);
                core.logger().info("Module " + id + " enabled");
            } catch (Exception | LinkageError e) {
                try {
                    module.disable(); // cancels whatever it started before failing (timers, stores)
                } catch (Exception | LinkageError cleanup) {
                    core.logger().log(Level.WARNING, "Module " + id + " did not clean up after failing", cleanup);
                }
                core.commands().drop(id);
                core.progression().drop(id);
                core.itemMakers().drop(id);
                core.itemIds().drop(id);
                core.services().drop(id);
                core.rewardEnchants().drop(id);
                core.logger().log(Level.SEVERE, "Module " + id + " failed to start, skipped", e);
            }
        }
    }

    public void disableAll(Core core) {
        for (Module module : running.reversed()) {
            try {
                core.itemMakers().drop(module.id());
                core.itemIds().drop(module.id());
                core.services().drop(module.id());
                module.disable();
            } catch (Exception e) {
                core.logger().log(Level.WARNING, "Module " + module.id() + " failed to disable cleanly", e);
            }
        }
        running.clear();
    }

    public List<String> enabledIds() {
        return running.stream().map(Module::id).toList();
    }
}
