package net.amaguri.yume.isolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.core.Groups;
import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.YumeState;
import net.amaguri.yume.schedule.RevealStage;

/**
 * The decisions behind the mixins: what a viewer must not be told about the other group (until the finale).
 * Everything here fails closed: if something goes wrong the message is dropped for that viewer, never thrown.
 */
public final class Gate {
	private static boolean warned = false;
	/** Who is running /list right now (set by the /list mixin for the duration of the call; server thread only). */
	public static ServerPlayer listViewer = null;

	private Gate() {
	}

	/** Separation is in force (everything except the finale). */
	public static boolean active() {
		return Groups.stage() != RevealStage.FINALE;
	}

	/** Names (from the config) of the other group as this viewer must not see them. Empty when nothing is hidden. */
	private static List<String> hiddenNames(ServerPlayer viewer) {
		if (!active()) return List.of();
		YumeConfig c = YumeConfig.get();
		List<String> names = switch (Groups.sideOf(viewer)) {
			case DREAMER -> c.lost;
			case LOST -> c.dreamers;
			case NONE -> null;
		};
		return names == null ? List.of() : names;
	}

	/** Does this text mention a member of the group hidden from the viewer (whole-word, case-insensitive)? */
	public static boolean mentionsHidden(ServerPlayer viewer, String text) {
		if (text == null || text.isEmpty()) return false;
		List<String> names = hiddenNames(viewer);
		if (names.isEmpty()) return false;
		String hay = text.toLowerCase(Locale.ROOT);
		for (String name : names) {
			if (name == null || name.isBlank()) continue;
			String needle = name.toLowerCase(Locale.ROOT);
			int from = 0;
			while (true) {
				int at = hay.indexOf(needle, from);
				if (at < 0) break;
				boolean leftOk = at == 0 || !isNameChar(hay.charAt(at - 1));
				int end = at + needle.length();
				boolean rightOk = end >= hay.length() || !isNameChar(hay.charAt(end));
				if (leftOk && rightOk) return true;
				from = at + 1;
			}
		}
		return false;
	}

	private static boolean isNameChar(char ch) {
		return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_';
	}

	/**
	 * Wraps the per-player message function of PlayerList.broadcastSystemMessage: join, leave, death, advancement and
	 * every other broadcast that names a player of the other group never reaches the viewer.
	 */
	public static Function<ServerPlayer, Component> wrapBroadcast(Function<ServerPlayer, Component> original) {
		return viewer -> {
			Component c = original.apply(viewer);
			if (c == null) return null;
			try {
				return mentionsHidden(viewer, c.getString()) ? null : c;
			} catch (Throwable t) {
				warnOnce(t);
				return null;
			}
		};
	}

	/**
	 * Chat, /me, /say, /msg and the rest end up in ServerPlayer.sendChatMessage. Returns true when the message must
	 * not be delivered. An operator who turned spy on gets a grey copy instead.
	 */
	public static boolean blockChat(ServerPlayer viewer, OutgoingChatMessage message, ChatType.Bound bound) {
		try {
			if (!active() || Groups.sideOf(viewer) == Groups.Side.NONE) return false;
			boolean hidden = false;
			String senderName = null;
			if (message instanceof OutgoingChatMessage.Player p) {
				UUID id = p.message().sender();
				ServerPlayer sender = id == null ? null : viewer.level().getServer().getPlayerList().getPlayer(id);
				if (sender != null) {
					senderName = sender.getGameProfile().name();
					hidden = Groups.hidden(viewer, sender);
				}
			}
			String boundName = bound == null || bound.name() == null ? "" : bound.name().getString();
			if (!hidden) hidden = mentionsHidden(viewer, boundName);
			if (!hidden) return false;
			if (senderName == null) senderName = boundName;
			spyCopy(viewer, senderName, message.content());
			return true;
		} catch (Throwable t) {
			warnOnce(t);
			return true;
		}
	}

	private static void spyCopy(ServerPlayer viewer, String senderName, Component content) {
		if (!Groups.isOperator(viewer)) return;
		if (!Boolean.TRUE.equals(YumeState.get().spy.get(viewer.getUUID().toString()))) return;
		String body = content == null ? "" : content.getString();
		viewer.sendSystemMessage(Component.literal("[夢の向こう] " + senderName + ": " + body).withStyle(ChatFormatting.GRAY));
	}

	/** Server list: the trapped group is not counted and no names are listed. */
	public static ServerStatus.Players statusPlayers(MinecraftServer server, ServerStatus.Players original) {
		try {
			if (!active()) return original;
			int count = 0;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (Groups.sideOf(p) != Groups.Side.LOST) count++;
			}
			return new ServerStatus.Players(original.max(), count, List.of());
		} catch (Throwable t) {
			warnOnce(t);
			return original;
		}
	}

	/** /list: only the players this viewer may know about. */
	public static List<ServerPlayer> visibleTo(ServerPlayer viewer, List<ServerPlayer> all) {
		if (viewer == null || !active()) return all;
		try {
			List<ServerPlayer> out = new ArrayList<>(all.size());
			for (ServerPlayer p : all) {
				if (p == viewer || !Groups.hidden(viewer, p)) out.add(p);
			}
			return out;
		} catch (Throwable t) {
			warnOnce(t);
			List<ServerPlayer> self = new ArrayList<>();
			self.add(viewer);
			return self;
		}
	}

	private static void warnOnce(Throwable t) {
		if (!warned) {
			warned = true;
			YumeMod.LOG.error("[yume] message gate failed (message dropped)", t);
		}
	}
}
