package dev.wayfarer.bosses;

import java.util.List;

/** A boss: its family (its own id), base stats at one star, variants and how often it may appear. */
public record BossDefinition(String id, String name, double health, double damage, List<BossVariant> variants,
        int cooldownMinutes, SpawnRules.BossSpawn spawn) {

    public BossDefinition(String id, String name, double health, double damage, List<BossVariant> variants,
            int cooldownMinutes) {
        this(id, name, health, damage, variants, cooldownMinutes, SpawnRules.BossSpawn.none());
    }

    /** The family this boss forms on the ladder: its own id. */
    public String family() {
        return id;
    }

    public java.util.Optional<BossVariant> variant(String variantId) {
        return variants.stream().filter(v -> v.id().equals(variantId)).findFirst();
    }
}
