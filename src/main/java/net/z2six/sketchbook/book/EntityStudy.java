package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EntityStudy(ResourceLocation entityTypeId, String variantData, String label) {
    private static final List<String> VOLATILE_KEYS = List.of(
        "Air",
        "Age",
        "ArmorDropChances",
        "ArmorItems",
        "Attributes",
        "CanBreakDoors",
        "Brain",
        "CanPickUpLoot",
        "CannotEnterHiveTicks",
        "DeathTime",
        "FallDistance",
        "FallFlying",
        "Fire",
        "ForcedAge",
        "HandDropChances",
        "HandItems",
        "Health",
        "HomePos",
        "HurtByTimestamp",
        "HurtTime",
        "InLove",
        "Invulnerable",
        "LeftHanded",
        "LoveCause",
        "Motion",
        "NoAI",
        "OnGround",
        "Passengers",
        "PatrolTarget",
        "PersistenceRequired",
        "PortalCooldown",
        "Pos",
        "Rotation",
        "Sitting",
        "SleepingX",
        "SleepingY",
        "SleepingZ",
        "UUID",
        "AbsorptionAmount",
        "leash"
    );
    private static final Set<String> VISUAL_VARIANT_KEYS = Set.of(
        "Variant",
        "variant",
        "CatVariant",
        "WolfVariant",
        "TropicalFishVariant",
        "Markings",
        "Color",
        "color"
    );

    public static final Codec<EntityStudy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("entity").forGetter(EntityStudy::entityTypeId),
        Codec.STRING.optionalFieldOf("variant", "{}").forGetter(EntityStudy::variantData),
        Codec.STRING.optionalFieldOf("label", "").forGetter(EntityStudy::label)
    ).apply(instance, EntityStudy::new));

    public EntityStudy {
        variantData = normalizeVariantData(variantData);
        label = label == null || label.isBlank() || label.equals(entityTypeId.toString()) ? defaultLabel(entityTypeId) : label;
    }

    public static EntityStudy fromEntity(ResourceLocation entityTypeId, Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        VOLATILE_KEYS.forEach(tag::remove);
        return new EntityStudy(entityTypeId, visualVariantData(tag), entity.getType().getDescription().getString());
    }

    public static EntityStudy legacy(ResourceLocation entityTypeId) {
        return new EntityStudy(entityTypeId, "{}", "");
    }

    public CompoundTag variantTag() {
        try {
            return TagParser.parseTag(this.variantData);
        } catch (Exception exception) {
            return new CompoundTag();
        }
    }

    public String displayLabel() {
        if ("{}".equals(this.variantData)) {
            return this.label;
        }
        CompoundTag tag = this.variantTag();
        String variant = tag.getAllKeys().stream().sorted().findFirst().map(key -> key + ": " + tag.get(key).getAsString()).orElse(this.variantData);
        return this.label + " (" + variant + ")";
    }

    private static String defaultLabel(ResourceLocation entityTypeId) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId)
            .map(type -> type.getDescription().getString())
            .orElse(entityTypeId.toString());
    }

    private static String normalizeVariantData(String variantData) {
        try {
            return visualVariantData(TagParser.parseTag(variantData == null || variantData.isBlank() ? "{}" : variantData));
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static String visualVariantData(CompoundTag tag) {
        CompoundTag variants = new CompoundTag();
        for (String key : VISUAL_VARIANT_KEYS) {
            if (tag.contains(key)) {
                variants.put(key, tag.get(key).copy());
            }
        }
        return variants.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EntityStudy study)) {
            return false;
        }
        return this.entityTypeId.equals(study.entityTypeId) && this.variantData.equals(study.variantData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.entityTypeId, this.variantData);
    }
}
