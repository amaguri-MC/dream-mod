package net.amaguri.yume;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;

import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.YumeState;
import net.amaguri.yume.director.DreamDirector;
import net.amaguri.yume.isolation.Isolation;
import net.amaguri.yume.net.YumeNetwork;
import net.amaguri.yume.stage.DreamStages;

/**
 * Entry point. Each module registers itself from its own init(); keep this file tiny.
 * CONTRACT FILE (owned by the lead). Workers must not edit it.
 */
public final class YumeMod implements ModInitializer {
	public static final String MOD_ID = "yume";
	public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("yume");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		YumeNetwork.registerPayloads();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			YumeConfig.load(server);
			YumeState.load(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(YumeState::save);
		DreamDirector.init();
		Isolation.init();
		DreamStages.init();
	}
}
