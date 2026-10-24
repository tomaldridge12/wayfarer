package dev.wayfarer.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The /wayfarer command root, built from the subtrees modules register. The root carries no permission; each
 * subtree checks its own. An owner's subtrees can also appear under an alias root (/mobbanners).
 */
public final class CommandTree<S> {
    private final String root;
    private final Map<String, List<LiteralArgumentBuilder<S>>> byOwner = new LinkedHashMap<>();
    private final Map<String, AliasRoot> aliasRoots = new LinkedHashMap<>();

    /** An alias root: every subtree of its owner, plus the named subcommands it borrows from other owners. */
    private record AliasRoot(String owner, Set<String> borrowed) {
    }

    public CommandTree(String root) {
        this.root = root;
    }

    /** Adds a subcommand under the root; throws if another owner already registered that name. */
    public void register(String owner, LiteralArgumentBuilder<S> subtree) {
        byOwner.forEach((other, subs) -> {
            if (!other.equals(owner) && subs.stream().anyMatch(s -> s.getLiteral().equals(subtree.getLiteral()))) {
                throw new IllegalArgumentException("/" + root + " " + subtree.getLiteral() + " is already registered by " + other);
            }
        });
        byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(subtree);
    }

    /**
     * Makes every subtree of the owner also run as /alias ..., plus the named subcommands of other owners (the
     * old /mobbanners progress and claim, now core's).
     */
    public void alias(String owner, String alias, String... borrowed) {
        aliasRoots.put(alias, new AliasRoot(owner, Set.of(borrowed)));
    }

    /** Forgets everything the owner registered, for a module that failed to start. */
    public void drop(String owner) {
        byOwner.remove(owner);
        aliasRoots.values().removeIf(a -> a.owner().equals(owner));
    }

    public LiteralCommandNode<S> build() {
        LiteralArgumentBuilder<S> node = LiteralArgumentBuilder.literal(root);
        byOwner.values().forEach(subs -> subs.forEach(node::then));
        return node.build();
    }

    public List<LiteralCommandNode<S>> aliases() {
        List<LiteralCommandNode<S>> nodes = new ArrayList<>();
        aliasRoots.forEach((alias, aliasRoot) -> {
            if (!byOwner.containsKey(aliasRoot.owner())) {
                return;
            }
            LiteralArgumentBuilder<S> node = LiteralArgumentBuilder.literal(alias);
            byOwner.forEach((owner, subs) -> subs.stream()
                    .filter(s -> owner.equals(aliasRoot.owner()) || aliasRoot.borrowed().contains(s.getLiteral()))
                    .forEach(node::then));
            nodes.add(node.build());
        });
        return nodes;
    }

    /** "banners [progress, claim], bosses [boss]", for the log. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        byOwner.forEach((owner, subs) -> parts.add(owner + " "
                + subs.stream().map(LiteralArgumentBuilder::getLiteral).toList()));
        return String.join(", ", parts);
    }
}
