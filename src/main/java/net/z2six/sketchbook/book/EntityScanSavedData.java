package net.z2six.sketchbook.book;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EntityScanSavedData extends SavedData {
    private static final Codec<List<EntityStudy>> ENTITY_LIST_CODEC = Codec.list(Codec.either(EntityStudy.CODEC, ResourceLocation.CODEC).xmap(either -> either.map(study -> study, EntityStudy::legacy), Either::left));
    private static final Codec<Map<UUID, List<EntityStudy>>> IDENTIFIED_CODEC = ExtraCodecs.sizeLimitedMap(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, ENTITY_LIST_CODEC),
        10_000
    );
    private static final Codec<Map<UUID, List<EntityDetailData>>> DETAILS_CODEC = ExtraCodecs.sizeLimitedMap(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.list(EntityDetailData.CODEC)),
        10_000
    );
    private static final Codec<Packed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        IDENTIFIED_CODEC.optionalFieldOf("identified", Map.of()).forGetter(Packed::identified),
        DETAILS_CODEC.optionalFieldOf("details", Map.of()).forGetter(Packed::details)
    ).apply(instance, Packed::new));
    private static final String NAME = "sketchbook_entity_scans";

    private final Map<UUID, Set<EntityStudy>> identifiedByPlayer;
    private final Map<UUID, Map<EntityStudy, EntityDetailData>> detailsByPlayer;

    public EntityScanSavedData() {
        this(Map.of(), Map.of());
    }

    private EntityScanSavedData(Packed packed) {
        this(packed.identified(), packed.details());
    }

    public EntityScanSavedData(Map<UUID, List<EntityStudy>> identifiedByPlayer, Map<UUID, List<EntityDetailData>> detailsByPlayer) {
        this.identifiedByPlayer = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<EntityStudy>> entry : identifiedByPlayer.entrySet()) {
            this.identifiedByPlayer.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        this.detailsByPlayer = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<EntityDetailData>> entry : detailsByPlayer.entrySet()) {
            Map<EntityStudy, EntityDetailData> details = new LinkedHashMap<>();
            for (EntityDetailData data : entry.getValue()) {
                details.merge(data.study(), data, EntityDetailData::merge);
            }
            this.detailsByPlayer.put(entry.getKey(), details);
        }
    }

    public static EntityScanSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(EntityScanSavedData::new, (tag, provider) -> load(tag)), NAME);
    }

    public boolean identify(UUID playerId, EntityStudy study) {
        Set<EntityStudy> identified = this.identifiedByPlayer.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        boolean changed = identified.add(study);
        if (changed) {
            this.setDirty();
        }
        return changed;
    }

    public boolean hasIdentified(UUID playerId, EntityStudy study) {
        return this.identifiedByPlayer.getOrDefault(playerId, Set.of()).contains(study);
    }

    public List<EntityStudy> getIdentified(UUID playerId) {
        return List.copyOf(this.identifiedByPlayer.getOrDefault(playerId, Set.of()));
    }

    public boolean hasAllDetails(UUID playerId, EntityDetailData detailData) {
        EntityDetailData known = this.detailsByPlayer.getOrDefault(playerId, Map.of()).get(detailData.study());
        return known != null && known.includesAll(detailData);
    }

    public boolean learnDetails(UUID playerId, EntityDetailData detailData) {
        Map<EntityStudy, EntityDetailData> details = this.detailsByPlayer.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
        EntityDetailData current = details.get(detailData.study());
        EntityDetailData merged = current == null ? detailData : current.merge(detailData);
        if (current != null && current.values().equals(merged.values())) {
            return false;
        }
        details.put(detailData.study(), merged);
        this.setDirty();
        return true;
    }

    public List<EntityDetailData> getDetails(UUID playerId) {
        return List.copyOf(this.detailsByPlayer.getOrDefault(playerId, Map.of()).values());
    }

    private Packed pack() {
        Map<UUID, List<EntityStudy>> packedIdentified = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<EntityStudy>> entry : this.identifiedByPlayer.entrySet()) {
            packedIdentified.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        Map<UUID, List<EntityDetailData>> packedDetails = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<EntityStudy, EntityDetailData>> entry : this.detailsByPlayer.entrySet()) {
            packedDetails.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return new Packed(Map.copyOf(packedIdentified), Map.copyOf(packedDetails));
    }

    private static EntityScanSavedData load(CompoundTag tag) {
        Packed packed = CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).getOrThrow(IllegalStateException::new);
        return new EntityScanSavedData(packed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return (CompoundTag)CODEC.encodeStart(NbtOps.INSTANCE, this.pack()).getOrThrow(IllegalStateException::new);
    }

    private record Packed(Map<UUID, List<EntityStudy>> identified, Map<UUID, List<EntityDetailData>> details) {
    }
}
