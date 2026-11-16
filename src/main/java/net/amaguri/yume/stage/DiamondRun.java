package net.amaguri.yume.stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;

import net.amaguri.yume.core.YumeConfig;

/** Small cave: the miner digs a ceiling diamond, a creeper drops through the hole, swells, and goes off (visuals only). */
final class DiamondRun extends BaseRun {
	/** Ore block (relative) in the ceiling. */
	private static final int ORE_X = 0, ORE_Y = 3, ORE_Z = -1;
	private static final int SWELL_TICKS = 28;
	private static final double GRAVITY = 0.08;

	private Mannequin miner;
	private Creeper creeper;
	private final int breakAt;
	private final int igniteAt;
	private final int boomAt;
	private int fallStart = -1;
	private boolean landed;
	private int perchedAt = -1;
	/** Height of the miner's head: the creeper lands here first. */
	private static final double HEAD = 1.9;
	private boolean exploded;
	private boolean ignited;
	private boolean broken;
	private double minerX = 0.5;

	DiamondRun(ServerLevel level, BlockPos origin, int dur) {
		super(level, origin, dur);
		int br = (int) Math.round(dur * 0.50);
		int fall = 10;
		int ig = (int) Math.round(dur * 0.62);
		ig = Math.min(ig, dur - SWELL_TICKS - 24);
		ig = Math.max(ig, br + fall + 6);
		this.breakAt = br;
		this.igniteAt = ig;
		this.boomAt = ig + SWELL_TICKS;
	}

	@Override
	int[] bounds() {
		return new int[]{-6, -2, -5, 6, 8, 7};
	}

