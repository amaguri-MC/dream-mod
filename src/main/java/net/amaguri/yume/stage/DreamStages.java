package net.amaguri.yume.stage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.schedule.Scene;

/**
 * Staged comedy/eerie dreams in the dream dimension "yume:dreamland".
 * OWNER: worker "stage". Public signatures are a CONTRACT.
 */
public final class DreamStages {
	public static final ResourceKey<Level> DREAMLAND = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, YumeMod.id("dreamland"));

	private DreamStages() {
	}

	public static void init() {
	}

	/** True for SHEEP, DIAMOND, BALLOON, HELP. */
	public static boolean isStaged(Scene scene) {
		return scene == Scene.SHEEP || scene == Scene.DIAMOND || scene == Scene.BALLOON || scene == Scene.HELP;
	}

	/** Build (idempotently) and start a staged scene lasting durationTicks. null if not staged or the dimension is missing. */
	public static SceneRun start(MinecraftServer server, Scene scene, int durationTicks) {
		return null;
	}
}
