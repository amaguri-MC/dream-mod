package net.amaguri.yume.director;

import java.util.Collection;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.amaguri.yume.schedule.Scene;

/**
 * Runs dreams: sleep detection, moving players into a dream and back, camera locking, POV, finale.
 * OWNER: worker "director". Public signatures are a CONTRACT: keep them exactly; other modules call them.
 */
public final class DreamDirector {
	private DreamDirector() {
	}

	public static void init() {
	}

	/** Is this player currently inside a dream (spectating a camera)? */
	public static boolean isInDream(ServerPlayer player) {
		return false;
	}

	/** Is any dream session running right now? */
	public static boolean isDreamActive() {
		return false;
	}

	/**
	 * Play the dream of the given day now, as if the dreamers had slept (no sleeping needed).
	 * advance=true: counts as that night (dreamsSeen becomes day, time jumps to morning afterwards).
	 * Returns a short Japanese status line for the command sender.
	 */
	public static String startDay(MinecraftServer server, int day, boolean advance) {
		return "未実装";
	}

	/** Test playback of one scene for the given viewers only. Changes no progress. */
	public static String playTest(MinecraftServer server, Scene scene, Collection<ServerPlayer> viewers) {
		return "未実装";
	}

	/** End every running dream immediately and put everyone back. */
	public static void abortAll(MinecraftServer server) {
	}

	/** Finish the secret: set finale, unhide everything. Called by a dreamer breaking the room or by /yume finale. */
	public static void finale(MinecraftServer server) {
	}
}
