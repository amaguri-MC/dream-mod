package net.amaguri.yume.isolation;

import net.minecraft.server.MinecraftServer;

/**
 * Keeps the two groups apart (messages, tab list, server list, voice gate), the /yume command, the clock speed.
 * OWNER: worker "isolation". Public signatures are a CONTRACT.
 */
public final class Isolation {
	private Isolation() {
	}

	public static void init() {
	}

	/** Recompute and send RevealPayload to every online player (call after any stage / dream change). */
	public static void sync(MinecraftServer server) {
	}

	/** Give config.giftItems to every online trapped player (quietly). Returns how many players got items. */
	public static int giftLost(MinecraftServer server) {
		return 0;
	}
}
