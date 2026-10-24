package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ModuleManagerTest {
    private record Stub(String id, boolean fails) implements Module {
        @Override
        public void enable(Core core) throws Exception {
            core.commands().register(id, LiteralArgumentBuilder.literal(id));
            if (fails) {
                throw new Exception("boom");
            }
        }

        @Override
        public void disable() {
        }
    }

    private static Core core(Set<String> disabled) {
        return core(disabled, new CommandTree<>("wayfarer"));
    }

    private static Core core(Set<String> disabled, CommandTree<CommandSourceStack> commands) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return new Core() {
            @Override
            public Logger logger() {
                return logger;
            }

            @Override
            public ItemTagService itemTags() {
                return null;
            }

            @Override
            public ChunkDataService chunkData() {
                return null;
            }

            @Override
            public CommandTree<CommandSourceStack> commands() {
                return commands;
            }

            @Override
            public ConfigService config() {
                return new ConfigService() {
                    @Override
                    public boolean enabled(String id) {
                        return !disabled.contains(id);
                    }

                    @Override
                    public java.util.Map<String, Object> load(String id) {
                        return java.util.Map.of();
                    }
                };
            }
        };
    }

    @Test
    void failingAndDisabledModulesAreSkippedOthersRun() {
        ModuleManager manager = new ModuleManager();
        manager.register(new Stub("broken", true));
        manager.register(new Stub("off", false));
        manager.register(new Stub("good", false));
        manager.enableAll(core(Set.of("off")));
        assertEquals(List.of("good"), manager.enabledIds());
    }

    @Test
    void onlyRunningModulesKeepTheirSubcommands() {
        CommandTree<CommandSourceStack> commands = new CommandTree<>("wayfarer");
        ModuleManager manager = new ModuleManager();
        manager.register(new Stub("broken", true));
        manager.register(new Stub("off", false));
        manager.register(new Stub("good", false));
        manager.enableAll(core(Set.of("off"), commands));
        assertEquals("good [good]", commands.describe());
    }
}
