package net.amaguri.yume.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.core.Groups;

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
			ServerTickEvents.END_SERVER_TICK.register(VoiceLink::tick);
		}
		YumeMod.LOG.info("[yume] voice chat plugin ready");
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoicechatServerStartedEvent.class, event -> VoiceLink.setApi(event.getVoicechat()));
		registration.registerEvent(VoicechatServerStoppedEvent.class, event -> VoiceLink.setApi(null));
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
