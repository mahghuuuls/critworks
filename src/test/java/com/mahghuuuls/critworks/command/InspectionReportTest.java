package com.mahghuuuls.critworks.command;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.core.CritFormula;
import com.mahghuuuls.critworks.core.CritProfile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the production inspection text: eligibility and its reason, sources, cap, and both hands. */
class InspectionReportTest {

    @Test
    void reportsEveryFieldForBothHands() {
        CritProfile sword = CritProfile.eligible("recognized weapon", 5.0D, "fallback", 1.5D,
                "pack rule #2 minecraft:iron_sword", 5.0D, 1.5D, CritFormula.NO_CAP, Collections.<Bonus>emptyList());
        CritProfile stick = CritProfile.notEligible("not a recognized weapon");
        List<String> lines = InspectionReport.lines("Steve", CritFormula.NO_CAP, Collections.<Bonus>emptyList(),
                new InspectionReport.HandView("Iron Sword (minecraft:iron_sword)", sword),
                new InspectionReport.HandView("Stick (minecraft:stick)", stick));
        assertEquals("Critworks inspect: Steve", lines.get(0));
        assertEquals("Damage cap: none", lines.get(1));
        assertEquals("Player bonus totals: critworks.increasedCritChance +0.00, critworks.additionalCritChance +0.00,"
                + " critworks.increasedCritDamage +0.00, critworks.additionalCritDamage +0.00", lines.get(2));
        assertEquals("Main hand: Iron Sword (minecraft:iron_sword) - eligible: recognized weapon", lines.get(3));
        assertEquals("  Base chance 5.00% from fallback", lines.get(4));
        assertEquals("  Base damage x1.50 from pack rule #2 minecraft:iron_sword", lines.get(5));
        assertEquals("  Bonuses: none", lines.get(6));
        assertEquals("  Effective chance 5.00%, damage x1.50", lines.get(7));
        assertEquals("Offhand: Stick (minecraft:stick) - not eligible: not a recognized weapon", lines.get(8));
        assertEquals("Guarantees: none", lines.get(9));
    }

    @Test
    void capShowsEvenWhenNoHandIsEligible() {
        CritProfile none = CritProfile.notEligible("not a recognized weapon");
        List<Bonus> food = Arrays.asList(new Bonus(CritStat.ADDITIONAL_CHANCE, 10.0D, "JAFS"));
        List<String> lines = InspectionReport.lines("Alex", 3.0D, food, new InspectionReport.HandView("Stick", none),
                new InspectionReport.HandView("empty", none));
        assertEquals("Damage cap: x3.00", lines.get(1));
        assertEquals("Player bonus totals: critworks.increasedCritChance +0.00, critworks.additionalCritChance +10.00,"
                + " critworks.increasedCritDamage +0.00, critworks.additionalCritDamage +0.00", lines.get(2));
    }

    @Test
    void listsEachBonusWithItsSource() {
        CritProfile sword = CritProfile.eligible("recognized weapon", 5.0D, "fallback", 1.5D, "fallback", 15.0D, 1.5D,
                CritFormula.NO_CAP, Arrays.asList(new Bonus(CritStat.ADDITIONAL_CHANCE, 10.0D, "JAFS"),
                        new Bonus(CritStat.INCREASED_DAMAGE, -20.0D, "critworks_devtest:test")));
        List<String> lines = InspectionReport.lines("Alex", CritFormula.NO_CAP, sword.getBonuses(),
                new InspectionReport.HandView("Sword", sword),
                new InspectionReport.HandView("empty", CritProfile.notEligible("not a recognized weapon")));
        assertEquals("  Bonus +10.00 additional chance from JAFS", lines.get(6));
        assertEquals("  Bonus -20.00 increased damage from critworks_devtest:test", lines.get(7));
    }

    @Test
    void ruleEnabledItemNamesTheRule() {
        CritProfile stick = CritProfile.eligible("pack rule #1 minecraft:stick", 50.0D, "pack rule #1 minecraft:stick",
                1.5D, "fallback", 50.0D, 1.5D, CritFormula.NO_CAP, Collections.<Bonus>emptyList());
        List<String> lines = InspectionReport.lines("Alex", CritFormula.NO_CAP, Collections.<Bonus>emptyList(),
                new InspectionReport.HandView("Stick", stick),
                new InspectionReport.HandView("empty", CritProfile.notEligible("not a recognized weapon")));
        assertEquals("Main hand: Stick - eligible: pack rule #1 minecraft:stick", lines.get(3));
    }

    @Test
    void listsPendingGuaranteesInUseOrderWithTimeLeft() {
        com.mahghuuuls.critworks.state.PlayerCritState state = new com.mahghuuuls.critworks.state.PlayerCritState();
        state.grant(com.mahghuuuls.critworks.api.GuaranteeSpec.of("b").keptOnDeath());
        state.grant(com.mahghuuuls.critworks.api.GuaranteeSpec.of("a").lasting(1200));
        List<String> lines = InspectionReport.lines("Alex", CritFormula.NO_CAP, Collections.<Bonus>emptyList(),
                new InspectionReport.HandView("empty", CritProfile.notEligible("not a recognized weapon")),
                new InspectionReport.HandView("empty", CritProfile.notEligible("not a recognized weapon")),
                state.list());
        assertEquals("Guarantees (next used first): a (60.0 s left), b (no time limit, kept on death)",
                lines.get(lines.size() - 1));
    }
}
