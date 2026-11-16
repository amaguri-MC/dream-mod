package net.amaguri.yume.client;

/** Implemented onto AvatarRenderState by a mixin: how this avatar is drawn this frame. */
public interface YumeRenderFlag {
	byte yume$getMode();

	void yume$setMode(byte mode);

	/** Belly swelling 0..1 (0 = normal). */
	float yume$getBelly();

	void yume$setBelly(float belly);
}
