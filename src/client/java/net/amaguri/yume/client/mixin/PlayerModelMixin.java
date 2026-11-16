package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import net.amaguri.yume.client.BellyLayer;
import net.amaguri.yume.client.YumeRenderFlag;

/** With a swollen belly: the plain torso and jacket are not drawn (the belly layer replaces them) and the arms move out. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void yume$belly(AvatarRenderState state, CallbackInfo ci) {
		PlayerModel model = (PlayerModel) (Object) this;
		float belly = ((YumeRenderFlag) (Object) state).yume$getBelly();
		float push = belly > 0f ? BellyLayer.Size.armPush(Math.min(1f, belly)) : 0f;
		model.body.skipDraw = belly > 0f;
		if (belly > 0f) {
			model.jacket.visible = false;
		}
		model.rightArm.x = model.rightArm.getInitialPose().x() - push;
		model.leftArm.x = model.leftArm.getInitialPose().x() + push;
	}
}
