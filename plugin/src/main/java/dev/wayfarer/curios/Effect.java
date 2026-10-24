package dev.wayfarer.curios;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a curio does, from the fixed library: charms work while carried, tools on right-click with a cooldown. Names
 * of potions, attributes, blocks and mobs are lower-case vanilla keys, checked by the parser.
 */
public sealed interface Effect {
    /** Whether this is a right-click tool (with a cooldown) rather than a carried charm. */
    default boolean tool() {
        return false;
    }

    /** Potion effects kept on while carried (shown as ambient, so they are told apart from brewed ones). */
    record Potion(List<String> potions, int amplifier) implements Effect {
    }

    /** An attribute modifier kept on while carried; {@code operation} is add, multiply_base or multiply_total. */
    record Attribute(String attribute, double amount, String operation) implements Effect {
    }

    /**
     * Shrugs off harm: potion {@code effects} from the listed {@code causes} (any cause when none is listed), damage of
     * the listed {@code damage} causes, and every splash or lingering potion thrown by the {@code splashFrom} mobs.
     */
    record Immunity(Set<String> effects, Set<String> causes, Set<String> damage, Set<String> splashFrom) implements Effect {
    }

    /** These mobs never target the carrier; unless {@code evenWhenHit}, they still fight back when struck. */
    record Ignored(Set<String> mobs, boolean evenWhenHit) implements Effect {
    }

    /** Breaking one of these blocks has a {@code chance} of also dropping {@code amount} of the item. */
    record DropBonus(Set<String> blocks, String item, int amount, double chance) implements Effect {
    }

    /** Every {@code everySeconds}, one random crop or sapling within {@code radius} grows a stage. */
    record Grow(int radius, int everySeconds) implements Effect {
    }

    /** More experience from {@code sources} (fishing, ores, mobs), times {@code multiplier}. */
    record XpBonus(Set<String> sources, double multiplier) implements Effect {
    }

    /** Slow falling as soon as a fall passes {@code distance} blocks. */
    record FallGuard(double distance) implements Effect {
    }

    /** Pulls dropped items within {@code radius} towards the carrier; sneaking pauses it. */
    record Magnet(double radius) implements Effect {
    }

    /** A multiplier other modules ask for by name ({@code treasure}: the treasure chance when brushing). */
    record Boost(String what, double factor) implements Effect {
    }

    /** Jukebox discs the carrier starts play with a ring of notes. */
    record Chorus() implements Effect {
    }

    /** Right-click: blocks within {@code radius} turn ({@code turns}: from -> to), each with a {@code chance}. */
    record Spread(int radius, double chance, Map<String, String> turns) implements Effect {
        @Override
        public boolean tool() {
            return true;
        }
    }

    /** Right-click: light follows the carrier for {@code seconds}. */
    record Light(int seconds) implements Effect {
        @Override
        public boolean tool() {
            return true;
        }
    }

    /** Right-click: these blocks within {@code radius} glow through walls for {@code seconds}, seen only by the user. */
    record Sense(Set<String> blocks, int radius, int seconds) implements Effect {
        @Override
        public boolean tool() {
            return true;
        }
    }

    /** Right-click: after standing still for {@code channelSeconds}, return to your bed (or the world spawn). */
    record Recall(int channelSeconds) implements Effect {
        @Override
        public boolean tool() {
            return true;
        }
    }

    /** Right-click: one free Wayfinder pointing (needs the exploration module). */
    record Locate() implements Effect {
        @Override
        public boolean tool() {
            return true;
        }
    }
}
