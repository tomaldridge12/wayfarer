package dev.wayfarer.banners;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.tree.LiteralCommandNode;

/** The /mobbanners command tree; each branch has an explicit permission and does its work in the module. */
final class BannerCommands {
    private BannerCommands() {
    }

    static LiteralCommandNode<CommandSourceStack> build(BannersModule m) {
        return Commands.literal("mobbanners")
                .then(Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission(BannersModule.RELOAD_PERMISSION))
                        .executes(ctx -> run(() -> m.reload(ctx.getSource().getSender()))))
                .then(Commands.literal("give")
                        .requires(s -> s.getSender().hasPermission(BannersModule.GIVE_PERMISSION))
                        .then(player(m).then(banner(m).executes(ctx -> run(() -> m.give(
                                ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "banner")))))))
                .then(Commands.literal("progress")
                        .requires(s -> s.getSender().hasPermission(BannersModule.PROGRESS_PERMISSION))
                        .executes(ctx -> run(() -> m.progress(ctx.getSource().getSender(), null)))
                        .then(player(m)
                                .requires(s -> s.getSender().hasPermission(BannersModule.PROGRESS_OTHERS_PERMISSION))
                                .executes(ctx -> run(() -> m.progress(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("setkills")
                        .requires(s -> s.getSender().hasPermission(BannersModule.SETKILLS_PERMISSION))
                        .then(player(m).then(banner(m).then(
                                Commands.argument("kills", IntegerArgumentType.integer(0))
                                        .executes(ctx -> run(() -> m.setKills(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "banner"),
                                                IntegerArgumentType.getInteger(ctx, "kills"))))))))
                .then(Commands.literal("place")
                        .requires(s -> s.getSender().hasPermission(BannersModule.PLACE_PERMISSION))
                        .then(banner(m).then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> run(() -> place(m, ctx, null)))
                                                .then(Commands.argument("world", StringArgumentType.word())
                                                        .executes(ctx -> run(() -> place(m, ctx,
                                                                StringArgumentType.getString(ctx, "world"))))))))))
                .then(Commands.literal("claim")
                        .requires(s -> s.getSender().hasPermission(BannersModule.CLAIM_PERMISSION))
                        .then(banner(m).executes(ctx -> run(() -> m.claim(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "banner"))))))
                .build();
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

    private static RequiredArgumentBuilder<CommandSourceStack, String> banner(BannersModule m) {
        return Commands.argument("banner", StringArgumentType.word()).suggests((ctx, builder) -> {
            m.banners().banners().keySet().forEach(builder::suggest);
            return builder.buildFuture();
        });
    }
}
