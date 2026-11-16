package net.amaguri.yume.core;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.schedule.RevealStage;

/**
 * Progress saved next to the world as yume_state.json. Call {@link #save(MinecraftServer)} after changes.
 * CONTRACT FILE (owned by the lead). Workers read/write the fields but must not edit the class.
 */
public final class YumeState {
	/** Where a player was before a dream moved them; restored afterwards (also after a crash/rejoin). */
	public static final class Saved {
		public String dimension;
		public double x, y, z;
		public float yaw, pitch;
		/** GameType name, e.g. "survival". */
		public String gameMode;
	}

	/** Number of dreams already seen. The next dream is for day {@code dreamsSeen + 1}. */
	public int dreamsSeen = 0;
	/** null = automatic from the schedule. Otherwise a forced stage (set by /yume stage). */
	public RevealStage stageOverride = null;
	/** Set once a dreamer breaks into the white room (or /yume finale). Everything is unhidden. */
	public boolean finale = false;
	/** Current day/night speed multiplier (1 = vanilla). */
	public int timeSpeed = 1;
	/** Players currently inside a dream, by UUID string, with where to put them back. */
	public Map<String, Saved> inDream = new LinkedHashMap<>();
	/** Operators (UUID strings) who chose to see the other group's chat. */
	public Map<String, Boolean> spy = new LinkedHashMap<>();

	private static YumeState current = new YumeState();
	private static Path file;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static YumeState get() {
		return current;
	}

	public static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve("yume_state.json");
		current = new YumeState();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				YumeState s = GSON.fromJson(r, YumeState.class);
				if (s != null) current = s;
			} catch (Exception e) {
				YumeMod.LOG.error("[yume] yume_state.json is broken, starting fresh", e);
			}
		}
		if (current.inDream == null) current.inDream = new LinkedHashMap<>();
		if (current.spy == null) current.spy = new LinkedHashMap<>();
		if (current.timeSpeed < 1) current.timeSpeed = 1;
	}

	public static void save(MinecraftServer server) {
		if (file == null) return;
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(current, w);
		} catch (Exception e) {
			YumeMod.LOG.error("[yume] could not write yume_state.json", e);
		}
	}
}
