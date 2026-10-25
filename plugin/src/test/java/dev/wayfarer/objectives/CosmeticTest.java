package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class CosmeticTest {
    @Test
    void hatsAndPetsParseAndBadOnesAreSkippedWithAProblem() {
        ObjectiveConfigParser.Result r = ObjectiveConfigParser.parse(new Yaml().load("""
                cosmetics:
                  top: {kind: hat, name: "Top", item: wayfarer:hat/top}
                  pumpkin: {kind: hat, name: "Pumpkin", item: carved_pumpkin}
                  bare: {kind: hat, name: "Bare"}
                  sheep: {kind: pet, name: "Rainbow Sheep", entity: sheep, pet-name: jeb_}
                  shulker: {kind: pet, name: "Mini Shulker", model: wayfarer:pet/shulker, scale: 0.5}
                  both: {kind: pet, entity: sheep, model: wayfarer:pet/x}
                  neither: {kind: pet}
                  huge: {kind: pet, model: wayfarer:pet/x, scale: 9}
                  odd: {kind: cape}
                """), ObjectiveFixtures.SERVER);

        assertEquals(List.of("top", "pumpkin", "sheep", "shulker", "huge"), List.copyOf(r.cosmetics().keySet()));
        assertTrue(r.cosmetics().get("top").hat().model());
        assertFalse(r.cosmetics().get("pumpkin").hat().model());
        assertTrue(r.cosmetics().get("pumpkin").hat().plain());
        assertEquals("jeb_", r.cosmetics().get("sheep").pet().customName());
        assertTrue(r.cosmetics().get("sheep").pet().baby());
        assertEquals(0.5, r.cosmetics().get("shulker").pet().scale());
        assertEquals(1.0, r.cosmetics().get("huge").pet().scale());
        assertEquals(5, r.problems().size(), r.problems().toString());
    }

    @Test
    void eachKindHasItsOwnSlotThatWearsAndTakesOffAndIsSaved() {
        ObjectivesPlayerData data = new ObjectivesPlayerData();
        Cosmetic title = new Cosmetic("t", Cosmetic.TITLE, "T", 0, null);
        Cosmetic hat = new Cosmetic("h", Cosmetic.HAT, "H", 0, null, new Cosmetic.Hat("lantern", null), null);
        Cosmetic hat2 = new Cosmetic("h2", Cosmetic.HAT, "H2", 0, null, new Cosmetic.Hat("cake", null), null);
        Cosmetic pet = new Cosmetic("p", Cosmetic.PET, "P", 0, null, null, new Cosmetic.Pet("sheep", null, true, null, 1));

        assertTrue(data.toggleWorn(title));
        assertTrue(data.toggleWorn(hat));
        assertTrue(data.toggleWorn(pet));
        assertTrue(data.toggleWorn(hat2), "a second hat replaces the first");
        assertEquals("h2", data.worn(Cosmetic.HAT));
        assertEquals("t", data.worn(Cosmetic.TITLE));
        assertNull(data.worn(Cosmetic.TRAIL));

        ObjectivesPlayerData.Codec codec = new ObjectivesPlayerData.Codec();
        ObjectivesPlayerData back = codec.decode(ObjectivesPlayerData.VERSION, codec.encode(data));
        assertEquals("h2", back.hat);
        assertEquals("p", back.pet);

        assertFalse(back.toggleWorn(pet), "wearing the same one again takes it off");
        assertNull(back.pet);
    }
}
