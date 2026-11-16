package net.amaguri.yume.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

import net.amaguri.yume.client.DreamClient;

/** During a dream (and just after) the dimension loading screen is replaced by plain black. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
	private void yume$black(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
		if (DreamClient.hidesLoading()) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
			ci.cancel();
		}
	}

	@Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
	private void yume$blackBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
		if (DreamClient.hidesLoading()) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
			ci.cancel();
		}
	}
}
