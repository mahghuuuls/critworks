package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.WeaponBonus;
import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.bonus.WeaponBonusProviders;
import com.mahghuuuls.critworks.core.CritFormula;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.core.WeaponRules;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the tooltip text through the production resolver and the shipped en_us language file:
 * the one-line form (REQ-092), the Shift preview with sources (REQ-093), and the preview of a weapon
 * not in hand (REQ-094).
 */
class TooltipLinesTest {

    private static final Map<String, String> LANG = new HashMap<String, String>();

    @BeforeAll
    static void setUp() throws Exception {
        Bootstrap.register();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                TooltipLinesTest.class.getResourceAsStream("/assets/critworks/lang/en_us.lang"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0) {
                    LANG.put(line.substring(0, equals), line.substring(equals + 1));
                }
            }
        }
    }

    /** Like I18n: a missing key shows as the key itself, which the tests would catch. */
    private static String format(String key, Object... args) {
        String pattern = LANG.get(key);
        return pattern == null ? key : String.format(pattern, args);
    }

    private static List<String> plain(List<String> lines) {
        String[] result = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            result[i] = TextFormatting.getTextWithoutFormattingCodes(lines.get(i));
        }
        return Arrays.asList(result);
    }

    private static StatResolver resolver(WeaponBonusProviders providers) {
        return new StatResolver(CritSettings.defaults(), WeaponRules.empty(),
                new BonusCollector(LogManager.getLogger("test"), providers));
    }

    @Test
    void ironSwordWithTheDefaultConfigShowsOneLine() {
        CritProfile profile = resolver(new WeaponBonusProviders(s -> { }))
                .resolve(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND);
        assertEquals(Collections.singletonList("Crit: 5% | x1.50"),
                plain(TooltipLines.lines(profile, false, TooltipLinesTest::format)));
    }

    @Test
    void aStickShowsNothing() {
        CritProfile profile = resolver(new WeaponBonusProviders(s -> { }))
                .resolve(null, new ItemStack(Items.STICK), EnumHand.MAIN_HAND);
        assertTrue(TooltipLines.lines(profile, false, TooltipLinesTest::format).isEmpty());
        assertTrue(TooltipLines.lines(profile, true, TooltipLinesTest::format).isEmpty());
    }

    @Test
    void shiftShowsThePreviewWithEachBonusAndItsSource() {
        CritProfile profile = CritProfile.eligible("recognized weapon", 5.0D, "fallback", 1.5D, "fallback", 15.0D, 1.5D,
                CritFormula.NO_CAP, Collections.singletonList(new Bonus(CritStat.ADDITIONAL_CHANCE, 10.0D, "JAFS")));
        assertEquals(Arrays.asList("Crit: 5% | x1.50", "Current preview, not for arrows already fired:",
                " Effective: 15% | x1.50", " +10 Additional Crit Chance (JAFS)"),
                plain(TooltipLines.lines(profile, true, TooltipLinesTest::format)));
    }

    @Test
    void aBonusWithoutASyncedNameSaysSo() {
        CritProfile profile = CritProfile.eligible("recognized weapon", 5.0D, "fallback", 1.5D, "fallback", 5.5D, 1.5D,
                CritFormula.NO_CAP, Collections.singletonList(
                        new Bonus(CritStat.INCREASED_CHANCE, 10.0D, TooltipLines.UNNAMED_SYNCED_MODIFIER)));
        assertEquals(" +10 Increased Crit Chance (unknown source)",
                plain(TooltipLines.lines(profile, true, TooltipLinesTest::format)).get(3));
    }

    @Test
    void shiftWithoutBonusesSaysSo() {
        CritProfile profile = resolver(new WeaponBonusProviders(s -> { }))
                .resolve(null, new ItemStack(Items.BOW), EnumHand.MAIN_HAND);
        assertEquals(" No bonuses", plain(TooltipLines.lines(profile, true, TooltipLinesTest::format)).get(3));
    }

    @Test
    void theBowPreviewLeavesOutTheSwordsOwnBonus() {
        WeaponBonusProviders providers = new WeaponBonusProviders(s -> { });
        providers.register((player, stack) -> stack.getItem() == Items.IRON_SWORD
                ? Collections.singletonList(new WeaponBonus(CritStat.INCREASED_CHANCE, 20.0D, "Sword Bonus"))
                : Collections.<WeaponBonus>emptyList(), "test");
        StatResolver resolver = resolver(providers);
        List<String> sword = plain(TooltipLines.lines(
                resolver.resolve(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND), true, TooltipLinesTest::format));
        List<String> bow = plain(TooltipLines.lines(
                resolver.resolve(null, new ItemStack(Items.BOW), EnumHand.MAIN_HAND), true, TooltipLinesTest::format));
        assertEquals(" +20 Increased Crit Chance (Sword Bonus)", sword.get(3));
        assertEquals(" Effective: 5% | x1.50", bow.get(2));
        assertEquals(" No bonuses", bow.get(3));
    }

    @Test
    void numbersKeepAtMostTwoDecimals() {
        assertEquals("12.5%", TooltipLines.percent(12.5D));
        assertEquals("0.13%", TooltipLines.percent(0.125D));
        assertEquals("100%", TooltipLines.percent(100.0D));
        assertEquals("x2.00", TooltipLines.multiplier(2.0D));
        assertEquals("-0.25", TooltipLines.signed(-0.25D));
    }
}
