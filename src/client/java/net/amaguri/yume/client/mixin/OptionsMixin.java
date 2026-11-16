package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.CameraType;
import net.minecraft.client.Options;

import net.amaguri.yume.client.DreamClient;

@Mixin(Options.class)
public abstract class OptionsMixin {
	/** The view stays first person for the whole dream (F5 does nothing). */
	@Inject(method = "setCameraType(Lnet/minecraft/client/CameraType;)V", at = @At("HEAD"), cancellable = true)
	private void yume$keepFirstPerson(CameraType type, CallbackInfo ci) {
		if (DreamClient.isActive()) {
			ci.cancel();
		}
	}
}
