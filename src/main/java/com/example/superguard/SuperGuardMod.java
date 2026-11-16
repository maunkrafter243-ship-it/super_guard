package com.example.superguard;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SuperGuardMod.MOD_ID)
public class SuperGuardMod {
    public static final String MOD_ID = "superguard";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public SuperGuardMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SuperGuardConfig.SPEC, "superguard-server.toml");
        NeoForge.EVENT_BUS.register(new SecurityEventHandler());
        NeoForge.EVENT_BUS.register(new CommandHandler());
        LOGGER.info("SuperGuard ULTIMATE SUITE v8 (100% Server-side, Nickname-based GUI, Inventory Viewer & Total Protection) initialized.");
    }
}
