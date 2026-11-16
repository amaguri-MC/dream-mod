package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;

import net.amaguri.yume.client.DreamClient;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	/** No pop-ups (advancements, recipes, system messages) over a dream. */
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
	private void yume$noToasts(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (DreamClient.isActive()) {
			ci.cancel();
		}
	}
}
