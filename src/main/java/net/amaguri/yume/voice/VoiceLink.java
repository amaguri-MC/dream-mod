package net.amaguri.yume.voice;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.core.Groups;
import net.amaguri.yume.director.DreamDirector;
import net.amaguri.yume.schedule.RevealStage;

/**
 * Opens and closes the voice window every tick: while a dream with voice plays, everybody of both groups is put into one
 * hidden voice group (so distance does not matter); afterwards each player goes back to the group they were in.
 */
final class VoiceLink {
	private static volatile VoicechatServerApi api;
	private static Group meeting;
	/** Players we moved into the meeting group -> the group they had before (null = none). */
	private static final Map<UUID, UUID> previous = new HashMap<>();

	private VoiceLink() {
	}

	static void setApi(VoicechatServerApi value) {
		api = value;
		if (value == null) {
			meeting = null;
			previous.clear();
			VoiceState.window = false;
		}
	}

	static void tick(MinecraftServer server) {
		try {
			// Snapshot of who is on which side, for the voice threads.
			Map<UUID, Groups.Side> now = new HashMap<>();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				now.put(p.getUUID(), Groups.sideOf(p));
			}
			VoiceState.sides.keySet().retainAll(now.keySet());
			VoiceState.sides.putAll(now);

			RevealStage stage = Groups.stage();
			VoiceState.separated = stage != RevealStage.FINALE;
			boolean open = VoiceState.separated && stage == RevealStage.VISIBLE && DreamDirector.isDreamActive();
			VoiceState.window = open;

			VoicechatServerApi voice = api;
			if (voice == null) return;
			if (open) {
				join(server, voice);
			} else if (meeting != null || !previous.isEmpty()) {
				release(voice);
			}
		} catch (Throwable t) {
			YumeMod.LOG.error("[yume] voice link failed", t);
		}
	}

	private static void join(MinecraftServer server, VoicechatServerApi voice) {
		if (meeting == null) {
			meeting = voice.groupBuilder().setName("yume").setHidden(true).setPersistent(false).setType(Group.Type.NORMAL).build();
		}
		if (meeting == null) return;
		Iterator<UUID> it = previous.keySet().iterator();
		while (it.hasNext()) {
			if (server.getPlayerList().getPlayer(it.next()) == null) it.remove();
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Groups.sideOf(p) == Groups.Side.NONE || previous.containsKey(p.getUUID())) continue;
			VoicechatConnection c = voice.getConnectionOf(p.getUUID());
			if (c == null) continue;
			Group old = c.getGroup();
			previous.put(p.getUUID(), old == null ? null : old.getId());
			c.setGroup(meeting);
		}
	}

	private static void release(VoicechatServerApi voice) {
		for (Map.Entry<UUID, UUID> e : previous.entrySet()) {
			try {
				VoicechatConnection c = voice.getConnectionOf(e.getKey());
				if (c == null) continue;
				Group back = e.getValue() == null ? null : voice.getGroup(e.getValue());
				c.setGroup(back);
			} catch (Throwable t) {
				YumeMod.LOG.warn("[yume] could not restore a voice group", t);
			}
		}
		previous.clear();
		if (meeting != null) {
			try {
				voice.removeGroup(meeting.getId());
			} catch (Throwable t) {
				YumeMod.LOG.warn("[yume] could not remove the voice group", t);
			}
			meeting = null;
		}
	}
}
