package dev.wayfarer.core.progression;

/** One mob death, as plain values. {@code mobType} is an activity target ("husk", "incendium:inferno_knight"). */
public record KillFacts(Killer killer, String mobType, String spawnReason, boolean tamed, boolean leashed, long ageTicks) {
    /** Who landed the killing blow; a projectile counts as its shooter. */
    public enum Killer {
        PLAYER, MOB_OR_PET, ENVIRONMENT
    }
}
