package dev.wayfarer.bosses;

/** Phase, leash and heal decisions for a boss fight. No server types. */
public final class FightRules {
    public static final double LEASH = 32;
    public static final double LEASH_TELEPORT = 48;
    public static final int STUCK_TICKS = 200;
    public static final double ABANDON_RANGE = 64;
    public static final int ABANDON_TICKS = 600;

    public enum LeashAction {
        STAY, WALK_HOME, TELEPORT_HOME
    }

    private FightRules() {
    }

    /** Phase two starts at half health and never ends until the boss heals. */
    public static boolean phaseTwo(double health, double maxHealth) {
        return health <= maxHealth / 2;
    }

    /**
     * @param distanceFromHome blocks from where it spawned
     * @param ticksWalkingHome how long it has been walking back already
     */
    public static LeashAction leash(double distanceFromHome, int ticksWalkingHome) {
        if (distanceFromHome > LEASH_TELEPORT || (distanceFromHome > LEASH && ticksWalkingHome >= STUCK_TICKS)) {
            return LeashAction.TELEPORT_HOME;
        }
        return distanceFromHome > LEASH ? LeashAction.WALK_HOME : LeashAction.STAY;
    }

    /** A boss with no player in range for long enough heals fully and leaves phase two. */
    public static boolean shouldHeal(int ticksWithoutPlayers) {
        return ticksWithoutPlayers >= ABANDON_TICKS;
    }

    /** How many minions a summon brings: 2 at one star, up to 4 at five. */
    public static int minionCount(int stars) {
        return 2 + (StarScale.clamp(stars) - 1) / 2;
    }

    public static final int MAX_MINIONS = 6;
}
