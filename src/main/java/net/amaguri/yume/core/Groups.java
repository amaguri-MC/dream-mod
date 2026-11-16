package net.amaguri.yume.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.amaguri.yume.schedule.RevealStage;
import net.amaguri.yume.schedule.DreamSchedule;

/**
 * Who belongs to which group, and the current reveal stage.
 * CONTRACT FILE (owned by the lead). Workers must not edit it.
 */
public final class Groups {
	public enum Side { DREAMER, LOST, NONE }

	private Groups() {
	}

	public static Side sideOf(String name) {
		YumeConfig c = YumeConfig.get();
		if (containsIgnoreCase(c.dreamers, name)) return Side.DREAMER;
		if (containsIgnoreCase(c.lost, name)) return Side.LOST;
		return Side.NONE;
	}

	public static Side sideOf(ServerPlayer p) {
		return sideOf(p.getGameProfile().name());
	}

	public static boolean isOperator(ServerPlayer p) {
		return containsIgnoreCase(YumeConfig.get().operators, p.getGameProfile().name());
	}

	/** True when a and b are in different, real groups (DREAMER vs LOST). NONE is never "other". */
	public static boolean areSeparated(Side a, Side b) {
		return a != Side.NONE && b != Side.NONE && a != b;
	}

	/** Should viewer be kept from knowing about target (tab list, chat, join/death messages)? */
	public static boolean hidden(ServerPlayer viewer, ServerPlayer target) {
		if (stage() == RevealStage.FINALE) return false;
		return areSeparated(sideOf(viewer), sideOf(target));
	}

	public static List<ServerPlayer> online(MinecraftServer server, Side side) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (sideOf(p) == side) out.add(p);
		}
		return out;
	}

	public static ServerPlayer byName(MinecraftServer server, String name) {
		if (name == null || name.isBlank()) return null;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getGameProfile().name().equalsIgnoreCase(name)) return p;
		}
		return null;
	}

	/** Current reveal stage: finale flag, then override, then the schedule for the latest dream seen. */
	public static RevealStage stage() {
		YumeState s = YumeState.get();
		if (s.finale) return RevealStage.FINALE;
		if (s.stageOverride != null) return s.stageOverride;
		return DreamSchedule.stageAfter(s.dreamsSeen);
	}

	private static boolean containsIgnoreCase(List<String> list, String name) {
		if (list == null || name == null) return false;
		for (String s : list) {
			if (s != null && s.equalsIgnoreCase(name)) return true;
		}
		return false;
	}
}
