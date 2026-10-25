package com.mahghuuuls.critworks.state;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.util.Constants;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The stats an arrow was fired with, fixed at release, and the targets it already hit successfully as
 * a critical arrow. Saved with the arrow, so it survives save, reload, and chunk unload. An arrow
 * Critworks did not fire (a skeleton's, a dispenser's) is not captured and saves an empty tag.
 */
public final class ArrowCritState {

    public static final int VERSION = 1;

    private static final String KEY_VERSION = "v";
    private static final String KEY_ELIGIBLE = "eligible";
    private static final String KEY_CHANCE = "chance";
    private static final String KEY_MULTIPLIER = "multiplier";
    private static final String KEY_CRITICAL = "critical";
    private static final String KEY_GUARANTEED = "guaranteed";
    private static final String KEY_HAND = "hand";
    private static final String KEY_HIT_TARGETS = "hitTargets";
    private static final String HAND_MAIN = "main";
    private static final String HAND_OFF = "off";

    private boolean captured;
    private boolean eligible;
    private double chance = Double.NaN;
    private double multiplier = 1.0D;
    private boolean critical;
    private boolean guaranteed;
    private EnumHand hand = EnumHand.MAIN_HAND;
    private final Set<UUID> hitTargets = new LinkedHashSet<UUID>();
    /** The bow at release, for the critical-hit event; not saved, so empty for an arrow loaded from a save. */
    private ItemStack weapon = ItemStack.EMPTY;

    /** Fixes the arrow's result at release. Called once; later calls do nothing. */
    public void capture(boolean eligible, double chance, double multiplier, boolean critical, boolean guaranteed,
                        EnumHand hand) {
        if (captured) {
            return;
        }
        this.captured = true;
        this.eligible = eligible;
        this.chance = chance;
        this.multiplier = multiplier;
        this.critical = critical;
        this.guaranteed = guaranteed;
        this.hand = hand;
    }

    public boolean isCaptured() {
        return captured;
    }

    public boolean isEligible() {
        return eligible;
    }

    public double getChance() {
        return chance;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public boolean isCritical() {
        return critical;
    }

    public boolean isGuaranteed() {
        return guaranteed;
    }

    public EnumHand getHand() {
        return hand;
    }

    public ItemStack getWeapon() {
        return weapon;
    }

    public void setWeapon(ItemStack weapon) {
        this.weapon = weapon;
    }

    public boolean hasHitAnyTarget() {
        return !hitTargets.isEmpty();
    }

    /** Records a successful critical hit on a target; returns false when that target was already hit. */
    public boolean recordHit(UUID target) {
        return hitTargets.add(target);
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        if (!captured) {
            return tag;
        }
        tag.setInteger(KEY_VERSION, VERSION);
        tag.setBoolean(KEY_ELIGIBLE, eligible);
        tag.setDouble(KEY_CHANCE, chance);
        tag.setDouble(KEY_MULTIPLIER, multiplier);
        tag.setBoolean(KEY_CRITICAL, critical);
        tag.setBoolean(KEY_GUARANTEED, guaranteed);
        tag.setString(KEY_HAND, hand == EnumHand.OFF_HAND ? HAND_OFF : HAND_MAIN);
        NBTTagList targets = new NBTTagList();
        for (UUID id : hitTargets) {
            targets.appendTag(new NBTTagString(id.toString()));
        }
        tag.setTag(KEY_HIT_TARGETS, targets);
        return tag;
    }

    /**
     * Reads saved data into this state. An empty tag means "not captured". A tag with another version is
     * read as not captured, and {@code warn} gets one message for it.
     */
    public void read(NBTTagCompound tag, Consumer<String> warn) {
        if (!tag.hasKey(KEY_VERSION)) {
            return;
        }
        int version = tag.getInteger(KEY_VERSION);
        if (version != VERSION) {
            warn.accept("Critworks: an arrow has saved crit data of unknown version " + version
                    + "; it is read as an arrow without Critworks data");
            return;
        }
        captured = true;
        eligible = tag.getBoolean(KEY_ELIGIBLE);
        chance = tag.getDouble(KEY_CHANCE);
        multiplier = tag.getDouble(KEY_MULTIPLIER);
        critical = tag.getBoolean(KEY_CRITICAL);
        guaranteed = tag.getBoolean(KEY_GUARANTEED);
        hand = HAND_OFF.equals(tag.getString(KEY_HAND)) ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        hitTargets.clear();
        NBTTagList targets = tag.getTagList(KEY_HIT_TARGETS, Constants.NBT.TAG_STRING);
        for (int i = 0; i < targets.tagCount(); i++) {
            try {
                hitTargets.add(UUID.fromString(targets.getStringTagAt(i)));
            } catch (IllegalArgumentException ignored) {
                // A damaged id cannot match any target; dropping it only allows that target one more event.
            }
        }
    }
}
