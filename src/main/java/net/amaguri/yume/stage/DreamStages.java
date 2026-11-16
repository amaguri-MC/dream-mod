package net.amaguri.yume.stage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.schedule.Scene;

/**
 * Staged comedy/eerie dreams in the dream dimension "yume:dreamland".
 * OWNER: worker "stage". Public signatures are a CONTRACT.
 */
public final class DreamStages {
	public static final ResourceKey<Level> DREAMLAND = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, YumeMod.id("dreamland"));

	/** Each scene lives far from the others (more than any view distance) so nobody can see two at once. */
	private static final int SPACING = 1024;
	private static final int BASE_Y = 100;
	private static final int DEFAULT_TICKS = 200;

	private static final Map<Scene, BaseRun> ACTIVE = new EnumMap<>(Scene.class);
	private static boolean warnedMissing;

	private DreamStages() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(DreamStages::onStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(DreamStages::onStopping);
	}

	/** True for SHEEP, DIAMOND, BALLOON, HELP. */
	public static boolean isStaged(Scene scene) {
		return scene == Scene.SHEEP || scene == Scene.DIAMOND || scene == Scene.BALLOON || scene == Scene.HELP;
	}

	/** Build (idempotently) and start a staged scene lasting durationTicks. null if not staged or the dimension is missing. */
	public static SceneRun start(MinecraftServer server, Scene scene, int durationTicks) {
		if (server == null || scene == null || !isStaged(scene)) {
			return null;
		}
		ServerLevel level = server.getLevel(DREAMLAND);
		if (level == null) {
			if (!warnedMissing) {
				warnedMissing = true;
				YumeMod.LOG.warn("[yume] dimension {} is missing, staged dreams are unavailable", DREAMLAND.identifier());
			}
			return null;
		}
		int dur = durationTicks <= 0 ? DEFAULT_TICKS : Math.max(60, Math.min(2400, durationTicks));

		BaseRun previous = ACTIVE.get(scene);
		if (previous != null) {
			previous.cleanup();
		}

		BaseRun run = create(level, scene, dur);
		if (run == null) {
			return null;
		}
		ACTIVE.put(scene, run);
		try {
			run.build();
		} catch (RuntimeException e) {
			YumeMod.LOG.error("[yume] could not build staged scene {}", scene, e);
			run.cleanup();
			return null;
		}
		return run;
	}

	private static BaseRun create(ServerLevel level, Scene scene, int dur) {
		return switch (scene) {
			case SHEEP -> new SheepRun(level, new BlockPos(SPACING, BASE_Y, SPACING), dur);
			case DIAMOND -> new DiamondRun(level, new BlockPos(2 * SPACING, BASE_Y, SPACING), dur);
			case BALLOON -> new BalloonRun(level, new BlockPos(3 * SPACING, BASE_Y, SPACING), dur);
			case HELP -> new HelpRun(level, new BlockPos(4 * SPACING, BASE_Y, SPACING), dur);
			default -> null;
		};
	}

	/** Called by a run when it has cleaned up. */
	static void forget(BaseRun run) {
		ACTIVE.values().removeIf(r -> r == run);
	}

	// ---- leftovers ----

	private static void onStarted(MinecraftServer server) {
		sweep(server);
		ServerLevel level = server.getLevel(DREAMLAND);
		if (level != null) {
			// chunks that were still force-loaded when the server last stopped
			for (long c : new LongArrayList(level.getForceLoadedChunks())) {
				level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
			}
		}
	}

	private static void onStopping(MinecraftServer server) {
		for (BaseRun run : new ArrayList<>(ACTIVE.values())) {
			run.cleanup();
		}
		ACTIVE.clear();
		sweep(server);
	}

	/** Remove every entity carrying our tag, in every dimension (stage actors and camera anchors). */
	private static void sweep(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			try {
				List<Entity> found = new ArrayList<>();
				for (Entity e : level.getAllEntities()) {
					if (e.entityTags().contains(Kit.TAG)) {
						found.add(e);
					}
				}
				for (Entity e : found) {
					e.discard();
				}
			} catch (RuntimeException ex) {
				YumeMod.LOG.warn("[yume] could not sweep stage entities", ex);
			}
		}
	}
}
