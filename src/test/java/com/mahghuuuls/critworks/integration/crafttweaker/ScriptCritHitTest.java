package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a script reads from a critical hit. The values differ from the common case on purpose (off hand, sweep,
 * guaranteed, a deeper hit), so a number, word, or flag wired to the wrong source or to a fixed value fails here.
 * The attacker, player, and target are not checked here: the events carry no entities, because these tests build
 * none.
 */
class ScriptCritHitTest {

    @BeforeAll
    static void setUp() {
        Bootstrap.register();
    }

    @Test
    void everyValueComesFromItsOwnEventField() {
        CritworksHitEvent event = new CritworksHitEvent(null, null, new ItemStack(Items.IRON_SWORD),
                EnumHand.OFF_HAND, CritworksHitEvent.Kind.MELEE_SWEEP, 2.25D, true, 3.5F, 2);
        ScriptCritHit hit = ScriptCritHit.of(event);

        assertEquals("off", hit.getHand());
        assertEquals("sweep", hit.getKind());
        assertEquals(2.25D, hit.getMultiplier());
        assertTrue(hit.isGuaranteed());
        assertEquals(3.5F, hit.getDamage());
        assertEquals(2, hit.getDepth());
        assertNull(hit.getAttacker(), "an unknown attacker reaches scripts as null");
        assertNull(hit.getPlayer());
        assertNull(hit.getTarget());
    }

    @Test
    void theWeaponIsACopyOfTheEventsStack() {
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        CritworksHitEvent event = new CritworksHitEvent(null, null, weapon, EnumHand.MAIN_HAND,
                CritworksHitEvent.Kind.MELEE_PRIMARY, 2.0D, false, 6.0F, 1);
        ScriptCritHit hit = ScriptCritHit.of(event);

        assertNotNull(hit.getWeapon());
        ItemStack seen = CraftTweakerMC.getItemStack(hit.getWeapon());
        assertSame(Items.IRON_SWORD, seen.getItem());
        // A script's mutable() view edits the stack the wrapper was built from, so that stack must not be the event's.
        assertNotSame(weapon, hit.getWeapon().mutable().getInternal(), "a script must not hold the event's own stack");
        assertEquals("main", hit.getHand());
        assertEquals("melee", hit.getKind());
        assertFalse(hit.isGuaranteed());
        assertEquals(1, hit.getDepth());
    }

    @Test
    void anEmptyWeaponReachesScriptsAsNull() {
        CritworksHitEvent event = new CritworksHitEvent(null, null, ItemStack.EMPTY, EnumHand.MAIN_HAND,
                CritworksHitEvent.Kind.PROJECTILE, 1.5D, false, 4.0F, 1);
        ScriptCritHit hit = ScriptCritHit.of(event);

        assertNull(hit.getWeapon());
        assertEquals("arrow", hit.getKind());
    }
}
