package com.mahghuuuls.critworks.combat;

import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * A combat mod that runs melee attacks through its own subclasses of the Forge attack and crit events
 * and knows the attacking hand and charge itself. Its events are recognized even when it is off, so an
 * attack it owns is then left alone instead of being read as a vanilla main-hand attack.
 */
public interface MeleeAttackSource {

    /** No combat mod: every attack is a vanilla attack. */
    MeleeAttackSource NONE = new MeleeAttackSource() {
        @Override
        public boolean owns(Event event) {
            return false;
        }

        @Override
        public EnumHand handOf(AttackEntityEvent event) {
            return null;
        }

        @Override
        public float chargeOf(CriticalHitEvent event) {
            return Float.NaN;
        }
    };

    /** Whether the event is one of this source's attack or crit events. */
    boolean owns(Event event);

    /** The attacking hand of an owned attack event, or null when the source is off: the attack is left alone. */
    EnumHand handOf(AttackEntityEvent event);

    /** The charge (0 to 1) of an owned crit event, or NaN when the source is off: the attack is left alone. */
    float chargeOf(CriticalHitEvent event);
}
