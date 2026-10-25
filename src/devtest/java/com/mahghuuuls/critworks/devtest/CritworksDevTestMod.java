package com.mahghuuuls.critworks.devtest;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Development-only addon. It uses the Critworks public API exactly as a real addon would, so runtime
 * checks exercise the API boundary. It is never part of the shipped jar.
 */
@Mod(modid = "critworks_devtest", name = "Critworks Dev Test", version = "dev",
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:critworks")
public class CritworksDevTestMod {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        DevTestApiUse.register();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new DevTestEvents());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new DevTestCommand());
    }
}
