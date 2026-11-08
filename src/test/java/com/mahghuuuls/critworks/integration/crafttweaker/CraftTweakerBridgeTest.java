package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.CritSettings.Integration;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import crafttweaker.api.event.IEventHandle;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge passes each critical hit to the script handlers and builds nothing when no script listens; a failure
 * of the bridge itself (not of a script handler, which CraftTweaker catches) turns the CraftTweaker integration off
 * through the gate, and later hits skip the scripts. The tests share CraftTweaker's static handler list with
 * {@code ScriptContractTest}, so each closes its handle and the suite must not run them in parallel.
 */
class CraftTweakerBridgeTest {

    private final List<String> warnings = new ArrayList<String>();
    private final List<ScriptCritHit> seen = new ArrayList<ScriptCritHit>();

    @BeforeAll
    static void setUp() {
        Bootstrap.register();
    }

    @Test
    void aWorkingBridgePassesTheHitToTheScripts() {
        IntegrationGate gate = gate();
        IEventHandle handle = ScriptEvents.onHit(seen::add);
        try {
            new CraftTweakerBridge(gate).onCriticalHit(hit(new ItemStack(Items.IRON_SWORD)));
        } finally {
            handle.close();
        }
        assertEquals(1, seen.size());
        assertEquals("melee", seen.get(0).getKind());
        assertTrue(gate.isOn(Integration.CRAFTTWEAKER));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void aFailureWhileBuildingTheScriptHitTurnsTheIntegrationOff() {
        IntegrationGate gate = gate();
        IEventHandle handle = ScriptEvents.onHit(seen::add);
        try {
            CraftTweakerBridge bridge = new CraftTweakerBridge(gate);
            // An event that breaks its own contract (a null weapon) forces an exception while the script's view is
            // built; the bridge must treat it like any failure of the conversion.
            bridge.onCriticalHit(hit(null));
            assertFalse(gate.isOn(Integration.CRAFTTWEAKER));
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("crafttweaker"), warnings.get(0));

            bridge.onCriticalHit(hit(new ItemStack(Items.IRON_SWORD)));
            assertTrue(seen.isEmpty(), "after the failure no hit reaches the scripts");
        } finally {
            handle.close();
        }
    }

    @Test
    void withNoScriptHandlerTheBridgeDoesNotBuildTheScriptView() {
        IntegrationGate gate = gate();
        // The same contract-breaking event: building the view would fail, so the bridge must not build it.
        new CraftTweakerBridge(gate).onCriticalHit(hit(null));
        assertTrue(gate.isOn(Integration.CRAFTTWEAKER));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    private IntegrationGate gate() {
        Set<String> loaded = Collections.singleton("crafttweaker");
        return new IntegrationGate(loaded::contains, CritSettings.defaults(), warnings::add);
    }

    private static CritworksHitEvent hit(ItemStack weapon) {
        return new CritworksHitEvent(null, null, weapon, EnumHand.MAIN_HAND, CritworksHitEvent.Kind.MELEE_PRIMARY,
                2.0D, false, 6.0F, 1);
    }
}