	@Override
	void layout(Plan plan) {
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();
		// solid rock around everything, with a little variety
		for (int x = -6; x <= 6; x++) {
			for (int y = -2; y <= 8; y++) {
				for (int z = -5; z <= 7; z++) {
					int h = Kit.hash(x * 7 + y, z * 5 + y) % 9;
					var b = h == 0 ? Blocks.COBBLESTONE : h == 1 ? Blocks.DEEPSLATE : Blocks.STONE;
					plan.set(ox + x, oy + y, oz + z, b.defaultBlockState());
				}
			}
		}
		// the room: x -5..5, y 0..2, z -3..5
		plan.fill(ox - 5, oy, oz - 3, ox + 5, oy + 2, oz + 5, Blocks.AIR.defaultBlockState());
		// closed hollow above the ore (the room has a solid 5-thick roof; the creeper waits inside)
		plan.fill(ox + ORE_X, oy + ORE_Y + 1, oz + ORE_Z, ox + ORE_X, oy + ORE_Y + 2, oz + ORE_Z, Blocks.AIR.defaultBlockState());
		plan.set(ox + ORE_X, oy + ORE_Y, oz + ORE_Z, Blocks.DIAMOND_ORE.defaultBlockState());
		// lamps in the ceiling and invisible light
		BlockState lamp = Blocks.GLOWSTONE.defaultBlockState();
		plan.set(ox - 4, oy + 3, oz + 1, lamp);
		plan.set(ox + 4, oy + 3, oz + 1, lamp);
		plan.set(ox - 3, oy + 3, oz - 2, lamp);
		plan.set(ox + 3, oy + 3, oz + 3, lamp);
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13);
		plan.set(ox - 3, oy + 2, oz, light);
		plan.set(ox + 3, oy + 2, oz, light);
		plan.set(ox, oy + 2, oz + 3, light);
		plan.set(ox, oy + 2, oz - 2, light);
	}

	@Override
	void populate() {
		String name = YumeConfig.get().cast == null ? "" : YumeConfig.get().cast.miner;
		miner = track(Kit.mannequin(level, name, at(minerX, 0, -0.5), 0.0f));
		miner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
		Kit.face(miner, 0.0f, -70.0f);
		CompoundTag nbt = new CompoundTag();
		nbt.putShort("Fuse", (short) 1000); // never really explodes
		creeper = track(Kit.spawn(level, EntityTypes.CREEPER, nbt, at(ORE_X + 0.5, ORE_Y + 1, ORE_Z + 0.5), -90.0f, 0.0f));
		spawnCamera(at(0.5, 1.0, 3.2), 180.0f, -20.0f);
	}

	private BlockPos orePos() {
		return o.offset(ORE_X, ORE_Y, ORE_Z);
	}

	@Override
	void step(int t, double p) {
		int mineFrom = Math.max(2, (int) (dur * 0.04));
		Vec3 oreFace = at(ORE_X + 0.5, ORE_Y - 0.1, ORE_Z + 0.5);

		// ---- mining ----
		if (t >= mineFrom && t < breakAt) {
			int every = Math.max(3, sc(5));
			if ((t - mineFrom) % every == 0) {
				miner.swing(InteractionHand.MAIN_HAND);
				Kit.sound(level, oreFace, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0f, 0.8f + 0.05f * ((t / every) % 5));
				Kit.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIAMOND_ORE.defaultBlockState()),
						oreFace, 6, 0.25, 0.05, 0.25, 0.05);
				int stage = (int) Math.min(9, 10.0 * (t - mineFrom) / Math.max(1, breakAt - mineFrom));
				level.destroyBlockProgress(miner.getId(), orePos(), stage);
			}
		}
		if (t == breakAt && !broken) {
			broken = true;
			level.destroyBlockProgress(miner.getId(), orePos(), -1);
			level.setBlock(orePos(), Blocks.AIR.defaultBlockState(), Kit.FLAGS);
			Kit.sound(level, oreFace, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2f, 0.9f);
			Kit.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIAMOND_ORE.defaultBlockState()),
					oreFace, 40, 0.3, 0.1, 0.3, 0.1);
			fallStart = t;
		}

		// ---- the creeper falls through the hole onto the miner's head, then slides off ----
		if (broken && !landed && creeper != null && !creeper.isRemoved()) {
			double cx = o.getX() + ORE_X + 0.5;
			double cz = o.getZ() + ORE_Z + 0.5;
			if (perchedAt < 0) {
				int n = t - fallStart + 1;
				double y = Math.max(HEAD, (ORE_Y + 1) - 0.5 * GRAVITY * n * n);
				creeper.snapTo(cx, o.getY() + y, cz, 0.0f, 0.0f);
				Kit.face(creeper, 0.0f, 0.0f);
				if (y <= HEAD) {
					perchedAt = t;
					Vec3 hit = creeper.position();
					Kit.sound(level, hit, SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 1.5f, 0.6f);
					Kit.sound(level, hit, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5f, 0.5f);
					Kit.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
							hit, 25, 0.5, 0.05, 0.5, 0.05);
					Kit.particles(level, ParticleTypes.CLOUD, hit, 6, 0.4, 0.05, 0.4, 0.02);
				}
			} else {
				int k = t - perchedAt - 4;
				if (k >= 0) {
					double u = Math.min(1.0, k / 6.0);
					creeper.snapTo(cx + 0.8 * u, o.getY() + HEAD * (1.0 - u * u), cz, 0.0f, 0.0f);
					if (u >= 1.0) {
						landed = true;
						Kit.sound(level, creeper.position(), SoundEvents.STONE_STEP, SoundSource.HOSTILE, 1.0f, 0.7f);
					}
				}
			}
		}
		// the miner stumbles back and stares at it
		if (perchedAt >= 0 && !exploded) {
			double target = -0.6;
			if (minerX > target) {
				minerX = Math.max(target, minerX - 0.1);
				miner.snapTo(o.getX() + minerX, o.getY(), o.getZ() - 0.5, 0.0f, 8.0f);
			}
			float shake = ignited ? (float) (7.0 * Math.sin(t * 2.3)) : 0.0f;
			Kit.face(miner, shake, 8.0f);
		}

		// ---- fuse ----
		if (t == igniteAt && creeper != null && !creeper.isRemoved()) {
			ignited = true;
			creeper.setSwellDir(1);
			Kit.sound(level, creeper.position(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.4f, 0.5f);
		}

		// ---- boom (sound and particles only, no block is touched) ----
		if (t == boomAt && !exploded) {
			exploded = true;
			Vec3 c = creeper != null && !creeper.isRemoved() ? creeper.position() : at(ORE_X + 0.5, 0, ORE_Z + 0.5);
			if (creeper != null) {
				creeper.discard();
				forget(creeper);
			}
			Vec3 mid = c.add(0, 0.8, 0);
			Kit.sound(level, mid, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.9f);
			Kit.particles(level, ParticleTypes.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0);
			Kit.particles(level, ParticleTypes.EXPLOSION, mid, 12, 0.8, 0.5, 0.8, 0.0);
			Kit.particles(level, ParticleTypes.LARGE_SMOKE, mid, 40, 0.8, 0.6, 0.8, 0.05);
			miner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			miner.setInvulnerable(false);
			miner.setSilent(false);
			miner.kill(level);
		}
		if (exploded && t > boomAt) {
			if (t - boomAt < 24 && !miner.isRemoved()) {
				Kit.particles(level, ParticleTypes.SMOKE, miner.position().add(0, 0.4, 0), 3, 0.4, 0.3, 0.4, 0.02);
			}
		}

		// ---- camera: slow push-in, shake at the blast ----
		double push = Kit.ease(p);
		double sx = 0, sy = 0;
		if (exploded && t - boomAt < 10) {
			double k = 0.18 * (1.0 - (t - boomAt) / 10.0);
			sx = Math.sin(t * 7.1) * k;
			sy = Math.cos(t * 5.3) * k;
		}
		cam(at(0.5 + sx, 1.0 + sy, 3.2 - 0.3 * push), 180.0f, -20.0f);
	}
}
