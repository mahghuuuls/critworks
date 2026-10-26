package com.mahghuuuls.critworks.integration.jafs;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JAFS properties against the JAFS 1.0.0 API types (REQ-082): ids, ranges, the flat and percent
 * mapping onto the bonus attributes as "JAFS" bonuses, idempotence, removal at expiry, and the switch-off
 * when JAFS cannot take the registration.
 */
class JafsCritPropertiesTest {

    private AttributeMap map;

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

    private double amount(CritStat stat) {
        double total = 0.0D;
        for (AttributeModifier modifier : instance(stat).getModifiersByOperation(0)) {
            assertEquals(JafsCritProperties.SOURCE, modifier.getName());
            total += modifier.getAmount();
        }
        return total;
    }

    @Test
    void thePropertiesHaveThePublishedIdsAndRanges() {
        PropertySpec chance = JafsCritProperties.Property.CRIT_CHANCE.spec();
        assertEquals("critworks:crit_chance", chance.id());
        assertEquals("property.critworks.crit_chance", chance.displayNameKey());
        assertTrue(chance.range(PropertyOperation.FLAT).contains(100.0D));
        assertTrue(chance.range(PropertyOperation.FLAT).contains(-100.0D));
        assertFalse(chance.range(PropertyOperation.FLAT).contains(100.5D));
        assertTrue(chance.range(PropertyOperation.PERCENT).contains(1000.0D));
        assertFalse(chance.range(PropertyOperation.PERCENT).contains(2000.0D), "REQ-082: chance percent 2000 is rejected");
        assertFalse(chance.range(PropertyOperation.PERCENT).contains(-100.5D));

        PropertySpec damage = JafsCritProperties.Property.CRIT_DAMAGE.spec();
        assertEquals("critworks:crit_damage", damage.id());
        assertTrue(damage.range(PropertyOperation.FLAT).contains(10.0D));
        assertFalse(damage.range(PropertyOperation.FLAT).contains(-10.5D));
        assertTrue(damage.range(PropertyOperation.PERCENT).contains(-100.0D));
        assertFalse(damage.range(PropertyOperation.PERCENT).contains(1000.5D));
    }

    @Test
    void flatIsAdditionalAndPercentIsIncreased() {
        JafsCritProperties.apply(map::getAttributeInstance, JafsCritProperties.Property.CRIT_CHANCE, 10.0D, 25.0D);
        JafsCritProperties.apply(map::getAttributeInstance, JafsCritProperties.Property.CRIT_DAMAGE, 0.5D, 40.0D);
        assertEquals(10.0D, amount(CritStat.ADDITIONAL_CHANCE));
        assertEquals(25.0D, amount(CritStat.INCREASED_CHANCE));
        assertEquals(0.5D, amount(CritStat.ADDITIONAL_DAMAGE));
        assertEquals(40.0D, amount(CritStat.INCREASED_DAMAGE));
    }

    @Test
    void applyingTheSameSumsAgainChangesNothingAndZeroRemovesTheBonus() {
        JafsCritProperties.apply(map::getAttributeInstance, JafsCritProperties.Property.CRIT_CHANCE, 10.0D, 0.0D);
        JafsCritProperties.apply(map::getAttributeInstance, JafsCritProperties.Property.CRIT_CHANCE, 10.0D, 0.0D);
        Collection<AttributeModifier> modifiers = instance(CritStat.ADDITIONAL_CHANCE).getModifiers();
        assertEquals(1, modifiers.size());
        assertEquals(0, instance(CritStat.INCREASED_CHANCE).getModifiers().size(), "a zero sum adds no modifier");
        JafsCritProperties.apply(map::getAttributeInstance, JafsCritProperties.Property.CRIT_CHANCE, 0.0D, 0.0D);
        assertEquals(0, instance(CritStat.ADDITIONAL_CHANCE).getModifiers().size(), "expiry sends zero sums");
    }

    @Test
    void aFailedRegistrationTurnsTheIntegrationOffWithOneWarning() {
        List<String> warnings = new ArrayList<String>();
        IntegrationGate gate = new IntegrationGate(id -> true, CritSettings.defaults(), warnings::add);
        // JAFS has not bound its API in a unit test, so registration fails as it would with a broken JAFS.
        JafsCritProperties.register(gate);
        assertEquals(1, warnings.size());
        assertFalse(gate.isOn(CritSettings.Integration.JAFS));
    }

    @Test
    void theLoginCleanupRemovesOnlyTheJafsBonuses() {
        JafsCritProperties.apply(map::getAttributeInstance, JafsCritProperties.Property.CRIT_CHANCE, 10.0D, 25.0D);
        JafsCritProperties.apply(map::getAttributeInstance, JafsCritProperties.Property.CRIT_DAMAGE, 0.5D, 40.0D);
        CritAttributes.setOn(instance(CritStat.ADDITIONAL_CHANCE), "test:helmet", CritStat.ADDITIONAL_CHANCE, 5.0D);
        JafsBonusCleanup.removeFrom(map::getAttributeInstance);
        for (CritStat stat : CritStat.values()) {
            for (AttributeModifier modifier : instance(stat).getModifiers()) {
                assertFalse(JafsBonusCleanup.SOURCE.equals(modifier.getName()), "left over: " + stat);
            }
        }
        assertEquals(1, instance(CritStat.ADDITIONAL_CHANCE).getModifiers().size(), "another source's bonus stays");
    }
}
