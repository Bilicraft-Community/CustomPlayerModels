package com.tom.cpm;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.loader.api.FabricLoader;

import com.tom.cpl.config.ConfigEntry.ModConfigFile;
import com.tom.cpl.util.ILogger;
import com.tom.cpm.common.CommandCPM;
import com.tom.cpm.common.ServerHandler;
import com.tom.cpm.shared.MinecraftCommonAccess;
import com.tom.cpm.shared.MinecraftObjectHolder;
import com.tom.cpm.shared.config.ModConfig;

public class CustomPlayerModels implements MinecraftCommonAccess, ModInitializer {
	private ModConfigFile config;

	public static final Logger LOG = LogManager.getLogger("CPM");
	public static final ILogger log = new Log4JLogger(LOG);

	@Override
	public void onInitialize() {
		config = new ModConfigFile(new File(FabricLoader.getInstance().getConfigDir().toFile(), "cpm.json"));
		MinecraftObjectHolder.setCommonObject(this);

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			MinecraftObjectHolder.setServerObject(new MinecraftServerObject(s));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			ModConfig.getWorldConfig().save();
			MinecraftObjectHolder.setServerObject(null);
		});
		CommandRegistrationCallback.EVENT.register((d, isD) -> {
			CommandCPM.register(d);
		});
		EntityTrackingEvents.START_TRACKING.register(ServerHandler::onTrackingStart);
		LOG.info("Customizable Player Models Initialized");
	}

	@Override
	public ModConfigFile getConfig() {
		return config;
	}

	@Override
	public ILogger getLogger() {
		return log;
	}
}
