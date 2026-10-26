package com.mahghuuuls.critworks.integration.jafs;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.function.Function;

/**
 * Removes "JAFS" bonuses that vanilla saved with a player while the JAFS integration is off now (its
 * setting turned off, JAFS removed, or its registration failed), so a food bonus is never left over
 * (REQ-073). While the integration is on, JAFS itself re-applies the bonuses at login. Names no JAFS
 * class, so it is safe without JAFS.
 */
public final class JafsBonusCleanup {

    /** The source name of every JAFS food bonus; defined here so this class never loads a JAFS-typed class. */
    public static final String SOURCE = "JAFS";

    private final IntegrationGate gate;

    public JafsBonusCleanup(IntegrationGate gate) {
        this.gate = gate;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.player.world.isRemote && !gate.isOn(CritSettings.Integration.JAFS)) {
            removeFrom(event.player::getEntityAttribute);
        }
    }

    /** Removes the "JAFS" bonus from all four stats. */
    static void removeFrom(Function<IAttribute, IAttributeInstance> attributes) {
        for (CritStat stat : CritStat.values()) {
            IAttributeInstance instance = attributes.apply(CritAttributes.attribute(stat));
            if (instance != null) {
                CritAttributes.setOn(instance, SOURCE, stat, 0.0D);
            }
        }
    }
}
