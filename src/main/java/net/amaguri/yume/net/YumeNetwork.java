package net.amaguri.yume.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers all payload types (both sides). CONTRACT FILE. */
public final class YumeNetwork {
	private YumeNetwork() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay().register(DreamStartPayload.TYPE, DreamStartPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DreamEndPayload.TYPE, DreamEndPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RevealPayload.TYPE, RevealPayload.CODEC);
	}
}
