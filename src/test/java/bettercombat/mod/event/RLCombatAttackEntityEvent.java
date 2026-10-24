package bettercombat.mod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/** Test stand-in with the class name and method RLCombat 2.2.4 has; not RLCombat code. */
public class RLCombatAttackEntityEvent extends AttackEntityEvent {

    /** When set, {@link #getOffhand()} throws it, as a changed RLCombat could. */
    public static RuntimeException failure;

    private final boolean offhand;

    public RLCombatAttackEntityEvent(EntityPlayer player, Entity target, boolean offhand) {
        super(player, target);
        this.offhand = offhand;
    }

    public boolean getOffhand() {
        if (failure != null) {
            throw failure;
        }
        return offhand;
    }
}
