package net.amaguri.yume.stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.amaguri.yume.YumeMod;

/** Common machinery of the four staged scenes: forced chunks, block plan, spawned entities, cleanup. */
abstract class BaseRun implements SceneRun {
	protected final ServerLevel level;
	protected final int dur;
	/** Origin of the stage (block coordinates). */
	protected final BlockPos o;

	private final List<Entity> ents = new ArrayList<>();
	private final List<Long> forcedChunks = new ArrayList<>();
	protected Entity anchor;
	private boolean done;
	private boolean failed;

	BaseRun(ServerLevel level, BlockPos origin, int durationTicks) {
		this.level = level;
		this.o = origin;
		this.dur = durationTicks;
	}

	/** Relative bounding box of everything the stage builds: x1,y1,z1,x2,y2,z2. */
	abstract int[] bounds();

	abstract void layout(Plan plan);

	/** Spawn actors and the camera in their t = 0 state. */
	abstract void populate();

	/** One tick of the show. p runs 0..1 over the duration. */
	abstract void step(int t, double p);

	void onCleanup() {
	}

	// ---- helpers ----

	protected final Vec3 at(double rx, double ry, double rz) {
		return new Vec3(o.getX() + rx, o.getY() + ry, o.getZ() + rz);
	}

	/** Scale a tick count written for a 200 tick scene to this scene's duration. */
	protected final int sc(int ticksAt200) {
		return Math.max(1, Math.round(ticksAt200 * dur / 200.0f));
	}

	protected final <T extends Entity> T track(T e) {
		ents.add(e);
		return e;
	}

	protected final void forget(Entity e) {
		ents.remove(e);
	}

	protected final void spawnCamera(Vec3 pos, float yaw, float pitch) {
		anchor = CameraAnchor.spawn(level, pos, yaw, pitch);
		if (anchor == null) {
			throw new IllegalStateException("camera anchor could not be spawned");
		}
		ents.add(anchor);
	}

	protected final void cam(Vec3 pos, float yaw, float pitch) {
		CameraAnchor.move(anchor, pos, yaw, pitch);
	}

	// ---- lifecycle ----

	final void build() {
		int[] b = bounds();
		Plan plan = new Plan(o.getX() + b[0], o.getY() + b[1], o.getZ() + b[2], o.getX() + b[3], o.getY() + b[4], o.getZ() + b[5]);
		// load and keep the stage chunks loaded for as long as the scene runs
		for (int cx = plan.x1 >> 4; cx <= plan.x2 >> 4; cx++) {
			for (int cz = plan.z1 >> 4; cz <= plan.z2 >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				forcedChunks.add(ChunkPos.pack(cx, cz));
				level.getChunk(cx, cz);
			}
		}
		// anything left behind by a crashed earlier run
		AABB region = new AABB(plan.x1 - 2, plan.y1 - 2, plan.z1 - 2, plan.x2 + 3, plan.y2 + 40, plan.z2 + 3);
		for (Entity e : level.getEntitiesOfClass(Entity.class, region, x -> x.entityTags().contains(Kit.TAG))) {
			e.discard();
		}
		layout(plan);
		plan.apply(level);
		populate();
	}

	@Override
	public final ServerLevel level() {
		return level;
	}

	@Override
	public final Entity cameraAnchor() {
		return anchor;
	}

	@Override
	public List<Entity> whiteSilhouettes() {
		return Collections.emptyList();
	}

	@Override
	public final void tick(int t) {
		if (done || failed) {
			return;
		}
		try {
			double p = dur <= 1 ? 1.0 : Math.max(0.0, Math.min(1.0, t / (double) (dur - 1)));
			step(t, p);
		} catch (RuntimeException e) {
			failed = true;
			YumeMod.LOG.error("[yume] staged scene {} failed at tick {}", getClass().getSimpleName(), t, e);
		}
	}

	@Override
	public final void cleanup() {
		if (done) {
			return;
		}
		done = true;
		try {
			onCleanup();
		} catch (RuntimeException e) {
			YumeMod.LOG.warn("[yume] scene cleanup hook failed", e);
		}
		for (Entity e : new ArrayList<>(ents)) {
			try {
				if (!e.isRemoved()) {
					e.ejectPassengers();
					e.discard();
				}
			} catch (RuntimeException ex) {
				YumeMod.LOG.warn("[yume] could not remove a stage entity", ex);
			}
		}
		ents.clear();
		for (long c : forcedChunks) {
			try {
				level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
			} catch (RuntimeException ex) {
				YumeMod.LOG.warn("[yume] could not release a stage chunk", ex);
			}
		}
		forcedChunks.clear();
		DreamStages.forget(this);
	}
}
