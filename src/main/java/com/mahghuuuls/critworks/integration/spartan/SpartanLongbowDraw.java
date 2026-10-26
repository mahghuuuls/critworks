package com.mahghuuuls.critworks.integration.spartan;

import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * The full-draw time of a Spartan Weaponry longbow, which depends on its material and Spartan's config,
 * so a part-drawn longbow is measured on its own draw instead of the vanilla 20 ticks. Asked through the
 * longbow's public {@code getDrawTicks()} by reflection; any failure turns the Spartan integration off
 * with one warning and the draw falls back to the vanilla time.
 */
public final class SpartanLongbowDraw implements ToIntFunction<ItemStack> {

    static final String LONGBOW_CLASS = SpartanWeaponTypes.ITEM_PACKAGE + "ItemLongbow";

    private final IntegrationGate gate;
    private final String longbowClass;
    private final int vanillaTicks;
    private final Map<Class<?>, Boolean> isLongbow = new ConcurrentHashMap<Class<?>, Boolean>();
    private volatile Method getDrawTicks;

    public SpartanLongbowDraw(IntegrationGate gate, int vanillaTicks) {
        this(gate, vanillaTicks, LONGBOW_CLASS);
    }

    /** With another longbow class name, for tests. */
    SpartanLongbowDraw(IntegrationGate gate, int vanillaTicks, String longbowClass) {
        this.gate = gate;
        this.vanillaTicks = vanillaTicks;
        this.longbowClass = longbowClass;
    }

    /** The ticks of a full draw with this stack. */
    @Override
    public int applyAsInt(ItemStack stack) {
        if (stack.isEmpty() || !gate.isOn(CritSettings.Integration.SPARTAN_WEAPONRY)) {
            return vanillaTicks;
        }
        Item item = stack.getItem();
        if (!SpartanWeaponTypes.isInstanceByName(item, longbowClass, isLongbow)) {
            return vanillaTicks;
        }
        try {
            Method method = getDrawTicks;
            if (method == null) {
                method = longbowClassOf(item.getClass()).getMethod("getDrawTicks");
                getDrawTicks = method;
            }
            // Spartan treats any release as a full draw when its draw time is 0 or less; so does the draw fraction.
            return ((Number) method.invoke(item)).intValue();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            gate.fail(CritSettings.Integration.SPARTAN_WEAPONRY, e);
            return vanillaTicks;
        }
    }

    private Class<?> longbowClassOf(Class<?> itemClass) {
        for (Class<?> type = itemClass; type != null; type = type.getSuperclass()) {
            if (type.getName().equals(longbowClass)) {
                return type;
            }
        }
        throw new IllegalStateException("not a Spartan longbow: " + itemClass.getName());
    }
}
