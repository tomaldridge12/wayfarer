package dev.wayfarer.foods;

import dev.wayfarer.core.FishWaters;
import dev.wayfarer.core.ItemTagService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Keyed;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;

/**
 * Dish recipes, all registered under {@code wayfarer:dish_<id>}. Vanilla checks the item types; a forage ingredient is
 * a brick and a fish a cod, so this also checks which forage and which fish are really in the grid (and that no other
 * Wayfarer item stands in for a plain one). Main thread.
 */
final class DishRecipes implements Listener {
    private static final String PREFIX = "dish_";
    private static final List<Material> MEATS = List.of(Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
            Material.COOKED_MUTTON, Material.COOKED_RABBIT);
    private static final List<Material> ANY_FISH = List.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    private final Server server;
    private final Supplier<List<DishDefinition>> dishes;
    private final DishItems items;
    private final ItemTagService tags;
    private final Supplier<Optional<FishWaters>> waters;
    private final List<NamespacedKey> registered = new ArrayList<>();

    DishRecipes(Server server, Supplier<List<DishDefinition>> dishes, DishItems items, ItemTagService tags,
            Supplier<Optional<FishWaters>> waters) {
        this.server = server;
        this.dishes = dishes;
        this.items = items;
        this.tags = tags;
        this.waters = waters;
    }

    /** Registers every dish's recipe, replacing any from before; returns how many the server now holds. */
    int register() {
        unregister();
        for (DishDefinition d : dishes.get()) {
            NamespacedKey key = new NamespacedKey("wayfarer", PREFIX + d.id());
            ItemStack result = items.build(d);
            Recipe recipe;
            if ("crafting".equals(d.recipe().type())) {
                ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
                shapeless.setCategory(CraftingBookCategory.MISC);
                for (DishDefinition.Ingredient ing : d.recipe().ingredients()) {
                    for (int i = 0; i < ing.count(); i++) {
                        shapeless.addIngredient(choice(ing));
                    }
                }
                recipe = shapeless;
            } else {
                RecipeChoice only = choice(d.recipe().ingredients().getFirst());
                int ticks = d.recipe().seconds() * 20;
                if ("smoker".equals(d.recipe().type())) {
                    SmokingRecipe smoking = new SmokingRecipe(key, result, only, 0f, ticks);
                    smoking.setCategory(CookingBookCategory.FOOD);
                    recipe = smoking;
                } else {
                    CampfireRecipe campfire = new CampfireRecipe(key, result, only, 0f, ticks);
                    campfire.setCategory(CookingBookCategory.FOOD);
                    recipe = campfire;
                }
            }
            if (server.addRecipe(recipe)) {
                registered.add(key);
            }
        }
        return (int) registered.stream().filter(server::hasRecipe).count();
    }

    void unregister() {
        registered.forEach(server::removeRecipe);
        registered.clear();
    }

    private static RecipeChoice choice(DishDefinition.Ingredient ing) {
        return switch (ing.kind()) {
            case ITEM -> new RecipeChoice.MaterialChoice(Material.matchMaterial(ing.what()));
            case MEAT -> new RecipeChoice.MaterialChoice(MEATS);
            case FORAGE -> new RecipeChoice.MaterialChoice(ForageItems.BASE);
            case FISH -> new RecipeChoice.MaterialChoice("any".equals(ing.what()) ? ANY_FISH : List.of(Material.COD));
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onPrepare(PrepareItemCraftEvent event) {
        DishDefinition dish = dishOf(event.getRecipe());
        if (dish != null && !valid(dish, event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onCrafter(CrafterCraftEvent event) {
        DishDefinition dish = dishOf(event.getRecipe());
        if (dish != null && event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
                && !valid(dish, crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    private DishDefinition dishOf(Recipe recipe) {
        if (!(recipe instanceof Keyed k) || !k.getKey().getNamespace().equals("wayfarer") || !k.getKey().getKey().startsWith(PREFIX)) {
            return null;
        }
        String id = k.getKey().getKey().substring(PREFIX.length());
        return dishes.get().stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }

    /** The grid holds exactly the forage and fish the dish asks for, and no other Wayfarer item posing as a plain one. */
    boolean valid(DishDefinition dish, ItemStack[] grid) {
        List<String> forageWanted = new ArrayList<>();
        List<String> fishWanted = new ArrayList<>();
        for (DishDefinition.Ingredient ing : dish.recipe().ingredients()) {
            for (int i = 0; i < ing.count(); i++) {
                if (ing.kind() == DishDefinition.Kind.FORAGE) {
                    forageWanted.add(ing.what());
                } else if (ing.kind() == DishDefinition.Kind.FISH) {
                    fishWanted.add(ing.what());
                }
            }
        }
        List<String> forageHeld = new ArrayList<>();
        List<DishMatching.Held> fishHeld = new ArrayList<>();
        for (ItemStack stack : grid) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String id = tags.readId(stack);
            if (stack.getType() == ForageItems.BASE && !forageWanted.isEmpty()) {
                if (id == null || !id.startsWith(ForageItems.KIND + "/")) {
                    return false;
                }
                forageHeld.add(id.substring(ForageItems.KIND.length() + 1));
            } else if (!fishWanted.isEmpty() && ANY_FISH.contains(stack.getType())) {
                if (id != null && id.startsWith("fish/")) {
                    String species = id.substring("fish/".length());
                    fishHeld.add(new DishMatching.Held(species, waters.get().flatMap(w -> w.waterOf(species)).orElse("any")));
                } else if (id == null) {
                    fishHeld.add(new DishMatching.Held(null, null));
                } else {
                    return false;
                }
            } else if (id != null) {
                return false;
            }
        }
        return DishMatching.forage(forageWanted, forageHeld) && DishMatching.fish(fishWanted, fishHeld);
    }
}
