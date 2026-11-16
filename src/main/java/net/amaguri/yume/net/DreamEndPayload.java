package net.amaguri.yume.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.amaguri.yume.YumeMod;

/** Server -> client: the dream is over (restore HUD, remove filter/text). Safe to receive twice. CONTRACT FILE. */
public record DreamEndPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DreamEndPayload> TYPE = new CustomPacketPayload.Type<>(YumeMod.id("dream_end"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DreamEndPayload> CODEC = StreamCodec.unit(new DreamEndPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
