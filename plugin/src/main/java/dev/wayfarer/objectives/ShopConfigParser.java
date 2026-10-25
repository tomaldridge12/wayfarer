package dev.wayfarer.objectives;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shop section of objectives.yml. Only the categories ShopRules knows can be priced, so power (relics, ingots,
 * marks, curios, locked enchants) can never be put on sale; anything else is refused with a problem.
 */
public final class ShopConfigParser {
    static final Set<String> KEYS = Set.of("cosmetics", "extra-reroll", "template-copy", "wayfinder", "beacon-kindling", "supplies",
            "enchant-per-level");

    public record Result(ShopRules.Prices prices, List<String> problems) {
    }

    private ShopConfigParser() {
    }

    public static Result parse(Object section, Map<String, Cosmetic> cosmetics, Set<String> supplyIds) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> m = ObjectiveConfigParser.asMap(section);
        m.keySet().stream().filter(k -> !KEYS.contains(k)).forEach(k -> problems.add("Shop: '" + k
                + "' cannot be sold (the shop sells cosmetics, an extra reroll, template copies, Wayfinder pointings, beacon"
                + " kindling, supplies and unlocked enchants only); ignored"));
        Map<String, Long> cosmeticPrices = new LinkedHashMap<>();
        ObjectiveConfigParser.asMap(m.get("cosmetics")).forEach((id, price) -> {
            if (!cosmetics.containsKey(id)) {
                problems.add("Shop: cosmetic " + id + " is not in cosmetics; not sold");
            } else if (cosmetics.get(id).hat() != null && !cosmetics.get(id).hat().plain()) {
                problems.add("Shop: cosmetic " + id + " is a hat with an ability, and the shop never sells power; not sold");
            } else if (price(price) < 1) {
                problems.add("Shop: cosmetic " + id + " needs a price of 1 or more; not sold");
            } else {
                cosmeticPrices.put(id, price(price));
            }
        });
        Map<String, Long> supplies = new LinkedHashMap<>();
        ObjectiveConfigParser.asMap(m.get("supplies")).forEach((id, price) -> {
            if (id.contains(":")) {
                problems.add("Shop: supplies takes supply ids only ('" + id + "' is not one); not sold");
            } else if (!supplyIds.isEmpty() && !supplyIds.contains(id)) {
                problems.add("Shop: supply " + id + " is not a supply; not sold");
            } else if (price(price) < 1) {
                problems.add("Shop: supply " + id + " needs a price of 1 or more; not sold");
            } else {
                supplies.put(id, price(price));
            }
        });
        return new Result(new ShopRules.Prices(cosmeticPrices, price(m.getOrDefault("extra-reroll", 40)),
                price(m.getOrDefault("template-copy", 120)), price(m.getOrDefault("wayfinder", 60)),
                price(m.getOrDefault("beacon-kindling", 80)), supplies, price(m.getOrDefault("enchant-per-level", 60))), List.copyOf(problems));
    }

    private static long price(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
