package net.amaguri.yume.stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.amaguri.yume.net.BellyPayload;

import net.amaguri.yume.core.YumeConfig;

/** A picnic: the balloon cast member eats everything, swells, and drifts up into the sky. */
final class BalloonRun extends BaseRun {
	private static final double MAX_SCALE = 3.0;
	private static final double LIFT_HEIGHT = 46.0;
	private static final double EAT_END = 0.60;
	private static final double WOBBLE_END = 0.68;
	private static final int BITES = 12;

	private static final Item[] MENU = {
			Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.COOKIE, Items.PUMPKIN_PIE, Items.MELON_SLICE,
			Items.COOKED_CHICKEN, Items.CAKE, Items.BAKED_POTATO, Items.GOLDEN_APPLE, Items.COOKED_PORKCHOP, Items.CARROT
	};

	private Mannequin eater;
	private boolean burped;
	private boolean lifted;
	private boolean wobbled;

	BalloonRun(ServerLevel level, BlockPos origin, int dur) {
		super(level, origin, dur);
	}

	@Override
	int[] bounds() {
		return new int[]{-22, -10, -18, 22, 4, 18};
	}

	@Override
	void layout(Plan plan) {
		int cx = o.getX(), cy = o.getY(), cz = o.getZ();
		Kit.island(plan, cx, cy, cz, 20, 16, 8);
		for (int x = -19; x <= 19; x++) {
			for (int z = -15; z <= 15; z++) {
				if (plan.get(cx + x, cy - 1, cz + z) == null || (x >= -3 && x <= 3 && z >= -2 && z <= 4)) {
					continue;
				}
				int h = Kit.hash(x, z) % 19;
				if (h == 0) {
					plan.set(cx + x, cy, cz + z, Blocks.DANDELION.defaultBlockState());
				} else if (h == 1) {
					plan.set(cx + x, cy, cz + z, Blocks.POPPY.defaultBlockState());
				} else if (h < 4) {
					plan.set(cx + x, cy, cz + z, Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}
		// low picnic table x -2..2, z 2..3, with a red cloth and cakes
		plan.fill(cx - 2, cy, cz + 2, cx + 2, cy, cz + 3, Blocks.OAK_PLANKS.defaultBlockState());
		var cloth = Blocks.CARPET.red().defaultBlockState();
		plan.fill(cx - 2, cy + 1, cz + 2, cx + 2, cy + 1, cz + 2, cloth);
		plan.set(cx - 1, cy + 1, cz + 3, cloth);
		plan.set(cx + 1, cy + 1, cz + 3, cloth);
		var cake = Blocks.CAKE.defaultBlockState();
		plan.set(cx - 2, cy + 1, cz + 3, cake);
		plan.set(cx, cy + 1, cz + 3, cake);
		plan.set(cx + 2, cy + 1, cz + 3, cake);
	}

	@Override
	void populate() {
		// food laid out on the cloth (display entities, not item drops)
		Item[] food = {Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.COOKIE, Items.PUMPKIN_PIE, Items.MELON_SLICE, Items.GOLDEN_APPLE};
		double[][] spots = {{-2, 2}, {-1, 2}, {0, 2}, {1, 2}, {2, 2}, {-1, 3}, {1, 3}};
		for (int i = 0; i < food.length; i++) {
			CompoundTag item = new CompoundTag();
			item.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(food[i]).toString());
			item.putInt("count", 1);
			CompoundTag nbt = new CompoundTag();
			nbt.put("item", item);
			nbt.putString("item_display", "ground");
			nbt.putString("billboard", "vertical");
			track(Kit.spawn(level, EntityTypes.ITEM_DISPLAY, nbt, at(spots[i][0] + 0.5, 1.3, spots[i][1] + 0.5), 0.0f, 0.0f));
		}
		String name = YumeConfig.get().cast == null ? "" : YumeConfig.get().cast.balloon;
		eater = track(Kit.mannequin(level, name, at(0.5, 0, 0.5), 0.0f));
		eater.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MENU[0]));
		spawnCamera(at(-2.4, 1.3, -1.6), -54.0f, 3.0f);
	}

	/** Belly roundness 0..1: grows bite by bite while eating, then stays at 1. */
	private double bellyAt(int t, double p) {
		if (p >= EAT_END) {
			return 1.0;
		}
		int bite = Math.max(1, Math.round((float) (dur * EAT_END) / BITES));
		double done = t / bite + Kit.ease((t % bite) / (double) bite);
		return Math.min(1.0, done / BITES);
	}

