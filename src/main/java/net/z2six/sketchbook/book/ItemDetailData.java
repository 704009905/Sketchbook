package net.z2six.sketchbook.book;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemDetailData {
    private static final DecimalFormat FORMAT = new DecimalFormat("0.##");

    private ItemDetailData() {
    }

    public static Map<ItemDetail, String> valuesFor(ItemStack stack) {
        Map<ItemDetail, String> values = new LinkedHashMap<>();
        if (stack.isEmpty()) {
            return values;
        }

        values.put(ItemDetail.NAME, stack.getHoverName().getString());

        if (stack.getItem() instanceof ArmorItem armorItem) {
            if (armorItem.getDefense() > 0) {
                values.put(ItemDetail.ARMOR, Integer.toString(armorItem.getDefense()));
            }
            if (armorItem.getToughness() > 0.0F) {
                values.put(ItemDetail.ARMOR_TOUGHNESS, format(armorItem.getToughness()));
            }
            ItemAttributeModifiers armorModifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            putAttribute(values, armorModifiers, ItemDetail.KNOCKBACK_RESISTANCE, Attributes.KNOCKBACK_RESISTANCE, EquipmentSlotGroup.bySlot(armorItem.getEquipmentSlot()), 0.0D);
        }

        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        putAttribute(values, modifiers, ItemDetail.DAMAGE, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND, 1.0D);
        putAttribute(values, modifiers, ItemDetail.ATTACK_SPEED, Attributes.ATTACK_SPEED, EquipmentSlotGroup.MAINHAND, 4.0D);

        if (stack.isDamageableItem()) {
            values.put(ItemDetail.DURABILITY, Integer.toString(stack.getMaxDamage()));
        }

        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            if (food.nutrition() > 0) {
                values.put(ItemDetail.NUTRITION, Integer.toString(food.nutrition()));
            }
            if (food.saturation() > 0.0F) {
                values.put(ItemDetail.SATURATION, format(food.saturation()));
            }
        }

        return values;
    }

    private static void putAttribute(Map<ItemDetail, String> values, ItemAttributeModifiers modifiers, ItemDetail detail, Holder<Attribute> target, EquipmentSlotGroup slotGroup, double baseValue) {
        final double[] amount = {0.0D};
        modifiers.forEach(slotGroup, (attribute, modifier) -> {
            if (attribute.is(target) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                amount[0] += modifier.amount();
            }
        });
        double value = amount[0] + baseValue;
        if (value > 0.0D) {
            values.put(detail, format(value));
        }
    }

    private static String format(double value) {
        return FORMAT.format(value);
    }
}
