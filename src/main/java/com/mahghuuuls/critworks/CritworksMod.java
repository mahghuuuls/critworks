package com.mahghuuuls.critworks;

import com.mahghuuuls.critworks.api.CritworksApi;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import com.mahghuuuls.critworks.combat.CritFeedback;
import com.mahghuuuls.critworks.command.CritworksCommand;
import com.mahghuuuls.critworks.combat.MeleeAttackTracker;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.config.SettingsReader;
import com.mahghuuuls.critworks.config.WeaponRulesFile;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.diagnostics.DiagnosticLog;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.critworks.integration.rlcombat.RLCombatAttacks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:forge@[14.23.5.2847,);after:jafs@[1.0.0,);after:bettercombatmod;after:quark;after:spartanweaponry;after:qualitytools")
public class CritworksMod {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    private CritSettings settings;
    private File configFolder;
    private StatResolver resolver;
    private BonusCollector bonusCollector;
    private MeleeAttackTracker meleeTracker;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFolder = event.getModConfigurationDirectory();
        settings = new SettingsReader(LOGGER::warn).read(new File(configFolder, "critworks.cfg"));
        // Players get the bonus attributes from the first one created on.
        MinecraftForge.EVENT_BUS.register(new CritAttributes.Registrar());
        CritworksApi.bind(new ApiBackend(LOGGER));
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Items and sounds of every mod are registered by now, so rules and the crit sound can be resolved.
        bonusCollector = new BonusCollector(LOGGER);
        resolver = new StatResolver(settings, new WeaponRulesFile(LOGGER).load(new File(configFolder, "critworks")),
                bonusCollector);
        IntegrationGate gate = new IntegrationGate(Loader::isModLoaded, settings, LOGGER::warn);
        CritFeedback feedback = CritFeedback.choose(settings, gate.isLoaded(CritSettings.Integration.RLCOMBAT), LOGGER);
        DiagnosticLog diagnostics = new DiagnosticLog(LOGGER, settings.isDiagnosticMode());
        meleeTracker = new MeleeAttackTracker(resolver, settings, feedback, diagnostics, RLCombatAttacks.create(gate));
        MinecraftForge.EVENT_BUS.register(meleeTracker);
        if (diagnostics.isEnabled()) {
            LOGGER.info("[diag] diagnostic mode on; crit sound {}; rlcombat integration {}", feedback.describeSound(),
                    gate.isOn(CritSettings.Integration.RLCOMBAT) ? "on" : "off");
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CritworksCommand(resolver, bonusCollector, settings, LOGGER));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        meleeTracker.onServerStopping();
    }
}
