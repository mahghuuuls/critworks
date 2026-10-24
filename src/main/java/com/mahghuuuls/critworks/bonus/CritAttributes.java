package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the four player bonus attributes and the identity of global bonus modifiers. Every global bonus
 * source (armor, potions, foods, Quality Tools, addons) adds an attribute modifier; vanilla saves them
 * with the player and sends them to the player's client. Only operation 0 (add) counts toward a bonus.
 */
public final class CritAttributes {

    private static final double LIMIT = 1.0E6D;
    private static final Map<CritStat, IAttribute> ATTRIBUTES = new EnumMap<CritStat, IAttribute>(CritStat.class);

    static {
        for (CritStat stat : CritStat.values()) {
            ATTRIBUTES.put(stat, new RangedAttribute(null, stat.getAttributeName(), 0.0D, -LIMIT, LIMIT)
                    .setDescription(stat.getLabel()).setShouldWatch(true));
        }
    }

    private CritAttributes() {
    }

    public static IAttribute attribute(CritStat stat) {
        return ATTRIBUTES.get(stat);
    }

    /**
     * Adds the attributes to every player as it is created, on both sides. The attribute map is created
     * on first use, so this runs before saved modifiers are read and before vanilla adds its own.
     */
    public static final class Registrar {
        @SubscribeEvent
        public void onConstructing(EntityEvent.EntityConstructing event) {
            if (event.getEntity() instanceof EntityPlayer) {
                AbstractAttributeMap map = ((EntityPlayer) event.getEntity()).getAttributeMap();
                for (IAttribute attribute : ATTRIBUTES.values()) {
                    if (map.getAttributeInstance(attribute) == null) {
                        map.registerAttribute(attribute);
                    }
                }
            }
        }
    }

    /** Stable id of one source's modifier on one stat, so adding it again replaces it. */
    static UUID modifierId(String sourceId, CritStat stat) {
        return UUID.nameUUIDFromBytes(("critworks:" + sourceId + ":" + stat.name()).getBytes(StandardCharsets.UTF_8));
    }

    /** Sets a source's global bonus on one stat; adding the same source again replaces the old value. */
    public static void setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount) {
        setOn(player.getEntityAttribute(attribute(stat)), sourceId, stat, amount);
    }

    static void setOn(IAttributeInstance instance, String sourceId, CritStat stat, double amount) {
        UUID id = modifierId(sourceId, stat);
        instance.removeModifier(id);
        if (amount != 0.0D) {
            instance.applyModifier(new AttributeModifier(id, sourceId, amount, 0));
        }
    }

    public static void removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat) {
        player.getEntityAttribute(attribute(stat)).removeModifier(modifierId(sourceId, stat));
    }
}
