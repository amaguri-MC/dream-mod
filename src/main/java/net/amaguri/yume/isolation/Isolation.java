package net.amaguri.yume.isolation;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.core.Groups;
import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.YumeState;
import net.amaguri.yume.director.DreamDirector;
import net.amaguri.yume.net.RevealPayload;
import net.amaguri.yume.schedule.RevealStage;
import net.amaguri.yume.voice.VoiceState;

/**
 * Keeps the two groups apart (messages, tab list, server list, voice gate), the /yume command, the clock speed.
 * OWNER: worker "isolation". Public signatures are a CONTRACT.
 */
public final class Isolation {
	/** How often the reveal list is re-sent regardless of events. */
	private static final int SYNC_INTERVAL_TICKS = 100;
	/** Delay of the follow-up sync after somebody joins (the joining client is still setting up). */
	private static final int JOIN_SYNC_DELAY_TICKS = 20;

	/** Ticks until a follow-up sync after somebody joined / left; -1 = none. */
	private static int joinSync = -1;
	private static int leaveSync = -1;

	private Isolation() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> YumeCommand.register(dispatcher));
		TimeKeeper.init();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			sync(server);
			joinSync = JOIN_SYNC_DELAY_TICKS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leaveSync = 2);
		ServerTickEvents.END_SERVER_TICK.register(Isolation::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			joinSync = -1;
			leaveSync = -1;
			TimeKeeper.reset();
		});
	}

	private static void tick(MinecraftServer server) {
		boolean due = server.getTickCount() % SYNC_INTERVAL_TICKS == 0;
		if (joinSync >= 0 && --joinSync < 0) due = true;
		if (leaveSync >= 0 && --leaveSync < 0) due = true;
		if (due) sync(server);
		TimeKeeper.tick(server);
	}

	/** Recompute and send RevealPayload to every online player (call after any stage / dream change). */
	public static void sync(MinecraftServer server) {
		if (server == null) return;
		try {
			RevealStage stage = Groups.stage();
			List<ServerPlayer> players = server.getPlayerList().getPlayers();
			for (ServerPlayer viewer : players) {
				List<RevealPayload.Entry> entries = new ArrayList<>();
				if (stage != RevealStage.FINALE) {
					Groups.Side viewerSide = Groups.sideOf(viewer);
					for (ServerPlayer target : players) {
						if (target == viewer || !Groups.hidden(viewer, target)) continue;
						byte render = RevealPayload.RENDER_NORMAL;
						if (viewerSide == Groups.Side.DREAMER && Groups.sideOf(target) == Groups.Side.LOST) {
							render = switch (stage) {
								case HIDDEN -> RevealPayload.RENDER_INVISIBLE;
								case SILHOUETTE -> RevealPayload.RENDER_BLACK_SILHOUETTE;
								case VISIBLE, FINALE -> RevealPayload.RENDER_NORMAL;
							};
						}
						entries.add(new RevealPayload.Entry(target.getUUID(), true, render));
					}
				}
				try {
					if (ServerPlayNetworking.canSend(viewer, RevealPayload.TYPE)) {
						ServerPlayNetworking.send(viewer, new RevealPayload(entries));
					}
				} catch (Exception e) {
					YumeMod.LOG.warn("[yume] could not send the reveal list to a player", e);
				}
			}
		} catch (Exception e) {
			YumeMod.LOG.error("[yume] reveal sync failed", e);
		}
	}

	/** Give config.giftItems to every online trapped player (quietly). Returns how many players got items. */
	public static int giftLost(MinecraftServer server) {
		if (server == null) return 0;
		List<String> specs = YumeConfig.get().giftItems;
		if (specs == null || specs.isEmpty()) return 0;
		int served = 0;
		for (ServerPlayer p : Groups.online(server, Groups.Side.LOST)) {
			boolean any = false;
			for (String spec : specs) {
				any |= give(p, spec);
			}
			if (any) served++;
		}
		return served;
	}

	/** "minecraft:oak_sign 8" -> that many items into the inventory; what does not fit lands at the player's feet. */
	private static boolean give(ServerPlayer player, String spec) {
		if (spec == null || spec.isBlank()) return false;
		String[] parts = spec.trim().split("\\s+");
		Identifier id = Identifier.tryParse(parts[0]);
		if (id == null) {
			YumeMod.LOG.warn("[yume] giftItems: bad item id '{}'", parts[0]);
			return false;
		}
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item == null) {
			YumeMod.LOG.warn("[yume] giftItems: unknown item '{}'", parts[0]);
			return false;
		}
		int count = 1;
		if (parts.length > 1) {
			try {
				count = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				YumeMod.LOG.warn("[yume] giftItems: bad count in '{}'", spec);
			}
		}
		if (count <= 0) return false;
		boolean gave = false;
		while (count > 0) {
			ItemStack stack = new ItemStack(item, 1);
			int n = Math.min(count, Math.max(1, stack.getMaxStackSize()));
			stack = new ItemStack(item, n);
			count -= n;
			player.getInventory().add(stack);
			if (!stack.isEmpty()) {
				player.spawnAtLocation(player.level(), stack);
			}
			gave = true;
		}
		return gave;
	}

	/** Multi-line status for /yume status (sender only). */
	public static String status(MinecraftServer server) {
		YumeState s = YumeState.get();
		YumeConfig c = YumeConfig.get();
		StringBuilder sb = new StringBuilder();
		sb.append("見た夢: ").append(s.dreamsSeen).append(" 回（次は ").append(s.dreamsSeen + 1).append(" 日目）\n");
		String mode = s.finale ? "（終幕済み）" : s.stageOverride != null ? "（固定）" : "（自動）";
		sb.append("段階: ").append(Groups.stage()).append(mode).append('\n');
		sb.append("時間の速さ: ×").append(s.timeSpeed).append('\n');
		sb.append("夢の最中: ").append(DreamDirector.isDreamActive() ? "はい" : "いいえ").append('\n');
		sb.append("カメラ: waterfall=").append(yn(c.cameras.containsKey("waterfall")))
				.append(" room=").append(yn(c.cameras.containsKey("room")))
				.append(" / 部屋: ").append(yn(roomSet(c.room)))
				.append(" / URL: ").append(yn(c.streamUrl != null && !c.streamUrl.isBlank())).append('\n');
		sb.append("ボイス連携: ").append(VoiceState.active ? "あり" : "なし").append('\n');
		if (server != null) {
			sb.append("オンライン: dreamers ").append(Groups.online(server, Groups.Side.DREAMER).size())
					.append(" 人 / lost ").append(Groups.online(server, Groups.Side.LOST).size()).append(" 人");
		}
		return sb.toString();
	}

	private static String yn(boolean b) {
		return b ? "あり" : "なし";
	}

	private static boolean roomSet(YumeConfig.Box b) {
		return b != null && (b.x1 != 0 || b.y1 != 0 || b.z1 != 0 || b.x2 != 0 || b.y2 != 0 || b.z2 != 0);
	}
}
