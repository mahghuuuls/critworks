package com.mahghuuuls.critworks.integration.rlcombat;

import com.mahghuuuls.critworks.combat.MeleeAttackSource;
import com.mahghuuuls.critworks.core.CritSettings.Integration;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * RLCombat 2.2.4 runs both hands through its own copy of the vanilla attack and fires
 * {@code RLCombatAttackEntityEvent} (with the hand) and {@code RLCombatCriticalHitEvent} (with the hand
 * and the charge it read before its cooldown reset). This class is the only one that names them; it
 * reaches them by class name and cached method handles, so Critworks has no RLCombat dependency.
 */
public final class RLCombatAttacks implements MeleeAttackSource {

    static final String ATTACK_EVENT = "bettercombat.mod.event.RLCombatAttackEntityEvent";
    static final String CRIT_EVENT = "bettercombat.mod.event.RLCombatCriticalHitEvent";

    private final IntegrationGate gate;
    private MethodHandle attackOffhand;
    private MethodHandle critCharge;

    private RLCombatAttacks(IntegrationGate gate) {
        this.gate = gate;
    }

    /**
     * The RLCombat attack source when RLCombat is loaded, otherwise {@link MeleeAttackSource#NONE}.
     * Looks up RLCombat's methods once; a missing method turns the integration off with one warning.
     */
    public static MeleeAttackSource create(IntegrationGate gate) {
        if (!gate.isLoaded(Integration.RLCOMBAT)) {
            return NONE;
        }
        RLCombatAttacks attacks = new RLCombatAttacks(gate);
        if (gate.isOn(Integration.RLCOMBAT)) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                attacks.attackOffhand = lookup.findVirtual(Class.forName(ATTACK_EVENT), "getOffhand",
                        MethodType.methodType(boolean.class)).asType(MethodType.methodType(boolean.class, Object.class));
                attacks.critCharge = lookup.findVirtual(Class.forName(CRIT_EVENT), "getCooledStrength",
                        MethodType.methodType(float.class)).asType(MethodType.methodType(float.class, Object.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                gate.fail(Integration.RLCOMBAT, e);
            }
        }
        return attacks;
    }

    @Override
    public boolean owns(Event event) {
        String name = event.getClass().getName();
        return ATTACK_EVENT.equals(name) || CRIT_EVENT.equals(name);
    }

    @Override
    public EnumHand handOf(AttackEntityEvent event) {
        if (!gate.isOn(Integration.RLCOMBAT)) {
            return null;
        }
        try {
            return (boolean) attackOffhand.invokeExact((Object) event) ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        } catch (Throwable e) {
            gate.fail(Integration.RLCOMBAT, e);
            return null;
        }
    }

    @Override
    public float chargeOf(CriticalHitEvent event) {
        if (!gate.isOn(Integration.RLCOMBAT)) {
            return Float.NaN;
        }
        try {
            return (float) critCharge.invokeExact((Object) event);
        } catch (Throwable e) {
            gate.fail(Integration.RLCOMBAT, e);
            return Float.NaN;
        }
    }
}
