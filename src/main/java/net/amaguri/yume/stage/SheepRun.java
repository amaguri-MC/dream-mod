package net.amaguri.yume.stage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.amaguri.yume.core.YumeConfig;

/** Sheep leaping a fence on a floating island; the sheepRider sits on the first sheep. */
final class SheepRun extends BaseRun {
	private static final int COUNT = 3;
	/** Sheep run along +x from -RANGE to +RANGE and wrap around (off camera). */
	private static final double RANGE = 10.0;
	private static final double SPEED = 0.3125;
	private static final double JUMP_HALF = 4.0;
	/** Lane slope: z = LANE_Z0 - SLOPE * x, so every sheep crosses the long fence (z = 0.5) at x = 0. */
	private static final double SLOPE = 0.3;
	private static final double JUMP_HEIGHT = 1.9;
	private static final double[] START_X = {-6.0, 7.5, -9.5};

	/** Heading of the lane (towards +x, -z). */
	private static final float LANE_YAW = (float) Math.toDegrees(Math.atan2(-1.0, -SLOPE));

	private final Sheep[] sheep = new Sheep[COUNT];
	private final double[] lastX = new double[COUNT];
	private Mannequin rider;

	SheepRun(ServerLevel level, BlockPos origin, int dur) {
		super(level, origin, dur);
	}

	@Override
	int[] bounds() {
		return new int[]{-24, -12, -16, 24, 4, 16};
	}

	@Override
	void layout(Plan plan) {
		int cx = o.getX();
		int cy = o.getY();
		int cz = o.getZ();
		Kit.island(plan, cx, cy, cz, 22, 14, 10);
		// one long fence across the whole screen (runs along x)
		BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
		int len = 14;
		for (int dx = -len; dx <= len; dx++) {
			plan.set(cx + dx, cy, cz, fence
					.setValue(CrossCollisionBlock.WEST, dx > -len)
					.setValue(CrossCollisionBlock.EAST, dx < len));
		}
		// flowers and tufts, never on the lane itself
		for (int x = -21; x <= 21; x++) {
			for (int z = -13; z <= 13; z++) {
				if (z == 0 || z == 1 || Math.abs(z) > 12 || plan.get(cx + x, cy - 1, cz + z) == null) {
					continue;
				}
				int h = Kit.hash(x, z) % 23;
				if (h == 0) {
					plan.set(cx + x, cy, cz + z, Blocks.DANDELION.defaultBlockState());
				} else if (h == 1) {
					plan.set(cx + x, cy, cz + z, Blocks.POPPY.defaultBlockState());
				} else if (h < 5) {
					plan.set(cx + x, cy, cz + z, Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}
	}

	private double xAt(int k, int t) {
		double v = START_X[k] + SPEED * t + RANGE;
		v = ((v % (2 * RANGE)) + 2 * RANGE) % (2 * RANGE);
		return v - RANGE;
	}

	private static double laneZ(double x) {
		return 0.5 - SLOPE * x;
	}

	private static double hopY(double x) {
		double u = x / JUMP_HALF;
		return Math.abs(u) >= 1.0 ? 0.0 : JUMP_HEIGHT * (1.0 - u * u);
	}

	@Override
	void populate() {
		for (int k = 0; k < COUNT; k++) {
			double x = START_X[k];
			lastX[k] = x;
			sheep[k] = track(Kit.spawn(level, EntityTypes.SHEEP, null, at(0.5 + x, hopY(x), laneZ(x)), LANE_YAW, 0.0f));
		}
		String name = YumeConfig.get().cast == null ? "" : YumeConfig.get().cast.sheepRider;
		rider = track(Kit.mannequin(level, name, at(0.5 + START_X[0], hopY(START_X[0]), laneZ(START_X[0])), LANE_YAW));
		rider.startRiding(sheep[0], true, false);
		spawnCamera(at(0.5 - 1.5, 1.8, 0.5 + 5.0), 180.0f, 2.0f);
	}

	@Override
	void step(int t, double p) {
		boolean riderAir = false;
		for (int k = 0; k < COUNT; k++) {
			double x = xAt(k, t);
			double y = hopY(x);
			Sheep s = sheep[k];
			s.snapTo(o.getX() + 0.5 + x, o.getY() + y, o.getZ() + laneZ(x), LANE_YAW, (float) (18.0 * x / JUMP_HALF * (y > 0 ? 1 : 0)));
			Kit.face(s, LANE_YAW, s.getXRot());
			Vec3 pos = s.position();
			if (lastX[k] < -JUMP_HALF && x >= -JUMP_HALF && x < 0) {
				Kit.sound(level, pos, SoundEvents.SHEEP_AMBIENT, SoundSource.NEUTRAL, k == 0 ? 1.4f : 1.0f,
						k == 0 ? 1.25f : 0.95f + 0.1f * k);
			}
			if (lastX[k] < JUMP_HALF && x >= JUMP_HALF) {
				Kit.sound(level, pos, SoundEvents.WOOL_STEP, SoundSource.NEUTRAL, 1.0f, 0.9f);
			}
			lastX[k] = x;
			if (k == 0) {
				riderAir = y > 0;
			}
		}
		// an extra bleat now and then from a sheep that is not jumping
		if (t % sc(44) == sc(22)) {
			Sheep s = sheep[1 + (t / sc(44)) % (COUNT - 1)];
			Kit.sound(level, s.position(), SoundEvents.SHEEP_AMBIENT, SoundSource.NEUTRAL, 0.8f, 0.85f + 0.3f * (float) ((t % 7) / 7.0));
		}
		// the rider is thrilled
		int every = riderAir ? 2 : 4;
		if (t % every == 0) {
			rider.swing(((t / every) & 1) == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
		}
		Kit.face(rider, LANE_YAW, (float) (-8.0 + 14.0 * Math.sin(t * 0.55)));
		// slow pan from the side
		double e = Kit.ease(p);
		cam(at(0.5 - 1.5 + 3.0 * e, 1.8, 0.5 + 5.0), 180.0f, 2.0f);
	}
}
