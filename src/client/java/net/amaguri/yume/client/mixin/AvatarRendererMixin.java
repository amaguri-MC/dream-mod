package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

import net.amaguri.yume.client.RevealClient;
import net.amaguri.yume.client.YumeRenderFlag;

/** Marks avatars that must be hidden or drawn as a silhouette, and drops their name tag. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void yume$conceal(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		byte mode = RevealClient.renderMode(entity);
		((YumeRenderFlag) (Object) state).yume$setMode(mode);
		if (mode == RevealClient.MODE_NORMAL) {
			if (RevealClient.hidesInfo(entity.getUUID())) {
				state.nameTag = null;
				state.scoreText = null;
			}
			return;
		}
		RevealClient.applySilhouette(state, mode);
	}
}
