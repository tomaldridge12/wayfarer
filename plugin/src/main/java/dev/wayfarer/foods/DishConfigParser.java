package dev.wayfarer.foods;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The dishes in foods.yml. A dish with something wrong in it is skipped with a problem line; a bad effect level falls
 * back to the cap. Ingredients are written as text: {@code potato}, {@code wheat*2}, {@code meat:cooked},
 * {@code forage:onion}, {@code fish:any|fresh|salt|<species>}.
 */
public final class DishConfigParser {
    /** A crafting grid holds nine. */
    static final int MAX_SLOTS = 9;
    /** A recipe that takes {@code fish:} must not also name one of these, or the grid check cannot tell them apart. */
    static final Set<String> FISH_ITEMS = Set.of("cod", "salmon", "tropical_fish", "pufferfish");

    /** What the server knows, so the parser can check names without a server. */
    public interface Vocabulary {
        /** A vanilla item with a food component, usable as a dish base. */
        boolean isFoodItem(String name);

        boolean isItem(String name);

        boolean isEffect(String name);

        boolean isForage(String id);

        boolean isFishSpecies(String id);
    }

    public record Result(List<DishDefinition> dishes, int maxAmplifier, List<String> problems) {
    }

    private DishConfigParser() {
    }

    public static Result parse(Map<String, Object> root, Vocabulary v) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> section = map(root.get("dishes"));
        int cap = 0;
        if (section.get("max-amplifier") instanceof Number n && n.intValue() >= 0 && n.intValue() <= 4) {
            cap = n.intValue();
        } else if (section.get("max-amplifier") != null) {
            problems.add("dishes.max-amplifier must be 0 to 4 (0 is level I); using 0");
        }
        List<DishDefinition> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : map(section.get("items")).entrySet()) {
            DishDefinition d = dish(e.getKey().toLowerCase(Locale.ROOT), map(e.getValue()), cap, v, problems);
            if (d != null) {
                out.add(d);
            }
        }
        return new Result(List.copyOf(out), cap, List.copyOf(problems));
    }

    private static DishDefinition dish(String id, Map<String, Object> m, int cap, Vocabulary v, List<String> problems) {
        String where = "dishes." + id;
        String name = text(m.get("name"));
        if (name.isBlank()) {
            problems.add(where + " has no name; skipped");
            return null;
        }
        String base = text(m.get("base")).toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
        if (!v.isFoodItem(base)) {
            problems.add(where + ".base '" + base + "' is not a food item; skipped");
            return null;
        }
        int nutrition = whole(m.get("nutrition"), 1, 20, where + ".nutrition", problems);
        double saturation = number(m.get("saturation"), where + ".saturation", problems);
        boolean rare = Boolean.TRUE.equals(m.get("rare"));
        List<DishDefinition.Effect> effects = new ArrayList<>();
        if (m.get("effects") instanceof List<?> list) {
            for (Object o : list) {
                DishDefinition.Effect effect = effect(map(o), cap, rare, where, v, problems);
                if (effect != null) {
                    effects.add(effect);
                }
            }
        }
        if (effects.isEmpty()) {
            problems.add(where + " has no usable effect; skipped");
            return null;
        }
        DishDefinition.Recipe recipe = recipe(m, where, v, problems);
        if (recipe == null) {
            return null;
        }
        int colour = 0xE8D9A8;
        if (m.get("colour") != null) {
            try {
                colour = Integer.parseInt(text(m.get("colour")).replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException ex) {
                problems.add(where + ".colour '" + m.get("colour") + "' is not #RRGGBB; using the default");
            }
        }
        String family = text(m.get("family")).isBlank() ? "other" : text(m.get("family")).toLowerCase(Locale.ROOT);
        return new DishDefinition(id, name, colour, family, base, nutrition, (float) saturation, List.copyOf(effects),
                text(m.get("flavour")), text(m.get("hint")), rare, recipe);
    }

    private static DishDefinition.Effect effect(Map<String, Object> e, int cap, boolean rare, String where, Vocabulary v,
            List<String> problems) {
        String type = text(e.get("type")).toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
        if (!v.isEffect(type)) {
            problems.add(where + ".effects '" + type + "' is not an effect; skipped");
            return null;
        }
        int level = e.get("amplifier") instanceof Number n && n.intValue() >= 0 ? n.intValue() : 0;
        if (level > cap && !rare) {
            problems.add(where + ".effects " + type + " amplifier " + level + " is above the cap " + cap
                    + " and the dish is not rare; using " + cap);
            level = cap;
        }
        double minutes = e.get("minutes") instanceof Number n ? n.doubleValue() : 0;
        if (minutes <= 0 || minutes > 60) {
            problems.add(where + ".effects " + type + " minutes must be above 0 and at most 60; skipped");
            return null;
        }
        return new DishDefinition.Effect(type, level, (int) Math.round(minutes * 60));
    }

    private static DishDefinition.Recipe recipe(Map<String, Object> m, String where, Vocabulary v, List<String> problems) {
        String type = m.get("recipe") == null ? "crafting" : text(m.get("recipe")).toLowerCase(Locale.ROOT);
        if (!Set.of("crafting", "smoker", "campfire").contains(type)) {
            problems.add(where + ".recipe '" + type + "' must be crafting, smoker or campfire; skipped");
            return null;
        }
        List<DishDefinition.Ingredient> ingredients = new ArrayList<>();
        if (m.get("ingredients") instanceof List<?> list) {
            for (Object o : list) {
                DishDefinition.Ingredient ing = ingredient(text(o), where, v, problems);
                if (ing == null) {
                    return null;
                }
                ingredients.add(ing);
            }
        }
        int seconds = m.get("seconds") instanceof Number n && n.intValue() > 0 ? n.intValue() : "campfire".equals(type) ? 30 : 10;
        DishDefinition.Recipe recipe = new DishDefinition.Recipe(type, List.copyOf(ingredients), seconds);
        if (ingredients.isEmpty() || recipe.slots() > MAX_SLOTS) {
            problems.add(where + " needs 1 to " + MAX_SLOTS + " ingredients; skipped");
            return null;
        }
        boolean fish = ingredients.stream().anyMatch(i -> i.kind() == DishDefinition.Kind.FISH);
        if (fish && ingredients.stream().anyMatch(i -> i.kind() == DishDefinition.Kind.ITEM && FISH_ITEMS.contains(i.what()))) {
            problems.add(where + " mixes a fish: ingredient with a plain fish item; skipped");
            return null;
        }
        if (!"crafting".equals(type)) {
            DishDefinition.Ingredient only = ingredients.getFirst();
            if (ingredients.size() != 1 || only.count() != 1
                    || (only.kind() != DishDefinition.Kind.ITEM && only.kind() != DishDefinition.Kind.MEAT)) {
                problems.add(where + " is a " + type + " recipe, which takes exactly one plain item; skipped");
                return null;
            }
        }
        return recipe;
    }

    private static DishDefinition.Ingredient ingredient(String text, String where, Vocabulary v, List<String> problems) {
        String t = text.toLowerCase(Locale.ROOT).trim().replaceFirst("^minecraft:", "");
        int count = 1;
        int star = t.lastIndexOf('*');
        if (star > 0) {
            try {
                count = Integer.parseInt(t.substring(star + 1).trim());
            } catch (NumberFormatException e) {
                count = 0;
            }
            t = t.substring(0, star).trim();
        }
        if (count < 1 || count > MAX_SLOTS) {
            problems.add(where + ".ingredients '" + text + "' has a bad count; skipped");
            return null;
        }
        if (t.equals("meat:cooked")) {
            return new DishDefinition.Ingredient(DishDefinition.Kind.MEAT, "cooked", count);
        }
        if (t.startsWith("forage:")) {
            String id = t.substring("forage:".length());
            if (!v.isForage(id)) {
                problems.add(where + ".ingredients '" + text + "' is not a forage ingredient; skipped");
                return null;
            }
            return new DishDefinition.Ingredient(DishDefinition.Kind.FORAGE, id, count);
        }
        if (t.startsWith("fish:")) {
            String what = t.substring("fish:".length());
            if (!Set.of("any", "fresh", "salt").contains(what) && !v.isFishSpecies(what)) {
                problems.add(where + ".ingredients '" + text + "' is not any, fresh, salt or a known species; skipped");
                return null;
            }
            return new DishDefinition.Ingredient(DishDefinition.Kind.FISH, what, count);
        }
        if (!v.isItem(t)) {
            problems.add(where + ".ingredients '" + text + "' is not an item; skipped");
            return null;
        }
        return new DishDefinition.Ingredient(DishDefinition.Kind.ITEM, t, count);
    }

    private static int whole(Object value, int min, int max, String path, List<String> problems) {
        if (value instanceof Number n && n.intValue() >= min && n.intValue() <= max && n.doubleValue() == n.intValue()) {
            return n.intValue();
        }
        problems.add(path + " must be a whole number from " + min + " to " + max + "; using " + min);
        return min;
    }

    private static double number(Object value, String path, List<String> problems) {
        if (value instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() <= 40) {
            return n.doubleValue();
        }
        problems.add(path + " must be a number from 0 to 40; using 0");
        return 0;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> map(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }
}
