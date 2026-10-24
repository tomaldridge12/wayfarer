package dev.wayfarer.banners;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;

/** The banners subcommands under /wayfarer (and /mobbanners); each has an explicit permission and does its work in the module. */
final class BannerCommands {
    private BannerCommands() {
    }

    static List<LiteralArgumentBuilder<CommandSourceStack>> build(BannersModule m) {
        return List.of(
                Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission(BannersModule.RELOAD_PERMISSION))
                        .executes(ctx -> run(() -> m.reload(ctx.getSource().getSender()))),
                Commands.literal("give")
                        .requires(s -> s.getSender().hasPermission(BannersModule.GIVE_PERMISSION))
                        .then(player(m).then(banner(m).executes(ctx -> run(() -> m.give(
                                ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "banner")))))),
                Commands.literal("progress")
                        .requires(s -> s.getSender().hasPermission(BannersModule.PROGRESS_PERMISSION))
                        .executes(ctx -> run(() -> m.progress(ctx.getSource().getSender(), null)))
                        .then(player(m)
                                .requires(s -> s.getSender().hasPermission(BannersModule.PROGRESS_OTHERS_PERMISSION))
                                .executes(ctx -> run(() -> m.progress(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"))))),
                Commands.literal("setkills")
                        .requires(s -> s.getSender().hasPermission(BannersModule.SETKILLS_PERMISSION))
                        .then(player(m).then(banner(m).then(
                                Commands.argument("kills", IntegerArgumentType.integer(0))
                                        .executes(ctx -> run(() -> m.setKills(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "banner"),
                                                IntegerArgumentType.getInteger(ctx, "kills"))))))),
                Commands.literal("place")
                        .requires(s -> s.getSender().hasPermission(BannersModule.PLACE_PERMISSION))
                        .then(banner(m).then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> run(() -> place(m, ctx, null)))
                                                .then(Commands.argument("world", StringArgumentType.word())
                                                        .executes(ctx -> run(() -> place(m, ctx,
                                                                StringArgumentType.getString(ctx, "world"))))))))),
                Commands.literal("placehead")
                        .requires(s -> s.getSender().hasPermission(BannersModule.PLACE_PERMISSION))
                        .then(player(m).then(Commands.argument("head", StringArgumentType.word())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> run(() -> m.placeHead(
                                                                ctx.getSource().getSender(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "head"),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"))))))))),
                Commands.literal("forcedrop")
                        .requires(s -> s.getSender().hasPermission(BannersModule.FORCEDROP_PERMISSION))
                        .then(player(m).then(reward(m, false).executes(ctx -> run(() -> m.forceDrop(
                                ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "reward")))))),
                Commands.literal("claim")
                        .requires(s -> s.getSender().hasPermission(BannersModule.CLAIM_PERMISSION))
                        .then(reward(m, true).executes(ctx -> run(() -> m.claim(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "reward"))))));
    }

    private static void place(BannersModule m, CommandContext<CommandSourceStack> ctx, String world) {
        m.place(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "banner"),
                IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
                IntegerArgumentType.getInteger(ctx, "z"), world);
    }

    private static int run(Runnable action) {
        action.run();
        return Command.SINGLE_SUCCESS;
    }

    /** An online player name, or an offline player's name or UUID. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> player(BannersModule m) {
        return Commands.argument("player", StringArgumentType.word()).suggests((ctx, builder) -> {
            m.onlineNames().forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    /**
     * A reward such as "banner:zombie" or a bare "zombie" (a banner). It reads the rest of the line, because
     * single-word arguments cannot contain ':'. Suggests the sender's earned rewards, or every reward for operators.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> reward(BannersModule m, boolean earnedOnly) {
        return Commands.argument("reward", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
            (earnedOnly ? m.earnedKeys(ctx.getSource().getSender()) : m.rewardKeys()).forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> banner(BannersModule m) {
        return Commands.argument("banner", StringArgumentType.word()).suggests((ctx, builder) -> {
            m.banners().banners().keySet().forEach(builder::suggest);
            return builder.buildFuture();
        });
    }
}
