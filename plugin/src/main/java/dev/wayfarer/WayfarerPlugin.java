package dev.wayfarer;

import dev.wayfarer.alloys.AlloysModule;
import dev.wayfarer.banners.BannersModule;
import dev.wayfarer.bosses.BossesModule;
import dev.wayfarer.core.ChunkDataService;
import dev.wayfarer.core.CommandTree;
import dev.wayfarer.core.ConfigService;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemMakers;
import dev.wayfarer.core.ItemTagService;
import dev.wayfarer.core.ModuleManager;
import dev.wayfarer.core.PaperConfigService;
import dev.wayfarer.core.RewardEnchants;
import dev.wayfarer.core.pack.PackService;
import dev.wayfarer.core.progression.ProgressionService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class WayfarerPlugin extends JavaPlugin implements Core {
    private final ModuleManager modules = new ModuleManager();
    private PaperConfigService config;
    private ItemTagService itemTags;
    private ChunkDataService chunkData;
    private ProgressionService progression;
    private PackService pack;
    private final CommandTree<CommandSourceStack> commands = new CommandTree<>("wayfarer");
    private final ItemMakers<ItemStack> itemMakers = new ItemMakers<>();
    private final dev.wayfarer.core.ItemIds itemIds = new dev.wayfarer.core.ItemIds();
    private final dev.wayfarer.core.Services services = new dev.wayfarer.core.Services();
    private final dev.wayfarer.core.Xp xp = new dev.wayfarer.core.Xp();
    private final dev.wayfarer.core.ItemCatalogue catalogue = new dev.wayfarer.core.ItemCatalogue();
    private RewardEnchants rewardEnchants;
    private dev.wayfarer.core.Announcer announcer;

    private final dev.wayfarer.core.Trackables trackables = new dev.wayfarer.core.Trackables();
    private dev.wayfarer.core.PlayerBoards boards;
    @Override
    public void onEnable() {
        config = new PaperConfigService(this);
        announcer = new dev.wayfarer.core.Announcer(getServer()::broadcast);
        itemTags = new ItemTagService(this);
        boards = new dev.wayfarer.core.PlayerBoards(getServer());
        chunkData = new ChunkDataService(this);
        progression = new ProgressionService(this, this);
        progression.enable();
        rewardEnchants = new RewardEnchants(getServer(), getLogger());
        pack = new PackService(this, this);
        pack.enable();
        modules.register(new AlloysModule(this)); // first, so banners builds relics on alloy gear from the start
        modules.register(new BannersModule(this));
        modules.register(new BossesModule(this));
        modules.register(new dev.wayfarer.marks.MarksModule(this));
        modules.register(new dev.wayfarer.curios.CuriosModule(this));
        modules.register(new dev.wayfarer.collections.CollectionsModule(this)); // after every module whose items or ladders it lists
        modules.register(new dev.wayfarer.objectives.ObjectivesModule(this)); // after collections, whose Journal it adds pages to
        modules.register(new dev.wayfarer.exploration.ExplorationModule(this));
        modules.register(new dev.wayfarer.enchanting.EnchantingModule(this));
        modules.enableAll(this);
        dev.wayfarer.core.ItemBrowser itemBrowser = new dev.wayfarer.core.ItemBrowser(catalogue, getLogger());
        getServer().getPluginManager().registerEvents(itemBrowser, this);
        commands.register("items", itemBrowser.command());
        // After every plugin has enabled, so ExcellentEnchants can answer.
        getServer().getScheduler().runTask(this, rewardEnchants::check);
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
        if (pack != null) {
            pack.disable();
        }
        if (progression != null) {
            progression.disable();
        }
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
    public dev.wayfarer.core.Announcer announcer() {
        return announcer;
    }

    @Override
    public ItemTagService itemTags() {
        return itemTags;
    }
    @Override
    public dev.wayfarer.core.Trackables trackables() {
        return trackables;
    }

    @Override
    public dev.wayfarer.core.PlayerBoards boards() {
        return boards;
    }


    @Override
    public ChunkDataService chunkData() {
        return chunkData;
    }

    @Override
    public ProgressionService progression() {
        return progression;
    }

    @Override
    public RewardEnchants rewardEnchants() {
        return rewardEnchants;
    }

    @Override
    public ItemMakers<ItemStack> itemMakers() {
        return itemMakers;
    }

    @Override
    public dev.wayfarer.core.ItemCatalogue catalogue() {
        return catalogue;
    }

    @Override
    public dev.wayfarer.core.Services services() {
        return services;
    }

    @Override
    public dev.wayfarer.core.ItemIds itemIds() {
        return itemIds;
    }

    @Override
    public dev.wayfarer.core.Xp xp() {
        return xp;
    }

    @Override
    public CommandTree<CommandSourceStack> commands() {
        return commands;
    }
}
