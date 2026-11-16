package net.amaguri.yume.client.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import net.amaguri.yume.client.RevealClient;

/** Chat command completion must not offer the names of hidden players. */
@Mixin(ClientSuggestionProvider.class)
public abstract class ClientSuggestionProviderMixin {
	@Shadow
	@Final
	private ClientPacketListener connection;

	@Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true)
	private void yume$hideNames(CallbackInfoReturnable<Collection<String>> cir) {
		cir.setReturnValue(RevealClient.filterNames(this.connection, cir.getReturnValue()));
	}
}
