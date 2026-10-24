package com.mahghuuuls.critworks;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.CritworksApi;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import net.minecraft.entity.player.EntityPlayer;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/** Connects the public API to the internal components, so the api package depends on nothing inside. */
final class ApiBackend implements CritworksApi.Backend {

    private final Logger logger;
    private final AtomicBoolean warnedClientCall = new AtomicBoolean();

    ApiBackend(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount) {
        if (onServer(player, sourceId)) {
            CritAttributes.setGlobalBonus(player, sourceId, stat, amount);
        }
    }

    @Override
    public void removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat) {
        if (onServer(player, sourceId)) {
            CritAttributes.removeGlobalBonus(player, sourceId, stat);
        }
    }

    /** Bonuses are server state; a client-side call is ignored and reported once. */
    private boolean onServer(EntityPlayer player, String sourceId) {
        if (!player.world.isRemote) {
            return true;
        }
        if (warnedClientCall.compareAndSet(false, true)) {
            logger.warn("Critworks API: a bonus call from {} on the client side was ignored; call it on the server",
                    sourceId);
        }
        return false;
    }
}
