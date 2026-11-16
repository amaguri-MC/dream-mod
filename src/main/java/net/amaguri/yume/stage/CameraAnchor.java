package net.amaguri.yume.stage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible entity a spectator camera can be attached to with ServerPlayer.setCamera. The client must see the world
 * from exactly pos with exactly yaw/pitch. OWNER: worker "stage". Signatures are a CONTRACT (director uses them too).
 */
public final class CameraAnchor {
	private CameraAnchor() {
	}

	public static Entity spawn(ServerLevel level, Vec3 pos, float yaw, float pitch) {
		return null;
	}

	/** Move/turn an anchor smoothly (for pans). */
	public static void move(Entity anchor, Vec3 pos, float yaw, float pitch) {
	}
}
