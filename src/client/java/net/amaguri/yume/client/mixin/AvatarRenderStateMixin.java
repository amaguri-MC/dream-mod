package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import net.amaguri.yume.client.YumeRenderFlag;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements YumeRenderFlag {
	@Unique
	private byte yume$mode;

	@Override
	public byte yume$getMode() {
		return this.yume$mode;
	}

	@Override
	public void yume$setMode(byte mode) {
		this.yume$mode = mode;
	}
}
