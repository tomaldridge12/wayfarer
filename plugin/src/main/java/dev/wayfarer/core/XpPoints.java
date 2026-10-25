package dev.wayfarer.core;

import java.util.Optional;

/**
 * Vanilla's experience maths, in points: what a level takes, what a player holds from their level and progress, what
 * is left after paying, and how a price reads in levels. Vanilla's {@code getTotalExperience} is not trusted (it drifts
 * after enchanting), so level plus progress is the truth. No server types.
 */
public final class XpPoints {
    /** A level and the progress (0 to under 1) through it, as the XP bar shows. */
    public record Bar(int level, float progress) {
    }

    private XpPoints() {
    }

    /** Points needed to reach {@code level} from 0. */
    public static int toReach(int level) {
        long l = Math.max(0, level);
        if (l <= 16) {
            return (int) (l * l + 6 * l);
        }
        if (l <= 31) {
            return (int) Math.round(2.5 * l * l - 40.5 * l + 360);
        }
        return (int) Math.round(4.5 * l * l - 162.5 * l + 2220);
    }

    /** Points between {@code level} and the next. */
    public static int forLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        }
        return level <= 30 ? 5 * level - 38 : 9 * level - 158;
    }

    /** Points a player holds with this level and progress. */
    public static int held(int level, float progress) {
        return toReach(level) + Math.round(Math.max(0f, Math.min(1f, progress)) * forLevel(level));
    }

    /** The level and progress for a total of points. */
    public static Bar fromPoints(int points) {
        int level = 0;
        while (toReach(level + 1) <= points) {
            level++;
        }
        float progress = (points - toReach(level)) / (float) forLevel(level);
        return new Bar(level, Math.min(progress, 0.99999f));
    }

    /** What is left after taking {@code points}, or empty when the player holds less than that. */
    public static Optional<Bar> after(int level, float progress, int points) {
        int have = held(level, progress);
        return points < 0 || points > have ? Optional.empty() : Optional.of(fromPoints(have - points));
    }

    /** Levels (with the fraction of the bar) a total of points amounts to. */
    private static double levelsOf(int points) {
        Bar bar = fromPoints(points);
        return bar.level() + bar.progress();
    }

    /**
     * How a price reads to a player: "27 XP · about 2 of your levels". The levels are what the price would take off
     * their bar (capped at what they hold), rounded to a whole number; "under 1 level" below 0.5.
     */
    public static String describe(int level, float progress, int points) {
        int have = held(level, progress);
        double dropped = levelsOf(have) - levelsOf(Math.max(0, have - points));
        long whole = Math.round(dropped);
        String levels = dropped < 0.5 ? "under 1 level" : whole == 1 ? "about 1 level" : "about " + whole + " of your levels";
        return points + " XP · " + levels;
    }
}
