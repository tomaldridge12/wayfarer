package dev.wayfarer.objectives;

import com.mojang.brigadier.Command;
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
        load();
        core.services().get(JournalPages.class).ifPresent(journal ->
                journal.add(id(), new FeatsPage(core.progression(), () -> feats, journal).page()));
        core.commands().register(id(), commands());
    }

    @Override
    public void disable() {
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    @SuppressWarnings("unused")
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
        Map<String, Integer> enchants = new LinkedHashMap<>();
        feats.values().forEach(f -> f.rewards().stream().filter(x -> x.startsWith("enchant:")).forEach(x -> enchantOf(x, enchants)));
        core.rewardEnchants().register(id(), enchants);
        long hidden = feats.values().stream().filter(Feat::hidden).count();
        core.logger().info("Feats loaded: " + feats.size() + " feats (" + hidden + " hidden), " + cosmetics.size() + " cosmetics ("
                + cosmetics.values().stream().filter(Cosmetic::title).count() + " titles), " + problems.size() + " problems");
        return problems.size();
    }

    /** "enchant:excellentenchants:wisdom_3" adds excellentenchants:wisdom at level 3. */
    static void enchantOf(String reward, Map<String, Integer> into) {
        String id = reward.substring("enchant:".length());
        int cut = id.lastIndexOf('_');
        if (cut > 0 && id.substring(cut + 1).matches("\\d+")) {
            into.merge(id.substring(0, cut), Integer.parseInt(id.substring(cut + 1)), Math::max);
        }
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
            return f == null ? null : title(id).hoverEvent(ItemLook.text(f.flavour(), 0xB8AC94));
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
            return c.title() ? ItemLook.text("Title: ", 0xB8AC94).append(ItemLook.name(c.name(), c.colour()))
                    : ItemLook.name(c.name(), 0xC9B8E8);
        }
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
