package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommandTreeTest {
    /** A command source that holds a set of permissions. */
    private record Source(Set<String> permissions) {
        boolean has(String permission) {
            return permissions.contains(permission);
        }
    }

    private final List<String> ran = new ArrayList<>();

    private LiteralArgumentBuilder<Source> sub(String name, String permission) {
        return LiteralArgumentBuilder.<Source>literal(name)
                .requires(s -> s.has(permission))
                .executes(ctx -> {
                    ran.add(name);
                    return 1;
                });
    }

    private static CommandDispatcher<Source> dispatcher(CommandTree<Source> tree) {
        CommandDispatcher<Source> d = new CommandDispatcher<>();
        d.getRoot().addChild(tree.build());
        tree.aliases().forEach(d.getRoot()::addChild);
        return d;
    }

    @Test
    void rootNeedsNoPermissionAndEachSubcommandChecksItsOwn() throws CommandSyntaxException {
        CommandTree<Source> tree = new CommandTree<>("wayfarer");
        tree.register("banners", sub("progress", "banners.progress"));
        tree.register("bosses", sub("boss", "bosses.admin"));
        CommandNode<Source> root = tree.build();
        Source player = new Source(Set.of("banners.progress"));

        assertTrue(root.canUse(player));
        dispatcher(tree).execute("wayfarer progress", player);
        assertEquals(List.of("progress"), ran);
        assertThrows(CommandSyntaxException.class, () -> dispatcher(tree).execute("wayfarer boss", player));
    }

    @Test
    void aliasCarriesOnlyItsOwnersSubcommands() throws CommandSyntaxException {
        CommandTree<Source> tree = new CommandTree<>("wayfarer");
        tree.register("banners", sub("claim", "banners.claim"));
        tree.register("bosses", sub("boss", "bosses.admin"));
        tree.alias("banners", "mobbanners");
        Source admin = new Source(Set.of("banners.claim", "bosses.admin"));

        dispatcher(tree).execute("mobbanners claim", admin);
        assertEquals(List.of("claim"), ran);
        assertThrows(CommandSyntaxException.class, () -> dispatcher(tree).execute("mobbanners boss", admin));
    }

    @Test
    void droppedOwnerLosesItsSubcommandsAndAliases() {
        CommandTree<Source> tree = new CommandTree<>("wayfarer");
        tree.register("banners", sub("claim", "banners.claim"));
        tree.alias("banners", "mobbanners");
        tree.register("bosses", sub("boss", "bosses.admin"));
        tree.drop("banners");

        assertEquals(List.of("boss"), tree.build().getChildren().stream().map(CommandNode::getName).toList());
        assertTrue(tree.aliases().isEmpty());
        assertEquals("bosses [boss]", tree.describe());
    }

    @Test
    void aSubcommandNameTakenByAnotherOwnerIsRefused() {
        CommandTree<Source> tree = new CommandTree<>("wayfarer");
        tree.register("banners", sub("reload", "banners.reload"));
        assertThrows(IllegalArgumentException.class, () -> tree.register("bosses", sub("reload", "bosses.admin")));
    }

    @Test
    void describeListsOwnersAndSubcommandsInRegistrationOrder() {
        CommandTree<Source> tree = new CommandTree<>("wayfarer");
        tree.register("banners", sub("progress", "p"));
        tree.register("banners", sub("claim", "c"));
        tree.register("bosses", sub("boss", "b"));
        assertEquals("banners [progress, claim], bosses [boss]", tree.describe());
    }
}
