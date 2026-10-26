package com.mahghuuuls.critworks.integration.spartan;

import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.ItemType;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spartan type table against the Spartan Weaponry 1.6.1 jar on the test class path: every class
 * exists, every type is a recognized weapon class (REQ-010), crossbows and throwing weapons are not, and
 * matching by class name covers subclasses, as addon weapons made through Spartan's API are.
 */
class SpartanWeaponTypesTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private static Class<?> spartanClass(String simpleName) throws ClassNotFoundException {
        return Class.forName(SpartanWeaponTypes.ITEM_PACKAGE + simpleName, false, SpartanWeaponTypesTest.class.getClassLoader());
    }

    @Test
    void theTypesAreTheRd29List() {
        assertEquals(Arrays.asList("dagger", "parrying_dagger", "longsword", "katana", "saber", "rapier", "greatsword",
                "hammer", "warhammer", "spear", "halberd", "pike", "lance", "glaive", "quarterstaff", "scythe",
                "battleaxe", "mace", "club", "caestus", "longbow"), new ArrayList<String>(SpartanWeaponTypes.names()));
        assertNull(SpartanWeaponTypes.forName("crossbow"));
        assertNull(SpartanWeaponTypes.forName("javelin"));
        assertEquals("spartan type dagger", SpartanWeaponTypes.forName("dagger").describe());
    }

    @Test
    void everyTypeClassExistsAndIsARecognizedWeaponClass() throws Exception {
        for (String name : SpartanWeaponTypes.names()) {
            SpartanWeaponTypes.ClassType type = (SpartanWeaponTypes.ClassType) SpartanWeaponTypes.forName(name);
            Class<?> itemClass = Class.forName(type.className, false, getClass().getClassLoader());
            boolean longbow = "longbow".equals(name);
            assertTrue(longbow ? ItemBow.class.isAssignableFrom(itemClass) : ItemSword.class.isAssignableFrom(itemClass),
                    name + " is a " + (longbow ? "bow" : "sword") + " class, so it is eligible with no rule");
        }
        assertEquals(SpartanLongbowDraw.LONGBOW_CLASS, spartanClass("ItemLongbow").getName());
        assertTrue(spartanClass("ItemLongbow").getMethod("getDrawTicks").getReturnType() == int.class);
    }

    @Test
    void crossbowsAndThrowingWeaponsAreNotWeaponsForCritworks() throws Exception {
        for (String simpleName : new String[]{"ItemCrossbow", "ItemJavelin", "ItemThrowingKnife", "ItemThrowingAxe"}) {
            Class<?> itemClass = spartanClass(simpleName);
            assertFalse(ItemSword.class.isAssignableFrom(itemClass), simpleName);
            assertFalse(ItemBow.class.isAssignableFrom(itemClass), simpleName);
        }
    }

    /** Stands in for a Spartan item class and an addon subclass of it. */
    public static class Blade extends ItemSword {
        public Blade() {
            super(Item.ToolMaterial.IRON);
        }
    }

    public static class AddonBlade extends Blade {
    }

    @Test
    void aTypeMatchesItsClassAndSubclassesOnly() {
        ItemType type = new SpartanWeaponTypes.ClassType("blade", Blade.class.getName());
        assertTrue(type.matches(new Blade()));
        assertTrue(type.matches(new AddonBlade()), "an addon weapon made from the same class");
        assertFalse(type.matches(new ItemSword(Item.ToolMaterial.IRON)));
        assertTrue(type.matches(new AddonBlade()), "the cached answer");
    }

    /** Stands in for the Spartan longbow, which reports its own full draw. */
    public static class Longbow extends ItemBow {
        public int getDrawTicks() {
            return 30;
        }
    }

    public static class BrokenLongbow extends ItemBow {
        public int getDrawTicks() {
            throw new IllegalStateException("broken config");
        }
    }

    @Test
    void aLongbowIsMeasuredOnItsOwnDrawAndEverythingElseOnTheVanillaDraw() {
        List<String> warnings = new ArrayList<String>();
        IntegrationGate gate = new IntegrationGate(id -> true, CritSettings.defaults(), warnings::add);
        SpartanLongbowDraw draw = new SpartanLongbowDraw(gate, 20, Longbow.class.getName());
        assertEquals(30, draw.applyAsInt(new ItemStack(new Longbow())));
        assertEquals(20, draw.applyAsInt(new ItemStack(new ItemBow())));
        assertEquals(20, draw.applyAsInt(ItemStack.EMPTY));
        assertEquals(0, warnings.size());
    }

    @Test
    void aBrokenLongbowTurnsTheIntegrationOffWithOneWarning() {
        List<String> warnings = new ArrayList<String>();
        IntegrationGate gate = new IntegrationGate(id -> true, CritSettings.defaults(), warnings::add);
        SpartanLongbowDraw draw = new SpartanLongbowDraw(gate, 20, BrokenLongbow.class.getName());
        assertEquals(20, draw.applyAsInt(new ItemStack(new BrokenLongbow())));
        assertEquals(20, draw.applyAsInt(new ItemStack(new BrokenLongbow())));
        assertEquals(1, warnings.size());
        assertFalse(gate.isOn(CritSettings.Integration.SPARTAN_WEAPONRY));
    }

    @Test
    void theSwitchOffKeepsTheVanillaDraw() {
        boolean[] integrations = new boolean[CritSettings.Integration.values().length];
        Arrays.fill(integrations, true);
        integrations[CritSettings.Integration.SPARTAN_WEAPONRY.ordinal()] = false;
        CritSettings settings = new CritSettings(5.0D, 1.5D, 100.0D, 100.0D, -1.0D, true, "auto", false, integrations);
        SpartanLongbowDraw draw = new SpartanLongbowDraw(new IntegrationGate(id -> true, settings, s -> { }), 20,
                Longbow.class.getName());
        assertEquals(20, draw.applyAsInt(new ItemStack(new Longbow())));
    }
}
