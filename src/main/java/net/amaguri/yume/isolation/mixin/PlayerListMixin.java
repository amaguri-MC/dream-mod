package net.amaguri.yume.isolation.mixin;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import net.amaguri.yume.isolation.Gate;

/** Join, leave, death and advancement messages all pass through here; the other group's never reach the viewer. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@ModifyVariable(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
			at = @At("HEAD"), argsOnly = true)
	private Function<ServerPlayer, Component> yume$gateBroadcast(Function<ServerPlayer, Component> original) {
		return Gate.wrapBroadcast(original);
	}
}
