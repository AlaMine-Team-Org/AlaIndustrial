package dev.alaindustrial.client.blueprint;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.compat.client.Poses;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Draws a blueprint scene into its own picture-in-picture texture (MOD-797): the blocks of the current
 * step at full brightness (a drawing has no shadows), a new block first as a bare outline, the block the
 * callout points at inside a box of drawing ink. Registered on both loaders from
 * {@code ClientContentManifest.PICTURE_IN_PICTURE_RENDERERS}.
 *
 * <p>The camera turn is built from {@link BlueprintCamera}'s angles in the order its {@code project}
 * uses, so the screen's callouts land on the blocks this draws.
 */
public final class BlueprintSceneRenderer extends PictureInPictureRenderer<BlueprintSceneState> {

	/** White drawing ink. */
	private static final int INK = 0xFFFFFFFF;
	/** Share of a step during which a newly placed block shows only its outline. */
	private static final float OUTLINE_UNTIL = 0.3f;
	/** After its outline a new block drops into its cell from this height, in blocks. */
	private static final float DROP_HEIGHT = 0.6f;
	/** Share of a step a new block takes to drop into place after its outline. */
	private static final float DROP_SPAN = 0.15f;
	/** How far the highlight box breathes in and out, in blocks. */
	private static final double PULSE = 0.035;

	private final Map<BlueprintDef, BlueprintBounds> bounds = new HashMap<>();
	private @Nullable BlockModelResolver resolver;

	@Override
	public Class<BlueprintSceneState> getRenderStateClass() {
		return BlueprintSceneState.class;
	}

	@Override
	protected String getTextureLabel() {
		return "alaindustrial_blueprint";
	}

	/** Centre the scene vertically, as vanilla's inventory doll does. */
	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected void renderToTexture(BlueprintSceneState state, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
		if (resolver == null) {
			resolver = new BlockModelResolver(mc.getModelManager());
		}
		BlueprintCamera camera = state.camera();
		Poses.rotate(pose, new Quaternionf().rotateZ((float) Math.PI)
				.rotateX(camera.pitchRadians()).rotateY(camera.yawRadians()));
		BlueprintBounds box = bounds.computeIfAbsent(state.def(), BlueprintBounds::of);
		Step step = state.def().steps().get(state.step());
		for (int i = 0; i < step.blocks().size(); i++) {
			Placement placement = step.blocks().get(i);
			BlockState block = BlueprintStates.resolve(placement, state.reveal() >= BlueprintDef.LATE_AT);
			if (block == null) {
				continue;
			}
			pose.pushPose();
			boolean isNew = state.def().isNewInStep(state.step(), i);
			pose.translate((float) (placement.x() - box.centerX()),
					(float) (placement.y() - box.centerY()) + (isNew ? dropOffset(state.reveal()) : 0f),
					(float) (placement.z() - box.centerZ()));
			if (isNew && state.reveal() < OUTLINE_UNTIL) {
				collector.submitShapeOutline(pose, block.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
						RenderTypes.lines(), INK, 2f, false);
			} else if (!BlueprintSpecials.drawFluid(block, pose, collector)) {
				BlockModelRenderState model = new BlockModelRenderState();
				resolver.update(model, block, BlockDisplayContext.create());
				model.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
				BlueprintSpecials.drawBlockEntity(placement, block, pose, collector);
			}
			if (i == step.highlight()) {
				double grow = 0.06 + PULSE * Math.sin(System.currentTimeMillis() / 220.0);
				collector.submitShapeOutline(pose, Shapes.box(-grow, -grow, -grow, 1 + grow, 1 + grow, 1 + grow),
						RenderTypes.lines(), INK, 2f, false);
			}
			pose.popPose();
		}
	}

	/** How high above its cell a new block is drawn: dropping right after its outline, resting after. */
	private static float dropOffset(float reveal) {
		if (reveal < OUTLINE_UNTIL || reveal >= OUTLINE_UNTIL + DROP_SPAN) {
			return 0f;
		}
		float t = (reveal - OUTLINE_UNTIL) / DROP_SPAN;
		float eased = 1f - (1f - t) * (1f - t);
		return DROP_HEIGHT * (1f - eased);
	}
}
