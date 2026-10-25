package dev.wayfarer.core;

import java.util.Arrays;
import java.util.Optional;

/** The kinds of earning the server hears about. Ordinary dailies and weeklies are deliberately not here. */
public enum AnnounceCategory {
    FEAT("feat"),
    BOSS_DEFEAT("boss-defeat"),
    ASCENDED_DROP("ascended-drop"),
    LEGENDARY_DROP("legendary-drop"),
    SERVER_GOAL("server-goal"),
    STREAK("streak"),
    LADDER_RUNG("ladder-rung");

    private final String switchName;

    AnnounceCategory(String switchName) {
        this.switchName = switchName;
    }

    /** The name of its switch under {@code announce:} in progression.yml. */
    public String switchName() {
        return switchName;
    }

    public static Optional<AnnounceCategory> ofSwitch(String switchName) {
        return Arrays.stream(values()).filter(c -> c.switchName.equals(switchName)).findFirst();
    }
}
