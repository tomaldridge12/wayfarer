package dev.wayfarer.objectives;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the shop offers a player and at what price: titles and trails they do not own, one extra reroll a day, copies of
 * templates they have owned, Wayfinder pointings and beacon kindling (when exploration is on), supplies, and books of
 * enchants they have unlocked (up to the level unlocked). Never relics, ingots, marks, curios or locked enchants: the
 * config cannot even name them. No server types.
 */
public final class ShopRules {
    /** One thing for sale: its loot key (or a special key), what it is called, its price, and why it cannot be bought now. */
    public record Offer(String id, String section, String label, long price, String blocked) {
        public boolean buyable() {
            return blocked == null;
        }
    }

    /** The shop's price list from config. */
    public record Prices(Map<String, Long> cosmetics, long extraReroll, long templateCopy, long wayfinder, long kindling,
            Map<String, Long> supplies, long enchantPerLevel) {
    }

    /** What the shop needs to know about the player. */
    public record Buyer(long tokens, Set<String> ownedCosmetics, boolean rerollBoughtToday, Set<String> ownedTemplates,
            Map<String, Integer> unlockedEnchants, boolean exploration) {
    }

    private ShopRules() {
    }

    /**
     * The offers, in shop order. {@code names} gives a cosmetic's or template's display name; enchant labels are built
     * from their key and level.
     */
    public static List<Offer> offers(Prices p, Buyer b, Map<String, Cosmetic> cosmetics, Map<String, String> templateNames) {
        List<Offer> out = new ArrayList<>();
        p.cosmetics().forEach((id, price) -> {
            Cosmetic c = cosmetics.get(id);
            if (c != null) {
                out.add(offer("cosmetic:" + id, c.title() ? "Titles" : "Trails", (c.title() ? "Title: " : "") + c.name(), price, b,
                        b.ownedCosmetics().contains(id) ? "You have it" : null));
            }
        });
        out.add(offer("reroll", "Convenience", "An extra free reroll today", p.extraReroll(), b,
                b.rerollBoughtToday() ? "Once a day" : null));
        if (b.exploration()) {
            out.add(offer("wayfinder", "Convenience", "A Wayfinder pointing", p.wayfinder(), b, null));
            out.add(offer("kindling", "Convenience", "Beacon Kindling", p.kindling(), b, null));
        }
        b.ownedTemplates().stream().sorted().forEach(t -> out.add(offer("template:" + t, "Templates",
                "A copy of " + templateNames.getOrDefault(t, t), p.templateCopy(), b, null)));
        p.supplies().forEach((id, price) -> out.add(offer("supply:" + id, "Supplies", CollectionWords.title(id), price, b, null)));
        b.unlockedEnchants().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            for (int level = 1; level <= e.getValue(); level++) {
                out.add(offer("enchant:" + e.getKey() + "_" + level, "Books", CollectionWords.enchant(e.getKey(), level),
                        p.enchantPerLevel() * level, b, null));
            }
        });
        return out;
    }

    private static Offer offer(String id, String section, String label, long price, Buyer b, String blocked) {
        String why = blocked != null ? blocked : b.tokens() < price ? "Needs " + (price - b.tokens()) + " more tokens" : null;
        return new Offer(id, section, label, price, why);
    }

    /** Word helpers shared by the shop's labels. */
    public static final class CollectionWords {
        private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

        private CollectionWords() {
        }

        public static String title(String key) {
            String path = key.substring(key.indexOf(':') + 1);
            StringBuilder out = new StringBuilder();
            for (String w : path.split("_")) {
                if (!w.isEmpty()) {
                    out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
            }
            return out.toString();
        }

        public static String enchant(String key, int level) {
            return title(key) + " " + (level < ROMAN.length ? ROMAN[level] : String.valueOf(level)) + " Book";
        }
    }
}
