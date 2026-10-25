package dev.wayfarer.marks;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.RecipeGuard;
import dev.wayfarer.core.progression.RewardType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Marks: the ladders for the dull jobs, mark templates and their effects on tools, and the reward-only books. */
public final class MarksModule implements Module {
    static final String ADMIN_PERMISSION = "wayfarer.marks.admin";

    private final JavaPlugin plugin;
    private final MarkVocabulary vocabulary = new PaperMarkVocabulary();
    private final List<Listener> listeners = new ArrayList<>();
    private Core core;
    private volatile MarkRules rules = MarkRules.empty();
    private MarkItems items;
    private MarkRecipes recipes;

    public MarksModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "marks";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        items = new MarkItems(core.itemTags(), () -> rules);
        recipes = new MarkRecipes(plugin, () -> rules, items);
        load();
        // Templates are never bricks. Marked tools stay vanilla tools (a netherite upgrade keeps the mark).
        listen(new RecipeGuard(i -> items.templateOf(i).isPresent()));
        listen(recipes);
        listen(new AreaMarks(() -> rules, items));
        FlockAndRodMarks flockAndRod = new FlockAndRodMarks(() -> rules, items,
                (player, activity) -> core.progression().record(player, activity, "a marked tool"), core.logger());
        listen(flockAndRod);
        core.logger().info("Angler treasure check: " + flockAndRod.treasure()
                .map(i -> "rolled " + i.getType().getKey().getKey()).orElse("no roll"));
        core.progression().registerRewardType(id(), MarkRules.MARK, new Templates());
        core.progression().registerRewardType(id(), MarkRules.BOOK, new Books());
        core.commands().register(id(), commands());
        core.catalogue().add(id(), "mark_templates", Component.text("Mark templates", NamedTextColor.WHITE), Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                viewer -> rules.marks().stream().flatMap(m -> java.util.stream.IntStream.rangeClosed(1, m.topRank())
                        .mapToObj(rank -> items.template(m.id(), rank))).flatMap(java.util.Optional::stream).toList());
        core.catalogue().add(id(), "mark_books", Component.text("Mark books", NamedTextColor.WHITE), Material.ENCHANTED_BOOK,
                viewer -> rules.books().stream().flatMap(b -> java.util.stream.IntStream.rangeClosed(1, b.thresholds().size())
                        .mapToObj(level -> items.book(b.id(), level))).flatMap(java.util.Optional::stream).toList());
    }

    @Override
    public void disable() {
        if (recipes != null) {
            recipes.unregister();
        }
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    /** Reads marks.yml and registers its ladders and reward enchants; throws if the file is unreadable. */
    private int load() throws IOException {
        MarkConfigParser.Result r = MarkConfigParser.parse(core.config().load(id()), vocabulary);
        r.problems().forEach(p -> core.logger().warning("[marks] " + p));
        r.notices().forEach(n -> core.logger().info("[marks] " + n));
        rules = r.rules();
        core.progression().setLadders(id(), rules.ladders());
        core.rewardEnchants().register(id(), rules.rewardEnchants());
        core.logger().info("Marks loaded: " + rules.marks().size() + " marks ("
                + String.join(", ", rules.marks().stream().map(MarkDefinition::id).toList()) + "), "
                + rules.books().size() + " books (" + String.join(", ", rules.books().stream().map(BookDefinition::id).toList())
                + "), " + r.problems().size() + " problems");
        core.logger().info("Mark ladders: " + rules.ladders().size() + " ("
                + String.join(", ", rules.ladders().stream().map(l -> l.id() + " " + l.selector().size()).toList()) + ")");
        core.logger().info("Mark recipes registered: " + recipes.register() + " live");
        return r.problems().size();
    }

    MarkRules rules() {
        return rules;
    }

    private final class Templates implements RewardType {
        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            return rules.rewardId(id).flatMap(t -> items.template(t.mark(), t.rank())).orElse(null);
        }

        @Override
        public Component title(String id) {
            return rules.rewardId(id).flatMap(t -> rules.mark(t.mark()).map(m -> items.title(m, t.rank()))).orElse(null);
        }
    }

    private final class Books implements RewardType {
        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            ItemStack book = rules.bookId(id).flatMap(t -> items.book(t.mark(), t.rank())).orElse(null);
            if (book == null) {
                core.logger().warning("[marks] Book " + id + " cannot be made (is ExcellentEnchants installed?); "
                        + "it stays earned and can be claimed later");
            }
            return book;
        }

        @Override
        public Component title(String id) {
            return rules.bookId(id).flatMap(t -> rules.book(t.mark()).map(b ->
                    Component.text(b.name() + " " + MarkItems.roman(t.rank()) + " Book", TextColor.color(0x279CF5))
                            .decoration(TextDecoration.ITALIC, false)))
                    .orElse(null);
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("marks")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("list").executes(ctx -> {
                    list(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    try {
                        int problems = load();
                        core.rewardEnchants().check();
                        sender.sendMessage("Marks reloaded: " + rules.marks().size() + " marks, " + rules.books().size()
                                + " books, " + problems + " problems");
                    } catch (IOException e) {
                        sender.sendMessage("Marks reload failed, keeping the old config: " + e.getMessage());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests((ctx, b) -> {
                            plugin.getServer().getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        }).then(Commands.argument("template", StringArgumentType.word()).suggests((ctx, b) -> {
                            rules.marks().forEach(m -> {
                                for (int r = 1; r <= m.topRank(); r++) {
                                    b.suggest(m.id() + "_" + r);
                                }
                            });
                            return b.buildFuture();
                        }).executes(ctx -> {
                            give(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "template"));
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    private void list(CommandSender sender) {
        for (MarkDefinition m : rules.marks()) {
            sender.sendMessage(m.name() + " Mark: " + m.tool().label() + ", ranks at " + m.thresholds());
        }
        for (BookDefinition b : rules.books()) {
            sender.sendMessage(b.name() + " book (" + b.enchant() + "): levels at " + b.thresholds() + ", along "
                    + b.selector().size() + " activities");
        }
    }

    private void give(CommandSender sender, String playerName, String template) {
        Player player = plugin.getServer().getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage("No online player named '" + playerName + "'");
            return;
        }
        ItemStack item = rules.rewardId(template).flatMap(t -> items.template(t.mark(), t.rank())).orElse(null);
        if (item == null) {
            sender.sendMessage("Unknown mark template '" + template + "' (try dune_1)");
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        sender.sendMessage("Gave " + template + " to " + player.getName());
    }
}
