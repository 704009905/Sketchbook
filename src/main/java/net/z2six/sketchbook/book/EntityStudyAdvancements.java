package net.z2six.sketchbook.book;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.z2six.sketchbook.Sketchbook;

import java.util.Set;

public final class EntityStudyAdvancements {
    private static final Set<String> VANILLA_MOBS = Set.of(
        "allay", "armadillo", "axolotl", "bat", "bee", "blaze", "bogged", "breeze", "camel", "cat", "cave_spider",
        "chicken", "cod", "cow", "creeper", "dolphin", "donkey", "drowned", "elder_guardian", "ender_dragon",
        "enderman", "endermite", "evoker", "fox", "frog", "ghast", "giant", "glow_squid", "goat", "guardian",
        "hoglin", "horse", "husk", "illusioner", "iron_golem", "llama", "magma_cube", "mooshroom", "mule",
        "ocelot", "panda", "parrot", "phantom", "pig", "piglin", "piglin_brute", "pillager", "polar_bear",
        "pufferfish", "rabbit", "ravager", "salmon", "sheep", "shulker", "silverfish", "skeleton",
        "skeleton_horse", "slime", "sniffer", "snow_golem", "spider", "squid", "stray", "strider", "tadpole",
        "trader_llama", "tropical_fish", "turtle", "vex", "villager", "vindicator", "wandering_trader", "warden",
        "witch", "wither", "wither_skeleton", "wolf", "zoglin", "zombie", "zombie_horse", "zombie_villager",
        "zombified_piglin"
    );

    private EntityStudyAdvancements() {
    }

    public static void awardStudiedInDetail(ServerPlayer player, ResourceLocation entityTypeId) {
        award(player, entityTypeId);
    }

    private static void award(ServerPlayer player, ResourceLocation entityTypeId) {
        if (!"minecraft".equals(entityTypeId.getNamespace()) || !VANILLA_MOBS.contains(entityTypeId.getPath())) {
            return;
        }

        AdvancementHolder root = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "study_detail/root"));
        if (root != null) {
            player.getAdvancements().award(root, "studied");
        }
        AdvancementHolder advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "study_detail/" + entityTypeId.getPath()));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "studied");
        }
        AdvancementHolder allAdvancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "study_detail/all"));
        if (allAdvancement != null) {
            player.getAdvancements().award(allAdvancement, entityTypeId.getPath());
        }
    }
}
