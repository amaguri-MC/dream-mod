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
	private static final double CAM_START_Z = 34.0;
	private static final double CAM_END_Z = 26.0;

	/** Figure stands mid-corridor. */
	private static final double FIG_Z = 20.5;

	private Mannequin figure;

	HelpRun(ServerLevel level, BlockPos origin, int dur) {
		super(level, origin, dur);
	}

	@Override
	int[] bounds() {
		return new int[]{-9, -1, -9, 9, 8, 45};
	}

	@Override
	void layout(Plan plan) {
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();
		BlockState white = Blocks.CONCRETE.white().defaultBlockState();
		plan.fill(ox - 9, oy - 1, oz - 9, ox + 9, oy - 1, oz + 45, white);
		plan.fill(ox - 9, oy + 8, oz - 9, ox + 9, oy + 8, oz + 45, white);
		plan.fill(ox - 9, oy, oz - 9, ox - 9, oy + 7, oz + 45, white);
		plan.fill(ox + 9, oy, oz - 9, ox + 9, oy + 7, oz + 45, white);
		plan.fill(ox - 9, oy, oz - 9, ox + 9, oy + 7, oz - 9, white);
		plan.fill(ox - 9, oy, oz + 45, ox + 9, oy + 7, oz + 45, white);
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
		for (int z = -4; z <= 42; z += 7) {
			for (int x = -6; x <= 6; x += 6) {
				plan.set(ox + x, oy + 4, oz + z, light);
			}
		}
	}

	@Override
	void populate() {
		String name = YumeConfig.get().cast == null ? "" : YumeConfig.get().cast.helpFigure;
		figure = track(Kit.mannequin(level, name, at(0.5, 0, FIG_Z), 180.0f));
		spawnCamera(at(0.5, 1.62, CAM_START_Z), 180.0f, 0.0f);
	}

	@Override
	public List<Entity> whiteSilhouettes() {
		return figure == null ? List.of() : List.of(figure);
	}

	@Override
	void step(int t, double p) {
		double dolly = p;
		double camZ = CAM_START_Z + (CAM_END_Z - CAM_START_Z) * dolly;
		Vec3 cpos = at(0.5, 1.62, camZ);
		cam(cpos, 180.0f, 0.0f);

		// the figure turns, slowly, only in the middle of the dream
		float yaw = (float) (180.0 - 180.0 * Kit.ease((p - 0.2) / 0.6));
		Kit.face(figure, yaw, 0.0f);

		// faint ringing in the ears, nothing else
		if (t % sc(60) == 0) {
			Kit.sound(level, cpos, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.12f, 2.0f);
		}
		if (t % sc(30) == sc(15)) {
			Kit.sound(level, cpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.04f, 2.0f);
		}

		// thin white haze in the air between camera and figure
		double zNear = camZ - 3.0;
		double zFar = Math.max(1.0, camZ - 28.0);
		for (int i = 0; i < 3; i++) {
			double z = zFar + (zNear - zFar) * ((t * 3 + i * 5) % 7) / 6.0;
			Kit.particles(level, ParticleTypes.WHITE_ASH, at(0.5, 3.0, z), 6, 6.0, 2.5, 3.0, 0.0);
		}
		Kit.particles(level, ParticleTypes.CLOUD, at(0.5, 2.5, camZ - 14.0), 1, 6.0, 2.0, 10.0, 0.0);
	}
}
