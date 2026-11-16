package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

import net.amaguri.yume.client.DreamClient;

@Mixin(Hud.class)
public abstract class HudMixin {
	/** No debug screen while a dream is on. */
	@Inject(method = "extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
	private void yume$noDebug(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (DreamClient.isActive()) {
			ci.cancel();
		}
	}
}
