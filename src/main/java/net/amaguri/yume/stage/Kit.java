package net.amaguri.yume.stage;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** Small helpers shared by the staged scenes. */
final class Kit {
	/** Entity tag on everything this module spawns (used to sweep leftovers after a crash or stop). */
	static final String TAG = "yume_stage";
	/** Send to clients, do not run neighbour/shape updates (so nothing pops off or drops). */
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private Kit() {
	}

	/**
	 * Create, configure and add an entity: no AI, no gravity, no physics, silent, invulnerable, tagged.
	 * {@code nbt} may be null; {@code Pos}/{@code Rotation} are applied afterwards by snapTo.
	 */
	static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, CompoundTag nbt, Vec3 pos, float yaw, float pitch) {
		T e = type.create(level, EntitySpawnReason.COMMAND);
		if (e == null) {
			throw new IllegalStateException("could not create entity " + type);
		}
		if (nbt != null) {
			e.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt));
		}
		e.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		e.setNoGravity(true);
		e.setSilent(true);
		e.setInvulnerable(true);
		e.noPhysics = true;
		e.addTag(TAG);
		if (e instanceof Mob m) {
			m.setNoAi(true);
			m.setPersistenceRequired();
		}
		if (!level.addFreshEntity(e)) {
			throw new IllegalStateException("could not add entity " + type);
		}
		return e;
	}

	/** A player-shaped mannequin wearing the skin of {@code mcid}; an empty or invalid name gives the default skin. */
	static Mannequin mannequin(ServerLevel level, String mcid, Vec3 pos, float yaw) {
		CompoundTag nbt = new CompoundTag();
		String n = mcid == null ? "" : mcid.trim();
		if (n.matches("[A-Za-z0-9_]{1,16}")) {
			nbt.putString("profile", n);
		}
		nbt.putBoolean("immovable", true);
		nbt.putBoolean("hide_description", true);
		return spawn(level, EntityTypes.MANNEQUIN, nbt, pos, yaw, 0.0f);
	}

	static void face(Entity e, float yaw, float pitch) {
		e.setYRot(yaw);
		e.setXRot(pitch);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
	}

	static void sound(ServerLevel l, Vec3 p, SoundEvent s, SoundSource c, float volume, float pitch) {
		l.playSound(null, p.x, p.y, p.z, s, c, volume, pitch);
	}

	static void sound(ServerLevel l, Vec3 p, Holder<SoundEvent> s, SoundSource c, float volume, float pitch) {
		l.playSound(null, p.x, p.y, p.z, s, c, volume, pitch);
	}

	static void particles(ServerLevel l, ParticleOptions o, Vec3 p, int count, double dx, double dy, double dz, double speed) {
		l.sendParticles(o, p.x, p.y, p.z, count, dx, dy, dz, speed);
	}

	/** A round grass island that narrows downwards; the grass blocks are at cy - 1, so the walkable surface is y = cy. */
	static void island(Plan plan, int cx, int cy, int cz, int a, int b, int depth) {
		for (int d = 0; d <= depth; d++) {
			double s = 1.0 - d / (double) (depth + 1);
			double ra = Math.max(1.0, a * s);
			double rb = Math.max(1.0, b * s);
			for (int x = -a; x <= a; x++) {
				for (int z = -b; z <= b; z++) {
					double u = x / ra;
					double v = z / rb;
					if (u * u + v * v <= 1.0) {
						plan.set(cx + x, cy - 1 - d, cz + z,
								(d == 0 ? Blocks.GRASS_BLOCK : d <= 2 ? Blocks.DIRT : Blocks.STONE).defaultBlockState());
					}
				}
			}
		}
	}

	static int hash(int x, int z) {
		int h = x * 73856093 ^ z * 19349663;
		h ^= h >>> 13;
		return h & 0x7fffffff;
	}

	static double ease(double x) {
		double c = Math.max(0.0, Math.min(1.0, x));
		return c * c * (3.0 - 2.0 * c);
	}
}
