package bettercombat.mod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.CriticalHitEvent;

/** Test stand-in with the class name and methods RLCombat 2.2.4 has; not RLCombat code. */
public class RLCombatCriticalHitEvent extends CriticalHitEvent {

    private final boolean offhand;
    private final float cooledStrength;

    public RLCombatCriticalHitEvent(EntityPlayer player, Entity target, float damageModifier, boolean vanillaCritical,
                                    boolean offhand, float cooledStrength) {
        super(player, target, damageModifier, vanillaCritical);
        this.offhand = offhand;
        this.cooledStrength = cooledStrength;
    }

    public boolean getOffhand() {
        return offhand;
    }

    public float getCooledStrength() {
        return cooledStrength;
    }
}
