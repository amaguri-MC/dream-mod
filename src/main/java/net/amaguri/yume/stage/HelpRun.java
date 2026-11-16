package net.amaguri.yume.stage;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.amaguri.yume.core.YumeConfig;

/** An all-white corridor; a lone figure far away slowly turns to face the camera, which creeps closer. */
final class HelpRun extends BaseRun {
	/** Small white room: interior x/z -3..3 (7 wide), y 0..3 (4 high). Centre of the room is (0.5, 0.5). */
	private static final double[] FIG = {3.0, 3.0};
	private static final double[] CAM_FROM = {-2.5, -2.5};
	private static final double[] CAM_TO = {0.0, 0.0};
	/** Camera height: the ceiling surface is at y = 4, so keep at least 2 blocks below it. */
	private static final double CAM_Y = 2.0;

	private Mannequin figure;

	HelpRun(ServerLevel level, BlockPos origin, int dur) {
		super(level, origin, dur);
	}

	@Override
	int[] bounds() {
		return new int[]{-4, -1, -4, 4, 4, 4};
	}

	@Override
	void layout(Plan plan) {
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();
		BlockState white = Blocks.CONCRETE.white().defaultBlockState();
		plan.fill(ox - 4, oy - 1, oz - 4, ox + 4, oy - 1, oz + 4, white);
		plan.fill(ox - 4, oy + 4, oz - 4, ox + 4, oy + 4, oz + 4, white);
		plan.fill(ox - 4, oy, oz - 4, ox - 4, oy + 3, oz + 4, white);
		plan.fill(ox + 4, oy, oz - 4, ox + 4, oy + 3, oz + 4, white);
		plan.fill(ox - 4, oy, oz - 4, ox + 4, oy + 3, oz - 4, white);
		plan.fill(ox - 4, oy, oz + 4, ox + 4, oy + 3, oz + 4, white);
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
		plan.set(ox, oy + 2, oz, light);
		plan.set(ox - 2, oy + 2, oz - 2, light);
		plan.set(ox + 2, oy + 2, oz + 2, light);
		plan.set(ox - 2, oy + 2, oz + 2, light);
		plan.set(ox + 2, oy + 2, oz - 2, light);
	}

	@Override
	void populate() {
		String name = YumeConfig.get().cast == null ? "" : YumeConfig.get().cast.helpFigure;
		float yaw = yawTo(FIG[0], FIG[1], CAM_FROM[0], CAM_FROM[1]);
		figure = track(Kit.mannequin(level, name, at(FIG[0], 0, FIG[1]), yaw + 180.0f));
		spawnCamera(at(CAM_FROM[0], CAM_Y, CAM_FROM[1]), yawTo(CAM_FROM[0], CAM_FROM[1], FIG[0], FIG[1]), pitchTo(CAM_FROM[0], CAM_FROM[1]));
	}

	/** Yaw looking from (fx,fz) towards (tx,tz). */
	private static float yawTo(double fx, double fz, double tx, double tz) {
		return (float) Math.toDegrees(Math.atan2(-(tx - fx), tz - fz));
	}

	private static float pitchTo(double cx, double cz) {
		double h = Math.hypot(FIG[0] - cx, FIG[1] - cz);
		return (float) Math.toDegrees(Math.atan2(CAM_Y - 1.2, h));
	}

	@Override
	public List<Entity> whiteSilhouettes() {
		return figure == null ? List.of() : List.of(figure);
	}

	@Override
	void step(int t, double p) {
		double d = Kit.ease(p);
		double cx = CAM_FROM[0] + (CAM_TO[0] - CAM_FROM[0]) * d;
		double cz = CAM_FROM[1] + (CAM_TO[1] - CAM_FROM[1]) * d;
		Vec3 cpos = at(cx, CAM_Y, cz);
		float camYaw = yawTo(cx, cz, FIG[0], FIG[1]);
		cam(cpos, camYaw, pitchTo(cx, cz));

		// the figure turns, slowly, to face the camera
		float toCam = yawTo(FIG[0], FIG[1], cx, cz);
		float yaw = (float) (toCam + 180.0 * (1.0 - Kit.ease((p - 0.2) / 0.6)));
		Kit.face(figure, yaw, 0.0f);

		// faint ringing in the ears, nothing else
		if (t % sc(60) == 0) {
			Kit.sound(level, cpos, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.12f, 2.0f);
		}
		if (t % sc(30) == sc(15)) {
			Kit.sound(level, cpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.04f, 2.0f);
		}
		// thin white haze
		Kit.particles(level, ParticleTypes.WHITE_ASH, at(0.5, 1.8, 0.5), 3, 2.5, 1.0, 2.5, 0.0);
	}
}
