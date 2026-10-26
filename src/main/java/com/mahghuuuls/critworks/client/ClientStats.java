package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.integration.spartan.SpartanWeaponTypes;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.config.WeaponRulesFile;
import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.core.WeaponRules;
import com.mahghuuuls.critworks.network.BonusLabelsMessage;
import com.mahghuuuls.critworks.network.ConfigSyncMessage;
import com.mahghuuuls.critworks.network.CritworksNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The client's resolver: the server's settings and rules while connected (REQ-132), this client's own
 * config otherwise, and the synced bonus names. Every change runs on the client thread, where tooltips
 * read it. Public, because Forge's event wrapper for its disconnect listener can only reach public classes.
 */
@SideOnly(Side.CLIENT)
public final class ClientStats implements CritworksNetwork.ClientReceiver {

    private final CritSettings localSettings;
    private final BonusCollector bonuses;
    private final AddonDefaults defaults;
    private final Logger logger;
    private final StatResolver local;
    private StatResolver current;
    private Map<UUID, String> labels = Collections.emptyMap();

    ClientStats(CritSettings localSettings, WeaponRules localRules, BonusCollector bonuses, AddonDefaults defaults,
                Logger logger) {
        this.localSettings = localSettings;
        this.bonuses = bonuses.forClient(this::label);
        this.defaults = defaults.withOwnFailures();
        this.logger = logger;
        this.local = new StatResolver(localSettings, localRules, this.bonuses, this.defaults);
        this.current = local;
    }

    StatResolver resolver() {
        return current;
    }

    private String label(AttributeModifier modifier) {
        String name = labels.get(modifier.getID());
        return name != null ? name : modifier.getName();
    }

    @Override
    public void onConfigSync(ConfigSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (!message.isReadable()) {
                logger.warn("Critworks: the server sent settings in a format this client does not know; tooltips use the local config");
                current = local;
                return;
            }
            current = new StatResolver(message.settingsOver(localSettings),
                    message.rules(WeaponRulesFile::lookup, SpartanWeaponTypes::forName, logger::warn), bonuses, defaults);
        });
    }

    @Override
    public void onBonusLabels(BonusLabelsMessage message) {
        Map<UUID, String> received = new HashMap<UUID, String>(message.getLabels());
        Minecraft.getMinecraft().addScheduledTask(() -> labels = received);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            current = local;
            labels = Collections.emptyMap();
        });
    }
}
