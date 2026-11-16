package net.amaguri.yume.client;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.net.DreamEndPayload;
import net.amaguri.yume.net.DreamStartPayload;
import net.amaguri.yume.net.RevealPayload;
import net.amaguri.yume.schedule.DreamFilter;

/** Client side state of the dream screen: receives the payloads, counts the dream down, hides the HUD. */
public final class DreamClient {
	/** Length of the fade from/to black. */
	public static final int FADE_TICKS = 20;
	private static final int EXIT_TICKS = 12;
	private static final int TYPE_TICKS = 3;

	private static boolean active;
	private static int elapsed;
	private static int duration;
	private static DreamFilter filter = DreamFilter.CLEAR;
	private static String text = "";
	private static int textDelay;
	private static int[] charAt = new int[0];
	private static CameraType savedCamera;

	/** Extra ticks to wait for DreamEnd after the dream time is up (the way back may still be loading). */
	private static final int END_WAIT_TICKS = 60;
	/** After a dream ends, loading screens stay hidden this long. */
	private static final int LOADING_GRACE_TICKS = 100;
	/** Give up waiting for the world to be ready after this many ticks. */
	private static final int READY_TIMEOUT_TICKS = 120;

	private static int readyAt = -1;
	private static int stable;
	private static Object startLevel;
	private static double startX, startY, startZ;
	private static int loadingGrace;

	private static int exitTicks;
	private static float exitFrom;

