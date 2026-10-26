package dev.wayfarer.foods;

import java.util.List;

/**
 * One dish: a food built on a vanilla base with its own saturation and effects, and the recipe that makes it.
 * {@code hint} is what the Cookbook shows before it is found; {@code rare} lets its effects exceed the level cap.
 */
record DishDefinition(String id, String name, int colour, String family, String base, int nutrition, float saturation,
        List<Effect> effects, String flavour, String hint, boolean rare, Recipe recipe) {

    /** One effect: a potion effect name (no namespace), a level counted from 0 (0 is level I) and how long it lasts. */
    record Effect(String type, int amplifier, int seconds) {
    }

    /** What an ingredient asks for. */
    enum Kind {
        /** A vanilla item by name. */
        ITEM,
        /** Any cooked meat (beef, porkchop, chicken, mutton, rabbit). */
        MEAT,
        /** A forage ingredient by id. */
        FORAGE,
        /** A fish: {@code any}, {@code fresh}, {@code salt} or a species id. */
        FISH
    }

    record Ingredient(Kind kind, String what, int count) {
    }

    /** {@code type} is crafting (shapeless), smoker or campfire (one item, cooked for {@code seconds}). */
    record Recipe(String type, List<Ingredient> ingredients, int seconds) {
        int slots() {
            return ingredients.stream().mapToInt(Ingredient::count).sum();
        }
    }

    String model() {
        return "dish/" + id;
    }
}
