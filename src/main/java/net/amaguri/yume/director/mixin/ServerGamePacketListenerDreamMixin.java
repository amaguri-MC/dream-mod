package net.amaguri.yume.director.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import net.amaguri.yume.director.DreamLock;

/** The spectator menu "teleport to player" does nothing while the viewer is pinned to a camera. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerDreamMixin {
	@Inject(method = "handleTeleportToEntityPacket(Lnet/minecraft/network/protocol/game/ServerboundTeleportToEntityPacket;)V", at = @At("HEAD"), cancellable = true)
	private void yume$noSpectatorTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
		ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).getPlayer();
		if (player != null && DreamLock.isDreaming(player.getUUID())) {
			ci.cancel();
		}
	}
}
