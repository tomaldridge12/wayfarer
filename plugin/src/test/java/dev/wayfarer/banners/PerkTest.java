package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PerkTest {
    private static final Set<String> FAMILIES = Set.of("zombie", "skeleton");

    @Test
    void perksParseWithDefaultsAndFamilies() {
        assertEquals(new Perk(Perk.Type.ARROW_SAVER, null, 0.3), Perk.parse(Map.of("type", "arrow_saver"), FAMILIES));
        assertEquals(new Perk(Perk.Type.PROJECTILE_WARD, "skeleton", 40),
                Perk.parse(Map.of("type", "projectile_ward", "family", "skeleton", "amount", 40), FAMILIES));
    }

    @Test
    void badPerksExplainThemselves() {
        assertEquals("unknown perk type 'flying'", Perk.parse(Map.of("type", "flying"), FAMILIES));
        assertEquals("perk no_knockback needs a defined family", Perk.parse(Map.of("type", "no_knockback"), FAMILIES));
        assertEquals("perk amount must be a number above 0",
                Perk.parse(Map.of("type", "fall_cushion", "amount", -2), FAMILIES));
        assertEquals("perk must be a section with a type", Perk.parse("hunger", FAMILIES));
    }

    @Test
    void theSamePerkNeverStacksTheStrongestApplies() {
        List<Perk> worn = List.of(new Perk(Perk.Type.EXPLOSION_WARD, null, 50), new Perk(Perk.Type.EXPLOSION_WARD, null, 65),
                new Perk(Perk.Type.FALL_CUSHION, null, 5));
        assertEquals(65, Perk.strongest(worn, Perk.Type.EXPLOSION_WARD, null).orElseThrow().amount());
        assertEquals(Optional.empty(), Perk.strongest(worn, Perk.Type.HUNGER_IMMUNITY, null));
    }

    @Test
    void familyPerksOnlyMatchTheirFamily() {
        List<Perk> worn = List.of(new Perk(Perk.Type.NO_KNOCKBACK, "zombie", 1));
        assertTrue(Perk.strongest(worn, Perk.Type.NO_KNOCKBACK, "zombie").isPresent());
        assertTrue(Perk.strongest(worn, Perk.Type.NO_KNOCKBACK, "skeleton").isEmpty());
    }

    @Test
    void perksDescribeThemselvesForTooltips() {
        assertEquals("30% chance an arrow is not used up", new Perk(Perk.Type.ARROW_SAVER, null, 0.3).describe());
        assertEquals("−40% damage from skeleton arrows", new Perk(Perk.Type.PROJECTILE_WARD, "skeleton", 40).describe());
        assertEquals("No knockback from the zombie family", new Perk(Perk.Type.NO_KNOCKBACK, "zombie", 1).describe());
        assertEquals("No fall damage from falls of 5 blocks or less", new Perk(Perk.Type.FALL_CUSHION, null, 5).describe());
        assertEquals("Ender pearls do no damage; 75% shorter cooldown", new Perk(Perk.Type.PEARL_MASTERY, null, 75).describe());
    }
}
