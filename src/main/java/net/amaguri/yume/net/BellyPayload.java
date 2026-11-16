package net.amaguri.yume.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.amaguri.yume.YumeMod;

/**
 * Server -> client: how swollen the belly of a (mannequin) entity is. 0 = normal, 1 = fully round balloon belly.
 * Only the belly grows (head, arms and legs keep their size). Sent repeatedly while it changes; belly &lt;= 0 clears it.
 * CONTRACT FILE.
 */
public record BellyPayload(int entityId, float belly) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BellyPayload> TYPE = new CustomPacketPayload.Type<>(YumeMod.id("belly"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BellyPayload> CODEC = StreamCodec.ofMember(BellyPayload::write, BellyPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeFloat(belly);
	}

	private static BellyPayload read(RegistryFriendlyByteBuf buf) {
		return new BellyPayload(buf.readVarInt(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
