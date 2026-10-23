package dev.wayfarer.banners;

/** One mob death, as plain values. */
public record Kill(
        Killer killer, String mobType, String spawnReason, boolean tamed, boolean leashed, long ageTicks) {

    /** Who landed the killing blow; a projectile counts as its shooter. */
    public enum Killer {
        PLAYER, MOB_OR_PET, ENVIRONMENT
    }
}
