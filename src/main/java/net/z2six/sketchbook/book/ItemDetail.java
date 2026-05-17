package net.z2six.sketchbook.book;

public enum ItemDetail {
    NAME("name", 1, "menu.sketchbook.item_detail.name"),
    ARMOR("armor", 1 << 1, "menu.sketchbook.item_detail.armor"),
    ARMOR_TOUGHNESS("armor_toughness", 1 << 2, "menu.sketchbook.item_detail.armor_toughness"),
    KNOCKBACK_RESISTANCE("knockback_resistance", 1 << 3, "menu.sketchbook.item_detail.knockback_resistance"),
    DAMAGE("damage", 1 << 4, "menu.sketchbook.item_detail.damage"),
    ATTACK_SPEED("attack_speed", 1 << 5, "menu.sketchbook.item_detail.attack_speed"),
    DURABILITY("durability", 1 << 6, "menu.sketchbook.item_detail.durability"),
    NUTRITION("nutrition", 1 << 7, "menu.sketchbook.item_detail.nutrition"),
    SATURATION("saturation", 1 << 8, "menu.sketchbook.item_detail.saturation");

    private static final int ALL_MASK = NAME.bit | ARMOR.bit | ARMOR_TOUGHNESS.bit | KNOCKBACK_RESISTANCE.bit | DAMAGE.bit | ATTACK_SPEED.bit | DURABILITY.bit | NUTRITION.bit | SATURATION.bit;

    private final String id;
    private final int bit;
    private final String translationKey;

    ItemDetail(String id, int bit, String translationKey) {
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
