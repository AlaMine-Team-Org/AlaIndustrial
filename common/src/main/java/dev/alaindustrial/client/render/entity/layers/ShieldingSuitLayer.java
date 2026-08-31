package dev.alaindustrial.client.render.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the shielding suit on a villager as overlay textures on the villager's OWN model
 * (MOD-536) — the same trick {@code VillagerProfessionLayer} uses for its robes.
 *
 * <p><b>Why an overlay and not armor.</b> Vanilla armor rendering is
 * {@code HumanoidArmorLayer}, which requires a humanoid model and render state; the villager has
 * neither, which is why NO armor — vanilla included — has ever shown on one. Overlay cutouts on
 * the parent {@link VillagerModel} inherit every animation for free (crossed arms, sleeping,
 * walking) and the baby model too, because {@code AgeableMobRenderer} swaps the active model
 * before layers run.
 *
 * <p>Each worn piece submits its own 64×64 overlay whose painted pixels follow the villager UV
 * sheet; transparent pixels show the villager underneath. The head overlay deliberately leaves the
 * HAT region transparent so profession hats (farmer straw and friends) keep their look instead of
 * z-fighting with a hood.
 */
public class ShieldingSuitLayer extends RenderLayer<VillagerRenderState, VillagerModel> {

	private static final Logger LOGGER = LoggerFactory.getLogger("ShieldingSuitLayer");

	private static final Identifier HEAD = Industrialization.id("textures/entity/villager/shielding/head.png");
	private static final Identifier CHEST = Industrialization.id("textures/entity/villager/shielding/chest.png");
	private static final Identifier LEGS = Industrialization.id("textures/entity/villager/shielding/legs.png");
	private static final Identifier FEET = Industrialization.id("textures/entity/villager/shielding/feet.png");

	/** Set after the first failure: a layer that cannot render must vanish, not spam or kill frames. */
	private boolean broken;

	public ShieldingSuitLayer(RenderLayerParent<VillagerRenderState, VillagerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
			VillagerRenderState state, float yaw, float tick) {
		if (this.broken || state.isInvisible || !(state instanceof ShieldingSuitRenderState suit)) {
			return;
		}
		try {
			VillagerModel model = this.getParentModel();
			// Orders 3..6: the profession layer submits its overlays at 1..2, so the suit always
			// sits ON the clothes rather than under them.
			if (!suit.alaindustrial$head().isEmpty()) {
				renderColoredCutoutModel(model, HEAD, poseStack, collector, light, state, -1, 3);
			}
			if (!suit.alaindustrial$chest().isEmpty()) {
				renderColoredCutoutModel(model, CHEST, poseStack, collector, light, state, -1, 4);
			}
			if (!suit.alaindustrial$legs().isEmpty()) {
				renderColoredCutoutModel(model, LEGS, poseStack, collector, light, state, -1, 5);
			}
			if (!suit.alaindustrial$feet().isEmpty()) {
				renderColoredCutoutModel(model, FEET, poseStack, collector, light, state, -1, 6);
			}
		} catch (Throwable t) {
			this.broken = true;
			LOGGER.error("shielding suit overlay failed once and is now disabled until relaunch", t);
		}
	}
}
