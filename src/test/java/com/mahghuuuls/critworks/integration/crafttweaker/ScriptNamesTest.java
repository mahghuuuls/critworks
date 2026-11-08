package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The words scripts see for attack kinds and hands, and the CraftTweaker switch. */
public class ScriptNamesTest {

    @Test
    void everyAttackKindHasItsScriptWord() {
        assertEquals("melee", ScriptNames.kind(CritworksHitEvent.Kind.MELEE_PRIMARY));
        assertEquals("sweep", ScriptNames.kind(CritworksHitEvent.Kind.MELEE_SWEEP));
        assertEquals("arrow", ScriptNames.kind(CritworksHitEvent.Kind.PROJECTILE));
        assertEquals("integration", ScriptNames.kind(CritworksHitEvent.Kind.INTEGRATION));
        Set<String> words = new HashSet<String>();
        for (CritworksHitEvent.Kind kind : CritworksHitEvent.Kind.values()) {
            words.add(ScriptNames.kind(kind));
        }
        assertEquals(CritworksHitEvent.Kind.values().length, words.size());
    }

    @Test
    void handsAreMainAndOff() {
        assertEquals("main", ScriptNames.hand(EnumHand.MAIN_HAND));
        assertEquals("off", ScriptNames.hand(EnumHand.OFF_HAND));
    }

    @Test
    void craftTweakerSwitchFollowsItsModAndSetting() {
        assertEquals("crafttweaker", CritSettings.Integration.CRAFTTWEAKER.getKey());
        assertEquals("crafttweaker", IntegrationGate.modId(CritSettings.Integration.CRAFTTWEAKER));
        assertTrue(new IntegrationGate("crafttweaker"::equals, CritSettings.defaults(), warning -> { })
                .isOn(CritSettings.Integration.CRAFTTWEAKER));
        assertFalse(new IntegrationGate(id -> false, CritSettings.defaults(), warning -> { })
                .isOn(CritSettings.Integration.CRAFTTWEAKER));
        boolean[] on = new boolean[CritSettings.Integration.values().length];
        java.util.Arrays.fill(on, true);
        on[CritSettings.Integration.CRAFTTWEAKER.ordinal()] = false;
        CritSettings off = new CritSettings(15, 2, 100, 100, 0, true, "auto", false, on);
        assertFalse(new IntegrationGate("crafttweaker"::equals, off, warning -> { })
                .isOn(CritSettings.Integration.CRAFTTWEAKER));
    }
}
