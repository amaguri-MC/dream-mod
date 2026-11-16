package net.amaguri.yume.stage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible entity a spectator camera can be attached to with ServerPlayer.setCamera. The client must see the world
 * from exactly pos with exactly yaw/pitch. OWNER: worker "stage". Signatures are a CONTRACT (director uses them too).
 *
 * <p>Implemented as an item display with no item: it is sent to clients, draws nothing, has no hitbox, no gravity,
 * and its position/rotation changes are interpolated on the client (teleport_duration).
 */
public final class CameraAnchor {
	/** Client-side interpolation length in ticks for position/rotation changes. */
	private static final int INTERPOLATION_TICKS = 3;

	private CameraAnchor() {
	}

	public static Entity spawn(ServerLevel level, Vec3 pos, float yaw, float pitch) {
		if (level == null || pos == null) {
			return null;
		}
		try {
			CompoundTag nbt = new CompoundTag();
			nbt.putInt("teleport_duration", INTERPOLATION_TICKS);
			return Kit.spawn(level, EntityTypes.ITEM_DISPLAY, nbt, pos, yaw, pitch);
		} catch (RuntimeException e) {
			net.amaguri.yume.YumeMod.LOG.error("[yume] could not spawn camera anchor", e);
			return null;
		}
	}

	/** Move/turn an anchor smoothly (for pans). */
	public static void move(Entity anchor, Vec3 pos, float yaw, float pitch) {
		if (anchor == null || pos == null || anchor.isRemoved()) {
			return;
		}
		anchor.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
	}
}
