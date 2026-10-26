package com.mahghuuuls.critworks.network;

import com.mahghuuuls.critworks.core.ItemType;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.WeaponRule;
import com.mahghuuuls.critworks.core.WeaponRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Server to client, once at login: the server's numeric settings, its integration switches, and its parsed
 * weapon rules, so the client's tooltips show the server's values (REQ-132). The client keeps them until it
 * disconnects.
 */
public final class ConfigSyncMessage implements IMessage {

    static final int VERSION = 3;

    private int version = VERSION;
    private double fallbackChance;
    private double fallbackMultiplier;
    private double meleeChargeThreshold;
    private double bowChargeThreshold;
    private double damageCap;
    /** The server's integration switches by setting key, so a client resolves Quality Tools and Spartan as the server does. */
    private Map<String, Boolean> integrations = new LinkedHashMap<String, Boolean>();
    private List<RuleData> rules = new ArrayList<RuleData>();

    /** For Forge, which fills it with {@link #fromBytes}. */
    public ConfigSyncMessage() {
    }

    public static ConfigSyncMessage of(CritSettings settings, WeaponRules weaponRules) {
        ConfigSyncMessage message = new ConfigSyncMessage();
        message.fallbackChance = settings.getFallbackChance();
        message.fallbackMultiplier = settings.getFallbackMultiplier();
        message.meleeChargeThreshold = settings.getMeleeChargeThreshold();
        message.bowChargeThreshold = settings.getBowChargeThreshold();
        message.damageCap = settings.getDamageCap();
        for (CritSettings.Integration integration : CritSettings.Integration.values()) {
            message.integrations.put(integration.getKey(), settings.isIntegrationEnabled(integration));
        }
        for (WeaponRule rule : weaponRules.all()) {
            String itemId = rule.getItem() == null ? null : String.valueOf(rule.getItem().getRegistryName());
            String type = rule.getType() == null ? null : rule.getType().name();
            message.rules.add(new RuleData(rule.getIndex(), itemId, type, rule.getMeta(), rule.getNbt(),
                    rule.getEligible(), rule.getChance(), rule.getDamage()));
        }
        return message;
    }

    /** False when the server sent a format this client does not know. */
    public boolean isReadable() {
        return version == VERSION;
    }

    /**
     * The server's numeric settings and integration switches over this client's own. Sound, diagnostics, and
     * the Quark switch stay local: they change only what this client shows or plays.
     */
    public CritSettings settingsOver(CritSettings local) {
        boolean[] integrations = new boolean[CritSettings.Integration.values().length];
        for (CritSettings.Integration integration : CritSettings.Integration.values()) {
            Boolean server = this.integrations.get(integration.getKey());
            integrations[integration.ordinal()] = integration == CritSettings.Integration.QUARK || server == null
                    ? local.isIntegrationEnabled(integration) : server;
        }
        return new CritSettings(fallbackChance, fallbackMultiplier, meleeChargeThreshold, bowChargeThreshold,
                damageCap, local.isPvpCrits(), local.getCritSound(), local.isDiagnosticMode(), integrations);
    }

    /** As {@link #rules(Function, Function, Consumer)} on a client without item types. */
    public WeaponRules rules(Function<String, Item> items, Consumer<String> warnings) {
        return rules(items, name -> null, warnings);
    }

