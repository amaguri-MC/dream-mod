package net.amaguri.yume.client;

/** Implemented onto AvatarRenderState by a mixin: how this avatar is drawn this frame (RevealClient.MODE_*). */
public interface YumeRenderFlag {
	byte yume$getMode();

	void yume$setMode(byte mode);
}
