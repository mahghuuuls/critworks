package com.mahghuuuls.critworks;

import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.bonus.WeaponBonusProviders;
import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.AttackerPolicies;
import com.mahghuuuls.critworks.core.CritFormula;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.core.WeaponRule;
import com.mahghuuuls.critworks.core.WeaponRules;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Which resolver, and so which rules and cap, the public API's resolve uses on each side. */
class ApiBackendTest {

    @BeforeAll
    static void setUp() {
        Bootstrap.register();
    }

    private static ApiBackend backend() {
        return new ApiBackend(LogManager.getLogger("test"), new AddonDefaults(s -> { }),
                new WeaponBonusProviders(s -> { }), new AttackerPolicies(s -> { }));
    }

    private static StatResolver resolver(WeaponRules rules) {
        return resolver(CritSettings.defaults(), rules);
    }

    private static StatResolver resolver(CritSettings settings, WeaponRules rules) {
        return new StatResolver(settings, rules, new BonusCollector(LogManager.getLogger("test")));
    }

    private static CritSettings withCap(double cap) {
        CritSettings d = CritSettings.defaults();
        boolean[] on = new boolean[CritSettings.Integration.values().length];
        Arrays.fill(on, true);
        return new CritSettings(d.getFallbackChance(), d.getFallbackMultiplier(), d.getMeleeChargeThreshold(),
                d.getBowChargeThreshold(), cap, d.isPvpCrits(), d.getCritSound(), false, on);
    }

    @Test
    void aClientCallReturnsTheSyncedRulesAndCap() {
        StatResolver server = resolver(WeaponRules.empty());
        StatResolver synced = resolver(withCap(3.0D), new WeaponRules(Collections.singletonList(
                new WeaponRule(0, Items.IRON_SWORD, null, null, null, 30.0D, null)), s -> { }));
        ApiBackend backend = backend();
        backend.ready(server, () -> synced, null);

        assertEquals(30.0D, backend.resolve(true, null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND)
                .getBaseChance(), "the server's synced iron sword rule");
        assertEquals(15.0D, backend.resolve(false, null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND)
                .getBaseChance());
        assertFalse(backend.resolve(true, null, new ItemStack(Items.STICK), EnumHand.MAIN_HAND).isEligible());
        assertEquals(3.0D, backend.resolve(true, null, new ItemStack(Items.STICK), EnumHand.MAIN_HAND)
                .getDamageCap(), "a not-eligible stack reports the synced cap");
        assertEquals(CritFormula.NO_CAP, backend.resolve(false, null, new ItemStack(Items.STICK), EnumHand.MAIN_HAND)
                .getDamageCap());
    }

    @Test
    void aClientCallPicksTheSuppliedResolver() {
        StatResolver server = resolver(WeaponRules.empty());
        StatResolver synced = resolver(new WeaponRules(Collections.singletonList(
                new WeaponRule(0, Items.IRON_SWORD, null, null, null, 30.0D, null)), s -> { }));
        ApiBackend backend = backend();
        backend.ready(server, () -> synced, null);

        assertSame(synced, backend.resolverFor(true));
        assertSame(server, backend.resolverFor(false));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        assertEquals(30.0D, backend.resolverFor(true).resolve(null, sword, EnumHand.MAIN_HAND).getBaseChance(),
                "the server's synced iron sword rule");
        assertEquals(15.0D, backend.resolverFor(false).resolve(null, sword, EnumHand.MAIN_HAND).getBaseChance());
    }

    @Test
    void withoutAClientResolverEveryCallUsesTheCommonOne() {
        StatResolver server = resolver(WeaponRules.empty());
        ApiBackend backend = backend();
        backend.ready(server, null, null);
        assertSame(server, backend.resolverFor(true));
        assertSame(server, backend.resolverFor(false));
    }

    @Test
    void resolveBeforeLoadingFinishesFails() {
        assertThrows(IllegalStateException.class, () -> backend().resolverFor(false));
        assertThrows(IllegalStateException.class, () -> backend().resolverFor(true));
    }
}
