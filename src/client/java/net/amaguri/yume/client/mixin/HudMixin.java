package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

import net.amaguri.yume.client.DreamClient;
import net.amaguri.yume.client.DreamOverlay;

@Mixin(value = Hud.class, priority = 100)
public abstract class HudMixin {
	/** While a dream is on, the whole HUD pass (vanilla and other mods, e.g. voice chat icons) is skipped; only the dream overlay is drawn. */
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
	private void yume$onlyDream(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (DreamClient.isActive()) {
			DreamOverlay.render(graphics, deltaTracker);
			ci.cancel();
		}
	}

	/** No debug screen while a dream is on. */
	@Inject(method = "extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
	private void yume$noDebug(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (DreamClient.isActive()) {
			ci.cancel();
		}
	}
}
