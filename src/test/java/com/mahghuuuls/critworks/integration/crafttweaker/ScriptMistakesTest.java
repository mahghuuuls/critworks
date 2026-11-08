package com.mahghuuuls.critworks.integration.crafttweaker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mistake is reported the first time only; a different call, place, or message is a different mistake. */
class ScriptMistakesTest {

    @Test
    void theSameMistakeIsReportedOnce() {
        ScriptMistakes mistakes = new ScriptMistakes();
        assertTrue(mistakes.firstTime("setCritBonus", "rage.zs:12", "unknown stat \"x\""));
        for (int second = 0; second < 10; second++) {
            assertFalse(mistakes.firstTime("setCritBonus", "rage.zs:12", "unknown stat \"x\""));
        }
    }

    @Test
    void anotherCallPlaceOrMessageIsAnotherMistake() {
        ScriptMistakes mistakes = new ScriptMistakes();
        assertTrue(mistakes.firstTime("setCritBonus", "rage.zs:12", "unknown stat \"x\""));
        assertTrue(mistakes.firstTime("removeCritBonus", "rage.zs:12", "unknown stat \"x\""));
        assertTrue(mistakes.firstTime("setCritBonus", "rage.zs:13", "unknown stat \"x\""));
        assertTrue(mistakes.firstTime("setCritBonus", "rage.zs:12", "unknown stat \"y\""));
    }

    @Test
    void partsDoNotRunTogether() {
        ScriptMistakes mistakes = new ScriptMistakes();
        assertTrue(mistakes.firstTime("ab", "c", "d"));
        assertTrue(mistakes.firstTime("a", "bc", "d"));
    }
}
