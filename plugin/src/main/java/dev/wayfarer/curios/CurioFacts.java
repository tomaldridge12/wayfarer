package dev.wayfarer.curios;

/** What a curio's condition asks about the player carrying it, right now. Free of server types. */
public interface CurioFacts {
    /** Block height of the player's feet. */
    int y();

    /** No sky above: the block at the player's head gets no sky light. */
    boolean skyHidden();

    /** The player's eyes are in water. */
    boolean underwater();

    /** {@code overworld}, {@code nether} or {@code end}. */
    String dimension();

    /** Night in the overworld (time 13000 to 23000). */
    boolean night();

    /** Rain (or snow) is falling on the player: a storm in their world and open sky above them. */
    boolean raining();

    /** The biome at the player is in this biome tag ({@code minecraft:is_ocean}). */
    boolean inBiomeTag(String tag);

    /** The kind of tool in the main hand ({@code pickaxe}, {@code fishing_rod}, ...), or empty. */
    String heldTool();

    /** Water within a few blocks. */
    boolean nearWater();

    /** Any worn piece is alloy gear. */
    boolean wearingAlloy();
}
