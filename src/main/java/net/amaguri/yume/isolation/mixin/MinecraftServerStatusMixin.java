package net.amaguri.yume.isolation.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;

import net.amaguri.yume.isolation.Gate;

/** Server list entry: player count without the trapped group, no name sample. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerStatusMixin {
	@Inject(method = "buildPlayerStatus()Lnet/minecraft/network/protocol/status/ServerStatus$Players;", at = @At("RETURN"), cancellable = true)
	private void yume$status(CallbackInfoReturnable<ServerStatus.Players> cir) {
		cir.setReturnValue(Gate.statusPlayers((MinecraftServer) (Object) this, cir.getReturnValue()));
	}
}