	private DreamClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DreamStartPayload.TYPE, (payload, context) -> start(payload));
		ClientPlayNetworking.registerGlobalReceiver(DreamEndPayload.TYPE, (payload, context) -> end());
		ClientPlayNetworking.registerGlobalReceiver(RevealPayload.TYPE, (payload, context) -> RevealClient.setEntries(payload.entries()));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetAll());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetAll());
		BellyClient.init();
		ClientTickEvents.END_CLIENT_TICK.register(DreamClient::tick);

		// Every vanilla HUD element is skipped while a dream is on.
		List<Identifier> elements = List.of(VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR,
				VanillaHudElements.SPECTATOR_MENU, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
				VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
				VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
				VanillaHudElements.BOSS_BAR, VanillaHudElements.SLEEP, VanillaHudElements.DEMO_TIMER,
				VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE,
				VanillaHudElements.CHAT, VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES);
		for (Identifier id : elements) {
			try {
				HudElementRegistry.replaceElement(id, previous -> (graphics, deltaTracker) -> {
					if (!active) {
						previous.extractRenderState(graphics, deltaTracker);
					}
				});
			} catch (RuntimeException e) {
				YumeMod.LOG.warn("Could not wrap HUD element {}", id, e);
			}
		}
		HudElementRegistry.addLast(YumeMod.id("dream_overlay"), DreamOverlay::render);
	}

	public static boolean isActive() {
		return active;
	}

	public static int elapsed() {
		return elapsed;
	}

	public static int duration() {
		return duration;
	}

	public static DreamFilter filter() {
		return filter;
	}

	public static String text() {
		return text;
	}

	public static int textDelay() {
		return textDelay;
	}

	public static int[] charAt() {
		return charAt;
	}

	/** True while a dimension/world loading screen must not be shown (a black screen is drawn instead). */
	public static boolean hidesLoading() {
		return active || loadingGrace > 0;
	}

	/** Dream time (ticks) at which the picture became visible; -1 while still black and waiting for the world. */
	public static int readyAt() {
		return readyAt;
	}

	public static boolean isExiting() {
		return !active && exitTicks > 0;
	}

	public static float exitAlpha(float partial) {
		if (active || exitTicks <= 0) {
			return 0f;
		}
		float t = Math.max(0f, exitTicks - partial) / EXIT_TICKS;
		return exitFrom * t;
	}

	/** Black cover at the given dream time: 1 at the very start and end, 0 in between. */
	public static float fadeAlpha(float t) {
		int len = Math.max(1, Math.min(FADE_TICKS, duration / 2));
		if (readyAt < 0) {
			return 1f;
		}
		float in = 1f - (t - readyAt) / len;
		float out = (t - (duration - len)) / len;
		float a = Math.max(in, out);
		a = Math.max(0f, Math.min(1f, a));
		return a * a * (3f - 2f * a);
	}

	private static void start(DreamStartPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (!active) {
			savedCamera = mc.options.getCameraType();
			if (savedCamera != CameraType.FIRST_PERSON) {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			}
		}
		DreamFilter f;
		try {
			f = DreamFilter.valueOf(p.filter());
		} catch (IllegalArgumentException | NullPointerException e) {
			f = DreamFilter.CLEAR;
		}
		filter = f;
		duration = Math.max(1, p.durationTicks());
		text = p.text() == null ? "" : p.text();
		textDelay = Math.max(0, p.textDelayTicks());
		charAt = schedule(text);
		RevealClient.setWhiteIds(p.whiteSilhouetteIds());
		elapsed = 0;
		exitTicks = 0;
		readyAt = -1;
		stable = 0;
		startLevel = mc.level;
		if (mc.player != null) {
			startX = mc.player.getX();
			startY = mc.player.getY();
			startZ = mc.player.getZ();
		}
		loadingGrace = 0;
		active = true;
	}

	/** When each character appears, relative to the typing start (about 3 ticks a character, longer after pauses). */
	private static int[] schedule(String s) {
		int[] at = new int[s.length()];
		int t = 0;
		for (int i = 0; i < s.length(); i++) {
			at[i] = t;
			char c = s.charAt(i);
			t += TYPE_TICKS;
			switch (c) {
				case '…' -> t += 8; // ellipsis
				case '、', ',' -> t += 5;
				case '。', '.', '！', '？', '!', '?' -> t += 8;
				case '\n' -> t += 8;
				default -> {
				}
			}
		}
		return at;
	}

	/** Ends the dream (also called when the time is up). Safe to call twice. */
	public static void end() {
		if (!active) {
			return;
		}
		float cover = fadeAlpha(Math.min(elapsed, duration));
		active = false;
		loadingGrace = LOADING_GRACE_TICKS;
		RevealClient.clearWhiteIds();
		BellyClient.clear();
		Minecraft mc = Minecraft.getInstance();
		if (savedCamera != null && savedCamera != CameraType.FIRST_PERSON && mc.options != null) {
			mc.options.setCameraType(savedCamera);
		}
		savedCamera = null;
		if (cover > 0.05f) {
			exitFrom = cover;
			exitTicks = EXIT_TICKS;
		}
	}

	/** The picture fades in only once the new place (camera entity, chunks) can be drawn. */
	private static void checkReady(Minecraft mc) {
		// ready once we moved away, or once the dream camera has been attached (short same-level moves)
		boolean moved = mc.level != startLevel
				|| mc.player.distanceToSqr(startX, startY, startZ) > 16.0
				|| (mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player);
		boolean ok = moved && !(mc.gui.screen() instanceof LevelLoadingScreen) && mc.getCameraEntity() != null
				&& mc.levelRenderer.hasRenderedAllSections();
		if (ok) {
			for (int id : RevealClient.whiteIds()) {
				if (mc.level.getEntity(id) == null) {
					ok = false;
					break;
				}
			}
		}
		stable = ok ? stable + 1 : 0;
		if (stable >= 3 || elapsed >= READY_TIMEOUT_TICKS) {
			readyAt = elapsed;
		}
	}

	private static void resetAll() {
		active = false;
		exitTicks = 0;
		loadingGrace = 0;
		savedCamera = null;
		text = "";
		charAt = new int[0];
		RevealClient.reset();
		BellyClient.clear();
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			if (active || exitTicks > 0) {
				loadingGrace = active ? LOADING_GRACE_TICKS : loadingGrace;
				active = false;
				exitTicks = 0;
				savedCamera = null;
				RevealClient.clearWhiteIds();
				BellyClient.clear();
			}
			return;
		}
		if (!active) {
			if (exitTicks > 0) {
				exitTicks--;
			}
			if (loadingGrace > 0) {
				loadingGrace--;
			}
			return;
		}
		elapsed++;
		if (elapsed >= duration + END_WAIT_TICKS) {
			end();
			return;
		}
		if (readyAt < 0) {
			checkReady(mc);
		}
		// Nothing the player presses may break the picture.
		if (mc.gui.hud.isHidden()) {
			mc.gui.hud.toggle();
		}
		Screen screen = mc.gui.screen();
		if (screen instanceof ChatScreen || screen instanceof AbstractContainerScreen<?> || screen instanceof PauseScreen) {
			mc.gui.setScreen(null);
		}
	}
}
