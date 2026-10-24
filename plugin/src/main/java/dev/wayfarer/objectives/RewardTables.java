package dev.wayfarer.objectives;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Themed reward tables in tiers (daily, weekly, goal, event): weighted entries of tokens, supplies, books, curios,
 * special gear, cosmetics and vanilla items. A roll gives loot keys ({@code curio:moss_heart}, {@code tokens:20},
 * {@code supply:seed_bundle*2}, {@code enchant:excellentenchants:haste_2}, {@code item:diamond*3}); a unique reward the
 * player already owns turns into tokens. No server types.
 */
public final class RewardTables {
    public static final List<String> TIERS = List.of("daily", "weekly", "goal", "event");

    /** One weighted line: a reward key ({@code enchant:excellentenchants:haste}, {@code item:diamond}) and a range. */
    public record Entry(String reward, int weight, int min, int max) {
        /** Curios and cosmetics are one of a kind: owning one already turns a roll of it into tokens. */
        public boolean unique() {
            return reward.startsWith("curio:") || reward.startsWith("cosmetic:");
        }
    }

    private final Map<String, Map<String, List<Entry>>> tables;
    private final int duplicateTokens;

    public RewardTables(Map<String, Map<String, List<Entry>>> tables, int duplicateTokens) {
        this.tables = Map.copyOf(tables);
        this.duplicateTokens = duplicateTokens;
    }

    public boolean has(String table, String tier) {
        return !tables.getOrDefault(table, Map.of()).getOrDefault(tier, List.of()).isEmpty();
    }

    public Map<String, Map<String, List<Entry>>> all() {
        return tables;
    }

    /** One weighted roll from a table's tier; empty when it has none. {@code owned} answers for unique rewards. */
    public List<String> roll(String table, String tier, Predicate<String> owned, RandomGenerator random) {
        List<Entry> entries = tables.getOrDefault(table, Map.of()).getOrDefault(tier, List.of());
        int total = entries.stream().mapToInt(Entry::weight).sum();
        if (total <= 0) {
            return List.of();
        }
        int pick = random.nextInt(total);
        for (Entry e : entries) {
            pick -= e.weight();
            if (pick < 0) {
                return List.of(resolve(e, owned, random));
            }
        }
        return List.of();
    }

    private String resolve(Entry e, Predicate<String> owned, RandomGenerator random) {
        int n = e.min() + (e.max() > e.min() ? random.nextInt(e.max() - e.min() + 1) : 0);
        if (e.unique() && owned.test(e.reward())) {
            return "tokens:" + duplicateTokens;
        }
        if (e.reward().startsWith("tokens")) {
            return "tokens:" + n;
        }
        if (e.reward().startsWith("enchant:")) {
            return e.reward() + "_" + n; // a level from the range
        }
        if (e.unique() || e.reward().startsWith("gear:")) {
            return e.reward();
        }
        return n > 1 ? e.reward() + "*" + n : e.reward();
    }

    /** Every ExcellentEnchants key the tables can give, at the highest level, for core's reward-only check. */
    public Map<String, Integer> enchants() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        tables.values().forEach(tiers -> tiers.values().forEach(list -> list.stream().filter(e -> e.reward().startsWith("enchant:"))
                .forEach(e -> out.merge(e.reward().substring(8), e.max(), Math::max))));
        return out;
    }

    /** Splits a loot key's amount: "supply:seed_bundle*3" gives ("supply:seed_bundle", 3). */
    public record Loot(String what, int amount) {
        public static Loot of(String text) {
            int star = text.lastIndexOf('*');
            if (star > 0 && text.substring(star + 1).matches("\\d+")) {
                return new Loot(text.substring(0, star), Integer.parseInt(text.substring(star + 1)));
            }
            return new Loot(text, 1);
        }
    }
}
