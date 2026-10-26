package com.mahghuuuls.critworks.integration.jafs;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.jafs.api.IPropertyApplier;
import com.mahghuuuls.jafs.api.JafsApi;
import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.function.Function;

/**
 * The two JAFS food properties (REQ-082): {@code critworks:crit_chance} and {@code critworks:crit_damage}.
 * A food's "flat" amount is an additional bonus and its "percent" amount an increased bonus; the sums of
 * the player's active foods become global bonuses named "JAFS". JAFS checks the amounts against the
 * ranges below and calls the applier on every change, so expiry removes the bonus. Nothing outside this
 * package names a JAFS class; this class loads only when JAFS is present.
 */
public final class JafsCritProperties {

    public static final String SOURCE = JafsBonusCleanup.SOURCE;

    /** One property and the two stats its operations feed. */
    enum Property {
        CRIT_CHANCE("critworks:crit_chance", "yellow", CritStat.ADDITIONAL_CHANCE, -100.0D, 100.0D,
                CritStat.INCREASED_CHANCE, -100.0D, 1000.0D),
        CRIT_DAMAGE("critworks:crit_damage", "red", CritStat.ADDITIONAL_DAMAGE, -10.0D, 10.0D,
                CritStat.INCREASED_DAMAGE, -100.0D, 1000.0D);

        final String id;
        final String color;
        final CritStat flatStat;
        final double flatMin;
        final double flatMax;
        final CritStat percentStat;
        final double percentMin;
        final double percentMax;

        Property(String id, String color, CritStat flatStat, double flatMin, double flatMax, CritStat percentStat,
                 double percentMin, double percentMax) {
            this.id = id;
            this.color = color;
            this.flatStat = flatStat;
            this.flatMin = flatMin;
            this.flatMax = flatMax;
            this.percentStat = percentStat;
            this.percentMin = percentMin;
            this.percentMax = percentMax;
        }

        PropertySpec spec() {
            return PropertySpec.builder(id).color(color)
                    .operation(PropertyOperation.FLAT, flatMin, flatMax)
                    .operation(PropertyOperation.PERCENT, percentMin, percentMax)
                    .build();
        }
    }

    private JafsCritProperties() {
    }

    /** Registers both properties with JAFS in preInit; a failure turns the JAFS integration off with one warning. */
    public static void register(IntegrationGate gate) {
        try {
            for (Property property : Property.values()) {
                JafsApi.registerProperty(property.spec(), applier(property, gate));
            }
        } catch (RuntimeException | LinkageError e) {
            gate.fail(CritSettings.Integration.JAFS, e);
        }
    }

    private static IPropertyApplier applier(Property property, IntegrationGate gate) {
        return (EntityPlayerMP player, double flatSum, double percentSum) -> {
            try {
                if (gate.isOn(CritSettings.Integration.JAFS)) {
                    apply(player::getEntityAttribute, property, flatSum, percentSum);
                } else {
                    apply(player::getEntityAttribute, property, 0.0D, 0.0D);
                }
            } catch (RuntimeException | LinkageError e) {
                gate.fail(CritSettings.Integration.JAFS, e);
                try {
                    JafsBonusCleanup.removeFrom(player::getEntityAttribute);
                } catch (RuntimeException | LinkageError ignored) {
                    // Best effort; the login cleanup removes what is left at the next login.
                }
            }
        };
    }

    /** Writes the sums as the "JAFS" bonuses; applying the same sums again changes nothing, and zero removes them. */
    static void apply(Function<IAttribute, IAttributeInstance> attributes, Property property, double flatSum,
                      double percentSum) {
        CritAttributes.setOn(attributes.apply(CritAttributes.attribute(property.flatStat)), SOURCE, property.flatStat, flatSum);
        CritAttributes.setOn(attributes.apply(CritAttributes.attribute(property.percentStat)), SOURCE, property.percentStat,
                percentSum);
    }
}
