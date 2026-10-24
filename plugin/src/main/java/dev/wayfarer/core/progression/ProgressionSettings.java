package dev.wayfarer.core.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Core's progression config (progression.yml), parsed. */
public record ProgressionSettings(CreditRules credit, List<String> problems) {
    public static ProgressionSettings defaults() {
        return new ProgressionSettings(CreditRules.defaults(), List.of());
    }

    public ProgressionSettings withCredit(CreditRules rules) {
        return new ProgressionSettings(rules, problems);
    }

    public static ProgressionSettings parse(Map<String, Object> root, Predicate<String> isSpawnReason) {
        List<String> problems = new ArrayList<>();
        CreditRules credit = CreditRules.parse(root.get("counting"), isSpawnReason, problems);
        return new ProgressionSettings(credit, List.copyOf(problems));
    }
}
