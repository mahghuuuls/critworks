package com.mahghuuuls.critworks.network;

import com.mahghuuuls.critworks.Tags;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Owns the Critworks channel. Both messages go from the server to the client. Their handlers are common
 * code and hand the message to the client receiver, which only the client side sets, so a dedicated server
 * never loads client classes (REQ-131).
 */
public final class CritworksNetwork {

    private static SimpleNetworkWrapper channel;
    private static volatile ClientReceiver receiver = ClientReceiver.NONE;

    private CritworksNetwork() {
    }

    /** Called once in preInit, on both sides. */
    public static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);
        channel.registerMessage(ConfigSyncMessage.Handler.class, ConfigSyncMessage.class, 0, Side.CLIENT);
        channel.registerMessage(BonusLabelsMessage.Handler.class, BonusLabelsMessage.class, 1, Side.CLIENT);
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        channel.sendTo(message, player);
    }

    /** Set by the client side at start. */
    public static void setReceiver(ClientReceiver clientReceiver) {
        receiver = clientReceiver;
    }

    static ClientReceiver receiver() {
        return receiver;
    }

    /** What the client does with the messages. Called on the network thread. */
    public interface ClientReceiver {

        ClientReceiver NONE = new ClientReceiver() {
            @Override
            public void onConfigSync(ConfigSyncMessage message) {
            }

            @Override
            public void onBonusLabels(BonusLabelsMessage message) {
            }
        };

        void onConfigSync(ConfigSyncMessage message);

        void onBonusLabels(BonusLabelsMessage message);
    }
}
