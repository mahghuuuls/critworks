package com.mahghuuuls.critworks.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server to client, when they change: the names of the player's global bonus modifiers by modifier id.
 * Vanilla sends the modifiers' amounts but not their names, so without this the client could not name a
 * bonus's source. The amounts still come from vanilla.
 */
public final class BonusLabelsMessage implements IMessage {

    private Map<UUID, String> labels = new HashMap<UUID, String>();

    /** For Forge, which fills it with {@link #fromBytes}. */
    public BonusLabelsMessage() {
    }

    public BonusLabelsMessage(Map<UUID, String> labels) {
        this.labels = new HashMap<UUID, String>(labels);
    }

    public Map<UUID, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, labels.size(), 5);
        for (Map.Entry<UUID, String> entry : labels.entrySet()) {
            buf.writeLong(entry.getKey().getMostSignificantBits());
            buf.writeLong(entry.getKey().getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, entry.getValue());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = ByteBufUtils.readVarInt(buf, 5);
        labels = new HashMap<UUID, String>();
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            labels.put(id, ByteBufUtils.readUTF8String(buf));
        }
    }

    /** Runs on the network thread; the client receiver moves the work to the client thread. */
    public static final class Handler implements IMessageHandler<BonusLabelsMessage, IMessage> {
        @Override
        public IMessage onMessage(BonusLabelsMessage message, MessageContext context) {
            CritworksNetwork.receiver().onBonusLabels(message);
            return null;
        }
    }
}
