package dev.wayfarer.core.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Core's progression config (progression.yml), parsed. */
public record ProgressionSettings(CreditRules credit, List<PityDrop> pity, StageSnapshots snapshots, List<String> problems) {
    /** What the server knows by name, so parsing stays free of server types. */
    public interface Names {
        /** An upper-case spawn reason such as SPAWNER. */
        boolean isSpawnReason(String name);

        /** An item as an activity target, such as trident. */
        boolean isItem(String name);

        /** A mob type as an activity target, such as drowned. */
        boolean isMob(String name);

        /** An advancement without the minecraft: namespace, such as story/mine_diamond. */
        boolean isAdvancement(String name);
    }

    /** Accepts every name; for tests and the shipped-config check. */
    public static final Names ANY = new Names() {
        @Override
        public boolean isSpawnReason(String name) {
            return true;
        }

        @Override
        public boolean isItem(String name) {
            return true;
        }

        @Override
        public boolean isMob(String name) {
            return true;
        }

        @Override
        public boolean isAdvancement(String name) {
            return true;
        }
    };

    public static ProgressionSettings defaults() {
        return new ProgressionSettings(CreditRules.defaults(), List.of(), StageSnapshots.defaults(), List.of());
    }

    public ProgressionSettings withCredit(CreditRules rules) {
        return new ProgressionSettings(rules, pity, snapshots, problems);
    }

    public ProgressionSettings withPity(List<PityDrop> drops) {
        return new ProgressionSettings(credit, List.copyOf(drops), snapshots, problems);
    }

    public static ProgressionSettings parse(Map<String, Object> root, Names names) {
        List<String> problems = new ArrayList<>();
        CreditRules credit = CreditRules.parse(root.get("counting"), names::isSpawnReason, problems);
        List<PityDrop> pity = PityDrop.parse(root.get("pity"), names, problems);
        StageSnapshots snapshots = StageSnapshots.parse(root.get("snapshots"), names, problems);
        return new ProgressionSettings(credit, pity, snapshots, List.copyOf(problems));
    }
}
