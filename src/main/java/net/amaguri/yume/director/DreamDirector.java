package net.amaguri.yume.director;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.core.Groups;
import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.YumeState;
import net.amaguri.yume.isolation.Isolation;
import net.amaguri.yume.net.DreamEndPayload;
import net.amaguri.yume.net.DreamStartPayload;
import net.amaguri.yume.schedule.DreamFilter;
import net.amaguri.yume.schedule.DreamPlan;
import net.amaguri.yume.schedule.DreamSchedule;
import net.amaguri.yume.schedule.RevealStage;
import net.amaguri.yume.schedule.Scene;
import net.amaguri.yume.stage.CameraAnchor;
import net.amaguri.yume.stage.DreamStages;
import net.amaguri.yume.stage.SceneRun;

/**
 * Runs dreams: sleep detection, moving players into a dream and back, camera locking, POV, finale.
 * OWNER: worker "director". Public signatures are a CONTRACT: keep them exactly; other modules call them.
 *
 * Everything here runs on the server thread. Players are always put back through {@link #restorePlayer}; the
 * place to put them back is written to {@link YumeState#inDream} (and saved) BEFORE they are moved.
 */
public final class DreamDirector {
	private enum Phase { MAIN, POV }

	private static final class Viewer {
		final UUID id;
		Entity cam;
		int age;

		Viewer(UUID id, Entity cam) {
			this.id = id;
			this.cam = cam;
		}
	}

	private static final class Session {
		final int day;
		final String label;
		final boolean advance;
		final boolean test;
		final Scene scene;
		final DreamPlan plan;
		Phase phase = Phase.MAIN;
		int t;
		int dur;
		SceneRun run;
		/** Anchor spawned by the director itself (waterfall / room). Staged scenes own theirs. */
		Entity anchor;
		/** What the viewers watch right now (scene anchor, own anchor, or the POV target player). */
		Entity cam;
		boolean povAfter;
		int errors;
		int sceneErrors;
		final Map<UUID, Viewer> viewers = new LinkedHashMap<>();
		final Set<UUID> participants = new HashSet<>();

		Session(int day, String label, boolean advance, boolean test, Scene scene, DreamPlan plan) {
			this.day = day;
			this.label = label;
			this.advance = advance;
			this.test = test;
			this.scene = scene;
			this.plan = plan;
		}
	}

	private static Session session;
	/** Chunks of the fixed cameras (waterfall / room, plus a ring of one chunk) kept force-loaded at all times. */
	private record ChunkRef(String dim, int cx, int cz) {
	}

	private static final Set<ChunkRef> forcedCamChunks = new HashSet<>();
	/** DreamEndPayload goes out a little after the walk back, so the black screen covers the return loading. */
	private static final Map<UUID, Integer> pendingEnd = new LinkedHashMap<>();
	private static final int END_DELAY_TICKS = 20;
	private static final int[] CAMERA_RESEND_AGES = { 5, 10, 20, 40 };
	private static int tickCounter;
	/** After a start attempt the sleep check rests for a moment, so a failing start cannot repeat every tick. */
	private static int sleepCooldownUntil;

