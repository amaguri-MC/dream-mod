package net.amaguri.yume.director;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.Entity;

/**
 * Who is currently pinned to a camera. Read by the mixins (one of them runs on the network thread),
 * so the collections are concurrent. Written only by {@link DreamDirector} on the server thread.
 */
public final class DreamLock {
	private static final Map<UUID, Entity> CAMERA = new ConcurrentHashMap<>();
	private static final Set<UUID> DREAMING = ConcurrentHashMap.newKeySet();

	private DreamLock() {
	}

	static void lock(UUID id, Entity camera) {
		DREAMING.add(id);
		if (camera != null) CAMERA.put(id, camera);
	}

	static void release(UUID id) {
		DREAMING.remove(id);
		CAMERA.remove(id);
	}

	static void clear() {
		DREAMING.clear();
		CAMERA.clear();
	}

	/** True while the player is pinned (no damage, no spectator teleport). */
	public static boolean isDreaming(UUID id) {
		return DREAMING.contains(id);
	}

	/** The entity this player must keep watching, or null when free (also null when that entity is gone). */
	public static Entity camera(UUID id) {
		Entity e = CAMERA.get(id);
		return e == null || e.isRemoved() ? null : e;
	}
}
