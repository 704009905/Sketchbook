package net.z2six.sketchbook.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.book.EntityDetailData;
import net.z2six.sketchbook.book.EntityStudy;
import net.z2six.sketchbook.network.EntityScanListRequestPayload;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientEntityScanCache {
    private static Set<EntityStudy> identified = Set.of();
    private static Map<EntityStudy, EntityDetailData> details = Map.of();
    private static boolean requested;

    private ClientEntityScanCache() {
    }

    public static boolean hasIdentified(EntityStudy study) {
        return identified.contains(study);
    }

    public static void addIdentified(EntityStudy study) {
        LinkedHashSet<EntityStudy> updated = new LinkedHashSet<>(identified);
        updated.add(study);
        identified = Set.copyOf(updated);
    }

    public static void putDetails(EntityDetailData detailData) {
        LinkedHashMap<EntityStudy, EntityDetailData> updated = new LinkedHashMap<>(details);
        updated.merge(detailData.study(), detailData, EntityDetailData::merge);
        details = Map.copyOf(updated);
    }

    public static List<EntityStudy> getIdentified() {
        return identified.stream().toList();
    }

    public static Map<String, String> getDetails(EntityStudy study) {
        EntityDetailData detailData = details.get(study);
        return detailData == null ? Map.of() : detailData.values();
    }

    public static boolean hasDetailedStudy(EntityStudy study) {
        Map<String, String> knownDetails = getDetails(study);
        return knownDetails.containsKey("health")
            || knownDetails.containsKey("armor")
            || knownDetails.containsKey("movement_speed")
            || knownDetails.containsKey("armor_toughness")
            || knownDetails.containsKey("damage");
    }

    public static void setIdentified(List<EntityStudy> studies, List<EntityDetailData> detailData) {
        identified = Set.copyOf(studies);
        LinkedHashMap<EntityStudy, EntityDetailData> updatedDetails = new LinkedHashMap<>();
        for (EntityDetailData data : detailData) {
            updatedDetails.merge(data.study(), data, EntityDetailData::merge);
        }
        details = Map.copyOf(updatedDetails);
        requested = true;
    }

    public static void ensureRequested() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            identified = Set.of();
            details = Map.of();
            requested = false;
            return;
        }

        if (!requested) {
            requested = true;
            PacketDistributor.sendToServer(new EntityScanListRequestPayload());
        }
    }
}
