package net.amaguri.yume.isolation.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;

import net.amaguri.yume.isolation.Gate;

/** Chat, /me, /say, /msg: every chat line is delivered through here. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerChatMixin {
	@Inject(method = "sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V",
			at = @At("HEAD"), cancellable = true)
	private void yume$gateChat(OutgoingChatMessage message, boolean filtered, ChatType.Bound bound, CallbackInfo ci) {
		if (Gate.blockChat((ServerPlayer) (Object) this, message, bound)) {
			ci.cancel();
		}
	}
}
