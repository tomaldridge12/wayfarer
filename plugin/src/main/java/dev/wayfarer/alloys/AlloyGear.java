package dev.wayfarer.alloys;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.datacomponent.item.Enchantable;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

/**
 * Puts an alloy's stats on an item through its data components: model, name, durability, mining speed, attributes,
 * worn look, enchantability, fire resistance. Keeps enchants, trims, custom names, other tags and the share of
 * durability used. Vanilla repair-by-material is removed; the anvil listener repairs alloys.
 */
final class AlloyGear {
    /** The share of an item's durability already used (0 to 1). Read before changing its type: max durability is the type's. */
    static double used(ItemStack item) {
        Integer max = item.getData(DataComponentTypes.MAX_DAMAGE);
        Integer damage = item.getData(DataComponentTypes.DAMAGE);
        return max == null || max <= 0 || damage == null ? 0 : (double) damage / max;
    }

    /** @param used the share of durability already used, kept on the new stats */
    void apply(ItemStack item, AlloyDefinition a, AlloyRules.Gear g, AlloyRules.Stats s, double used) {
        ItemType type = item.getType().asItemType();
        item.setData(DataComponentTypes.MAX_DAMAGE, s.durability());
        item.setData(DataComponentTypes.DAMAGE, Math.min(s.durability() - 1, (int) Math.round(used * s.durability())));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", g.model()));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(a.name() + " " + g.shape().title(), TextColor.color(a.colour())));
        item.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(s.enchantability()));
        item.unsetData(DataComponentTypes.REPAIRABLE);
        if (g.shape().miningTool() && type.hasDefaultData(DataComponentTypes.TOOL)) {
            item.setData(DataComponentTypes.TOOL, faster(type.getDefaultData(DataComponentTypes.TOOL), (float) s.miningSpeed()));
        }
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributes(type, g.shape(), s));
        if (g.shape().armour() && type.hasDefaultData(DataComponentTypes.EQUIPPABLE)) {
            Equippable worn = type.getDefaultData(DataComponentTypes.EQUIPPABLE);
            item.setData(DataComponentTypes.EQUIPPABLE, worn.toBuilder().assetId(Key.key("wayfarer", a.id())).build());
        }
        if (s.fireproof()) {
            item.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(
                    RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getTag(DamageTypeTagKeys.IS_FIRE)));
        } else {
            item.resetData(DataComponentTypes.DAMAGE_RESISTANT); // back to the material's own (netherite resists fire)
        }
    }

    /** The material's tool rules with the alloy's speed wherever a rule sets one. */
    private static Tool faster(Tool base, float speed) {
        Tool.Builder out = Tool.tool().defaultMiningSpeed(base.defaultMiningSpeed()).damagePerBlock(base.damagePerBlock())
                .canDestroyBlocksInCreative(base.canDestroyBlocksInCreative());
        for (Tool.Rule rule : base.rules()) {
            out.addRule(Tool.rule(rule.blocks(), rule.speed() == null ? null : speed, rule.correctForDrops()));
        }
        return out.build();
    }

    /**
     * The material's own attributes with the alloy's numbers: attack damage plus the bonus on held items; armour,
     * toughness and knockback resistance on armour; then the alloy's identity bonuses.
     */
    private static ItemAttributeModifiers attributes(ItemType type, GearShape shape, AlloyRules.Stats s) {
        ItemAttributeModifiers.Builder out = ItemAttributeModifiers.itemAttributes();
        EquipmentSlotGroup group = group(shape);
        boolean toughness = false;
        boolean knockback = false;
        if (type.hasDefaultData(DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
            for (ItemAttributeModifiers.Entry e : type.getDefaultData(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers()) {
                AttributeModifier m = e.modifier();
                double amount = m.getAmount();
                if (e.attribute() == Attribute.ATTACK_DAMAGE && shape.held()) {
                    amount += s.damageBonus();
                } else if (e.attribute() == Attribute.ARMOR && shape.armour()) {
                    amount = s.armour();
                } else if (e.attribute() == Attribute.ARMOR_TOUGHNESS && shape.armour()) {
                    amount = s.toughness();
                    toughness = true;
                } else if (e.attribute() == Attribute.KNOCKBACK_RESISTANCE && shape.armour()) {
                    amount = s.knockbackResistance();
                    knockback = true;
                }
                out.addModifier(e.attribute(), new AttributeModifier(m.getKey(), amount, m.getOperation(), e.getGroup()),
                        e.getGroup(), e.display());
            }
        }
        if (shape.armour() && !toughness && s.toughness() > 0) {
            out.addModifier(Attribute.ARMOR_TOUGHNESS, modifier("armor_toughness", shape, s.toughness(), false), group);
        }
        if (shape.armour() && !knockback && s.knockbackResistance() > 0) {
            out.addModifier(Attribute.KNOCKBACK_RESISTANCE, modifier("knockback_resistance", shape, s.knockbackResistance(), false), group);
        }
        for (AlloyDefinition.Bonus b : s.bonuses()) {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(b.attribute()));
            if (attribute != null) {
                out.addModifier(attribute, modifier(b.attribute(), shape, b.amount(), b.multiply()), group);
            }
        }
        return out.build();
    }

    private static AttributeModifier modifier(String what, GearShape shape, double amount, boolean multiply) {
        return new AttributeModifier(new NamespacedKey("wayfarer", "alloy_" + what + "_" + shape.id()), amount,
                multiply ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER, group(shape));
    }

    static EquipmentSlotGroup group(GearShape shape) {
        return switch (shape) {
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }
}
