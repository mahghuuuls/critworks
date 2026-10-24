package com.mahghuuuls.critworks.integration.rlcombat;

import bettercombat.mod.event.RLCombatAttackEntityEvent;
import bettercombat.mod.event.RLCombatCriticalHitEvent;
import com.mahghuuuls.critworks.combat.MeleeAttackSource;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.CritSettings.Integration;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.critworks.integration.IntegrationGateTest;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hand and charge of RLCombat attacks, read by name against stand-ins with RLCombat's class names. */
class RLCombatAttacksTest {

    private final List<String> warnings = new ArrayList<String>();

    @AfterEach
    void clearFailure() {
        RLCombatAttackEntityEvent.failure = null;
    }

    @Test
    void withoutRLCombatEveryAttackIsVanilla() {
        assertSame(MeleeAttackSource.NONE, RLCombatAttacks.create(gate(CritSettings.defaults(), false)));
    }

    @Test
    void readsTheAttackingHandAndItsCharge() {
        MeleeAttackSource source = RLCombatAttacks.create(gate(CritSettings.defaults(), true));
        assertEquals(EnumHand.OFF_HAND, source.handOf(new RLCombatAttackEntityEvent(null, null, true)));
        assertEquals(EnumHand.MAIN_HAND, source.handOf(new RLCombatAttackEntityEvent(null, null, false)));
        assertEquals(0.75F, source.chargeOf(new RLCombatCriticalHitEvent(null, null, 1.0F, false, true, 0.75F)));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void ownsOnlyRLCombatEvents() {
        MeleeAttackSource source = RLCombatAttacks.create(gate(CritSettings.defaults(), true));
        assertTrue(source.owns(new RLCombatAttackEntityEvent(null, null, false)));
        assertTrue(source.owns(new RLCombatCriticalHitEvent(null, null, 1.0F, false, false, 1.0F)));
        assertFalse(source.owns(new AttackEntityEvent(null, null)));
        assertFalse(source.owns(new CriticalHitEvent(null, null, 1.0F, false)));
    }

    @Test
    void settingOffLeavesRLCombatAttacksAlone() {
        MeleeAttackSource source = RLCombatAttacks.create(gate(IntegrationGateTest.settingsWithOff(Integration.RLCOMBAT), true));
        assertTrue(source.owns(new RLCombatAttackEntityEvent(null, null, true)));
        assertNull(source.handOf(new RLCombatAttackEntityEvent(null, null, true)));
        assertTrue(Float.isNaN(source.chargeOf(new RLCombatCriticalHitEvent(null, null, 1.0F, false, true, 1.0F))));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aFailingCallTurnsTheIntegrationOffWithOneWarning() {
        MeleeAttackSource source = RLCombatAttacks.create(gate(CritSettings.defaults(), true));
        RLCombatAttackEntityEvent.failure = new IllegalStateException("changed RLCombat");
        assertNull(source.handOf(new RLCombatAttackEntityEvent(null, null, true)));
        RLCombatAttackEntityEvent.failure = null;
        assertNull(source.handOf(new RLCombatAttackEntityEvent(null, null, true)));
        assertTrue(Float.isNaN(source.chargeOf(new RLCombatCriticalHitEvent(null, null, 1.0F, false, true, 1.0F))));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("changed RLCombat"), warnings.get(0));
    }

    private IntegrationGate gate(CritSettings settings, boolean loaded) {
        return new IntegrationGate(modId -> loaded && "bettercombatmod".equals(modId), settings, warnings::add);
    }
}
