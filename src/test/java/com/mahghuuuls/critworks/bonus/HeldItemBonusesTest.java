package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.integration.qualitytools.QualityToolsBonuses;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Quality Tools qualities through the production collector and the production tag reader (REQ-083,
 * ARC-006): Quality Tools has put every listed quality's modifier on the player, as it does in game; a
 * helmet quality counts for every attack, a sword's hand quality only for attacks with that sword.
 */
class HeldItemBonusesTest {

    private static final UUID HELMET = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SWORD = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID AXE = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private AttributeMap map;
    private BonusCollector collector;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @BeforeEach
    void setUp() {
        map = new AttributeMap();
        for (CritStat stat : CritStat.values()) {
            map.registerAttribute(CritAttributes.attribute(stat));
        }
        collector = new BonusCollector(LogManager.getLogger("test"));
        collector.addHeldItemBonuses(new QualityToolsBonuses());
    }

    /** A stack with a quality whose one modifier adds {@code amount} additional chance in {@code slots}. */
    static ItemStack withQuality(Item item, UUID id, double amount, int operation, String... slots) {
        return withQuality(item, CritStat.ADDITIONAL_CHANCE.getAttributeName(), id, amount, operation, slots);
    }

    static ItemStack withQuality(Item item, String attributeName, UUID id, double amount, int operation, String... slots) {
        NBTTagCompound modifier = new NBTTagCompound();
        modifier.setString("AttributeName", attributeName);
        modifier.setString("Name", "quality");
        modifier.setDouble("Amount", amount);
        modifier.setInteger("Operation", operation);
        modifier.setLong("UUIDMost", id.getMostSignificantBits());
        modifier.setLong("UUIDLeast", id.getLeastSignificantBits());
        NBTTagList modifiers = new NBTTagList();
        modifiers.appendTag(modifier);
        NBTTagList slotList = new NBTTagList();
        for (String slot : slots) {
            slotList.appendTag(new NBTTagString(slot));
        }
        NBTTagCompound quality = new NBTTagCompound();
        quality.setString("Name", "quality.test.sharp");
        quality.setTag("Slots", slotList);
        quality.setTag("AttributeModifiers", modifiers);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Quality", quality);
        ItemStack stack = new ItemStack(item);
        stack.setTagCompound(tag);
        return stack;
    }

    /** What Quality Tools does every 7 ticks: the quality's modifier on the player. */
    private void onPlayer(UUID id, double amount) {
        IAttributeInstance instance = map.getAttributeInstance(CritAttributes.attribute(CritStat.ADDITIONAL_CHANCE));
        instance.applyModifier(new AttributeModifier(id, "quality", amount, 0));
    }

    /** The additional chance for an attack; {@code mainHand} stands for the whole main inventory here. */
    private double chance(ItemStack mainHand, ItemStack offHand, ItemStack attacking, EnumHand hand, boolean on) {
        return chance(Collections.singletonList(mainHand), offHand, attacking, hand, on);
    }

    private double chance(List<ItemStack> mainInventory, ItemStack offHand, ItemStack attacking, EnumHand hand, boolean on) {
        List<Bonus> bonuses = collector.collect(null, map::getAttributeInstance, mainInventory, offHand, attacking, hand,
                key -> on || !"qualitytools".equals(key));
        return BonusCollector.total(bonuses, CritStat.ADDITIONAL_CHANCE);
    }

