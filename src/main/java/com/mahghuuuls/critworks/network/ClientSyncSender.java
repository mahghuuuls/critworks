package com.mahghuuuls.critworks.network;

import com.mahghuuuls.critworks.bonus.BonusCollector;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server side: sends the settings and rules once at login, and the bonus labels whenever they change.
 * Labels are compared every {@link #LABEL_INTERVAL} ticks, so a new bonus is named on the client within
 * half a second (REQ-132). Runs on the server thread only.
 */
public final class ClientSyncSender {

    static final int LABEL_INTERVAL = 10;

    private final ConfigSyncMessage config;
    private final Map<UUID, Map<UUID, String>> sentLabels = new HashMap<UUID, Map<UUID, String>>();

    public ClientSyncSender(ConfigSyncMessage config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (isConnected(event.player)) {
            sentLabels.remove(event.player.getUniqueID());
            CritworksNetwork.sendTo(config, (EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sentLabels.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isConnected(event.player)
                || event.player.ticksExisted % LABEL_INTERVAL != 0) {
            return;
        }
        Map<UUID, String> labels = labelsOf(event.player::getEntityAttribute);
        Map<UUID, String> sent = sentLabels.get(event.player.getUniqueID());
        if (!labels.equals(sent == null ? Collections.<UUID, String>emptyMap() : sent)) {
            sentLabels.put(event.player.getUniqueID(), labels);
            CritworksNetwork.sendTo(new BonusLabelsMessage(labels), (EntityPlayerMP) event.player);
        }
    }

    /** A real player with a connection; fake players of other mods have no client to send to. */
    private static boolean isConnected(EntityPlayer player) {
        return player instanceof EntityPlayerMP && !(player instanceof FakePlayer)
                && ((EntityPlayerMP) player).connection != null;
    }

    /** The names of the modifiers that count as global bonuses, by modifier id. */
    static Map<UUID, String> labelsOf(Function<IAttribute, IAttributeInstance> attributes) {
        Map<UUID, String> labels = new HashMap<UUID, String>();
        BonusCollector.forEachCountedModifier(attributes, (stat, modifier) -> labels.put(modifier.getID(), modifier.getName()));
        return labels;
    }
}
