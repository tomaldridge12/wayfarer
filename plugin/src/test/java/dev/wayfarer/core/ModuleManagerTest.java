package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import dev.wayfarer.core.progression.Ladder;
import dev.wayfarer.core.progression.Progression;
import dev.wayfarer.core.progression.RewardType;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    /** Records which owners were dropped; everything else does nothing. */
    private static final class StubProgression implements Progression {
        final List<String> dropped = new java.util.ArrayList<>();

        @Override
        public void registerRewardType(String owner, String type, RewardType rewardType) {
        }

        @Override
        public void setLadders(String owner, List<Ladder> ladders) {
        }

        @Override
        public void drop(String owner) {
            dropped.add(owner);
        }

        @Override
        public void record(UUID player, String activity, String how) {
        }

        @Override
        public void award(UUID player, String reward, String how, boolean announce) {
        }

        @Override
        public OptionalLong progressIfLoaded(UUID player, String ladderId) {
            return OptionalLong.empty();
        }

        @Override
        public CompletableFuture<Void> load(UUID player) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static Core core(Set<String> disabled, CommandTree<CommandSourceStack> commands) {
        return core(disabled, commands, new StubProgression());
    }

    private static Core core(Set<String> disabled, CommandTree<CommandSourceStack> commands, Progression progression) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        ItemMakers<org.bukkit.inventory.ItemStack> makers = new ItemMakers<>();
        ItemIds ids = new ItemIds();
        Services services = new Services();
        return new Core() {
            @Override
            public Services services() {
                return services;
            }

            @Override
            public Trackables trackables() {
                return new Trackables();
            }

            @Override
            public PlayerBoards boards() {
                return null;
            }

            @Override
            public Announcer announcer() {
                return new Announcer(line -> {
                });
            }

            @Override
            public ItemIds itemIds() {
                return ids;
            }

            @Override
            public Logger logger() {
                return logger;
            }

            @Override
            public ItemMakers<org.bukkit.inventory.ItemStack> itemMakers() {
                return makers;
            }

            @Override
            public RewardEnchants rewardEnchants() {
                return new RewardEnchants(null, logger);
            }

            @Override
            public ItemTagService itemTags() {
                return null;
            }

            @Override
            public Xp xp() {
                return null;
            }

            @Override
            public ChunkDataService chunkData() {
                return null;
            }

            @Override
            public Progression progression() {
                return progression;
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
        StubProgression progression = new StubProgression();
        manager.enableAll(core(Set.of("off"), commands, progression));
        assertEquals("good [good]", commands.describe());
        assertEquals(List.of("broken"), progression.dropped);
    }
}
