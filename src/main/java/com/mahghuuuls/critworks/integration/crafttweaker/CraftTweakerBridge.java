package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import com.mahghuuuls.critworks.core.CritSettings.Integration;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.function.Consumer;

/**
 * The CraftTweaker entry point: passes each critical hit to the script handlers, and gives the player script calls
 * the switch they check. Registered only when CraftTweaker is loaded; the caller must not touch this class
 * otherwise, because it links CraftTweaker types. It listens at the lowest priority, so Java
 * handlers of the event normally see the hit first (a lowest-priority handler registered later runs after it), and
 * it does no work for a hit when no script registered a handler. Damage a script deals runs inside the event, so
 * the event's reaction rules apply to it, including a chain a Java handler allowed.
 */
public final class CraftTweakerBridge {

    private final IntegrationGate gate;

    private CraftTweakerBridge(IntegrationGate gate) {
        this.gate = gate;
    }

    /**
     * Registers the bridge and binds the gate for the player calls. With the setting off it still registers, so the
     * gate stays the one place that decides, and it logs once that script crit events and player changes are off.
     */
    public static void register(IntegrationGate gate, Consumer<String> info) {
        if (!gate.isOn(Integration.CRAFTTWEAKER)) {
            info.accept("Critworks crafttweaker integration is off in critworks.cfg: script crit events do not run"
                    + " and script calls that change a player do nothing.");
        }
        ScriptPlayerCalls.bind(gate);
        MinecraftForge.EVENT_BUS.register(new CraftTweakerBridge(gate));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCriticalHit(CritworksHitEvent event) {
        if (!gate.isOn(Integration.CRAFTTWEAKER)) {
            return;
        }
        // Script handler exceptions never reach here: CraftTweaker's event list catches and logs each one. What is
        // caught here is a failure of this bridge or of CraftTweaker itself, which turns the integration off.
        try {
            if (ScriptEvents.hasHitHandlers()) {
                ScriptEvents.publishHit(ScriptCritHit.of(event));
            }
        } catch (RuntimeException | LinkageError failure) {
            gate.fail(Integration.CRAFTTWEAKER, failure);
        }
    }
}
