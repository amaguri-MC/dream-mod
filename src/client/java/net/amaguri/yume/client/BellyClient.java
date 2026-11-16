package net.amaguri.yume.client;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

import net.amaguri.yume.net.BellyPayload;

/** Belly swelling per entity id (0..1), set by the server and cleared when the dream ends. */
public final class BellyClient {
	private static final Map<Integer, Float> BELLY = new HashMap<>();

	private BellyClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BellyPayload.TYPE, (payload, context) -> {
			if (payload.belly() <= 0f || Float.isNaN(payload.belly())) {
				BELLY.remove(payload.entityId());
			} else {
				BELLY.put(payload.entityId(), Math.min(1f, payload.belly()));
			}
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new BellyLayer(avatarRenderer));
			}
		});
	}

	public static float get(int entityId) {
		if (BELLY.isEmpty()) {
			return 0f;
		}
		Float v = BELLY.get(entityId);
		return v == null ? 0f : v;
	}

	public static void clear() {
		BELLY.clear();
	}
}
