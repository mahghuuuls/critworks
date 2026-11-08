package com.mahghuuuls.critworks.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.event.IEventHandle;
import crafttweaker.api.event.IEventManager;
import crafttweaker.util.EventList;
import crafttweaker.util.IEventHandler;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenExpansion;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * Script subscriptions to critical hits: {@code events.onCritworksHit(function(event as mods.critworks.CritHitEvent) {...})},
 * or the same through {@code mods.critworks.Events.onHit(...)}. CraftTweaker's event list catches a handler that
 * throws, logs it to crafttweaker.log, and goes on with the other handlers, so a broken script cannot break a hit.
 */
@ZenRegister
@ZenClass("mods.critworks.Events")
public final class ScriptEvents {

    private static final EventList<ScriptCritHit> HITS = new EventList<ScriptCritHit>();

    private ScriptEvents() {
    }

    @ZenMethod
    public static IEventHandle onHit(IEventHandler<ScriptCritHit> handler) {
        return HITS.add(handler);
    }

    static boolean hasHitHandlers() {
        return HITS.hasHandlers();
    }

    static void publishHit(ScriptCritHit hit) {
        HITS.publish(hit);
    }

    /** {@code events.onCritworksHit(...)}. */
    @ZenRegister
    @ZenExpansion("crafttweaker.events.IEventManager")
    public static final class ManagerExpansion {
        private ManagerExpansion() {
        }

        @ZenMethod
        public static IEventHandle onCritworksHit(IEventManager manager, IEventHandler<ScriptCritHit> handler) {
            return onHit(handler);
        }
    }
}
