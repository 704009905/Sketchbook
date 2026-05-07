package net.z2six.sketchbook.book;

public enum EntityDetail {
    NAME("name", 1, "menu.sketchbook.detail.name"),
    HEALTH("health", 1 << 1, "menu.sketchbook.detail.health"),
    ARMOR("armor", 1 << 2, "menu.sketchbook.detail.armor"),
    ARMOR_TOUGHNESS("armor_toughness", 1 << 4, "menu.sketchbook.detail.armor_toughness"),
    DAMAGE("damage", 1 << 5, "menu.sketchbook.detail.damage"),
    MOVEMENT_SPEED("movement_speed", 1 << 3, "menu.sketchbook.detail.movement_speed");

    private static final int ALL_MASK = NAME.bit | HEALTH.bit | ARMOR.bit | MOVEMENT_SPEED.bit | ARMOR_TOUGHNESS.bit | DAMAGE.bit;

    private final String id;
    private final int bit;
    private final String translationKey;

    EntityDetail(String id, int bit, String translationKey) {
        this.id = id;
        this.bit = bit;
        this.translationKey = translationKey;
    }

    public String id() {
        return this.id;
    }

    public int bit() {
        return this.bit;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public static int normalizeMask(int mask) {
        return mask & ALL_MASK;
    }
}
