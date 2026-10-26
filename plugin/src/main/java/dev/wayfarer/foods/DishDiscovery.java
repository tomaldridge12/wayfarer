package dev.wayfarer.foods;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which dishes' recipes unlock when a player first holds an item: every dish that takes it as an ingredient, like
 * vanilla's recipe book. Plain rules; the listener and the scans around it are in {@link DishDiscoveryListener}.
 */
final class DishDiscovery {
    static final Set<String> MEATS = Set.of("cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit");
    static final Set<String> FISH = Set.of("cod", "salmon", "tropical_fish", "pufferfish");

    private DishDiscovery() {
    }

    /**
     * @param material the item's vanilla material, such as {@code potato}
     * @param forage the forage id if the item is a forage ingredient, else null
     * @param wayfarerFish whether the item is a Wayfarer fish
     */
    static List<String> unlocks(List<DishDefinition> dishes, String material, String forage, boolean wayfarerFish) {
        List<String> out = new ArrayList<>();
        for (DishDefinition dish : dishes) {
            for (DishDefinition.Ingredient ing : dish.recipe().ingredients()) {
                if (matches(ing, material, forage, wayfarerFish)) {
                    out.add(dish.id());
                    break;
                }
            }
        }
        return out;
    }

    private static boolean matches(DishDefinition.Ingredient ing, String material, String forage, boolean wayfarerFish) {
        return switch (ing.kind()) {
            case ITEM -> forage == null && !wayfarerFish && ing.what().equals(material);
            case MEAT -> forage == null && MEATS.contains(material);
            case FORAGE -> ing.what().equals(forage);
            case FISH -> "any".equals(ing.what()) ? wayfarerFish || FISH.contains(material) && forage == null : wayfarerFish;
        };
    }
}
