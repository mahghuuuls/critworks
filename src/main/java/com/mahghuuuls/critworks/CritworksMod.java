package com.mahghuuuls.critworks;

import com.mahghuuuls.critworks.integration.spartan.SpartanLongbowDraw;
import com.mahghuuuls.critworks.integration.spartan.SpartanWeaponTypes;
import com.mahghuuuls.critworks.api.CritworksApi;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import com.mahghuuuls.critworks.bonus.WeaponBonusProviders;
import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.AttackerPolicies;
import com.mahghuuuls.critworks.combat.CombatHandlers;
import com.mahghuuuls.critworks.combat.CritFeedback;
import com.mahghuuuls.critworks.command.CritworksCommand;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.config.SettingsReader;
import com.mahghuuuls.critworks.config.WeaponRulesFile;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.core.WeaponRules;
import com.mahghuuuls.critworks.diagnostics.DiagnosticLog;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.critworks.integration.jafs.JafsBonusCleanup;
import com.mahghuuuls.critworks.integration.jafs.JafsCritProperties;
import com.mahghuuuls.critworks.integration.qualitytools.QualityToolsBonuses;
import com.mahghuuuls.critworks.integration.rlcombat.RLCombatAttacks;
import com.mahghuuuls.critworks.network.ClientSyncSender;
import com.mahghuuuls.critworks.network.ConfigSyncMessage;
import com.mahghuuuls.critworks.network.CritworksNetwork;
import com.mahghuuuls.critworks.state.ArrowCritCapability;
import com.mahghuuuls.critworks.state.PlayerCritCapability;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
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

    @SidedProxy(clientSide = "com.mahghuuuls.critworks.client.ClientProxy",
            serverSide = "com.mahghuuuls.critworks.CommonProxy")
    public static CommonProxy proxy;

    private CritSettings settings;
    private File configFolder;
    private StatResolver resolver;
    private BonusCollector bonusCollector;
    private CombatHandlers combat;
    private ApiBackend api;
    private AddonDefaults addonDefaults;
    private AttackerPolicies attackerPolicies;
    private IntegrationGate gate;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFolder = event.getModConfigurationDirectory();
        settings = new SettingsReader(LOGGER::warn).read(new File(configFolder, "critworks.cfg"));
        // Players get the bonus attributes from the first one created on.
        MinecraftForge.EVENT_BUS.register(new CritAttributes.Registrar());
        ArrowCritCapability.register(LOGGER::warn);
        PlayerCritCapability.register(LOGGER::warn);
        // Addons register defaults, providers, and policies from their own loading on, so these exist first.
        addonDefaults = new AddonDefaults(LOGGER::warn);
        attackerPolicies = new AttackerPolicies(LOGGER::warn);
        WeaponBonusProviders providers = new WeaponBonusProviders(LOGGER::warn);
        bonusCollector = new BonusCollector(LOGGER, providers);
        api = new ApiBackend(LOGGER, addonDefaults, providers, attackerPolicies);
        CritworksApi.bind(api);
        CritworksNetwork.register();
        gate = new IntegrationGate(Loader::isModLoaded, settings, LOGGER::warn);
        // JAFS takes property registrations from its own pre-init until loading completes.
        if (gate.isOn(CritSettings.Integration.JAFS)) {
            JafsCritProperties.register(gate);
        }
        MinecraftForge.EVENT_BUS.register(new JafsBonusCleanup(gate));
        // Quality Tools needs no registration: Critworks reads its item tags; the switch is checked per attack.
        if (gate.isLoaded(CritSettings.Integration.QUALITY_TOOLS)) {
            bonusCollector.addHeldItemBonuses(new QualityToolsBonuses());
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Items and sounds of every mod are registered by now, so rules and the crit sound can be resolved.
        boolean spartan = gate.isOn(CritSettings.Integration.SPARTAN_WEAPONRY);
        WeaponRules rules = new WeaponRulesFile(LOGGER, spartan ? SpartanWeaponTypes::forName : null,
                SpartanWeaponTypes.names()).load(new File(configFolder, "critworks"));
        resolver = new StatResolver(settings, rules, bonusCollector, addonDefaults);
        CritFeedback feedback = CritFeedback.choose(settings, gate.isLoaded(CritSettings.Integration.RLCOMBAT), LOGGER);
        DiagnosticLog diagnostics = new DiagnosticLog(LOGGER, settings.isDiagnosticMode());
        combat = CombatHandlers.create(resolver, settings, feedback, diagnostics, RLCombatAttacks.create(gate),
                attackerPolicies, new SpartanLongbowDraw(gate, CombatHandlers.VANILLA_FULL_DRAW_TICKS));
        combat.register(MinecraftForge.EVENT_BUS);
        MinecraftForge.EVENT_BUS.register(new ClientSyncSender(ConfigSyncMessage.of(settings, rules)));
        api.ready(resolver, proxy.postInit(settings, rules, bonusCollector, addonDefaults, LOGGER), combat);
        if (diagnostics.isEnabled()) {
            LOGGER.info("[diag] diagnostic mode on; crit sound {}; rlcombat integration {}; jafs integration {}; "
                    + "qualitytools integration {}; spartanweaponry integration {}", feedback.describeSound(),
                    gate.isOn(CritSettings.Integration.RLCOMBAT) ? "on" : "off",
                    gate.isOn(CritSettings.Integration.JAFS) ? "on" : "off",
                    gate.isOn(CritSettings.Integration.QUALITY_TOOLS) ? "on" : "off",
                    gate.isOn(CritSettings.Integration.SPARTAN_WEAPONRY) ? "on" : "off");
        }
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // Every mod's post-initialization is done: addon defaults, providers, policies, and integrations are final.
        api.closeRegistration();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CritworksCommand(resolver, bonusCollector, settings, LOGGER));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        combat.onServerStopping();
    }
}
