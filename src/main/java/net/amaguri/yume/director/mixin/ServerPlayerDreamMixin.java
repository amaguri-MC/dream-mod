package net.amaguri.yume.director.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import net.amaguri.yume.director.DreamLock;

/** While a viewer is pinned to a camera: sneaking / attacking cannot detach the camera, and no damage is taken. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDreamMixin {
	@Inject(method = "setCamera(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void yume$keepCamera(Entity target, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Entity locked = DreamLock.camera(self.getUUID());
		if (locked != null && target != locked) {
			ci.cancel();
		}
	}

	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void yume$noDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (DreamLock.isDreaming(self.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