	private void sendBelly(float belly) {
		if (eater == null) {
			return;
		}
		BellyPayload payload = new BellyPayload(eater.getId(), belly);
		for (ServerPlayer pl : level.players()) {
			ServerPlayNetworking.send(pl, payload);
		}
	}

	@Override
	void onCleanup() {
		sendBelly(0.0f);
	}

	@Override
	void step(int t, double p) {
		final double scale = 1.0; // the body keeps its size; only the belly swells (client side)
		if (t % 2 == 0 || p >= EAT_END) {
			sendBelly((float) bellyAt(t, p));
		}

		// ---- eating ----
		if (p < EAT_END) {
			int bite = Math.max(1, Math.round((float) (dur * EAT_END) / BITES));
			if (t % bite == 0) {
				int i = t / bite;
				Item food = MENU[i % MENU.length];
				eater.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(food));
				eater.swing(InteractionHand.MAIN_HAND);
				Vec3 mouth = eater.position().add(0, 1.45 * scale, 0.28 * scale);
				Kit.sound(level, mouth, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0f, 0.9f + 0.05f * (i % 5));
				Kit.particles(level, new ItemParticleOption(ParticleTypes.ITEM, food), mouth, 10, 0.12 * scale, 0.08 * scale, 0.12 * scale, 0.06);
			} else if (t % bite == Math.max(1, bite / 2)) {
				Vec3 mouth = eater.position().add(0, 1.45 * scale, 0.28 * scale);
				Kit.sound(level, mouth, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.7f, 1.1f);
				Kit.particles(level, new ItemParticleOption(ParticleTypes.ITEM, MENU[(t / bite) % MENU.length]), mouth, 5,
						0.1 * scale, 0.06 * scale, 0.1 * scale, 0.05);
			}
		} else if (p < WOBBLE_END) {
			if (!wobbled) {
				wobbled = true;
				eater.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				Kit.sound(level, eater.position().add(0, 2.0, 0), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.PLAYERS, 1.5f, 0.7f);
			}
			if (!burped && p > EAT_END + (WOBBLE_END - EAT_END) * 0.5) {
				burped = true;
				Kit.sound(level, eater.position().add(0, 2.5, 0), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.6f, 0.6f);
			}
		}

		// ---- floating away ----
		double lift = 0.0;
		double drift = 0.0;
		float yaw = 0.0f;
		if (p >= WOBBLE_END) {
			double q = (p - WOBBLE_END) / (1.0 - WOBBLE_END);
			lift = LIFT_HEIGHT * q * q;
			drift = Math.sin(q * 5.0) * 1.2 * q;
			yaw = (float) (120.0 * q);
			if (!lifted) {
				lifted = true;
				Vec3 feet = eater.position();
				Kit.sound(level, feet, SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.6f, 0.6f);
				Kit.particles(level, ParticleTypes.CLOUD, feet, 25, 0.8, 0.1, 0.8, 0.05);
			}
		}
		eater.snapTo(o.getX() + 0.5 + drift, o.getY() + lift, o.getZ() + 0.5, yaw, 0.0f);
		Kit.face(eater, yaw, 0.0f);

		// ---- camera ----
		// front-diagonal view (he faces -x): the belly bulges toward the camera.
		// While he floats up the camera rises with him (a bit slower) and backs off, so the belly stays in view.
		double rise = Math.max(0.0, lift - 1.0) * 0.8;
		double back = Math.min(3.0, lift * 0.25);
		Vec3 cpos = at(-2.4 - back * 0.8, 1.3 + rise, -1.6 - back * 0.6);
		double aim = Kit.ease((p - 0.62) / 0.2);
		double tx = eater.getX() - cpos.x;
		double tz = eater.getZ() - cpos.z;
		double horiz = Math.max(0.5, Math.sqrt(tx * tx + tz * tz));
		float camYaw = (float) Math.toDegrees(Math.atan2(-tx, tz));
		double targetY = eater.getY() + 0.9 * scale;
		double lookUp = Math.toDegrees(Math.atan2(targetY - cpos.y, horiz));
		float pitch = (float) (3.0 + (-lookUp - 3.0) * aim);
		cam(cpos, camYaw, Math.max(-80.0f, Math.min(12.0f, pitch)));
	}
}
