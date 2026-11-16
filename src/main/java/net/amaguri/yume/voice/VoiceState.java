package net.amaguri.yume.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.amaguri.yume.core.Groups;

/**
 * Plain state shared between the voice plugin and the rest of the mod. This class must never reference a Simple
 * Voice Chat class, so that it can be read when Simple Voice Chat is not installed.
 */
public final class VoiceState {
	/** True once the Simple Voice Chat plugin was initialized (Simple Voice Chat is installed and found us). */
	public static volatile boolean active = false;
	/** The groups are being kept apart (everything but the finale). */
	static volatile boolean separated = true;
	/** A dream with voice is running: both groups may hear each other. */
	static volatile boolean window = false;
	/** Side of every online player, refreshed on the server thread; read from the voice threads. */
	static final Map<UUID, Groups.Side> sides = new ConcurrentHashMap<>();

	private VoiceState() {
	}
}
