package com.mahghuuuls.critworks.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import org.junit.jupiter.api.Test;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenExpansion;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The script names are a public contract: scripts written against them must keep compiling. These checks read the
 * CraftTweaker annotations themselves, so a renamed method or a dropped annotation fails here instead of in a pack.
 */
public class ScriptContractTest {

    @Test
    void hitEventTypeHasItsNameAndEveryField() {
        assertNotNull(ScriptCritHit.class.getAnnotation(ZenRegister.class));
        assertEquals("mods.critworks.CritHitEvent", ScriptCritHit.class.getAnnotation(ZenClass.class).value());
        Set<String> getters = new HashSet<String>();
        for (Method method : ScriptCritHit.class.getMethods()) {
            ZenGetter getter = method.getAnnotation(ZenGetter.class);
            if (getter != null) {
                getters.add(getter.value());
            }
        }
        assertEquals(new HashSet<String>(Arrays.asList("attacker", "player", "target", "weapon", "hand", "kind",
                "multiplier", "guaranteed", "damage", "depth")), getters);
    }

    @Test
    void eventsHolderOffersOnHit() throws Exception {
        assertNotNull(ScriptEvents.class.getAnnotation(ZenRegister.class));
        assertEquals("mods.critworks.Events", ScriptEvents.class.getAnnotation(ZenClass.class).value());
        Method onHit = ScriptEvents.class.getMethod("onHit", crafttweaker.util.IEventHandler.class);
        assertNotNull(onHit.getAnnotation(ZenMethod.class));
        assertTrue(Modifier.isStatic(onHit.getModifiers()));
    }

    @Test
    void eventManagerExpansionOffersOnCritworksHit() throws Exception {
        Class<?> expansion = ScriptEvents.ManagerExpansion.class;
        assertNotNull(expansion.getAnnotation(ZenRegister.class));
        assertEquals(crafttweaker.api.event.IEventManager.class.getAnnotation(ZenClass.class).value(),
                expansion.getAnnotation(ZenExpansion.class).value());
        Method method = expansion.getMethod("onCritworksHit", crafttweaker.api.event.IEventManager.class,
                crafttweaker.util.IEventHandler.class);
        assertNotNull(method.getAnnotation(ZenMethod.class));
        assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void bothRegistrationFormsFeedTheListTheBridgePublishesTo() {
        java.util.List<ScriptCritHit> seenByExpansion = new java.util.ArrayList<ScriptCritHit>();
        java.util.List<ScriptCritHit> seenByHolder = new java.util.ArrayList<ScriptCritHit>();
        crafttweaker.api.event.IEventHandle viaExpansion = ScriptEvents.ManagerExpansion.onCritworksHit(null, seenByExpansion::add);
        crafttweaker.api.event.IEventHandle viaHolder = ScriptEvents.onHit(seenByHolder::add);
        try {
            assertTrue(ScriptEvents.hasHitHandlers());
            ScriptEvents.publishHit(null);
            assertEquals(1, seenByExpansion.size());
            assertEquals(1, seenByHolder.size());
        } finally {
            viaExpansion.close();
            viaHolder.close();
        }
        assertTrue(!ScriptEvents.hasHitHandlers());
    }
}