    @Test
    void aHelmetQualityCountsForEveryAttack() {
        onPlayer(HELMET, 5.0D);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        assertEquals(5.0D, chance(sword, ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, true));
        assertEquals(5.0D, chance(ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.BOW), EnumHand.MAIN_HAND, true));
    }

    @Test
    void aSwordQualityCountsOnceForTheSwordAndNotForOtherAttacks() {
        onPlayer(HELMET, 5.0D);
        ItemStack sword = withQuality(Items.IRON_SWORD, SWORD, 10.0D, 0, "mainhand");
        onPlayer(SWORD, 10.0D);
        assertEquals(15.0D, chance(sword, ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, true), "sword: +5 helmet +10 sword, once");

        ItemStack axe = new ItemStack(Items.IRON_AXE);
        assertEquals(5.0D, chance(sword, axe, axe, EnumHand.OFF_HAND, true), "offhand axe (RLCombat): not the sword's +10");

        ItemStack bow = new ItemStack(Items.BOW);
        assertEquals(5.0D, chance(sword, bow, bow, EnumHand.OFF_HAND, true), "offhand bow: not the sword's +10");
    }

    @Test
    void anOffhandQualityCountsForOffhandAttacksWithItsItem() {
        ItemStack axe = withQuality(Items.IRON_AXE, AXE, 7.0D, 0, "mainhand", "offhand");
        onPlayer(AXE, 7.0D);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        assertEquals(7.0D, chance(sword, axe, axe, EnumHand.OFF_HAND, true));
        assertEquals(0.0D, chance(sword, axe, sword, EnumHand.MAIN_HAND, true));
    }

    @Test
    void anInventoryQualityStaysGlobal() {
        ItemStack sword = withQuality(Items.IRON_SWORD, SWORD, 10.0D, 0, "mainhand", "inventory");
        onPlayer(SWORD, 10.0D);
        ItemStack bow = new ItemStack(Items.BOW);
        assertEquals(10.0D, chance(sword, ItemStack.EMPTY, bow, EnumHand.MAIN_HAND, true));
        assertEquals(10.0D, chance(sword, ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, true), "counted once, as global");
    }

    @Test
    void aQualityStillOnThePlayerAfterASwapCountsOnlyForItsItem() {
        onPlayer(HELMET, 5.0D);
        ItemStack sword = withQuality(Items.IRON_SWORD, SWORD, 10.0D, 0, "mainhand");
        onPlayer(SWORD, 10.0D);
        ItemStack bow = new ItemStack(Items.BOW);
        // The sword was just scrolled away from: Quality Tools removes its modifier at its next refresh.
        assertEquals(5.0D, chance(Arrays.asList(bow, sword), ItemStack.EMPTY, bow, EnumHand.MAIN_HAND, true));
        // The sword was just moved to the offhand: its main-hand quality still on the player counts for nothing.
        assertEquals(5.0D, chance(bow, sword, bow, EnumHand.MAIN_HAND, true));
    }

    @Test
    void attributeNamesMatchIgnoringCaseAsInQualityTools() {
        ItemStack sword = withQuality(Items.IRON_SWORD, "critworks.ADDITIONALCRITCHANCE", SWORD, 10.0D, 0, "mainhand");
        onPlayer(SWORD, 10.0D);
        ItemStack bow = new ItemStack(Items.BOW);
        assertEquals(0.0D, chance(sword, ItemStack.EMPTY, bow, EnumHand.MAIN_HAND, true));
        assertEquals(10.0D, chance(sword, ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, true));
    }

    @Test
    void anOffhandAndInventoryQualityCountsOnlyForOffhandAttacksWhileInTheOffhand() {
        ItemStack axe = withQuality(Items.IRON_AXE, AXE, 7.0D, 0, "offhand", "inventory");
        onPlayer(AXE, 7.0D);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        assertEquals(0.0D, chance(sword, axe, sword, EnumHand.MAIN_HAND, true), "Quality Tools applies inventory never to the offhand");
        assertEquals(7.0D, chance(sword, axe, axe, EnumHand.OFF_HAND, true));
        assertEquals(7.0D, chance(Arrays.asList(sword, axe), ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, true),
                "in the main inventory it is a global bonus");
    }

    @Test
    void theHoveredStackGetsItsOwnQualityAsIfInTheMainHand() {
        ItemStack sword = withQuality(Items.IRON_SWORD, SWORD, 10.0D, 0, "mainhand");
        assertEquals(10.0D, chance(ItemStack.EMPTY, ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, true),
                "the tooltip preview of a sword in the inventory");
    }

    @Test
    void onlyOperationZeroCounts() {
        ItemStack sword = withQuality(Items.IRON_SWORD, SWORD, 10.0D, 1, "mainhand");
        assertEquals(0.0D, chance(sword, ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, true));
        QualityToolsBonuses reader = new QualityToolsBonuses();
        Set<UUID> ids = reader.heldModifierIds(sword, EnumHand.MAIN_HAND);
        assertEquals(Collections.singleton(SWORD), ids);
    }

    @Test
    void theSourceNamesTheQuality() {
        ItemStack sword = withQuality(Items.IRON_SWORD, SWORD, 10.0D, 0, "mainhand");
        List<Bonus> bonuses = new QualityToolsBonuses().ownBonuses(sword, EnumHand.MAIN_HAND);
        assertEquals(1, bonuses.size());
        assertEquals("Quality: quality.test.sharp", bonuses.get(0).getSource(), "an untranslated key stays as it is");
    }

    @Test
    void theSwitchOffLeavesQualitiesAsQualityToolsAppliesThem() {
        ItemStack sword = withQuality(Items.IRON_SWORD, SWORD, 10.0D, 0, "mainhand");
        onPlayer(SWORD, 10.0D);
        ItemStack bow = new ItemStack(Items.BOW);
        assertEquals(10.0D, chance(sword, ItemStack.EMPTY, bow, EnumHand.MAIN_HAND, false),
                "with qualitytools off, a held quality is a plain global bonus");
        assertEquals(10.0D, chance(sword, ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, false), "and is not added twice");
    }

    @Test
    void aSourceThatThrowsIsReportedOnceAndSkipped() {
        List<String> asked = new ArrayList<String>();
        BonusCollector failing = new BonusCollector(LogManager.getLogger("test"));
        failing.addHeldItemBonuses(new HeldItemBonuses() {
            @Override
            public String integrationKey() {
                return "qualitytools";
            }

            @Override
            public Set<UUID> heldModifierIds(ItemStack stack, EnumHand hand) {
                asked.add(hand.name());
                throw new IllegalStateException("broken tag");
            }

            @Override
            public List<Bonus> ownBonuses(ItemStack stack, EnumHand hand) {
                return Collections.emptyList();
            }
        });
        onPlayer(SWORD, 10.0D);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        for (int i = 0; i < 3; i++) {
            List<Bonus> bonuses = failing.collect(null, map::getAttributeInstance, Collections.singletonList(sword),
                    ItemStack.EMPTY, sword, EnumHand.MAIN_HAND, key -> true);
            assertEquals(10.0D, BonusCollector.total(bonuses, CritStat.ADDITIONAL_CHANCE));
        }
        assertEquals(1, asked.size(), "the source is asked once, then skipped");
    }
}
