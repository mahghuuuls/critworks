package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.WeaponBonus;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weapon-specific bonuses count only for the stack they belong to (REQ-072); a broken provider is skipped. */
class WeaponBonusProvidersTest {

    private final List<String> warnings = new ArrayList<String>();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private BonusCollector collector(WeaponBonusProviders providers) {
        return new BonusCollector(LogManager.getLogger("test"), providers);
    }

    @Test
    void aSwordBonusCountsForTheSwordOnly() {
        WeaponBonusProviders providers = new WeaponBonusProviders(warnings::add);
        providers.register((player, stack) -> stack.getItem() == Items.IRON_SWORD
                ? Collections.singletonList(new WeaponBonus(CritStat.INCREASED_CHANCE, 20.0D, "test:sword"))
                : Collections.<WeaponBonus>emptyList(), "test");
        BonusCollector collector = collector(providers);

        List<Bonus> sword = collector.collect(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND);
        assertEquals(1, sword.size());
        assertEquals(20.0D, BonusCollector.total(sword, CritStat.INCREASED_CHANCE));
        assertEquals("test:sword", sword.get(0).getSource());

        assertTrue(collector.collect(null, new ItemStack(Items.IRON_AXE), EnumHand.OFF_HAND).isEmpty(),
                "an offhand axe does not get the sword's bonus");
        assertTrue(collector.collect(null, new ItemStack(Items.BOW), EnumHand.MAIN_HAND).isEmpty(),
                "a bow's arrow does not get the sword's bonus");
        assertTrue(collector.collect(null, ItemStack.EMPTY, EnumHand.MAIN_HAND).isEmpty(),
                "an empty hand asks no provider");
    }

    @Test
    void providersAreAskedAboutTheAttackingStackOnly() {
        final List<ItemStack> asked = new ArrayList<ItemStack>();
        WeaponBonusProviders providers = new WeaponBonusProviders(warnings::add);
        providers.register((player, stack) -> {
            asked.add(stack);
            return Collections.singletonList(new WeaponBonus(CritStat.INCREASED_CHANCE, 5.0D, "test:any"));
        }, "test");
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        collector(providers).collect(null, axe, EnumHand.OFF_HAND);
        assertEquals(1, asked.size());
        assertTrue(asked.get(0) == axe, "the provider sees the attacking stack itself, not another hand's");
    }

    @Test
    void aProviderThatThrowsIsReportedOnceAndSkipped() {
        WeaponBonusProviders providers = new WeaponBonusProviders(warnings::add);
        providers.register((player, stack) -> {
            throw new IllegalStateException("broken");
        }, "bad");
        providers.register((player, stack) -> Collections.singletonList(
                new WeaponBonus(CritStat.ADDITIONAL_DAMAGE, 0.5D, "good:one")), "good");
        BonusCollector collector = collector(providers);
        for (int i = 0; i < 3; i++) {
            List<Bonus> bonuses = collector.collect(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND);
            assertEquals(1, bonuses.size());
            assertEquals("good:one", bonuses.get(0).getSource());
        }
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("bad"), warnings.get(0));
    }

    @Test
    void aBonusThatIsNotAFiniteNumberIsLeftOut() {
        WeaponBonusProviders providers = new WeaponBonusProviders(warnings::add);
        providers.register((player, stack) -> Collections.singletonList(
                new WeaponBonus(CritStat.INCREASED_CHANCE, Double.NaN, "test:nan")), "test");
        assertTrue(collector(providers).collect(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND).isEmpty());
    }
}
