package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.LinkedHashMap;
import java.util.Map;

public record EntityDetailData(EntityStudy study, Map<String, String> values) {
    public static final Codec<EntityDetailData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        EntityStudy.CODEC.fieldOf("study").forGetter(EntityDetailData::study),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("values", Map.of()).forGetter(EntityDetailData::values)
    ).apply(instance, EntityDetailData::new));

    public EntityDetailData {
        values = Map.copyOf(values);
    }

    public static EntityDetailData fromEntity(EntityStudy study, LivingEntity entity) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(EntityDetail.NAME.id(), study.label());
        values.put(EntityDetail.HEALTH.id(), format(entity.getMaxHealth()));
        values.put(EntityDetail.ARMOR.id(), Integer.toString(entity.getArmorValue()));
        putAttribute(values, EntityDetail.MOVEMENT_SPEED, entity.getAttribute(Attributes.MOVEMENT_SPEED));
        putAttribute(values, EntityDetail.ARMOR_TOUGHNESS, entity.getAttribute(Attributes.ARMOR_TOUGHNESS));
        putAttribute(values, EntityDetail.DAMAGE, entity.getAttribute(Attributes.ATTACK_DAMAGE));
        return new EntityDetailData(study, values);
    }

    public static EntityDetailData nameOnly(EntityStudy study) {
        return new EntityDetailData(study, Map.of(EntityDetail.NAME.id(), study.label()));
    }

    public static EntityDetailData detailsOnly(EntityStudy study, LivingEntity entity) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(EntityDetail.HEALTH.id(), format(entity.getMaxHealth()));
        values.put(EntityDetail.ARMOR.id(), Integer.toString(entity.getArmorValue()));
        putAttribute(values, EntityDetail.MOVEMENT_SPEED, entity.getAttribute(Attributes.MOVEMENT_SPEED));
        putAttribute(values, EntityDetail.ARMOR_TOUGHNESS, entity.getAttribute(Attributes.ARMOR_TOUGHNESS));
        putAttribute(values, EntityDetail.DAMAGE, entity.getAttribute(Attributes.ATTACK_DAMAGE));
        return new EntityDetailData(study, values);
    }

    public EntityDetailData merge(EntityDetailData other) {
        Map<String, String> merged = new LinkedHashMap<>(this.values);
        merged.putAll(other.values());
        return new EntityDetailData(this.study, merged);
    }

    public boolean includesAll(EntityDetailData other) {
        return this.values.keySet().containsAll(other.values().keySet());
    }

    private static void putAttribute(Map<String, String> values, EntityDetail detail, AttributeInstance attribute) {
        if (attribute != null) {
            values.put(detail.id(), format(attribute.getValue()));
        }
    }

    private static String format(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.0001D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
