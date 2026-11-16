package net.amaguri.yume.isolation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

import net.amaguri.yume.core.Groups;
import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.YumeState;
import net.amaguri.yume.director.DreamDirector;

/**
 * Day/night speed: right-click a clock to cycle the speed, and every tick push the overworld clock forward by
 * (speed - 1) extra ticks. The tick rate itself is never touched.
 */
final class TimeKeeper {
	private static long lastTotal = Long.MIN_VALUE;
	private static final Map<UUID, Integer> lastClick = new HashMap<>();

	private TimeKeeper() {
	}

	static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (sp.getItemInHand(hand).getItem() != Items.CLOCK) return InteractionResult.PASS;
			// Trapped players: a clock does nothing (vanilla).
			if (Groups.sideOf(sp) != Groups.Side.DREAMER && !Groups.isOperator(sp)) return InteractionResult.PASS;
			MinecraftServer server = sp.level().getServer();
			int now = server.getTickCount();
			Integer last = lastClick.put(sp.getUUID(), now);
			if (last != null && now - last < 4) return InteractionResult.SUCCESS_SERVER;
			cycle(server, sp);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	static void reset() {
		lastTotal = Long.MIN_VALUE;
		lastClick.clear();
	}

	private static void cycle(MinecraftServer server, ServerPlayer player) {
		List<Integer> speeds = YumeConfig.get().timeSpeeds;
		YumeState state = YumeState.get();
		int next = 1;
		if (speeds != null && !speeds.isEmpty()) {
			int idx = speeds.indexOf(state.timeSpeed);
			next = speeds.get((idx + 1) % speeds.size());
		}
		state.timeSpeed = Math.max(1, next);
		YumeState.save(server);
		player.sendOverlayMessage(Component.literal("時間の速さ ×" + state.timeSpeed));
	}

	/** Called every server tick (end). */
	static void tick(MinecraftServer server) {
		try {
			Holder<WorldClock> clock = overworldClock(server);
			if (clock == null) return;
			long total = server.clockManager().getTotalTicks(clock);
			// "Advancing" = the clock moved by itself since we last looked (so /time pause and the game rule stay respected).
			boolean advancing = lastTotal != Long.MIN_VALUE && total > lastTotal;
			lastTotal = total;
			int speed = YumeState.get().timeSpeed;
			if (speed <= 1 || !advancing) return;
			if (!server.getGameRules().get(GameRules.ADVANCE_TIME)) return;
			if (DreamDirector.isDreamActive()) return;
			server.clockManager().addTicks(clock, speed - 1);
			lastTotal = server.clockManager().getTotalTicks(clock);
		} catch (Throwable t) {
			lastTotal = Long.MIN_VALUE;
		}
	}

	private static Holder<WorldClock> overworldClock(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) return null;
		Optional<Holder<WorldClock>> clock = overworld.dimensionType().defaultClock();
		return clock.orElse(null);
	}
}
