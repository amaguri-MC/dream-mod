package net.amaguri.yume.client.mixin;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import net.amaguri.yume.client.RevealClient;

/** The tab list, spectator menu, social screen and name suggestions all read these lists. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "getListedOnlinePlayers", at = @At("RETURN"), cancellable = true)
	private void yume$hideListed(CallbackInfoReturnable<Collection<PlayerInfo>> cir) {
		cir.setReturnValue(RevealClient.filter(cir.getReturnValue()));
	}

	@Inject(method = "getOnlinePlayers", at = @At("RETURN"), cancellable = true)
	private void yume$hideOnline(CallbackInfoReturnable<Collection<PlayerInfo>> cir) {
		cir.setReturnValue(RevealClient.filter(cir.getReturnValue()));
	}

	@Inject(method = "getOnlinePlayerIds", at = @At("RETURN"), cancellable = true)
	private void yume$hideIds(CallbackInfoReturnable<Collection<UUID>> cir) {
		cir.setReturnValue(RevealClient.filterIds(cir.getReturnValue()));
	}

	@Inject(method = "getSeenPlayers", at = @At("RETURN"), cancellable = true)
	private void yume$hideSeen(CallbackInfoReturnable<Map<UUID, PlayerInfo>> cir) {
		cir.setReturnValue(RevealClient.filterSeen(cir.getReturnValue()));
	}
}