    /**
     * The server's rules with items and types looked up on this client. A rule whose item or type the client
     * does not have is left out with a warning. Ties were already reported by the server, so they are not
     * reported again.
     */
    public WeaponRules rules(Function<String, Item> items, Function<String, ItemType> types, Consumer<String> warnings) {
        List<WeaponRule> result = new ArrayList<WeaponRule>();
        for (RuleData data : rules) {
            if (data.type != null) {
                ItemType type = types.apply(data.type);
                if (type == null) {
                    warnings.accept("Critworks: the server's weapon rule #" + (data.index + 1) + " names the spartan type "
                            + data.type + ", which this client does not have; tooltips leave it out");
                } else {
                    result.add(WeaponRule.forType(data.index, type, data.eligible, data.chance, data.damage));
                }
                continue;
            }
            Item item = items.apply(data.itemId);
            if (item == null) {
                warnings.accept("Critworks: the server's weapon rule #" + (data.index + 1) + " names " + data.itemId
                        + ", which this client does not have; tooltips leave it out");
                continue;
            }
            result.add(new WeaponRule(data.index, item, data.meta, data.nbt, data.eligible, data.chance, data.damage));
        }
        return new WeaponRules(result, message -> { });
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(VERSION);
        buf.writeDouble(fallbackChance);
        buf.writeDouble(fallbackMultiplier);
        buf.writeDouble(meleeChargeThreshold);
        buf.writeDouble(bowChargeThreshold);
        buf.writeDouble(damageCap);
        ByteBufUtils.writeVarInt(buf, integrations.size(), 5);
        for (Map.Entry<String, Boolean> entry : integrations.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey());
            buf.writeBoolean(entry.getValue());
        }
        ByteBufUtils.writeVarInt(buf, rules.size(), 5);
        for (RuleData rule : rules) {
            ByteBufUtils.writeVarInt(buf, rule.index, 5);
            buf.writeBoolean(rule.type != null);
            ByteBufUtils.writeUTF8String(buf, rule.type != null ? rule.type : rule.itemId);
            buf.writeBoolean(rule.meta != null);
            if (rule.meta != null) {
                buf.writeInt(rule.meta);
            }
            buf.writeBoolean(rule.nbt != null);
            if (rule.nbt != null) {
                ByteBufUtils.writeTag(buf, rule.nbt);
            }
            buf.writeByte(rule.eligible == null ? 0 : rule.eligible ? 2 : 1);
            writeOptional(buf, rule.chance);
            writeOptional(buf, rule.damage);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        version = buf.readUnsignedByte();
        if (version != VERSION) {
            return;
        }
        fallbackChance = buf.readDouble();
        fallbackMultiplier = buf.readDouble();
        meleeChargeThreshold = buf.readDouble();
        bowChargeThreshold = buf.readDouble();
        damageCap = buf.readDouble();
        int switches = ByteBufUtils.readVarInt(buf, 5);
        integrations = new LinkedHashMap<String, Boolean>();
        for (int i = 0; i < switches; i++) {
            integrations.put(ByteBufUtils.readUTF8String(buf), buf.readBoolean());
        }
        int count = ByteBufUtils.readVarInt(buf, 5);
        rules = new ArrayList<RuleData>(count);
        for (int i = 0; i < count; i++) {
            int index = ByteBufUtils.readVarInt(buf, 5);
            boolean typeRule = buf.readBoolean();
            String name = ByteBufUtils.readUTF8String(buf);
            Integer meta = buf.readBoolean() ? buf.readInt() : null;
            NBTTagCompound nbt = buf.readBoolean() ? ByteBufUtils.readTag(buf) : null;
            byte eligible = buf.readByte();
            Double chance = readOptional(buf);
            Double damage = readOptional(buf);
            rules.add(new RuleData(index, typeRule ? null : name, typeRule ? name : null, meta, nbt,
                    eligible == 0 ? null : eligible == 2, chance, damage));
        }
    }

    private static void writeOptional(ByteBuf buf, Double value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeDouble(value);
        }
    }

    private static Double readOptional(ByteBuf buf) {
        return buf.readBoolean() ? buf.readDouble() : null;
    }

    /** One rule as sent: the item by registry id or the type by name, because the client looks them up itself. */
    static final class RuleData {
        final int index;
        final String itemId;
        final String type;
        final Integer meta;
        final NBTTagCompound nbt;
        final Boolean eligible;
        final Double chance;
        final Double damage;

        RuleData(int index, String itemId, String type, Integer meta, NBTTagCompound nbt, Boolean eligible,
                 Double chance, Double damage) {
            this.index = index;
            this.itemId = itemId;
            this.type = type;
            this.meta = meta;
            this.nbt = nbt;
            this.eligible = eligible;
            this.chance = chance;
            this.damage = damage;
        }
    }

    /** Runs on the network thread; the client receiver moves the work to the client thread. */
    public static final class Handler implements IMessageHandler<ConfigSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(ConfigSyncMessage message, MessageContext context) {
            CritworksNetwork.receiver().onConfigSync(message);
            return null;
        }
    }
}
