package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.GuaranteeSpec;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a script may pass to the player calls, and what the script log says when it may not. */
class ScriptArgsTest {

    @Test
    void everyAttributeNameIsAStatAndNothingElseIs() {
        for (CritStat stat : CritStat.values()) {
            assertEquals(stat, ScriptArgs.stat(stat.getAttributeName()));
        }
        assertEquals(CritStat.ADDITIONAL_CHANCE, ScriptArgs.stat("critworks.additionalCritChance"));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> ScriptArgs.stat("critworks.bogus"));
        assertTrue(unknown.getMessage().contains("\"critworks.bogus\""));
        assertTrue(unknown.getMessage().contains("critworks.increasedCritDamage"), "the message lists the names");
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.stat("Critworks.AdditionalCritChance"));
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.stat(null));
    }

    @Test
    void idsAndSourcesMustHaveText() {
        assertEquals("mypack:focus", ScriptArgs.id("mypack:focus", "the guarantee id"));
        assertEquals("the guarantee id must not be empty",
                assertThrows(IllegalArgumentException.class, () -> ScriptArgs.id("", "the guarantee id")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.id("   ", "the bonus source"));
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.id(null, "the bonus source"));
    }

    @Test
    void ticksAreZeroOrMore() {
        assertEquals(0, ScriptArgs.ticks(0));
        assertEquals(200, ScriptArgs.ticks(200));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScriptArgs.ticks(-1)).getMessage().contains("-1"));
    }

    @Test
    void zeroTicksMeansNoTimeLimitAndTheFlagsCarryOver() {
        GuaranteeSpec unlimited = ScriptArgs.guarantee("mypack:focus", 0, false);
        assertEquals("mypack:focus", unlimited.getId());
        assertFalse(unlimited.hasDuration());
        assertEquals(GuaranteeSpec.NO_DURATION, unlimited.getDurationTicks());
        assertFalse(unlimited.isKeptOnDeath());

        GuaranteeSpec timed = ScriptArgs.guarantee("mypack:focus", 200, true);
        assertTrue(timed.hasDuration());
        assertEquals(200, timed.getDurationTicks());
        assertTrue(timed.isKeptOnDeath());

        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.guarantee("mypack:focus", -5, false));
        assertEquals("the guarantee id must not be empty",
                assertThrows(IllegalArgumentException.class, () -> ScriptArgs.guarantee(" ", 0, false)).getMessage());
    }

    @Test
    void handsAreMainOffOrLeftOut() {
        assertEquals(EnumHand.MAIN_HAND, ScriptArgs.hand(null));
        assertEquals(EnumHand.MAIN_HAND, ScriptArgs.hand("main"));
        assertEquals(EnumHand.OFF_HAND, ScriptArgs.hand("off"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScriptArgs.hand("left")).getMessage()
                .contains("\"left\""));
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.hand("Main"));
    }

    @Test
    void amountsAreFiniteNumbers() {
        assertEquals(-2.5D, ScriptArgs.amount(-2.5D));
        assertEquals(0.0D, ScriptArgs.amount(0.0D));
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.amount(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.amount(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> ScriptArgs.amount(Double.NEGATIVE_INFINITY));
    }
}
