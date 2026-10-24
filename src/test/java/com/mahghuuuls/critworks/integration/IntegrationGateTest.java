package com.mahghuuuls.critworks.integration;

import com.mahghuuuls.critworks.core.CritFormula;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.CritSettings.Integration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The integration on/off state: mod loaded, setting on, and not failed this session (REQ-122, REQ-181). */
public class IntegrationGateTest {

    private final List<String> warnings = new ArrayList<String>();

    @Test
    void integrationWithoutItsModIsOff() {
        IntegrationGate gate = gate(CritSettings.defaults());
        assertFalse(gate.isLoaded(Integration.QUARK));
        assertFalse(gate.isOn(Integration.QUARK));
    }

    @Test
    void loadedModWithSettingOnIsOn() {
        assertTrue(gate(CritSettings.defaults(), "bettercombatmod").isOn(Integration.RLCOMBAT));
    }

    @Test
    void settingOffTurnsALoadedIntegrationOff() {
        IntegrationGate gate = gate(settingsWithOff(Integration.RLCOMBAT), "bettercombatmod");
        assertTrue(gate.isLoaded(Integration.RLCOMBAT));
        assertFalse(gate.isOn(Integration.RLCOMBAT));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void failureTurnsOnlyThatIntegrationOffWithOneWarning() {
        IntegrationGate gate = gate(CritSettings.defaults(), "bettercombatmod", "jafs");
        gate.fail(Integration.RLCOMBAT, new IllegalStateException("first"));
        gate.fail(Integration.RLCOMBAT, new IllegalStateException("second"));
        assertFalse(gate.isOn(Integration.RLCOMBAT));
        assertTrue(gate.isLoaded(Integration.RLCOMBAT));
        assertTrue(gate.isOn(Integration.JAFS));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("rlcombat") && warnings.get(0).contains("first"), warnings.get(0));
    }

    @Test
    void everyIntegrationHasAModId() {
        for (Integration integration : Integration.values()) {
            assertFalse(IntegrationGate.modId(integration).isEmpty());
        }
    }

    private IntegrationGate gate(CritSettings settings, String... loadedMods) {
        final Set<String> loaded = new HashSet<String>(Arrays.asList(loadedMods));
        return new IntegrationGate(loaded::contains, settings, warnings::add);
    }

    /** Default settings with one integration switched off; also used by the integration tests. */
    public static CritSettings settingsWithOff(Integration off) {
        boolean[] on = new boolean[Integration.values().length];
        Arrays.fill(on, true);
        on[off.ordinal()] = false;
        return new CritSettings(CritSettings.DEFAULT_FALLBACK_CHANCE, CritSettings.DEFAULT_FALLBACK_MULTIPLIER,
                CritSettings.DEFAULT_MELEE_THRESHOLD, CritSettings.DEFAULT_BOW_THRESHOLD, CritFormula.NO_CAP, true,
                CritSettings.SOUND_AUTO, false, on);
    }
}
