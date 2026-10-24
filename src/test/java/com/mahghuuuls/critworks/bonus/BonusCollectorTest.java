package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the production bonus attributes on a real vanilla attribute map: stable modifier identity,
 * replacement instead of duplication, operation-0-only counting, non-finite amounts, and totals.
 */
class BonusCollectorTest {

    private AttributeMap map;
    private final BonusCollector collector = new BonusCollector(LogManager.getLogger("test"));

    @BeforeEach
    void setUp() {
        map = new AttributeMap();
        for (CritStat stat : CritStat.values()) {
            map.registerAttribute(CritAttributes.attribute(stat));
        }
    }

    private IAttributeInstance instance(CritStat stat) {
        return map.getAttributeInstance(CritAttributes.attribute(stat));
    }

    @Test
    void attributeNamesAreThePublishedOnes() {
        assertEquals("critworks.additionalCritChance", CritAttributes.attribute(CritStat.ADDITIONAL_CHANCE).getName());
        assertEquals("critworks.increasedCritDamage", CritAttributes.attribute(CritStat.INCREASED_DAMAGE).getName());
        assertEquals(0.0D, instance(CritStat.ADDITIONAL_CHANCE).getAttributeValue());
    }

    @Test
    void settingTheSameSourceTwiceReplacesInsteadOfAdding() {
        CritAttributes.setOn(instance(CritStat.ADDITIONAL_CHANCE), "test:armor", CritStat.ADDITIONAL_CHANCE, 5.0D);
        CritAttributes.setOn(instance(CritStat.ADDITIONAL_CHANCE), "test:armor", CritStat.ADDITIONAL_CHANCE, 5.0D);
        assertEquals(1, instance(CritStat.ADDITIONAL_CHANCE).getModifiers().size());
        assertEquals(5.0D, BonusCollector.total(collector.globalBonuses(map::getAttributeInstance),
                CritStat.ADDITIONAL_CHANCE));
    }

    @Test
    void twoSourcesAddUp() {
        CritAttributes.setOn(instance(CritStat.ADDITIONAL_CHANCE), "test:helmet", CritStat.ADDITIONAL_CHANCE, 5.0D);
        CritAttributes.setOn(instance(CritStat.ADDITIONAL_CHANCE), "test:boots", CritStat.ADDITIONAL_CHANCE, 5.0D);
        List<Bonus> bonuses = collector.globalBonuses(map::getAttributeInstance);
        assertEquals(10.0D, BonusCollector.total(bonuses, CritStat.ADDITIONAL_CHANCE));
        assertEquals(2, bonuses.size());
    }

    @Test
    void zeroRemovesTheBonus() {
        CritAttributes.setOn(instance(CritStat.INCREASED_CHANCE), "test:food", CritStat.INCREASED_CHANCE, 20.0D);
        CritAttributes.setOn(instance(CritStat.INCREASED_CHANCE), "test:food", CritStat.INCREASED_CHANCE, 0.0D);
        assertTrue(instance(CritStat.INCREASED_CHANCE).getModifiers().isEmpty());
    }

    @Test
    void onlyOperationZeroCountsAndSourceIsTheModifierName() {
        instance(CritStat.ADDITIONAL_CHANCE).applyModifier(new AttributeModifier(UUID.randomUUID(), "qualitytools", 3.0D, 0));
        instance(CritStat.ADDITIONAL_CHANCE).applyModifier(new AttributeModifier(UUID.randomUUID(), "qualitytools", 0.5D, 1));
        List<Bonus> bonuses = collector.globalBonuses(map::getAttributeInstance);
        assertEquals(1, bonuses.size());
        assertEquals("qualitytools", bonuses.get(0).getSource());
        assertEquals(3.0D, bonuses.get(0).getAmount());
    }

    @Test
    void nonFiniteAmountIsLeftOut() {
        instance(CritStat.ADDITIONAL_DAMAGE).applyModifier(new AttributeModifier(UUID.randomUUID(), "bad:addon", Double.NaN, 0));
        CritAttributes.setOn(instance(CritStat.ADDITIONAL_DAMAGE), "good:addon", CritStat.ADDITIONAL_DAMAGE, 0.25D);
        assertEquals(0.25D, BonusCollector.total(collector.globalBonuses(map::getAttributeInstance),
                CritStat.ADDITIONAL_DAMAGE));
    }

    @Test
    void modifierIdIsStablePerSourceAndStat() {
        assertEquals(CritAttributes.modifierId("a", CritStat.ADDITIONAL_CHANCE),
                CritAttributes.modifierId("a", CritStat.ADDITIONAL_CHANCE));
        assertTrue(!CritAttributes.modifierId("a", CritStat.ADDITIONAL_CHANCE)
                .equals(CritAttributes.modifierId("a", CritStat.INCREASED_CHANCE)));
    }
}
