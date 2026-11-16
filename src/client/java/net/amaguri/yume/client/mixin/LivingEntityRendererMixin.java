package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import net.amaguri.yume.client.RevealClient;
import net.amaguri.yume.client.YumeRenderFlag;

/** Silhouettes use a flat render type: black takes any light as black, white ignores light and shading. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
	private void yume$silhouetteType(LivingEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		byte mode = ((YumeRenderFlag) (Object) avatar).yume$getMode();
		if (mode != RevealClient.MODE_BLACK && mode != RevealClient.MODE_WHITE) {
			return;
		}
		Identifier tex = avatar.skin.body().texturePath();
		cir.setReturnValue(mode == RevealClient.MODE_WHITE ? RenderTypes.eyes(tex) : RenderTypes.entityCutout(tex));
	}
}
