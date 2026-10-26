package dev.wayfarer.foods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A dish's recipe unlocks the first time a player holds any one of its ingredients, and not for anything else. */
class DishDiscoveryTest {
    private static DishDefinition dish(String id, DishDefinition.Ingredient... ingredients) {
        return new DishDefinition(id, id, 0, "mining", "bread", 6, 6f, List.of(new DishDefinition.Effect("haste", 0, 60)), "", "", false,
                new DishDefinition.Recipe("crafting", List.of(ingredients), 10));
    }

    private static DishDefinition.Ingredient item(String name) {
        return new DishDefinition.Ingredient(DishDefinition.Kind.ITEM, name, 1);
    }

    private static final DishDefinition STEW = dish("stew", item("potato"), new DishDefinition.Ingredient(DishDefinition.Kind.FORAGE, "onion", 1),
            new DishDefinition.Ingredient(DishDefinition.Kind.MEAT, "cooked", 1), item("bowl"));
    private static final DishDefinition CHOWDER = dish("chowder", new DishDefinition.Ingredient(DishDefinition.Kind.FISH, "any", 1), item("potato"));
    private static final DishDefinition PIE = dish("pie", new DishDefinition.Ingredient(DishDefinition.Kind.FISH, "northern_pike", 1), item("wheat"));
    private static final List<DishDefinition> ALL = List.of(STEW, CHOWDER, PIE);

    @Test
    void aPlainItemUnlocksEveryDishThatTakesIt() {
        assertEquals(List.of("stew", "chowder"), DishDiscovery.unlocks(ALL, "potato", null, false));
        assertEquals(List.of("pie"), DishDiscovery.unlocks(ALL, "wheat", null, false));
        assertEquals(List.of("stew"), DishDiscovery.unlocks(ALL, "bowl", null, false));
    }

    @Test
    void anUnrelatedItemUnlocksNothing() {
        assertEquals(List.of(), DishDiscovery.unlocks(ALL, "dirt", null, false));
        assertEquals(List.of(), DishDiscovery.unlocks(ALL, "diamond", null, false));
    }

    @Test
    void anyCookedMeatUnlocksTheDishesThatTakeCookedMeat() {
        for (String meat : List.of("cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit")) {
            assertEquals(List.of("stew"), DishDiscovery.unlocks(ALL, meat, null, false), meat);
        }
        assertEquals(List.of(), DishDiscovery.unlocks(ALL, "beef", null, false), "raw meat is not cooked meat");
    }

    @Test
    void aForageIngredientUnlocksOnlyDishesThatNameIt() {
        assertEquals(List.of("stew"), DishDiscovery.unlocks(ALL, "brick", "onion", false));
        assertEquals(List.of(), DishDiscovery.unlocks(ALL, "brick", "tomato", false));
        assertEquals(List.of(), DishDiscovery.unlocks(ALL, "brick", null, false), "a plain brick is not forage");
    }

    @Test
    void forageIsNeverMistakenForTheItemItIsBuiltOn() {
        DishDefinition brickDish = dish("brickish", item("brick"));
        assertEquals(List.of(), DishDiscovery.unlocks(List.of(brickDish), "brick", "onion", false));
        assertEquals(List.of("brickish"), DishDiscovery.unlocks(List.of(brickDish), "brick", null, false));
    }

    @Test
    void fishUnlockDishesThatTakeAnyFishAndASpeciesDishOnlyForWayfarerFish() {
        assertEquals(List.of("chowder"), DishDiscovery.unlocks(ALL, "salmon", null, false), "a vanilla fish suits any");
        assertTrue(DishDiscovery.unlocks(ALL, "cod", null, false).contains("chowder"));
        assertFalse(DishDiscovery.unlocks(ALL, "cod", null, false).contains("pie"), "a cod is not a named species");
        List<String> wayfarer = DishDiscovery.unlocks(ALL, "cod", null, true);
        assertTrue(wayfarer.containsAll(List.of("chowder", "pie")), "a Wayfarer fish may be any of them");
    }

    @Test
    void aWayfarerItemBuiltOnAPlainItemIsNotThatItem() {
        // A custom item posing as a potato must not unlock potato dishes (the listener skips non-ingredient Wayfarer items;
        // here a Wayfarer fish built on cod must not unlock a dish that takes a plain cod item).
        DishDefinition codDish = dish("codish", item("cod"));
        assertEquals(List.of(), DishDiscovery.unlocks(List.of(codDish), "cod", null, true));
        assertEquals(List.of("codish"), DishDiscovery.unlocks(List.of(codDish), "cod", null, false));
    }
}
