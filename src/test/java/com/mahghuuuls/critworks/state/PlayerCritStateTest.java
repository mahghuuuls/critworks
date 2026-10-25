package com.mahghuuuls.critworks.state;

import com.mahghuuuls.critworks.api.GuaranteeSpec;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guarantee rules: replace by id, use order, online countdown, death and travel, saved shape (NBT v 1). */
class PlayerCritStateTest {

    private final List<String> warnings = new ArrayList<String>();

    @Test
    void grantingTheSameIdReplacesIt() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("a"));
        state.grant(GuaranteeSpec.of("a").lasting(100));
        assertEquals(1, state.list().size());
        assertEquals(100, state.list().get(0).getDurationTicks());
    }

    @Test
    void differentIdsCoexist() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("a"));
        state.grant(GuaranteeSpec.of("b"));
        assertEquals(2, state.list().size());
    }

    @Test
    void theOneThatExpiresFirstIsUsedFirst() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("b"));
        state.grant(GuaranteeSpec.of("a").lasting(1200));
        assertEquals("a", state.nextId(), "a timed guarantee comes before one with no time limit");
        state.grant(GuaranteeSpec.of("c").lasting(600));
        assertEquals("c", state.nextId());
    }

    @Test
    void amongEqualsTheOldestIsUsedFirst() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("first"));
        state.grant(GuaranteeSpec.of("second"));
        assertEquals("first", state.nextId());
        state.grant(GuaranteeSpec.of("first"));
        assertEquals("second", state.nextId(), "a replaced guarantee counts as a new grant");
    }

    @Test
    void lookingChangesNothing() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("a").lasting(1200));
        for (int i = 0; i < 10; i++) {
            state.nextId();
            state.list();
        }
        assertEquals(1, state.list().size());
        assertEquals(1200, state.list().get(0).getDurationTicks());
    }

    @Test
    void timedGuaranteesCountDownAndExpire() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("short").lasting(2));
        state.grant(GuaranteeSpec.of("forever"));
        assertTrue(state.hasTimed());
        state.tick();
        assertEquals(1, state.list().get(0).getDurationTicks());
        state.tick();
        assertEquals(1, state.list().size());
        assertEquals("forever", state.nextId());
        assertFalse(state.hasTimed());
    }

    @Test
    void deathKeepsOnlyTheKeptOnes() {
        PlayerCritState old = new PlayerCritState();
        old.grant(GuaranteeSpec.of("plain"));
        old.grant(GuaranteeSpec.of("kept").lasting(600).keptOnDeath());
        PlayerCritState respawned = new PlayerCritState();
        respawned.copyFrom(old, true);
        assertEquals(1, respawned.list().size());
        assertEquals("kept", respawned.nextId());
        assertEquals(600, respawned.list().get(0).getDurationTicks());
    }

    @Test
    void leavingTheEndKeepsEverythingWithItsTimeLeft() {
        PlayerCritState old = new PlayerCritState();
        old.grant(GuaranteeSpec.of("plain").lasting(300));
        old.grant(GuaranteeSpec.of("other"));
        PlayerCritState next = new PlayerCritState();
        next.copyFrom(old, false);
        assertEquals(2, next.list().size());
        assertEquals(300, next.list().get(0).getDurationTicks());
    }

    @Test
    void savedGuaranteesComeBackWithTheirOrderAndTime() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("old"));
        state.grant(GuaranteeSpec.of("new").keptOnDeath());
        state.grant(GuaranteeSpec.of("timed").lasting(1199));
        NBTTagCompound tag = state.write();
        assertEquals(1, tag.getInteger("v"));
        NBTTagCompound first = tag.getTagList("guarantees", 10).getCompoundTagAt(0);
        for (String key : new String[] {"id", "ticks", "keepOnDeath", "order"}) {
            assertTrue(first.hasKey(key), "saved key " + key);
        }

        PlayerCritState loaded = new PlayerCritState();
        loaded.read(tag, warnings::add);
        assertEquals(3, loaded.list().size());
        assertEquals("timed", loaded.nextId());
        assertEquals(1199, loaded.list().get(0).getDurationTicks());
        assertEquals("old", loaded.list().get(1).getId());
        assertTrue(loaded.list().get(2).isKeptOnDeath());
        loaded.grant(GuaranteeSpec.of("later"));
        loaded.remove("timed");
        loaded.remove("old");
        assertEquals("new", loaded.nextId(), "a grant after loading is newer than every saved one");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void unknownVersionIsReadAsNoGuaranteesWithAWarning() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("a"));
        NBTTagCompound tag = state.write();
        tag.setInteger("v", 7);
        PlayerCritState loaded = new PlayerCritState();
        loaded.read(tag, warnings::add);
        assertTrue(loaded.isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void removeReportsWhetherItWasPending() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("a"));
        assertTrue(state.remove("a"));
        assertFalse(state.remove("a"));
        assertNull(state.nextId());
    }

    @Test
    void damagedAndRepeatedSavedEntriesAreDropped() {
        PlayerCritState state = new PlayerCritState();
        state.grant(GuaranteeSpec.of("a"));
        NBTTagCompound tag = state.write();
        net.minecraft.nbt.NBTTagList list = tag.getTagList("guarantees", 10);
        NBTTagCompound blank = list.getCompoundTagAt(0).copy();
        blank.setString("id", " ");
        list.appendTag(blank);
        list.appendTag(list.getCompoundTagAt(0).copy());
        NBTTagCompound zero = list.getCompoundTagAt(0).copy();
        zero.setString("id", "z");
        zero.setInteger("ticks", 0);
        list.appendTag(zero);
        PlayerCritState loaded = new PlayerCritState();
        loaded.read(tag, warnings::add);
        assertEquals(1, loaded.list().size());
        assertEquals("a", loaded.list().get(0).getId());
    }

    @Test
    void aCopyKeepsTheGrantOrder() {
        PlayerCritState old = new PlayerCritState();
        old.grant(GuaranteeSpec.of("first"));
        old.grant(GuaranteeSpec.of("second"));
        PlayerCritState next = new PlayerCritState();
        next.copyFrom(old, false);
        next.grant(GuaranteeSpec.of("third"));
        assertEquals("first", next.nextId());
        next.remove("first");
        assertEquals("second", next.nextId(), "a grant after the copy is newer than the copied ones");
    }

    @Test
    void anOptOutIsUsedByOneAttackAndNeverSaved() {
        PlayerCritState state = new PlayerCritState();
        assertFalse(state.consumeOptOut());
        state.optOutNextAttack();
        assertTrue(state.write().getTagList("guarantees", 10).isEmpty());
        assertFalse(state.write().toString().contains("opt"), "the opt-out is not saved");
        PlayerCritState copy = new PlayerCritState();
        copy.copyFrom(state, false);
        assertFalse(copy.consumeOptOut(), "the opt-out is not copied");
        assertTrue(state.consumeOptOut());
        assertFalse(state.consumeOptOut(), "the following attack is not opted out");
    }

    @Test
    void specsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> GuaranteeSpec.of(" "));
        assertThrows(IllegalArgumentException.class, () -> GuaranteeSpec.of("a").lasting(0));
        GuaranteeSpec spec = GuaranteeSpec.of("a");
        assertFalse(spec.hasDuration());
        assertEquals(GuaranteeSpec.NO_DURATION, spec.getDurationTicks());
    }
}
