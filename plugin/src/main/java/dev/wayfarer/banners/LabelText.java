package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The two lines of a placed banner's floating label. */
public final class LabelText {
    private LabelText() {
    }

    /** Name, then one effect line; an inert banner says so instead. */
    public static List<String> lines(String bannerId, Optional<BannerDefinition> def) {
        if (def.isEmpty()) {
            return List.of(bannerId, "Inactive: not in config");
        }
        BannerDefinition d = def.get();
        List<String> effects = new ArrayList<>();
        if (d.damageBonusPercent() > 0) {
            effects.add("+" + d.damageBonusPercent() + "% damage");
        }
        if (d.damageReductionPercent() > 0) {
            effects.add("-" + d.damageReductionPercent() + "% taken");
        }
        String effect = (effects.isEmpty() ? "No effect" : String.join(", ", effects))
                + " vs " + d.family() + " family, " + d.radius() + " blocks";
        return List.of(d.name(), effect);
    }
}
