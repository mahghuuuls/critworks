package com.mahghuuuls.critworks.integration.spartan;

import com.mahghuuuls.critworks.core.ItemType;
import net.minecraft.item.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Spartan Weaponry 1.6.1 weapon types a pack rule can name (RD-2.9). Spartan Weaponry makes each type
 * with one item class, also for addon weapons made through its API, so a type matches every item of that
 * class or a subclass. Classes are compared by name: no Spartan class is loaded or named outside this
 * package. Crossbows and throwing weapons are not offered: their attacks are not supported (REQ-030).
 */
public final class SpartanWeaponTypes {

    static final String ITEM_PACKAGE = "com.oblivioussp.spartanweaponry.item.";

    private static final Map<String, ItemType> TYPES = new LinkedHashMap<String, ItemType>();

    static {
        add("dagger", "ItemDagger");
        add("parrying_dagger", "ItemParryingDagger");
        add("longsword", "ItemLongsword");
        add("katana", "ItemKatana");
        add("saber", "ItemSaber");
        add("rapier", "ItemRapier");
        add("greatsword", "ItemGreatsword");
        add("hammer", "ItemHammer");
        add("warhammer", "ItemWarhammer");
        add("spear", "ItemSpear");
        add("halberd", "ItemHalberd");
        add("pike", "ItemPike");
        add("lance", "ItemLance");
        add("glaive", "ItemGlaive");
        add("quarterstaff", "ItemQuarterstaff");
        add("scythe", "ItemScythe");
        add("battleaxe", "ItemBattleaxe");
        add("mace", "ItemMace");
        add("club", "ItemClub");
        add("caestus", "ItemCaestus");
        add("longbow", "ItemLongbow");
    }

    private SpartanWeaponTypes() {
    }

    private static void add(String name, String simpleClassName) {
        TYPES.put(name, new ClassType(name, ITEM_PACKAGE + simpleClassName));
    }

    /** The type with this rule name, or null. */
    public static ItemType forName(String name) {
        return TYPES.get(name);
    }

    /** Every rule name, in the RD-2.9 order. */
    public static Set<String> names() {
        return Collections.unmodifiableSet(TYPES.keySet());
    }

    /** Whether the item's class is {@code className} or a subclass of it, compared by name and cached per class. */
    static boolean isInstanceByName(Item item, String className, Map<Class<?>, Boolean> cache) {
        Class<?> itemClass = item.getClass();
        Boolean known = cache.get(itemClass);
        if (known != null) {
            return known;
        }
        boolean result = false;
        for (Class<?> type = itemClass; type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) {
                result = true;
                break;
            }
        }
        cache.put(itemClass, result);
        return result;
    }

    /** A type made with one Spartan item class. */
    static final class ClassType implements ItemType {
        final String name;
        final String className;
        private final Map<Class<?>, Boolean> cache = new ConcurrentHashMap<Class<?>, Boolean>();

        ClassType(String name, String className) {
            this.name = name;
            this.className = className;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String describe() {
            return "spartan type " + name;
        }

        @Override
        public boolean matches(Item item) {
            return isInstanceByName(item, className, cache);
        }
    }
}
