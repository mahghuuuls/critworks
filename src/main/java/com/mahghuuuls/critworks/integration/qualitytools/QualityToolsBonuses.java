package com.mahghuuuls.critworks.integration.qualitytools;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.bonus.HeldItemBonuses;
import com.mahghuuuls.critworks.core.CritSettings;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Quality Tools 1.0.7 qualities on Critworks attributes (REQ-083), read from the item's {@code Quality}
 * tag with no Quality Tools class. Every 7 ticks Quality Tools puts a quality's modifiers on the player,
 * with the ids from the tag, while the item is in one of the quality's slots: the two hands, the armor
 * slots, baubles, and "inventory" (the 36 main inventory slots, not the offhand). For a hand slot Critworks
 * counts them only for attacks with that item from that hand; armor and other slots stay global bonuses.
 * A quality that also lists "inventory" applies to a main-inventory item anywhere, so there it stays
 * global too. Only operation 0 counts, as for every Critworks bonus. The tag shape is known only here.
 */
public final class QualityToolsBonuses implements HeldItemBonuses {

    static final String QUALITY = "Quality";
    static final String NAME = "Name";
    static final String SLOTS = "Slots";
    static final String MODIFIERS = "AttributeModifiers";
    static final String ATTRIBUTE_NAME = "AttributeName";
    static final String INVENTORY = "inventory";
    private static final int ADD = 0;

    @Override
    public String integrationKey() {
        return CritSettings.Integration.QUALITY_TOOLS.getKey();
    }

    /** A quality for either hand, wherever the stack is, unless it is global there. */
    @Override
    public Set<UUID> heldModifierIds(ItemStack stack, EnumHand hand) {
        Set<UUID> ids = new HashSet<UUID>();
        NBTTagCompound quality = qualityOf(stack);
        if (quality == null) {
            return ids;
        }
        NBTTagList slots = quality.getTagList(SLOTS, Constants.NBT.TAG_STRING);
        boolean handQuality = contains(slots, slotName(EnumHand.MAIN_HAND)) || contains(slots, slotName(EnumHand.OFF_HAND));
        if (handQuality && !globalWhereItIs(slots, hand)) {
            for (Entry entry : entries(quality)) {
                ids.add(entry.modifier.getID());
            }
        }
        return ids;
    }

    @Override
    public List<Bonus> ownBonuses(ItemStack stack, EnumHand hand) {
        List<Entry> entries = handEntries(stack, hand);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        String source = "Quality: " + qualityName(stack.getTagCompound().getCompoundTag(QUALITY));
        List<Bonus> bonuses = new ArrayList<Bonus>();
        for (Entry entry : entries) {
            if (entry.modifier.getOperation() == ADD) {
                bonuses.add(new Bonus(entry.stat, entry.modifier.getAmount(), source));
            }
        }
        return bonuses;
    }

    /** The quality's modifiers on Critworks attributes that apply because the stack is in {@code hand}. */
    static List<Entry> handEntries(ItemStack stack, EnumHand hand) {
        NBTTagCompound quality = qualityOf(stack);
        if (quality == null) {
            return Collections.emptyList();
        }
        NBTTagList slots = quality.getTagList(SLOTS, Constants.NBT.TAG_STRING);
        if (!contains(slots, slotName(hand)) || globalWhereItIs(slots, hand)) {
            return Collections.emptyList();
        }
        return entries(quality);
    }

    private static NBTTagCompound qualityOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getTagCompound() == null || !stack.getTagCompound().hasKey(QUALITY, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return stack.getTagCompound().getCompoundTag(QUALITY);
    }

    /** "inventory" covers the main inventory, the main hand's slot included, but never the offhand. */
    private static boolean globalWhereItIs(NBTTagList slots, EnumHand hand) {
        return hand == EnumHand.MAIN_HAND && contains(slots, INVENTORY);
    }

    private static List<Entry> entries(NBTTagCompound quality) {
        List<Entry> entries = new ArrayList<Entry>();
        NBTTagList modifiers = quality.getTagList(MODIFIERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < modifiers.tagCount(); i++) {
            NBTTagCompound tag = modifiers.getCompoundTagAt(i);
            CritStat stat = statFor(tag.getString(ATTRIBUTE_NAME));
            AttributeModifier modifier = stat == null ? null : SharedMonsterAttributes.readAttributeModifierFromNBT(tag);
            if (modifier != null) {
                entries.add(new Entry(stat, modifier));
            }
        }
        return entries;
    }

    /** Quality Tools' slot names are the vanilla equipment slot names. */
    static String slotName(EnumHand hand) {
        return hand == EnumHand.OFF_HAND ? "offhand" : "mainhand";
    }

    private static boolean contains(NBTTagList slots, String slot) {
        for (int i = 0; i < slots.tagCount(); i++) {
            if (slot.equalsIgnoreCase(slots.getStringTagAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static CritStat statFor(String attributeName) {
        for (CritStat stat : CritStat.values()) {
            // Quality Tools finds attributes ignoring case, as the vanilla attribute map does.
            if (stat.getAttributeName().equalsIgnoreCase(attributeName)) {
                return stat;
            }
        }
        return null;
    }

    /** Quality Tools stores a language key as the quality's name; translated where a language is loaded. */
    @SuppressWarnings("deprecation")
    static String qualityName(NBTTagCompound quality) {
        String key = quality.getString(NAME);
        return I18n.canTranslate(key) ? I18n.translateToLocal(key) : key;
    }

    /** One modifier of the quality and the stat it feeds. */
    static final class Entry {
        final CritStat stat;
        final AttributeModifier modifier;

        Entry(CritStat stat, AttributeModifier modifier) {
            this.stat = stat;
            this.modifier = modifier;
        }
    }
}
