package com.mahghuuuls.critworks.state;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The saved shape of an arrow's captured stats (NBT v 1) and its one-success-per-target record. */
class ArrowCritStateTest {

    private final List<String> warnings = new ArrayList<String>();

    @Test
    void capturedStateSurvivesASaveAndLoad() {
        ArrowCritState state = new ArrowCritState();
        state.capture(true, 37.5D, 2.25D, true, false, EnumHand.OFF_HAND);
        UUID target = UUID.randomUUID();
        state.recordHit(target);

        ArrowCritState loaded = new ArrowCritState();
        loaded.read(state.write(), warnings::add);

        assertTrue(loaded.isCaptured());
        assertTrue(loaded.isEligible());
        assertEquals(37.5D, loaded.getChance());
        assertEquals(2.25D, loaded.getMultiplier());
        assertTrue(loaded.isCritical());
        assertFalse(loaded.isGuaranteed());
        assertEquals(EnumHand.OFF_HAND, loaded.getHand());
        assertFalse(loaded.recordHit(target), "the saved target already had its success");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void savedShapeIsVersionOne() {
        ArrowCritState state = new ArrowCritState();
        state.capture(false, Double.NaN, Double.NaN, false, false, EnumHand.MAIN_HAND);
        NBTTagCompound tag = state.write();
        assertEquals(1, tag.getInteger("v"));
        assertFalse(tag.getBoolean("eligible"));
        assertEquals("main", tag.getString("hand"));
        for (String key : new String[] {"v", "eligible", "chance", "multiplier", "critical", "guaranteed", "hand",
                "hitTargets"}) {
            assertTrue(tag.hasKey(key), "saved key " + key);
        }
        assertEquals(8, tag.getKeySet().size());
    }

    @Test
    void anArrowCritworksDidNotFireSavesNothing() {
        ArrowCritState state = new ArrowCritState();
        assertTrue(state.write().isEmpty());
        ArrowCritState loaded = new ArrowCritState();
        loaded.read(new NBTTagCompound(), warnings::add);
        assertFalse(loaded.isCaptured());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void unknownVersionIsReadAsNoDataWithAWarning() {
        ArrowCritState state = new ArrowCritState();
        state.capture(true, 100.0D, 2.0D, true, false, EnumHand.MAIN_HAND);
        NBTTagCompound tag = state.write();
        tag.setInteger("v", 2);
        ArrowCritState loaded = new ArrowCritState();
        loaded.read(tag, warnings::add);
        assertFalse(loaded.isCaptured());
        assertFalse(loaded.isCritical());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("version 2"), warnings.get(0));
    }

    @Test
    void theResultIsFixedAtTheFirstCapture() {
        ArrowCritState state = new ArrowCritState();
        state.capture(true, 10.0D, 1.5D, false, false, EnumHand.MAIN_HAND);
        state.capture(true, 100.0D, 3.0D, true, false, EnumHand.MAIN_HAND);
        assertFalse(state.isCritical());
        assertEquals(1.5D, state.getMultiplier());
    }

    @Test
    void eachTargetHasOneSuccess() {
        ArrowCritState state = new ArrowCritState();
        state.capture(true, 100.0D, 2.0D, true, false, EnumHand.MAIN_HAND);
        UUID first = UUID.randomUUID();
        assertFalse(state.hasHitAnyTarget());
        assertTrue(state.recordHit(first));
        assertTrue(state.hasHitAnyTarget());
        assertFalse(state.recordHit(first));
        assertTrue(state.recordHit(UUID.randomUUID()));
    }
}
