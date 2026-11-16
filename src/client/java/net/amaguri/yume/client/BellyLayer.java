package net.amaguri.yume.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws the swollen belly in place of the torso: a ball made of stacked, shrinking boxes that use the
 * wearer's own torso texture. The real torso (and jacket) is hidden by PlayerModelMixin while this is drawn.
 */
public class BellyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final BellyModel MODEL = new BellyModel();

	public BellyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodes, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
		float belly = ((YumeRenderFlag) (Object) state).yume$getBelly();
		if (belly <= 0f || state.isInvisible || state.skin == null) {
			return;
		}
		byte mode = ((YumeRenderFlag) (Object) state).yume$getMode();
		if (mode == RevealClient.MODE_INVISIBLE) {
			return;
		}
		Identifier tex = state.skin.body().texturePath();
		RenderType type = mode == RevealClient.MODE_WHITE ? RenderTypes.eyes(tex) : RenderTypes.entityCutout(tex);
		poseStack.pushPose();
		this.getParentModel().body.translateAndRotate(poseStack);
		BellyModel.Shape shape = new BellyModel.Shape(Math.min(1f, belly));
		// Poses the parts now as well, in case the renderer does not call setupAnim itself.
		MODEL.setupAnim(shape);
		nodes.submitModel(MODEL, shape, poseStack, type, light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
		poseStack.popPose();
	}

	/** Belly size helpers shared with the torso-hiding mixin. */
	public static final class Size {
		/** Width and depth grow to 2.5 times at full belly. */
		public static float widthFactor(float belly) {
			return 1f + 1.5f * belly;
		}

		/** Front-to-back size: 4 px at rest, 12 px at full belly (3 times). */
		public static float depthFactor(float belly) {
			return 1f + 2f * belly;
		}

		/** The belly sticks out mostly to the front (-z): its centre moves forward by this many px. */
		public static float forwardShift(float belly) {
			return 2f * belly;
		}

		/** The arms' sideways push (model px) so they stay outside the belly. */
		public static float armPush(float belly) {
			return 7f * belly;
		}

		private Size() {
		}
	}

	static final class BellyModel extends Model<BellyModel.Shape> {
		record Shape(float belly) {
		}

		private static final int SLICES = 12;
		/** Half-size fractions of the three boxes that make one round-ish slice (corners cut). */
		private static final float[][] STEPS = { { 0.981f, 0.195f }, { 0.831f, 0.556f }, { 0.556f, 0.831f }, { 0.195f, 0.981f } };
		private final ModelPart[][] parts = new ModelPart[SLICES][STEPS.length];

		BellyModel() {
			super(build(), id -> RenderTypes.entityCutout(id));
			for (int i = 0; i < SLICES; i++) {
				for (int k = 0; k < STEPS.length; k++) {
					this.parts[i][k] = this.root.getChild("s" + i + "_" + k);
				}
			}
		}

		private static ModelPart build() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			for (int i = 0; i < SLICES; i++) {
				for (int k = 0; k < STEPS.length; k++) {
					// One texel row of the torso texture (front/back/sides share the same rows).
					root.addOrReplaceChild("s" + i + "_" + k, CubeListBuilder.create().texOffs(16, 16 + i).addBox(-4.0f, -0.5f, -2.0f, 8.0f, 1.0f, 4.0f), PartPose.ZERO);
				}
			}
			return LayerDefinition.create(mesh, 64, 64).bakeRoot();
		}

		@Override
		public void setupAnim(Shape shape) {
			float b = Math.max(0f, Math.min(1f, shape.belly()));
			float round = smooth(b / 0.35f);
			float wf = Size.widthFactor(b);
			float df = Size.depthFactor(b);
			float zc = -Size.forwardShift(b);
			float height = 12f * (1f + 0.3f * b);
			float centre = 6f + 0.2f * b;
			float slice = height / SLICES;
			for (int i = 0; i < SLICES; i++) {
				float t = ((i + 0.5f) / SLICES) * 2f - 1f;
				float ball = (float) Math.sqrt(Math.max(0.0, 1.0 - t * t));
				float sf = 1f + (ball - 1f) * round;
				float y = centre + ((i + 0.5f) / SLICES - 0.5f) * height;
				for (int k = 0; k < STEPS.length; k++) {
					ModelPart part = this.parts[i][k];
					float fx = 1f + (STEPS[k][0] - 1f) * round;
					float fz = 1f + (STEPS[k][1] - 1f) * round;
					part.x = 0f;
					part.y = y;
					part.z = zc;
					part.xScale = wf * sf * fx;
					part.zScale = df * sf * fz;
					part.yScale = slice * 1.04f;
				}
			}
		}

		private static float smooth(float x) {
			x = Math.max(0f, Math.min(1f, x));
			return x * x * (3f - 2f * x);
		}
	}
}
