package net.amaguri.yume.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import net.amaguri.yume.YumeMod;

/**
 * Server-side settings in config/yume.json. NEVER put real player names in code or in the
 * repository: they live only in this file on the server (spoiler protection).
 * CONTRACT FILE (owned by the lead). Workers read it but must not edit it.
 */
public final class YumeConfig {
	/** A fixed camera point. dimension like "minecraft:the_end". */
	public static final class Cam {
		public String dimension = "minecraft:the_end";
		public double x, y = 64, z;
		public float yaw, pitch;
	}

	/** An axis-aligned box (inclusive block coords). */
	public static final class Box {
		public String dimension = "minecraft:the_end";
		public int x1, y1, z1, x2, y2, z2;

		public boolean contains(String dim, int x, int y, int z) {
			return dim.equals(dimension)
					&& x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
					&& y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
					&& z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
		}
	}

	/** Player names (MCID) for the dream scenes. */
	public static final class Cast {
		/** Rides a sheep in the sheep dream. */
		public String sheepRider = "";
		/** Mines the ceiling diamond in the diamond dream. */
		public String miner = "";
		/** Swells into a balloon in the balloon dream. */
		public String balloon = "";
		/** Skin used for the white silhouette in the "help" dream (rendered pure white anyway). */
		public String helpFigure = "";
	}

	/** The group that sleeps and dreams (3 players). MCIDs, case-insensitive. */
	public List<String> dreamers = new ArrayList<>();
	/** The trapped group in the white room. MCIDs, case-insensitive. */
	public List<String> lost = new ArrayList<>();
	/** Who may use /yume and the spy feature. MCIDs. */
	public List<String> operators = new ArrayList<>();

	public Cast cast = new Cast();
	/** Length of one dream in seconds. */
	public int dreamSeconds = 10;
	/** Day 4: how long the trapped group watches the POV of {@link #povTarget}. */
	public int povSeconds = 10;
	/** MCID whose eyes the trapped group sees on day 4 (one of the dreamers). */
	public String povTarget = "";
	/** Unlisted stream URL shown (clickable) to the trapped group on day 4. */
	public String streamUrl = "";
	/** Camera points: keys "waterfall" and "room". */
	public Map<String, Cam> cameras = new LinkedHashMap<>();
	/** The white room. A dreamer breaking any block inside it ends the secret (finale). The trapped group cannot break blocks inside it. */
	public Box room = new Box();
	/** Given to the trapped group when the day-7 dream starts, e.g. "minecraft:oak_sign 8". */
	public List<String> giftItems = new ArrayList<>(List.of("minecraft:oak_sign 8", "minecraft:paper 16", "minecraft:black_wool 32"));
	/** Speeds cycled by right-clicking a clock. Only the day/night clock speeds up, not the tick rate. */
	public List<Integer> timeSpeeds = new ArrayList<>(List.of(1, 2, 4, 8));
	/** Whether all configured dreamers must be online and in bed for a dream (else: all online dreamers). */
	public boolean requireAllDreamers = false;

	private static YumeConfig current = new YumeConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static YumeConfig get() {
		return current;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("yume.json");
	}

	public static void load(MinecraftServer server) {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				YumeConfig c = GSON.fromJson(r, YumeConfig.class);
				if (c != null) {
					current = c;
				}
			} catch (Exception e) {
				YumeMod.LOG.error("[yume] config/yume.json is broken, using defaults", e);
			}
		}
		if (current.cast == null) current.cast = new Cast();
		if (current.room == null) current.room = new Box();
		if (current.cameras == null) current.cameras = new LinkedHashMap<>();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(current, w);
			}
		} catch (IOException e) {
			YumeMod.LOG.error("[yume] could not write config/yume.json", e);
		}
	}
}
