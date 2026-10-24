package dev.wayfarer.objectives;

/** Token balances: never below zero; every change makes one ledger line. No server types. */
public final class Tokens {
    /** A balance after a change, the change that really happened (spending more than you have takes nothing). */
    public record Change(long balance, long applied) {
    }

    private Tokens() {
    }

    public static Change add(long balance, long delta) {
        if (delta < 0 && balance + delta < 0) {
            return new Change(balance, 0);
        }
        return new Change(balance + delta, delta);
    }

    /** "2026-10-24T18:00:00Z +20 daily coal_run (balance 140)". */
    public static String ledger(java.time.Instant at, long applied, String why, long balance) {
        return at + " " + (applied >= 0 ? "+" : "") + applied + " " + why + " (balance " + balance + ")";
    }
}
