package dev.wayfarer.objectives;

/**
 * Something a player wears, one per slot: a title (shown before your name), a trail (particles behind you), a hat
 * (an item on your head) or a pet (a little companion). {@code colour} is for titles, {@code particle} for trails;
 * {@code hat} and {@code pet} hold the look of those kinds (the other is null).
 */
public record Cosmetic(String id, String kind, String name, int colour, String particle, Hat hat, Pet pet) {
    public static final String TITLE = "title";
    public static final String TRAIL = "trail";
    public static final String HAT = "hat";
    public static final String PET = "pet";
    /** The kinds, in the order the Wardrobe shows their slots. */
    public static final java.util.List<String> KINDS = java.util.List.of(TITLE, TRAIL, HAT, PET);

    /**
     * A hat: {@code item} is a vanilla item ({@code carved_pumpkin}) or a model from the resource pack
     * ({@code wayfarer:hat/top_hat}); {@code ability} names a small perk worn with it, or is null for a plain hat.
     */
    public record Hat(String item, String ability) {
        public boolean model() {
            return item.contains(":") && !item.startsWith("minecraft:");
        }

        public boolean plain() {
            return ability == null;
        }
    }

    /**
     * A pet: a baby vanilla mob ({@code entity: sheep}, with an optional name such as {@code jeb_}) or a small display
     * of a pack model ({@code model: wayfarer:pet/mini_shulker}); {@code scale} sizes a model.
     */
    public record Pet(String entity, String model, boolean baby, String customName, double scale) {
    }

    /** A title or trail, with no hat or pet look. */
    public Cosmetic(String id, String kind, String name, int colour, String particle) {
        this(id, kind, name, colour, particle, null, null);
    }

    public boolean title() {
        return kind.equals(TITLE);
    }

    public boolean trail() {
        return kind.equals(TRAIL);
    }

    public boolean isHat() {
        return kind.equals(HAT);
    }

    public boolean isPet() {
        return kind.equals(PET);
    }
}
