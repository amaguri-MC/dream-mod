package net.amaguri.yume.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.amaguri.yume.YumeMod;

/**
 * Server -> client: the full list of players this client must not "know" about, and how to draw them.
 * Replaces the previous list. Empty list = nothing hidden. CONTRACT FILE.
 */
public record RevealPayload(List<Entry> entries) implements CustomPacketPayload {
	public static final byte RENDER_NORMAL = 0;
	public static final byte RENDER_BLACK_SILHOUETTE = 1;
	public static final byte RENDER_INVISIBLE = 2;

	/**
	 * @param hideInfo hide from tab list, name tags, chat-command name suggestions, spectator menu, and any other
	 *                 client UI that lists player names
	 * @param render   RENDER_* how to draw this player's entity
	 */
	public record Entry(UUID uuid, boolean hideInfo, byte render) {
	}

	public static final CustomPacketPayload.Type<RevealPayload> TYPE = new CustomPacketPayload.Type<>(YumeMod.id("reveal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RevealPayload> CODEC = StreamCodec.ofMember(RevealPayload::write, RevealPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (Entry e : entries) {
			buf.writeLong(e.uuid().getMostSignificantBits());
			buf.writeLong(e.uuid().getLeastSignificantBits());
			buf.writeBoolean(e.hideInfo());
			buf.writeByte(e.render());
		}
	}

	private static RevealPayload read(RegistryFriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), 256);
		List<Entry> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			UUID u = new UUID(buf.readLong(), buf.readLong());
			list.add(new Entry(u, buf.readBoolean(), buf.readByte()));
		}
		return new RevealPayload(list);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
