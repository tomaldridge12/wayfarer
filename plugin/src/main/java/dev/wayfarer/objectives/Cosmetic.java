package dev.wayfarer.objectives;

/** A title (shown before your name) or a trail (particles behind you); {@code colour} for titles, {@code particle} for trails. */
public record Cosmetic(String id, String kind, String name, int colour, String particle) {
    public static final String TITLE = "title";
    public static final String TRAIL = "trail";

    public boolean title() {
        return kind.equals(TITLE);
    }
}
