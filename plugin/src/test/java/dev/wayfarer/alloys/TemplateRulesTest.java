package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Which structure guardian gives which template. */
class TemplateRulesTest {
    private final AlloyRules rules = AlloyFixtures.rules();

    private Optional<String> from(String boss, boolean guardian) {
        return rules.templateFrom(boss, guardian).map(AlloyDefinition::id);
    }

    @Test
    void eachGuardianGivesItsAlloysTemplate() {
        assertEquals(Optional.of("steel"), from("bone_marshal", true));
        assertEquals(Optional.of("bronze"), from("brood_mother", true));
        assertEquals(Optional.of("shakudo"), from("sand_pharaoh", true));
        assertEquals(Optional.of("electrum"), from("drowned_captain", true));
        assertEquals(Optional.of("hepatizon"), from("piglin_warlord", true));
        assertEquals(Optional.of("adamant"), from("void_stalker", true));
    }

    @Test
    void roamingBossesGiveNoTemplate() {
        assertEquals(Optional.empty(), from("bone_marshal", false));
    }

    @Test
    void bossesWithNoAlloyGiveNoTemplate() {
        assertEquals(Optional.empty(), from("rotting_warlord", true));
    }

    @Test
    void shippedChanceIsCertainForEveryDamager() {
        rules.alloys().forEach(a -> assertEquals(1.0, a.template().chance(), a.id()));
    }
}
