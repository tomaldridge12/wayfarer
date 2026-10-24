package dev.wayfarer.core;

import java.util.Map;

/** Offered by the bosses module: which structures have a guardian, for the Guardian's Chart. */
public interface GuardianSites {
    /** Structure key ({@code minecraft:trial_chambers}) to the id of the boss that guards it. */
    Map<String, String> guardians();

    /** A boss's name ("The Bone Marshal"), or its id when unknown. */
    String bossName(String bossId);
}
