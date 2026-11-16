package net.amaguri.yume.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;

/**
 * Simple Voice Chat plugin (loaded only when Simple Voice Chat is installed).
 * OWNER: worker "isolation".
 */
public class YumeVoicePlugin implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return "yume";
	}
}
