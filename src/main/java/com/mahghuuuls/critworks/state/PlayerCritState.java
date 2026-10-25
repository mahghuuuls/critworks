package com.mahghuuuls.critworks.state;

import com.mahghuuuls.critworks.api.GuaranteeSpec;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A player's pending guarantees. Owns the guarantee rules: a grant with an existing id replaces it; the
 * next one used is the one that expires first, then the oldest; timed ones count down one tick per
 * online player tick; death keeps only the ones granted to be kept. Saved with the player (NBT v 1).
 */
public final class PlayerCritState {

    public static final int VERSION = 1;

    private static final String KEY_VERSION = "v";
    private static final String KEY_GUARANTEES = "guarantees";
    private static final String KEY_ID = "id";
    private static final String KEY_TICKS = "ticks";
    private static final String KEY_KEEP = "keepOnDeath";
    private static final String KEY_ORDER = "order";

    /** Use order: timed before untimed, fewer ticks left first, then the older grant. */
    private static final Comparator<Entry> USE_ORDER = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            boolean aTimed = a.ticks != GuaranteeSpec.NO_DURATION;
            boolean bTimed = b.ticks != GuaranteeSpec.NO_DURATION;
            if (aTimed != bTimed) {
                return aTimed ? -1 : 1;
            }
            if (aTimed && a.ticks != b.ticks) {
                return a.ticks < b.ticks ? -1 : 1;
            }
            return Long.compare(a.order, b.order);
        }
    };

    private final List<Entry> guarantees = new ArrayList<Entry>();
    private long nextOrder;
    /** Set by an addon for the next attack only; not saved and not copied. */
    private boolean optOutNext;

    /** Marks the next attack as unable to crit. */
    public void optOutNextAttack() {
        optOutNext = true;
    }

    /** Called once by every attack as it is decided; returns whether that attack was opted out. */
    public boolean consumeOptOut() {
        boolean was = optOutNext;
        optOutNext = false;
        return was;
    }

    public void grant(GuaranteeSpec spec) {
        remove(spec.getId());
        guarantees.add(new Entry(spec.getId(), spec.getDurationTicks(), spec.isKeptOnDeath(), nextOrder++));
    }

    /** @return true when a guarantee with that id was pending */
    public boolean remove(String id) {
        for (int i = 0; i < guarantees.size(); i++) {
            if (guarantees.get(i).id.equals(id)) {
                guarantees.remove(i);
                return true;
            }
        }
        return false;
    }

    private boolean has(String id) {
        for (Entry entry : guarantees) {
            if (entry.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return guarantees.isEmpty();
    }

    /** The id of the guarantee the next eligible attack would use, or null. Changes nothing. */
    public String nextId() {
        return guarantees.isEmpty() ? null : Collections.min(guarantees, USE_ORDER).id;
    }

    /** The pending guarantees in use order, with the ticks each has left. Changes nothing. */
    public List<GuaranteeSpec> list() {
        List<Entry> sorted = new ArrayList<Entry>(guarantees);
        Collections.sort(sorted, USE_ORDER);
        List<GuaranteeSpec> result = new ArrayList<GuaranteeSpec>(sorted.size());
        for (Entry entry : sorted) {
            result.add(entry.toSpec());
        }
        return result;
    }

    public boolean hasTimed() {
        for (Entry entry : guarantees) {
            if (entry.ticks != GuaranteeSpec.NO_DURATION) {
                return true;
            }
        }
        return false;
    }

    /** One online tick: timed guarantees lose a tick, and the ones that reach 0 expire. */
    public void tick() {
        for (int i = guarantees.size() - 1; i >= 0; i--) {
            Entry entry = guarantees.get(i);
            if (entry.ticks == GuaranteeSpec.NO_DURATION) {
                continue;
            }
            entry.ticks--;
            if (entry.ticks <= 0) {
                guarantees.remove(i);
            }
        }
    }

    /** Takes over the guarantees of the player this one replaces: all of them, or after a death only the kept ones. */
    public void copyFrom(PlayerCritState old, boolean death) {
        guarantees.clear();
        for (Entry entry : old.guarantees) {
            if (!death || entry.keepOnDeath) {
                guarantees.add(new Entry(entry.id, entry.ticks, entry.keepOnDeath, entry.order));
            }
        }
        nextOrder = old.nextOrder;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(KEY_VERSION, VERSION);
        NBTTagList list = new NBTTagList();
        for (Entry entry : guarantees) {
            NBTTagCompound one = new NBTTagCompound();
            one.setString(KEY_ID, entry.id);
            one.setInteger(KEY_TICKS, entry.ticks);
            one.setBoolean(KEY_KEEP, entry.keepOnDeath);
            one.setLong(KEY_ORDER, entry.order);
            list.appendTag(one);
        }
        tag.setTag(KEY_GUARANTEES, list);
        return tag;
    }

    /** Reads saved guarantees. A tag of another version is read as no guarantees, with one message to {@code warn}. */
    public void read(NBTTagCompound tag, Consumer<String> warn) {
        guarantees.clear();
        nextOrder = 0;
        if (!tag.hasKey(KEY_VERSION)) {
            return;
        }
        int version = tag.getInteger(KEY_VERSION);
        if (version != VERSION) {
            warn.accept("Critworks: a player has saved guarantee data of unknown version " + version
                    + "; it is read as no guarantees");
            return;
        }
        NBTTagList list = tag.getTagList(KEY_GUARANTEES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound one = list.getCompoundTagAt(i);
            String id = one.getString(KEY_ID);
            int ticks = one.getInteger(KEY_TICKS);
            if (id.trim().isEmpty() || (ticks <= 0 && ticks != GuaranteeSpec.NO_DURATION) || has(id)) {
                // A damaged entry, or a second entry with an id already read, is dropped.
                continue;
            }
            long order = one.getLong(KEY_ORDER);
            guarantees.add(new Entry(id, ticks, one.getBoolean(KEY_KEEP), order));
            nextOrder = Math.max(nextOrder, order + 1);
        }
    }

    private static final class Entry {
        final String id;
        int ticks;
        final boolean keepOnDeath;
        final long order;

        Entry(String id, int ticks, boolean keepOnDeath, long order) {
            this.id = id;
            this.ticks = ticks;
            this.keepOnDeath = keepOnDeath;
            this.order = order;
        }

        GuaranteeSpec toSpec() {
            GuaranteeSpec spec = GuaranteeSpec.of(id);
            if (ticks != GuaranteeSpec.NO_DURATION) {
                spec = spec.lasting(ticks);
            }
            return keepOnDeath ? spec.keptOnDeath() : spec;
        }
    }
}
