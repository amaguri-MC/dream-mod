package net.amaguri.yume.stage;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** One running staged dream (built in the dream dimension). CONTRACT FILE. */
public interface SceneRun {
	/** Level the scene lives in (the dream dimension). */
	ServerLevel level();

	/** Entity the viewers' camera is attached to (may move for pans). Viewers are teleported next to it. */
	Entity cameraAnchor();

	/** Entities the client should draw as pure white silhouettes (e.g. the HELP figure). */
	List<Entity> whiteSilhouettes();

	/** Called every server tick, t = 0 .. durationTicks-1. */
	void tick(int t);

	/** Remove every entity the scene spawned. Blocks may stay (they are rebuilt next time). Must be safe to call twice. */
	void cleanup();
}