	private DreamDirector() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			session = null;
			tickCounter = 0;
			sleepCooldownUntil = 0;
			forcedCamChunks.clear();
			pendingEnd.clear();
			DreamLock.clear();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> safe("camera-chunks", () -> refreshCameraChunks(server)));
		ServerTickEvents.END_SERVER_TICK.register(DreamDirector::onTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> safe("join", () -> restoreStale(server, handler.getPlayer())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> safe("leave", () -> onLeave(server, handler.getPlayer())));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> safe("stopping", () -> {
			abortAll(server);
			flushPendingEnds(server);
			YumeState.save(server);
		}));
		// the trapped group cannot break the white room (until the secret is over); no automatic finale
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			try {
				if (!(player instanceof ServerPlayer sp)) return true;
				if (Groups.stage() == RevealStage.FINALE) return true;
				if (Groups.sideOf(sp) != Groups.Side.LOST) return true;
				return !inRoom(level, pos);
			} catch (Throwable t) {
				return true;
			}
		});
	}

	// ------------------------------------------------------------------ public contract

	/** Is this player currently inside a dream (spectating a camera)? */
	public static boolean isInDream(ServerPlayer player) {
		Session s = session;
		return s != null && player != null && s.viewers.containsKey(player.getUUID());
	}

	/**
	 * True only while a real (advance=true) dream whose plan.voice is set is playing: not tests, not POV,
	 * not with a forced stage.
	 */
	public static boolean voiceOpen() {
		Session s = session;
		return s != null && !s.test && s.advance && s.phase == Phase.MAIN && s.plan != null && s.plan.voice()
				&& YumeState.get().stageOverride == null;
	}

	/** Is any dream session running right now? */
	public static boolean isDreamActive() {
		return session != null;
	}

	/**
	 * Play the dream of the given day now, as if the dreamers had slept (no sleeping needed).
	 * advance=true: counts as that night (dreamsSeen becomes day, time jumps to morning afterwards).
	 * Returns a short Japanese status line for the command sender.
	 */
	public static String startDay(MinecraftServer server, int day, boolean advance) {
		try {
			if (day < 1) return "日数は1以上で指定してください";
			return beginDay(server, day, advance);
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] startDay failed", t);
			safe("abort-after-error", () -> abortAll(server));
			return "夢の開始に失敗しました（ログ参照）";
		}
	}

	/** Test playback of one scene for the given viewers only. Changes no progress. */
	public static String playTest(MinecraftServer server, Scene scene, Collection<ServerPlayer> viewers) {
		try {
			if (session != null) return "夢の再生中です";
			if (scene == null || scene == Scene.NONE) return "再生できるシーンではありません";
			if (viewers == null || viewers.isEmpty()) return "視聴者がいません";
			List<ServerPlayer> list = new ArrayList<>();
			for (ServerPlayer p : viewers) {
				if (p != null && !p.hasDisconnected()) list.add(p);
			}
			if (list.isEmpty()) return "視聴者がいません";

			Session s = new Session(0, "テスト(" + scene.name() + ")", false, true, scene, testPlan(scene));
			s.dur = dreamTicks();
			if (scene == Scene.POV_STREAM) {
				session = s;
				return beginPov(server, s, list);
			}
			String err = prepareScene(server, s);
			if (err != null) {
				warnOperators(server, err);
				return err;
			}
			session = s;
			startMain(server, s, list);
			if (session == s && s.viewers.isEmpty()) {
				finishPhase(server, s, false);
				return "視聴者を移動できませんでした";
			}
			return "テスト再生: " + scene.name() + "（" + s.viewers.size() + "人）";
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] playTest failed", t);
			safe("abort-after-error", () -> abortAll(server));
			return "テスト再生に失敗しました（ログ参照）";
		}
	}

	/** End every running dream immediately and put everyone back. */
	public static void abortAll(MinecraftServer server) {
		Session s = session;
		if (s != null) {
			safe("abort-release", () -> releaseViewers(server, s));
			safe("abort-cleanup", () -> cleanupScene(s));
			session = null;
			notifyOperators(server, s, "終了");
		}
		safe("abort-sweep", () -> sweepStale(server));
		DreamLock.clear();
		safe("abort-sync", () -> Isolation.sync(server));
		safe("abort-save", () -> YumeState.save(server));
	}

	/**
	 * Keep the chunks around config.cameras waterfall/room force-loaded (always, not only during a dream).
	 * Loads the new ones and releases the ones of an old camera position. Safe to call any time.
	 */
	public static void refreshCameraChunks(MinecraftServer server) {
		try {
			Set<ChunkRef> want = new HashSet<>();
			Map<String, YumeConfig.Cam> cams = YumeConfig.get().cameras;
			if (cams != null) {
				for (String key : new String[] { "waterfall", "room" }) {
					YumeConfig.Cam c = cams.get(key);
					if (c == null || levelOf(server, c.dimension) == null) continue;
					int cx = ((int) Math.floor(c.x)) >> 4;
					int cz = ((int) Math.floor(c.z)) >> 4;
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) want.add(new ChunkRef(c.dimension, cx + dx, cz + dz));
					}
				}
			}
			for (ChunkRef r : new ArrayList<>(forcedCamChunks)) {
				if (want.contains(r)) continue;
				ServerLevel lvl = levelOf(server, r.dim());
				if (lvl != null) lvl.setChunkForced(r.cx(), r.cz(), false);
				forcedCamChunks.remove(r);
			}
			for (ChunkRef r : want) {
				if (forcedCamChunks.contains(r)) continue;
				ServerLevel lvl = levelOf(server, r.dim());
				if (lvl == null) continue;
				lvl.setChunkForced(r.cx(), r.cz(), true);
				lvl.getChunk(r.cx(), r.cz());
				forcedCamChunks.add(r);
			}
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] camera chunk refresh failed", t);
		}
	}

	/** Finish the secret: set finale, unhide everything. Called by a dreamer breaking the room or by /yume finale. */
	public static void finale(MinecraftServer server) {
		YumeState.get().finale = true;
		YumeState.save(server);
		safe("finale-sync", () -> Isolation.sync(server));
	}

	// ------------------------------------------------------------------ tick

	private static void onTick(MinecraftServer server) {
		try {
			tickCounter++;
			Session s = session;
			if (s == null) {
				if (tickCounter >= sleepCooldownUntil && sleepersReady(server)) {
					sleepCooldownUntil = tickCounter + 60;
					int day = YumeState.get().dreamsSeen + 1;
					try {
						beginDay(server, day, true);
					} catch (Throwable t) {
						YumeMod.LOG.error("[yume] could not start the dream", t);
						safe("abort-after-error", () -> abortAll(server));
					}
				}
			} else {
				try {
					tickSession(server, s);
				} catch (Throwable t) {
					YumeMod.LOG.error("[yume] dream tick failed", t);
					if (++s.errors >= 3) {
						safe("abort-after-errors", () -> abortAll(server));
					}
				}
			}
			if (!pendingEnd.isEmpty()) safe("end-payloads", () -> flushDueEnds(server, false));
			if (tickCounter % 20 == 7) {
				safe("sweep", () -> sweepStale(server));
			}
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] director tick failed", t);
		}
	}

	/** All online dreamers are in the overworld, in bed, and have slept long enough for vanilla to count it as deep sleep. */
	private static boolean sleepersReady(MinecraftServer server) {
		YumeConfig c = YumeConfig.get();
		if (Groups.stage() == RevealStage.FINALE) return false;
		if (c.dreamers == null || c.dreamers.isEmpty()) return false;
		List<ServerPlayer> dreamers = Groups.online(server, Groups.Side.DREAMER);
		if (dreamers.isEmpty()) return false;
		if (c.requireAllDreamers) {
			for (String name : c.dreamers) {
				if (name == null || name.isBlank()) continue;
				if (Groups.byName(server, name) == null) return false;
			}
		}
		for (ServerPlayer p : dreamers) {
			if (!p.level().dimension().equals(Level.OVERWORLD)) return false;
			if (!p.isSleeping() || !p.isSleepingLongEnough()) return false;
		}
		return true;
	}

	private static void tickSession(MinecraftServer server, Session s) {
		// viewers that vanished without a disconnect event
		Iterator<Viewer> it = s.viewers.values().iterator();
		while (it.hasNext()) {
			Viewer v = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(v.id);
			if (p == null || p.hasDisconnected()) {
				DreamLock.release(v.id);
				it.remove();
			}
		}
		if (s.viewers.isEmpty()) {
			finishPhase(server, s, false);
			return;
		}

		Entity cam = s.phase == Phase.MAIN && s.run != null ? s.run.cameraAnchor() : s.cam;
		if (cam == null || cam.isRemoved() || !cam.isAlive()) {
			finishPhase(server, s, true);
			return;
		}
		s.cam = cam;

		if (s.phase == Phase.MAIN && s.run != null) {
			try {
				s.run.tick(s.t);
			} catch (Throwable t) {
				if (s.sceneErrors++ == 0) YumeMod.LOG.error("[yume] scene tick failed", t);
			}
		}

		for (Viewer v : new ArrayList<>(s.viewers.values())) {
			ServerPlayer p = server.getPlayerList().getPlayer(v.id);
			if (p == null) continue;
			try {
				if (v.cam != cam) {
					v.cam = cam;
					DreamLock.lock(v.id, cam);
				}
				keepNear(p, cam);
				v.age++;
				for (int a : CAMERA_RESEND_AGES) {
					// the client may not have the dimension yet when the first camera packet arrives
					if (v.age == a) p.connection.send(new ClientboundSetCameraPacket(cam));
				}
				if (s.phase == Phase.MAIN && s.scene == Scene.WATERFALL && s.t % 20 == 0) {
					waterfallSound(p, s.t / 20);
				}
			} catch (Throwable t) {
				YumeMod.LOG.error("[yume] could not keep a viewer on the camera", t);
			}
		}

		if (s.t % 20 == 0) notifyOperators(server, s, "再生中");

		s.t++;
		if (s.t >= s.dur) {
			finishPhase(server, s, true);
		}
	}

	// ------------------------------------------------------------------ starting

	private static String beginDay(MinecraftServer server, int day, boolean advance) {
		if (session != null) return "夢の再生中です";
		DreamPlan plan = DreamSchedule.planFor(day);
		List<ServerPlayer> dreamers = Groups.online(server, Groups.Side.DREAMER);
		if (dreamers.isEmpty()) return "夢を見る人がオンラインにいません";

		Session s = new Session(day, day + "日目", advance, false, plan.dreamerScene(), plan);
		s.dur = dreamTicks();
		s.povAfter = plan.lostScene() == Scene.POV_STREAM;

		String err = prepareScene(server, s);
		if (err != null) {
			// The dream is skipped; the night still ends so nobody is stuck in bed.
			warnOperators(server, "夢 " + day + "日目をスキップ: " + err);
			if (advance) advanceToMorning(server);
			return "夢 " + day + "日目をスキップ: " + err;
		}

		List<ServerPlayer> viewers = new ArrayList<>(dreamers);
		if (DreamStages.isStaged(plan.lostScene())) {
			viewers.addAll(Groups.online(server, Groups.Side.LOST));
		}

		session = s;
		startMain(server, s, viewers);
		if (session != s) return "夢の開始に失敗しました";
		if (s.viewers.isEmpty()) {
			finishPhase(server, s, false);
			return "視聴者を移動できませんでした";
		}
		if (plan.giftLost()) {
			safe("gift", () -> Isolation.giftLost(server));
		}
		return "夢 " + day + "日目を開始しました（" + s.viewers.size() + "人）";
	}

	/** Builds the scene (or the fixed camera anchor) and fills s.run / s.anchor / s.cam. Returns an error line or null. */
	private static String prepareScene(MinecraftServer server, Session s) {
		Scene sc = s.scene;
		if (DreamStages.isStaged(sc)) {
			SceneRun run = DreamStages.start(server, sc, s.dur);
			if (run == null) return "舞台を用意できません: " + sc.name();
			s.run = run;
			Entity cam = run.cameraAnchor();
			if (cam == null || cam.isRemoved()) {
				cleanupScene(s);
				return "舞台のカメラがありません: " + sc.name();
			}
			s.cam = cam;
			return null;
		}
		if (sc == Scene.WATERFALL || sc == Scene.ROOM) {
			String key = sc == Scene.WATERFALL ? "waterfall" : "room";
			Map<String, YumeConfig.Cam> cams = YumeConfig.get().cameras;
			YumeConfig.Cam c = cams == null ? null : cams.get(key);
			if (c == null) return "カメラ未設定: " + key;
			ServerLevel lvl = levelOf(server, c.dimension);
			if (lvl == null) return "カメラのディメンションがありません: " + key;
			refreshCameraChunks(server);
			lvl.getChunk(((int) Math.floor(c.x)) >> 4, ((int) Math.floor(c.z)) >> 4);
			Entity a = CameraAnchor.spawn(lvl, new Vec3(c.x, c.y, c.z), c.yaw, c.pitch);
			if (a == null) return "カメラを作れません: " + key;
			if (a.isRemoved() || lvl.getEntity(a.getId()) == null) {
				try {
					a.discard();
				} catch (Throwable ignored) {
					// already gone
				}
				return "カメラの実体がワールドに追加されていません: " + key;
			}
			s.anchor = a;
			s.cam = a;
			return null;
		}
		return "再生できないシーン: " + sc.name();
	}

	/** Moves the given players into the dream. The session must already be registered. */
	private static void startMain(MinecraftServer server, Session s, List<ServerPlayer> players) {
		List<Integer> ids = new ArrayList<>();
		if (s.run != null) {
			try {
				for (Entity e : s.run.whiteSilhouettes()) {
					if (e != null) ids.add(e.getId());
				}
			} catch (Throwable t) {
				YumeMod.LOG.error("[yume] could not read silhouettes", t);
			}
		}
		for (ServerPlayer p : players) {
			boolean lost = Groups.sideOf(p) == Groups.Side.LOST;
			DreamStartPayload payload;
			if (lost || s.plan == null) {
				payload = new DreamStartPayload(s.scene.name(), s.dur, DreamFilter.SOFT.name(), "", 0, ids);
			} else {
				payload = new DreamStartPayload(s.scene.name(), s.dur, s.plan.filter().name(), s.plan.text(), s.plan.textDelayTicks(), ids);
			}
			enter(server, s, p, s.cam, payload);
		}
		safe("sync-start", () -> Isolation.sync(server));
		notifyOperators(server, s, "再生中");
	}

	/** Day-4 style viewing through the eyes of config.povTarget. Returns a status line. */
	private static String beginPov(MinecraftServer server, Session s, List<ServerPlayer> lostPlayers) {
		YumeConfig c = YumeConfig.get();
		s.phase = Phase.POV;
		s.t = 0;
		s.dur = Math.max(0, c.povSeconds) * 20;
		s.run = null;
		ServerPlayer target = Groups.byName(server, c.povTarget);
		List<ServerPlayer> online = new ArrayList<>();
		for (ServerPlayer p : lostPlayers) {
			if (p != null && !p.hasDisconnected()) online.add(p);
		}
		if (online.isEmpty()) {
			endSession(server, s);
			return "視聴者がいません";
		}
		if (target == null || target.hasDisconnected() || s.dur <= 0) {
			sendUrl(server, online, !s.test);
			endSession(server, s);
			return "映像の視聴先がいないためURLだけ送りました";
		}
		s.cam = target;
		for (ServerPlayer p : online) {
			if (p == target) continue;
			DreamStartPayload payload = new DreamStartPayload(Scene.POV_STREAM.name(), s.dur, DreamFilter.CLEAR.name(), "", 0, List.of());
			enter(server, s, p, target, payload);
		}
		safe("sync-pov", () -> Isolation.sync(server));
		notifyOperators(server, s, "再生中");
		if (s.viewers.isEmpty()) {
			endSession(server, s);
			return "視聴者を移動できませんでした";
		}
		return "映像の視聴を開始しました（" + s.viewers.size() + "人）";
	}

	/**
	 * Save where the player goes back to, wake them, make them a spectator, put them next to the camera
	 * and send the start payload. Returns false (and puts the player back) when something fails.
	 */
	private static boolean enter(MinecraftServer server, Session s, ServerPlayer p, Entity cam, DreamStartPayload payload) {
		YumeState st = YumeState.get();
		String key = p.getUUID().toString();
		boolean fresh = !st.inDream.containsKey(key);
		try {
			if (fresh) {
				st.inDream.put(key, capture(p));
				YumeState.save(server);
			}
			if (p.isSleeping()) p.stopSleepInBed(true, true);
			if (p.isPassenger()) p.stopRiding();
			if (fresh) {
				// Waking up moves the player to the side of the bed: remember that spot, not the pillow.
				YumeState.Saved sv = st.inDream.get(key);
				sv.x = p.getX();
				sv.y = p.getY();
				sv.z = p.getZ();
				YumeState.save(server);
			}
			if (!(cam.level() instanceof ServerLevel lvl)) throw new IllegalStateException("camera is not in a server level");
			pendingEnd.remove(p.getUUID());
			// first: the client starts fading to black before the loading screen of the dimension change
			send(p, payload);
			p.setGameMode(GameType.SPECTATOR);
			p.teleportTo(lvl, cam.getX(), eyeY(p, cam), cam.getZ(), Set.<Relative>of(), cam.getYRot(), cam.getXRot(), false);
			s.viewers.put(p.getUUID(), new Viewer(p.getUUID(), cam));
			s.participants.add(p.getUUID());
			DreamLock.lock(p.getUUID(), cam);
			p.setCamera(cam);
			return true;
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] could not move a player into the dream", t);
			s.viewers.remove(p.getUUID());
			YumeState.Saved sv = st.inDream.get(key);
			if (sv != null && restorePlayer(server, p, sv)) {
				st.inDream.remove(key);
				YumeState.save(server);
			}
			return false;
		}
	}

	private static YumeState.Saved capture(ServerPlayer p) {
		YumeState.Saved sv = new YumeState.Saved();
		sv.dimension = p.level().dimension().identifier().toString();
		sv.x = p.getX();
		sv.y = p.getY();
		sv.z = p.getZ();
		sv.yaw = p.getYRot();
		sv.pitch = p.getXRot();
		sv.gameMode = p.gameMode().getName();
		return sv;
	}

	// ------------------------------------------------------------------ ending

	/** Ends the current phase. completed=false means it was cut short (no progress is recorded). */
	private static void finishPhase(MinecraftServer server, Session s, boolean completed) {
		if (session != s) return;
		if (s.phase == Phase.MAIN) {
			releaseViewers(server, s);
			cleanupScene(s);
			if (completed && !s.test && s.advance) {
				YumeState.get().dreamsSeen = s.day;
				YumeState.save(server);
				safe("morning", () -> advanceToMorning(server));
			}
			if (completed && s.povAfter && !s.test) {
				s.viewers.clear();
				String r = beginPov(server, s, Groups.online(server, Groups.Side.LOST));
				YumeMod.LOG.info("[yume] {}", r);
				return;
			}
			endSession(server, s);
		} else {
			List<ServerPlayer> watchers = new ArrayList<>();
			for (Viewer v : s.viewers.values()) {
				ServerPlayer p = server.getPlayerList().getPlayer(v.id);
				if (p != null) watchers.add(p);
			}
			releaseViewers(server, s);
			if (completed) sendUrl(server, watchers, !s.test);
			endSession(server, s);
		}
	}

	private static void endSession(MinecraftServer server, Session s) {
		if (session == s) session = null;
		DreamLock.clear();
		safe("sync-end", () -> Isolation.sync(server));
		notifyOperators(server, s, "終了");
	}

	private static void releaseViewers(MinecraftServer server, Session s) {
		List<Viewer> list = new ArrayList<>(s.viewers.values());
		s.viewers.clear();
		YumeState st = YumeState.get();
		for (Viewer v : list) {
			try {
				DreamLock.release(v.id);
				ServerPlayer p = server.getPlayerList().getPlayer(v.id);
				if (p == null) continue; // offline: they are put back when they join again
				YumeState.Saved sv = st.inDream.get(v.id.toString());
				if (sv == null) {
					send(p, new DreamEndPayload());
					if (p.getCamera() != p) p.setCamera(p);
					continue;
				}
				if (restorePlayer(server, p, sv)) {
					st.inDream.remove(v.id.toString());
				}
			} catch (Throwable t) {
				YumeMod.LOG.error("[yume] could not put a viewer back (will retry)", t);
			}
		}
		YumeState.save(server);
	}

	private static void cleanupScene(Session s) {
		SceneRun run = s.run;
		Entity anchor = s.anchor;
		s.run = null;
		s.anchor = null;
		if (run != null) {
			try {
				run.cleanup();
			} catch (Throwable t) {
				YumeMod.LOG.error("[yume] scene cleanup failed", t);
			}
		}
		if (anchor != null) {
			try {
				anchor.discard();
			} catch (Throwable t) {
				YumeMod.LOG.error("[yume] anchor removal failed", t);
			}
		}
	}

	/** Same as vanilla waking up after sleeping: everyone out of bed, the clock to morning, rain stops. */
	private static void advanceToMorning(MinecraftServer server) {
		ServerLevel ow = server.overworld();
		for (ServerPlayer p : new ArrayList<>(ow.players())) {
			if (p.isSleeping()) p.stopSleepInBed(false, false);
		}
		ow.dimensionType().defaultClock().ifPresent(clock -> ow.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.WAKE_UP_FROM_SLEEP));
		WeatherData w = ow.getWeatherData();
		if (w.isRaining() || w.isThundering()) ow.resetWeatherCycle();
	}

	// ------------------------------------------------------------------ putting players back

	/**
	 * DreamEndPayload, camera back to self, original game mode, original dimension / position / rotation.
	 * Returns true when done (the caller then removes the inDream entry).
	 */
	private static boolean restorePlayer(MinecraftServer server, ServerPlayer p, YumeState.Saved sv) {
		try {
			DreamLock.release(p.getUUID());
			if (p.getCamera() != p) p.setCamera(p);
			GameType gt = sv.gameMode == null ? GameType.SURVIVAL : GameType.byName(sv.gameMode, GameType.SURVIVAL);
			p.setGameMode(gt);

			ServerLevel lvl = levelOf(server, sv.dimension);
			double x = sv.x, y = sv.y, z = sv.z;
			float yaw = sv.yaw, pitch = sv.pitch;
			if (lvl == null) {
				lvl = server.overworld();
				LevelData.RespawnData rd = lvl.getRespawnData();
				BlockPos bp = rd.pos();
				x = bp.getX() + 0.5;
				y = bp.getY();
				z = bp.getZ() + 0.5;
				yaw = rd.yaw();
				pitch = rd.pitch();
			}
			boolean ok = p.teleportTo(lvl, x, y, z, Set.<Relative>of(), yaw, pitch, false);
			if (!ok || p.isRemoved() || p.hasDisconnected()) return false;
			p.resetFallDistance();
			pendingEnd.put(p.getUUID(), tickCounter + END_DELAY_TICKS);
			return true;
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] could not restore a player", t);
			return false;
		}
	}

	private static void flushDueEnds(MinecraftServer server, boolean all) {
		Iterator<Map.Entry<UUID, Integer>> it = pendingEnd.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			if (!all && tickCounter < e.getValue()) continue;
			it.remove();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p != null && !isInDream(p)) send(p, new DreamEndPayload());
		}
	}

	private static void flushPendingEnds(MinecraftServer server) {
		flushDueEnds(server, true);
	}

	/** Puts back an online player who still has an inDream entry but is not in the live session. */
	private static void restoreStale(MinecraftServer server, ServerPlayer p) {
		if (p == null) return;
		YumeState st = YumeState.get();
		String key = p.getUUID().toString();
		YumeState.Saved sv = st.inDream.get(key);
		if (sv == null || inLiveSession(p.getUUID())) return;
		if (restorePlayer(server, p, sv)) {
			st.inDream.remove(key);
			YumeState.save(server);
		}
	}

	private static void sweepStale(MinecraftServer server) {
		YumeState st = YumeState.get();
		if (st.inDream.isEmpty()) return;
		for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
			if (st.inDream.containsKey(p.getUUID().toString())) {
				safe("restore-stale", () -> restoreStale(server, p));
			}
		}
	}

	private static void onLeave(MinecraftServer server, ServerPlayer p) {
		if (p == null) return;
		// May run on a network thread: never touch the world here. inDream stays; JOIN puts them back.
		UUID id = p.getUUID();
		DreamLock.release(id);
		server.execute(() -> {
			Session s = session;
			if (s != null) s.viewers.remove(id);
		});
	}

	private static boolean inLiveSession(UUID id) {
		Session s = session;
		return s != null && s.viewers.containsKey(id);
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Put the viewer's own eyes where the camera is: the client culls the world from the player's eye position,
	 * so a viewer whose head sits inside a ceiling sees the sky through it.
	 */
	private static double eyeY(ServerPlayer p, Entity cam) {
		return cam instanceof net.minecraft.world.entity.player.Player ? cam.getY() : cam.getY() - p.getEyeHeight();
	}

	private static void keepNear(ServerPlayer p, Entity cam) {
		if (!(cam.level() instanceof ServerLevel cl)) return;
		if (p.level() != cl || p.distanceToSqr(cam) > 64.0) {
			p.teleportTo(cl, cam.getX(), eyeY(p, cam), cam.getZ(), Set.<Relative>of(), cam.getYRot(), cam.getXRot(), false);
		}
		if (p.getCamera() != cam) p.setCamera(cam);
	}

	/** Loud falling water, only for this viewer, layered rain + water ambience. Called once a second. */
	private static void waterfallSound(ServerPlayer p, int second) {
		long seed = p.level().getRandom().nextLong();
		double x = p.getX(), y = p.getY(), z = p.getZ();
		sendSound(p, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, x, y, z, 3.0f, 0.55f, seed);
		sendSound(p, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, x, y, z, 3.0f, 0.5f, seed + 1);
		sendSound(p, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT, x, y, z, 3.0f, 0.6f, seed + 2);
		if (second % 2 == 0) {
			sendSound(p, SoundEvents.GENERIC_SPLASH, SoundSource.AMBIENT, x, y, z, 1.5f, 0.5f, seed + 3);
		}
	}

	private static void sendSound(ServerPlayer p, SoundEvent ev, SoundSource src, double x, double y, double z, float vol, float pitch, long seed) {
		Holder<SoundEvent> h = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev);
		p.connection.send(new ClientboundSoundPacket(h, src, x, y, z, vol, pitch, seed));
	}

	private static void send(ServerPlayer p, CustomPacketPayload payload) {
		try {
			if (ServerPlayNetworking.canSend(p, payload.type())) {
				ServerPlayNetworking.send(p, payload);
			}
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] could not send a payload", t);
		}
	}

	/** Chat line with a clickable link, for the trapped group after the day-4 stream. Empty URL: operators only get a note. */
	private static void sendUrl(MinecraftServer server, List<ServerPlayer> players, boolean onlyLost) {
		String url = YumeConfig.get().streamUrl;
		if (url == null || url.isBlank()) {
			warnOperators(server, "URL未設定（streamUrl）");
			return;
		}
		URI uri;
		try {
			uri = URI.create(url.trim());
			String scheme = uri.getScheme();
			if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) throw new IllegalArgumentException("scheme");
		} catch (Throwable t) {
			warnOperators(server, "URLが正しくありません（streamUrl）");
			return;
		}
		Style link = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(uri)).withUnderlined(true).withColor(ChatFormatting.AQUA);
		for (ServerPlayer p : players) {
			try {
				if (onlyLost && Groups.sideOf(p) != Groups.Side.LOST) continue;
				p.sendSystemMessage(Component.literal("▶ さっきの映像の続き ").withStyle(link).append(Component.literal(url.trim()).withStyle(link)));
			} catch (Throwable t) {
				YumeMod.LOG.error("[yume] could not send the link", t);
			}
		}
	}

	private static void notifyOperators(MinecraftServer server, Session s, String state) {
		try {
			Component msg = Component.literal("夢 " + s.label + " " + state);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (Groups.isOperator(p) && !s.participants.contains(p.getUUID())) {
					p.sendOverlayMessage(msg);
				}
			}
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] operator notice failed", t);
		}
	}

	private static void warnOperators(MinecraftServer server, String text) {
		YumeMod.LOG.warn("[yume] {}", text);
		try {
			Component msg = Component.literal(text);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (Groups.isOperator(p)) p.sendSystemMessage(msg);
			}
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] operator warning failed", t);
		}
	}

	private static boolean inRoom(Level level, BlockPos pos) {
		YumeConfig.Box b = YumeConfig.get().room;
		if (b == null) return false;
		if (b.x1 == 0 && b.y1 == 0 && b.z1 == 0 && b.x2 == 0 && b.y2 == 0 && b.z2 == 0) return false; // not configured
		return b.contains(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	private static ServerLevel levelOf(MinecraftServer server, String dimension) {
		if (dimension == null || dimension.isBlank()) return null;
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) return null;
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	private static int dreamTicks() {
		return Math.max(1, YumeConfig.get().dreamSeconds) * 20;
	}

	/** Text / filter used for a test of a scene: whatever the schedule uses for that scene. */
	private static DreamPlan testPlan(Scene scene) {
		for (int d = 1; d <= 12; d++) {
			DreamPlan p = DreamSchedule.planFor(d);
			if (p.dreamerScene() == scene) return p;
		}
		return null;
	}

	private static void safe(String what, Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] " + what + " failed", t);
		}
	}
}
