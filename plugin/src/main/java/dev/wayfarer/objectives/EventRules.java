package dev.wayfarer.objectives;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Which event a weekend holds: the rotation, in order, one per week. No server types. */
public final class EventRules {
    public static final List<String> MODIFIERS = List.of("double_catch", "crop_growth", "ore_xp", "mob_drops");

    private EventRules() {
    }

    /** The event for the weekend starting at this moment: the rotation turns one step a week. */
    public static String forWeekend(List<String> rotation, Instant start, ZoneId zone) {
        if (rotation.isEmpty()) {
            return null;
        }
        long weeks = ChronoUnit.WEEKS.between(Instant.EPOCH.atZone(zone).toLocalDate(), start.atZone(zone).toLocalDate());
        return rotation.get((int) Math.floorMod(weeks, rotation.size()));
    }

    /** A doubled (or otherwise multiplied) amount, rounded: 2 x 5 experience is 10. */
    public static int multiply(int amount, double multiplier) {
        return (int) Math.round(amount * multiplier);
    }
}
