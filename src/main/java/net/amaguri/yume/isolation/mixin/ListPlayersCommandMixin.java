package net.amaguri.yume.isolation.mixin;

import java.util.List;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.level.ServerPlayer;

import net.amaguri.yume.isolation.Gate;

/** /list shows only the players the sender may know about. */
@Mixin(ListPlayersCommand.class)
public abstract class ListPlayersCommandMixin {
	@Inject(method = "format(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/function/Function;)I", at = @At("HEAD"))
	private static void yume$begin(CommandSourceStack source, Function<ServerPlayer, Component> formatter, CallbackInfoReturnable<Integer> cir) {
		Gate.listViewer = source.getPlayer();
	}

	@ModifyVariable(method = "format(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/function/Function;)I",
			at = @At("STORE"), ordinal = 0)
	private static List<ServerPlayer> yume$filter(List<ServerPlayer> players) {
		return Gate.visibleTo(Gate.listViewer, players);
	}

	@Inject(method = "format(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/function/Function;)I", at = @At("RETURN"))
	private static void yume$end(CommandSourceStack source, Function<ServerPlayer, Component> formatter, CallbackInfoReturnable<Integer> cir) {
		Gate.listViewer = null;
	}
}
