package net.amaguri.yume.director.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;

import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.Groups;
import net.amaguri.yume.schedule.RevealStage;

/** Before the final stage the vanilla "everyone slept, skip the night" never fires; the director handles sleeping itself. */
@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin {
	@Inject(method = "areEnoughDeepSleeping(ILjava/util/List;)Z", at = @At("HEAD"), cancellable = true)
	private void yume$noVanillaSkip(int percentage, List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
		try {
			if (Groups.stage() != RevealStage.FINALE && YumeConfig.get().dreamers != null && !YumeConfig.get().dreamers.isEmpty()) {
				cir.setReturnValue(false);
			}
		} catch (Throwable ignored) {
			// fall back to vanilla
		}
	}
}
