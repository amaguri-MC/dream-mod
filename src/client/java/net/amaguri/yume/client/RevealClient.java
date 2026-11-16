package net.amaguri.yume.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

import net.amaguri.yume.YumeMod;
import net.amaguri.yume.net.RevealPayload;

/** Which players this client must not know about, and which avatars are drawn as flat silhouettes. */
public final class RevealClient {
	public static final byte MODE_NORMAL = 0;
	public static final byte MODE_BLACK = 1;
	public static final byte MODE_INVISIBLE = 2;
	public static final byte MODE_WHITE = 3;

	private static volatile Map<UUID, RevealPayload.Entry> entries = Map.of();
	private static volatile Set<Integer> whiteIds = Set.of();

	private static final PlayerSkin[] BLACK_SKINS = new PlayerSkin[2];
	private static final PlayerSkin[] WHITE_SKINS = new PlayerSkin[2];

	private RevealClient() {
	}

	public static void setEntries(List<RevealPayload.Entry> list) {
		if (list == null || list.isEmpty()) {
			entries = Map.of();
			return;
		}
		Map<UUID, RevealPayload.Entry> map = new HashMap<>();
		for (RevealPayload.Entry e : list) {
			map.put(e.uuid(), e);
		}
		entries = map;
	}

	public static void setWhiteIds(List<Integer> ids) {
		whiteIds = (ids == null || ids.isEmpty()) ? Set.of() : new HashSet<>(ids);
	}

	public static void clearWhiteIds() {
		whiteIds = Set.of();
	}

	public static void reset() {
		entries = Map.of();
		whiteIds = Set.of();
	}

	public static boolean hidesInfo(UUID uuid) {
		RevealPayload.Entry e = entries.get(uuid);
		return e != null && e.hideInfo();
	}

	public static byte renderMode(Avatar entity) {
		if (!(entity instanceof Player) && whiteIds.contains(entity.getId())) {
			return MODE_WHITE;
		}
		RevealPayload.Entry e = entries.get(entity.getUUID());
		if (e == null) {
			return MODE_NORMAL;
		}
		return switch (e.render()) {
			case RevealPayload.RENDER_BLACK_SILHOUETTE -> MODE_BLACK;
			case RevealPayload.RENDER_INVISIBLE -> MODE_INVISIBLE;
			default -> MODE_NORMAL;
		};
	}

	public static Collection<PlayerInfo> filter(Collection<PlayerInfo> infos) {
		if (entries.isEmpty() || infos == null || infos.isEmpty()) {
			return infos;
		}
		List<PlayerInfo> out = new ArrayList<>(infos.size());
		for (PlayerInfo info : infos) {
			if (!hidesInfo(info.getProfile().id())) {
				out.add(info);
			}
		}
		return out;
	}

	public static Collection<UUID> filterIds(Collection<UUID> ids) {
		if (entries.isEmpty() || ids == null || ids.isEmpty()) {
			return ids;
		}
		List<UUID> out = new ArrayList<>(ids.size());
		for (UUID id : ids) {
			if (!hidesInfo(id)) {
				out.add(id);
			}
		}
		return out;
	}

	public static Map<UUID, PlayerInfo> filterSeen(Map<UUID, PlayerInfo> seen) {
		if (entries.isEmpty() || seen == null || seen.isEmpty()) {
			return seen;
		}
		Map<UUID, PlayerInfo> out = new HashMap<>();
		for (Map.Entry<UUID, PlayerInfo> e : seen.entrySet()) {
			if (!hidesInfo(e.getKey())) {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	/** Removes the names of hidden players from a list of name suggestions. */
	public static Collection<String> filterNames(ClientPacketListener connection, Collection<String> names) {
		if (entries.isEmpty() || names == null || names.isEmpty() || connection == null) {
			return names;
		}
		Set<String> hidden = new HashSet<>();
		for (RevealPayload.Entry e : entries.values()) {
			if (!e.hideInfo()) {
				continue;
			}
			PlayerInfo info = connection.getPlayerInfo(e.uuid());
			if (info != null) {
				hidden.add(info.getProfile().name());
			}
		}
		if (hidden.isEmpty()) {
			return names;
		}
		List<String> out = new ArrayList<>(names.size());
		for (String n : names) {
			if (!hidden.contains(n)) {
				out.add(n);
			}
		}
		return out;
	}

	/** Turns an extracted avatar state into a flat one-colour figure: no gear, cape, held items, name or tint. */
	public static void applySilhouette(AvatarRenderState state, byte mode) {
		boolean white = mode == MODE_WHITE;
		PlayerModelType model = state.skin != null ? state.skin.model() : PlayerModelType.WIDE;
		state.skin = skinFor(white, model);
		state.nameTag = null;
		state.scoreText = null;
		state.showCape = false;
		state.showExtraEars = false;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
		state.arrowCount = 0;
		state.stingerCount = 0;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.rightHandItemStack = ItemStack.EMPTY;
		state.leftHandItemStack = ItemStack.EMPTY;
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.headItem.clear();
		state.heldOnHead.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		state.isInvisible = false;
		state.isInvisibleToPlayer = false;
		state.hasRedOverlay = false;
		state.displayFireAnimation = false;
		if (white) {
			state.shadowRadius = 0f;
			state.shadowPieces.clear();
		}
	}

	private static PlayerSkin skinFor(boolean white, PlayerModelType model) {
		PlayerSkin[] cache = white ? WHITE_SKINS : BLACK_SKINS;
		int i = model == PlayerModelType.SLIM ? 1 : 0;
		PlayerSkin skin = cache[i];
		if (skin == null) {
			String name = white ? "silhouette_white" : "silhouette_black";
			ClientAsset.ResourceTexture tex = new ClientAsset.ResourceTexture(YumeMod.id("entity/" + name), YumeMod.id("textures/entity/" + name + ".png"));
			skin = new PlayerSkin(tex, null, null, model, true);
			cache[i] = skin;
		}
		return skin;
	}
}
