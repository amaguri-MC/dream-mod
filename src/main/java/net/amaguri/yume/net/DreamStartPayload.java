package net.amaguri.yume.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.amaguri.yume.YumeMod;

/**
 * Server -> client: "you are now in a dream". The client hides the HUD, fades in from black, applies the filter,
 * types the text, and fades out by itself near durationTicks. CONTRACT FILE.
 *
 * @param scene                Scene enum name
 * @param durationTicks        total length of this dream for this player
 * @param filter               DreamFilter enum name
 * @param text                 overlay text ('' = none, '\n' = new line), typed one character at a time
 * @param textDelayTicks       when the typing starts
 * @param whiteSilhouetteIds   entity ids (in the client level) to draw as pure white silhouettes
 */
public record DreamStartPayload(String scene, int durationTicks, String filter, String text, int textDelayTicks,
		List<Integer> whiteSilhouetteIds) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DreamStartPayload> TYPE = new CustomPacketPayload.Type<>(YumeMod.id("dream_start"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DreamStartPayload> CODEC = StreamCodec.ofMember(DreamStartPayload::write, DreamStartPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(scene);
		buf.writeVarInt(durationTicks);
		buf.writeUtf(filter);
		buf.writeUtf(text);
		buf.writeVarInt(textDelayTicks);
		buf.writeVarInt(whiteSilhouetteIds.size());
		for (int id : whiteSilhouetteIds) buf.writeVarInt(id);
	}

	private static DreamStartPayload read(RegistryFriendlyByteBuf buf) {
		String scene = buf.readUtf();
		int dur = buf.readVarInt();
		String filter = buf.readUtf();
		String text = buf.readUtf();
		int delay = buf.readVarInt();
		int n = Math.min(buf.readVarInt(), 256);
		List<Integer> ids = new ArrayList<>(n);
		for (int i = 0; i < n; i++) ids.add(buf.readVarInt());
		return new DreamStartPayload(scene, dur, filter, text, delay, ids);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
