package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.CommonProxy;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.core.WeaponRules;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.critworks.network.CritworksNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/** Client start-up: the synced resolver, the tooltip lines, and the Quark icon row. */
@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {

    @Override
    public Supplier<StatResolver> postInit(CritSettings settings, WeaponRules rules, BonusCollector bonuses,
                                           AddonDefaults defaults, Logger logger) {
        ClientStats stats = new ClientStats(settings, rules, bonuses, defaults, logger);
        CritworksNetwork.setReceiver(stats);
        MinecraftForge.EVENT_BUS.register(stats);
        QuarkStatRow quarkRow = new QuarkStatRow(new IntegrationGate(Loader::isModLoaded, settings, logger::warn));
        MinecraftForge.EVENT_BUS.register(quarkRow);
        MinecraftForge.EVENT_BUS.register(new TooltipPresenter(stats, quarkRow, settings.isDiagnosticMode() ? logger : null));
        return stats::resolver;
    }
}
