package net.amaguri.yume.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.core.Groups;
import net.amaguri.yume.director.DreamDirector;
import net.amaguri.yume.schedule.RevealStage;

/**
 * Simple Voice Chat plugin (loaded only when Simple Voice Chat is installed).
 * Before the finale the two groups cannot hear each other, except during a dream with voice.
 * OWNER: worker "isolation".
 */
public class YumeVoicePlugin implements VoicechatPlugin {
	private static boolean tickHooked = false;

	@Override
	public String getPluginId() {
		return "yume";
	}

	@Override
	public void initialize(VoicechatApi api) {
		VoiceState.active = true;
		if (!tickHooked) {
			tickHooked = true;
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				try {
					VoiceState.sides.keySet().retainAll(server.getPlayerList().getPlayersByUUID().keySet());
					for (ServerPlayer p : server.getPlayerList().getPlayers()) {
						VoiceState.sides.put(p.getUUID(), Groups.sideOf(p));
					}
					VoiceState.separated = Groups.stage() != RevealStage.FINALE;
					VoiceState.window = DreamDirector.voiceOpen();
				} catch (Throwable t) {
					YumeMod.LOG.error("[yume] voice state refresh failed", t);
				}
			});
		}
		YumeMod.LOG.info("[yume] voice chat plugin ready");
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(EntitySoundPacketEvent.class, YumeVoicePlugin::gate);
		registration.registerEvent(LocationalSoundPacketEvent.class, YumeVoicePlugin::gate);
		registration.registerEvent(StaticSoundPacketEvent.class, YumeVoicePlugin::gate);
	}

	/** Cancel sound between the two groups unless the voice window is open. Runs on a voice thread: reads snapshots only. */
	private static void gate(SoundPacketEvent<?> event) {
		try {
			if (!VoiceState.separated || VoiceState.window) return;
			VoicechatConnection sender = event.getSenderConnection();
			VoicechatConnection receiver = event.getReceiverConnection();
			if (sender == null || receiver == null) return;
			Groups.Side a = VoiceState.sides.getOrDefault(sender.getPlayer().getUuid(), Groups.Side.NONE);
			Groups.Side b = VoiceState.sides.getOrDefault(receiver.getPlayer().getUuid(), Groups.Side.NONE);
			if (Groups.areSeparated(a, b)) {
				event.cancel();
			}
		} catch (Throwable t) {
			// never break the voice server because of us
		}
	}
}
