package com.mahghuuuls.critworks.integration.spartan;

import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.ItemType;
import com.mahghuuuls.critworks.integration.IntegrationGate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The built-in base stats per Spartan Weaponry weapon type. Light weapons crit more often for less damage
 * and heavy weapons less often for more; every row gives the same average gain per hit as the shipped
 * fallback (15% at x2.00: 1 + chance x (damage - 1) = 1.15). They sit in the addon-default tier, so a pack
 * rule for the type or the item wins, and they are registered last, so every addon's own default wins too.
 * They do not follow a changed global fallback.
 */
public final class SpartanTypeDefaults {

    /** Type name to {chance, damage}; a default names its source "addon default from spartan type <name>". */
    private static final Map<String, double[]> VALUES = new LinkedHashMap<String, double[]>();

    static {
        put(30.0D, 1.50D, "dagger", "caestus");
        put(25.0D, 1.60D, "rapier");
        put(20.0D, 1.75D, "parrying_dagger", "saber", "katana", "quarterstaff");
        put(15.0D, 2.00D, "longsword", "spear", "mace", "club");
        put(12.0D, 2.25D, "halberd", "glaive", "pike");
        put(10.0D, 2.50D, "greatsword", "battleaxe", "hammer", "warhammer", "scythe", "lance", "longbow");
    }

    private SpartanTypeDefaults() {
    }

    private static void put(double chance, double damage, String... names) {
        for (String name : names) {
            VALUES.put(name, new double[] {chance, damage});
        }
    }

    static Map<String, double[]> values() {
        return Collections.unmodifiableMap(VALUES);
    }

    /**
     * Registers every type default when Spartan Weaponry is loaded; they count only while the spartanweaponry
     * setting is on in the settings a resolver uses, so a connected client follows the server's switch. A
     * later failure of the longbow draw-time lookup does not remove them, as it does not remove type rules.
     * Call it after every other mod could register its defaults (load complete).
     */
    public static void register(IntegrationGate gate, AddonDefaults defaults) {
        if (gate.isLoaded(CritSettings.Integration.SPARTAN_WEAPONRY)) {
            register(defaults, SpartanWeaponTypes::forName);
        }
    }

    /** With other type lookups, for tests. */
    static void register(AddonDefaults defaults, Function<String, ItemType> types) {
        String integration = CritSettings.Integration.SPARTAN_WEAPONRY.getKey();
        for (Map.Entry<String, double[]> entry : VALUES.entrySet()) {
            ItemType type = types.apply(entry.getKey());
            if (type == null) {
                throw new IllegalStateException("no Spartan type named " + entry.getKey());
            }
            defaults.register(null, stack -> type.matches(stack.getItem()), entry.getValue()[0], entry.getValue()[1],
                    type.describe(), integration);
        }
    }
}
