package dev.wayfarer.objectives;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.JournalPages;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.progression.Ladder;
import dev.wayfarer.core.progression.RewardType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Objectives: feats, and (later tickets) dailies, the weekly, the server goal, events, tokens and the shop. */
public final class ObjectivesModule implements Module {
    static final String ADMIN_PERMISSION = "wayfarer.objectives.admin";

    private final JavaPlugin plugin;
    private final ObjectiveVocabulary vocabulary = new PaperObjectiveVocabulary();
    private final List<Listener> listeners = new ArrayList<>();
    private Core core;
    private volatile Map<String, Feat> feats = Map.of();
    private volatile Map<String, Cosmetic> cosmetics = Map.of();
    private volatile ObjectivesService.State pools;
    private ObjectivesService service;
    private ServerGoals goals;
    private Events events;
    private volatile Events.Config eventConfig = Events.Config.empty();
    private volatile ShopRules.Prices prices;
    private CosmeticsDisplay display;

    public ObjectivesModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "objectives";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        core.progression().registerRewardType(id(), FeatRules.FEAT, new FeatMarker());
        core.progression().registerRewardType(id(), "cosmetic", new Cosmetics());
        ObjectivesService[] self = new ObjectivesService[1];
        Keepsakes keepsakes = new Keepsakes(core.itemTags(), () -> pools.goal());
        Loot loot = new Loot(core, () -> self[0], keepsakes);
        load();
        service = new ObjectivesService(plugin, core, () -> pools, loot);
        self[0] = service;
        listen(service);
        service.start();
        goals = new ServerGoals(plugin, core, () -> pools, () -> pools.goal(), service, loot);
        goals.start();
        listen(goals);
        events = new Events(plugin, core, () -> pools, () -> eventConfig, goals, service, loot);
        events.start();
        listen(events);
        display = new CosmeticsDisplay(plugin, service.store(), () -> cosmetics);
        display.start();
        listen(display);
        core.services().get(JournalPages.class).ifPresent(journal -> {
            journal.add(id(), new FeatsPage(core.progression(), () -> feats, journal).page());
            journal.add(id(), new ObjectivesPage(service, () -> pools, journal, goals).page());
            Shop shop = new Shop(core, service, loot, () -> pools, () -> prices, () -> cosmetics, journal, display);
            journal.add(id(), shop.shopPage());
            journal.add(id(), shop.wardrobePage());
        });
        core.commands().register(id(), commands());
    }

    @Override
    public void disable() {
        if (display != null) {
            display.stop();
        }
        if (events != null) {
            events.stop();
        }
        if (goals != null) {
            goals.stop();
        }
        if (service != null) {
            service.stop();
        }
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    /** Reads objectives.yml, registers feats as ladders and their reward enchants; throws if unreadable. */
    private int load() throws IOException {
        ObjectiveConfigParser.Result r = ObjectiveConfigParser.parse(core.config().load(id()), vocabulary);
        List<String> problems = new ArrayList<>(r.problems());
        cosmetics = r.cosmetics();
        feats = r.feats();
        // Rewards are earned once per player, so a reward another module's ladder already pays would never arrive twice.
        Map<String, String> others = new LinkedHashMap<>();
        for (Ladder l : core.progression().ladders()) {
            if (!l.id().startsWith("feat_")) {
                l.rungs().forEach(rung -> others.putIfAbsent(rung.reward(), l.id()));
            }
        }
        feats.values().forEach(f -> f.rewards().stream().filter(others::containsKey).forEach(reward ->
                problems.add("Feat " + f.id() + ": reward " + reward + " is also paid by ladder " + others.get(reward)
                        + "; whichever comes first is the only one a player gets")));
        problems.forEach(p -> core.logger().warning("[objectives] " + p));
        core.progression().setLadders(id(), feats.values().stream().map(FeatRules::ladder).toList());
        PoolConfigParser.Result pool = PoolConfigParser.parse(core.config().load(id()), vocabulary, key -> {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(key);
            return m != null && m.isItem();
        });
        pool.problems().forEach(p -> core.logger().warning("[objectives] " + p));
        problems.addAll(pool.problems());
        pools = new ObjectivesService.State(pool.settings(), pool.daily(), pool.weekly(), pool.goal(), pool.tables());
        Map<String, Object> root = core.config().load(id());
        EventConfigParser.Result ev = EventConfigParser.parse(root.get("events"), vocabulary, pool.tables());
        ev.problems().forEach(p -> core.logger().warning("[objectives] " + p));
        problems.addAll(ev.problems());
        eventConfig = new Events.Config(ev.rotation(), ev.events());
        ShopConfigParser.Result shop = ShopConfigParser.parse(root.get("shop"), cosmetics,
                java.util.Set.copyOf(core.itemIds().ids("supply")));
        shop.problems().forEach(p -> core.logger().warning("[objectives] " + p));
        problems.addAll(shop.problems());
        prices = shop.prices();
        core.logger().info("Shop loaded: " + prices.cosmetics().size() + " cosmetics, " + prices.supplies().size()
                + " supplies, books at " + prices.enchantPerLevel() + " tokens a level, " + shop.problems().size() + " problems");
        Map<String, Integer> enchants = new LinkedHashMap<>(pool.tables().enchants());
        feats.values().forEach(f -> f.rewards().stream().filter(x -> x.startsWith("enchant:")).forEach(x -> dev.wayfarer.core.RewardEnchants.addReward(x, enchants)));
        core.rewardEnchants().register(id(), enchants);
        core.logger().info("Objectives loaded: " + pool.daily().size() + " dailies, " + pool.weekly().size() + " weeklies, "
                + pool.goal().size() + " server goals, "
                + pool.tables().all().size() + " reward tables, " + pool.settings().stages().size() + " stages "
                + pool.settings().stages() + ", dailies turn over at " + pool.settings().schedule().dailyReset() + " "
                + pool.settings().schedule().zone() + ", " + pool.problems().size() + " problems");
        long hidden = feats.values().stream().filter(Feat::hidden).count();
        core.logger().info("Feats loaded: " + feats.size() + " feats (" + hidden + " hidden), " + cosmetics.size() + " cosmetics ("
                + cosmetics.values().stream().filter(Cosmetic::title).count() + " titles), " + problems.size() + " problems");
        return problems.size();
    }


    /** The feat itself: no item, just its name on the title card and in the announcement. */
    private final class FeatMarker implements RewardType {
        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            return null;
        }

        @Override
        public Component title(String id) {
            Feat f = feats.get(id);
            return f == null ? null : ItemLook.name(f.name(), FeatsPage.GOLD);
        }

        @Override
        public Component describe(String id) {
            Feat f = feats.get(id);
            return f == null ? null : title(id).hoverEvent(ItemLook.text(f.flavour(), 0xE4DAC2));
        }

        /** The feat's story, then what it paid. */
        @Override
        public net.kyori.adventure.text.event.HoverEventSource<?> announceHover(String id) {
            Feat f = feats.get(id);
            if (f == null) {
                return null;
            }
            Component hover = ItemLook.text(f.flavour(), 0xE4DAC2);
            if (!f.rewards().isEmpty()) {
                hover = hover.appendNewline().append(ItemLook.text("Reward:", ItemLook.MUTED));
                for (String reward : f.rewards()) {
                    hover = hover.appendNewline().append(ItemLook.text("  ", ItemLook.MUTED))
                            .append(core.progression().rewardTitle(reward));
                }
            }
            return hover;
        }
    }

    /** A title or a trail: no item; owning it lets you wear it (chosen in the Journal). */
    private final class Cosmetics implements RewardType {
        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            return null;
        }

        @Override
        public Component title(String id) {
            Cosmetic c = cosmetics.get(id);
            if (c == null) {
                return null;
            }
            return c.title() ? ItemLook.text("Title: ", 0xE4DAC2).append(ItemLook.name(c.name(), c.colour()))
                    : ItemLook.name(c.name(), 0xC9B8E8);
        }
    }

    private Loot loot() {
        return new Loot(core, () -> service, new Keepsakes(core.itemTags(), () -> pools.goal()));
    }

    /** The player's stage if they are online; offline players draw at the first stage. */
    private String stageOf(UUID id) {
        org.bukkit.entity.Player online = plugin.getServer().getPlayer(id);
        return online != null ? service.stage(online) : pools.settings().stages().getFirst();
    }

    /** Something to do with a player's objectives data and progression record. */
    private interface PlayerAction {
        void run(UUID id, ObjectivesPlayerData data, dev.wayfarer.core.progression.Progression.Snapshot snap);
    }

    private void withPlayer(CommandSender sender, String name, PlayerAction action) {
        UUID id = dev.wayfarer.core.PlayerNames.resolve(plugin.getServer(), name);
        if (id == null) {
            sender.sendMessage("Unknown player '" + name + "' (use their UUID if they have not joined recently)");
            return;
        }
        service.with(id, (data, snap) -> action.run(id, data, snap));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word()).suggests((ctx, b) -> {
            plugin.getServer().getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
            return b.buildFuture();
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("objectives")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("feats").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    feats.values().forEach(f -> sender.sendMessage(f.name() + " (" + f.id() + "): " + (f.distinct() ? "distinct " : "")
                            + f.selector().size() + " activities at " + f.at() + (f.hidden() ? ", hidden" : "") + " -> " + f.rewards()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("show").then(player().executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    withPlayer(sender, StringArgumentType.getString(ctx, "player"), (uuid, data, snap) -> {
                        service.refresh(uuid, data, snap, stageOf(uuid));
                        sender.sendMessage("Objectives for " + uuid + ": " + data.tokens + " tokens, streak " + data.streak);
                        for (int i = 0; i < data.dailies.size(); i++) {
                            ObjectivesPlayerData.Slot slot = data.dailies.get(i);
                            ObjectiveDefinition d = pools.daily().get(slot.id);
                            sender.sendMessage("  daily " + (i + 1) + ": " + slot.id + (d == null ? "" : " " + ObjectiveRules.progress(d,
                                    snap.counters(), slot.baseline) + "/" + d.target()) + (slot.done ? " done" : ""));
                        }
                        if (data.weekly != null) {
                            sender.sendMessage("  weekly: " + data.weekly.id + (data.weekly.done ? " done" : ""));
                        }
                    });
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("tokens").then(player().then(Commands.argument("change", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String[] parts = StringArgumentType.getString(ctx, "change").trim().split("\\s+");
                            withPlayer(sender, StringArgumentType.getString(ctx, "player"), (uuid, data, snap) -> {
                                long n;
                                try {
                                    n = Long.parseLong(parts[parts.length - 1]);
                                } catch (NumberFormatException e) {
                                    sender.sendMessage("Usage: /wayfarer objectives tokens <player> add|set <n>");
                                    return;
                                }
                                long delta = parts[0].equals("set") ? n - data.tokens : n;
                                if (!service.tokens(uuid, data, delta, "admin " + sender.getName())) {
                                    sender.sendMessage("That would take the balance below zero");
                                    return;
                                }
                                sender.sendMessage("Tokens for " + uuid + " now " + data.tokens);
                            });
                            return Command.SINGLE_SUCCESS;
                        }))))
                .then(Commands.literal("complete").then(player().then(Commands.argument("slot", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String which = StringArgumentType.getString(ctx, "slot");
                            withPlayer(sender, StringArgumentType.getString(ctx, "player"), (uuid, data, snap) -> {
                                service.refresh(uuid, data, snap, stageOf(uuid));
                                ObjectivesPlayerData.Slot slot = which.equals("weekly") ? data.weekly
                                        : which.matches("[1-9]") && Integer.parseInt(which) <= data.dailies.size()
                                        ? data.dailies.get(Integer.parseInt(which) - 1) : null;
                                ObjectiveDefinition d = slot == null ? null
                                        : (which.equals("weekly") ? pools.weekly() : pools.daily()).get(slot.id);
                                if (d == null || slot.done) {
                                    sender.sendMessage("No open objective '" + which + "' for " + uuid);
                                    return;
                                }
                                service.complete(uuid, data, slot, d, which.equals("weekly") ? "weekly" : "daily");
                                sender.sendMessage("Completed " + d.id() + " for " + uuid + "; tokens now " + data.tokens);
                            });
                            return Command.SINGLE_SUCCESS;
                        }))))
                .then(Commands.literal("reroll").then(player().then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 6))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            int slot = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot") - 1;
                            org.bukkit.entity.Player target = plugin.getServer().getPlayerExact(StringArgumentType.getString(ctx, "player"));
                            if (target == null) {
                                sender.sendMessage("No online player named '" + StringArgumentType.getString(ctx, "player") + "'");
                                return Command.SINGLE_SUCCESS;
                            }
                            service.with(target.getUniqueId(), (data, snap) -> sender.sendMessage(service.reroll(target, data, snap, slot, true)
                                    ? "Rerolled daily " + (slot + 1) + " for " + target.getName() : "Could not reroll that daily"));
                            return Command.SINGLE_SUCCESS;
                        }))))
                .then(Commands.literal("reset").then(player().executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    withPlayer(sender, StringArgumentType.getString(ctx, "player"), (uuid, data, snap) -> {
                        data.dailyStart = 0;
                        data.weeklyStart = 0;
                        service.refresh(uuid, data, snap, stageOf(uuid));
                        sender.sendMessage("Redrew objectives for " + uuid + ": " + data.dailies.stream().map(x -> x.id).toList());
                    });
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("table").then(Commands.literal("roll").then(Commands.argument("table tier player", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String[] parts = StringArgumentType.getString(ctx, "table tier player").trim().split("\\s+");
                            if (parts.length != 3) {
                                sender.sendMessage("Usage: /wayfarer objectives table roll <table> <tier> <player>");
                                return Command.SINGLE_SUCCESS;
                            }
                            withPlayer(sender, parts[2], (uuid, data, snap) -> {
                                java.util.List<String> rolled = pools.tables().roll(parts[0], parts[1], r -> service.owns(uuid, data, r),
                                        java.util.concurrent.ThreadLocalRandom.current());
                                if (rolled.isEmpty()) {
                                    sender.sendMessage("Table " + parts[0] + " has no " + parts[1] + " tier");
                                    return;
                                }
                                loot().give(uuid, data, rolled, "admin roll " + parts[0] + " " + parts[1]);
                                sender.sendMessage("Rolled " + parts[0] + " " + parts[1] + " for " + uuid + ": " + rolled);
                            });
                            return Command.SINGLE_SUCCESS;
                        }))))
                .then(Commands.literal("event")
                        .then(Commands.literal("start").then(Commands.argument("event", StringArgumentType.word()).suggests((ctx, b) -> {
                            eventConfig.events().keySet().forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "event");
                            EventDefinition e = eventConfig.events().get(id);
                            if (e == null) {
                                ctx.getSource().getSender().sendMessage("Unknown event '" + id + "' (" + eventConfig.events().keySet() + ")");
                            } else {
                                events.begin(e, System.currentTimeMillis(), true);
                                ctx.getSource().getSender().sendMessage("Started " + e.name() + " until /wayfarer objectives event stop");
                            }
                            return Command.SINGLE_SUCCESS;
                        })))
                        .then(Commands.literal("stop").executes(ctx -> {
                            EventDefinition e = events.running();
                            events.end("stopped by command");
                            ctx.getSource().getSender().sendMessage(e == null ? "No event is running" : "Stopped " + e.name());
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("goal")
                        .then(Commands.literal("show").executes(ctx -> {
                            ObjectiveDefinition g = goals.goal();
                            ServerGoalState st = goals.state();
                            ctx.getSource().getSender().sendMessage(g == null ? "No server goal" : "Server goal " + g.id() + ": "
                                    + ServerGoalRules.total(st.contributions) + " / " + g.target() + " (" + st.contributions.size()
                                    + " contributors" + (st.completed ? ", completed" : "") + ")");
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("new").executes(ctx -> {
                            goals.state().weekStart = 0;
                            goals.rollover(true);
                            ctx.getSource().getSender().sendMessage("New server goal: " + goals.state().goal);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("contribute").then(player().then(Commands.argument("amount",
                                com.mojang.brigadier.arguments.LongArgumentType.longArg(0)).executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    UUID id = dev.wayfarer.core.PlayerNames.resolve(plugin.getServer(), StringArgumentType.getString(ctx, "player"));
                                    if (id == null || goals.goal() == null) {
                                        sender.sendMessage("Unknown player, or no server goal");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    long amount = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "amount");
                                    goals.contribute(id.toString(), goals.state().contributions.getOrDefault(id.toString(), 0L) + amount);
                                    sender.sendMessage("Server goal now " + ServerGoalRules.total(goals.state().contributions) + " / "
                                            + goals.goal().target());
                                    return Command.SINGLE_SUCCESS;
                                })))))
                .then(Commands.literal("reload").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    try {
                        int problems = load();
                        core.rewardEnchants().check();
                        sender.sendMessage("Objectives reloaded: " + feats.size() + " feats, " + problems + " problems");
                    } catch (IOException e) {
                        sender.sendMessage("Objectives reload failed, keeping the old config: " + e.getMessage());
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
