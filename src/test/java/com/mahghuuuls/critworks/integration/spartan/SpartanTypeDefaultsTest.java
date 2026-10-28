package com.mahghuuuls.critworks.integration.spartan;

import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.ItemType;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.core.WeaponRule;
import com.mahghuuuls.critworks.core.WeaponRules;
import com.mahghuuuls.critworks.integration.IntegrationGateTest;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The built-in Spartan type values through the production resolver, with stand-ins for the Spartan item classes. */
class SpartanTypeDefaultsTest {

    @BeforeAll
    static void setUp() {
        Bootstrap.register();
    }

    /** Stand-ins for Spartan's dagger and greatsword classes. */
    public static class Dagger extends ItemSword {
        public Dagger() {
            super(Item.ToolMaterial.IRON);
        }
    }

    public static class Greatsword extends ItemSword {
        public Greatsword() {
            super(Item.ToolMaterial.IRON);
        }
    }

    private static final ItemType DAGGER = new SpartanWeaponTypes.ClassType("dagger", Dagger.class.getName());
    private static final ItemType GREATSWORD = new SpartanWeaponTypes.ClassType("greatsword", Greatsword.class.getName());

    /** The real type list, with the dagger and greatsword classes swapped for the stand-ins. */
    private static final Function<String, ItemType> TYPES = name -> "dagger".equals(name) ? DAGGER
            : "greatsword".equals(name) ? GREATSWORD : SpartanWeaponTypes.forName(name);

    private static AddonDefaults typeDefaults() {
        AddonDefaults defaults = new AddonDefaults(s -> { });
        SpartanTypeDefaults.register(defaults, TYPES);
        return defaults;
    }

    private static CritSettings settings(boolean spartanOn) {
        return spartanOn ? CritSettings.defaults()
                : IntegrationGateTest.settingsWithOff(CritSettings.Integration.SPARTAN_WEAPONRY);
    }

    private static StatResolver resolver(AddonDefaults defaults, boolean spartanOn, WeaponRule... rules) {
        return new StatResolver(settings(spartanOn), new WeaponRules(Arrays.asList(rules), s -> { }),
                new BonusCollector(LogManager.getLogger("test")), defaults);
    }

    private static StatResolver resolver(boolean spartanOn, WeaponRule... rules) {
        return resolver(typeDefaults(), spartanOn, rules);
    }

    private static CritProfile resolve(StatResolver resolver, Item item) {
        return resolver.resolve(null, new ItemStack(item), EnumHand.MAIN_HAND);
    }

    @Test
    void everyTypeHasTheApprovedValueWithTheFallbacksAverageGain() {
        Map<String, double[]> values = SpartanTypeDefaults.values();
        assertEquals(new ArrayList<String>(SpartanWeaponTypes.names()).size(), values.size(), "one value per type");
        List<String> missing = new ArrayList<String>(SpartanWeaponTypes.names());
        missing.removeAll(values.keySet());
        assertEquals(Collections.<String>emptyList(), missing);
        String[][] table = {
                {"30", "1.50", "dagger", "caestus"},
                {"25", "1.60", "rapier"},
                {"20", "1.75", "parrying_dagger", "saber", "katana", "quarterstaff"},
                {"15", "2.00", "longsword", "spear", "mace", "club"},
                {"12", "2.25", "halberd", "glaive", "pike"},
                {"10", "2.50", "greatsword", "battleaxe", "hammer", "warhammer", "scythe", "lance", "longbow"}};
        int pinned = 0;
        for (String[] row : table) {
            for (int k = 2; k < row.length; k++) {
                double[] value = values.get(row[k]);
                assertEquals(Double.parseDouble(row[0]), value[0], row[k] + " chance");
                assertEquals(Double.parseDouble(row[1]), value[1], row[k] + " damage");
                pinned++;
            }
        }
        assertEquals(21, pinned, "every type is pinned to the approved table");
        for (Map.Entry<String, double[]> entry : values.entrySet()) {
            double gain = 1.0D + entry.getValue()[0] / 100.0D * (entry.getValue()[1] - 1.0D);
            assertEquals(1.15D, gain, 1.0E-9, entry.getKey() + " keeps the fallback's average gain");
        }
    }

    @Test
    void withNoRuleASpartanTypeUsesItsDefault() {
        StatResolver resolver = resolver(true);
        CritProfile dagger = resolve(resolver, new Dagger());
        assertEquals(30.0D, dagger.getBaseChance());
        assertEquals(1.50D, dagger.getBaseMultiplier());
        assertEquals("addon default from spartan type dagger", dagger.getBaseChanceSource());
        assertEquals("addon default from spartan type dagger", dagger.getBaseMultiplierSource());
        CritProfile greatsword = resolve(resolver, new Greatsword());
        assertEquals(10.0D, greatsword.getBaseChance());
        assertEquals(2.50D, greatsword.getBaseMultiplier());
        CritProfile vanilla = resolve(resolver, Items.IRON_SWORD);
        assertEquals(15.0D, vanilla.getBaseChance(), "a vanilla sword keeps the fallback");
        assertEquals(2.00D, vanilla.getBaseMultiplier());
    }

    @Test
    void aTypeRuleSettingOnlyChanceKeepsTheTypeDamage() {
        CritProfile dagger = resolve(resolver(true, WeaponRule.forType(0, DAGGER, null, 25.0D, null)), new Dagger());
        assertEquals(25.0D, dagger.getBaseChance());
        assertEquals(1.50D, dagger.getBaseMultiplier());
        assertEquals("addon default from spartan type dagger", dagger.getBaseMultiplierSource());
    }

    @Test
    void withTheSwitchOffSpartanWeaponsUseTheFallback() {
        CritProfile dagger = resolve(resolver(false), new Dagger());
        assertEquals(15.0D, dagger.getBaseChance());
        assertEquals(2.00D, dagger.getBaseMultiplier());
        assertEquals("fallback", dagger.getBaseChanceSource());
    }

    @Test
    void eachResolverFollowsTheSwitchOfItsOwnSettings() {
        AddonDefaults shared = typeDefaults();
        StatResolver serverOff = resolver(shared, false);
        StatResolver clientOn = resolver(shared, true);
        assertEquals(15.0D, resolve(serverOff, new Dagger()).getBaseChance(), "the switch off in these settings");
        assertEquals(30.0D, resolve(clientOn, new Dagger()).getBaseChance(), "the same defaults, the switch on");
    }

    @Test
    void anUnknownTypeNameFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> SpartanTypeDefaults.register(new AddonDefaults(s -> { }), name -> null));
    }

    @Test
    void anAddonsOwnDefaultWinsOverTheTypeDefault() {
        AddonDefaults defaults = new AddonDefaults(s -> { });
        defaults.register(null, stack -> stack.getItem() instanceof Dagger, 40.0D, null, "someaddon");
        SpartanTypeDefaults.register(defaults, TYPES);
        CritProfile dagger = new StatResolver(CritSettings.defaults(), WeaponRules.empty(),
                new BonusCollector(LogManager.getLogger("test")), defaults).resolve(null, new ItemStack(new Dagger()),
                EnumHand.MAIN_HAND);
        assertEquals(40.0D, dagger.getBaseChance(), "registered earlier, so it wins");
        assertEquals(1.50D, dagger.getBaseMultiplier(), "the type default still sets the damage the addon left open");
    }
}
