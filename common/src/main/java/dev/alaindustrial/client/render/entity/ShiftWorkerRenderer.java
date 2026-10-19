package dev.alaindustrial.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.render.entity.layers.ShiftWorkerGearLayer;
import dev.alaindustrial.client.render.entity.layers.ShiftWorkerGlassLayer;
import dev.alaindustrial.client.render.entity.layers.ShiftWorkerNumberLayer;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Draws a shift worker (MOD-832): the look's skin on a player's body — the classic or the slim one, swapped per
 * frame the way vanilla's ageable renderer swaps its adult and baby models — and three layers on top: the balloon with
 * its hose, the number on the balloon, and the window's glass.
 *
 * <p><b>The look comes from the synced registry</b> {@code alaindustrial:shift_worker_look} (ADR-050 point 6): the
 * entity syncs the look's id, the renderer reads the entry from the client's copy of the registry. A resource pack
 * that replaces a look's texture file changes the worker without code (R28). An id the client does not know falls
 * back to the first built-in face of the right body, never to a missing texture.
 */
public class ShiftWorkerRenderer extends MobRenderer<ShiftWorker, ShiftWorkerRenderState, ShiftWorkerModel> {

	public static final ModelLayerLocation CLASSIC_LAYER =
			new ModelLayerLocation(Industrialization.id("shift_worker"), "main");
	public static final ModelLayerLocation SLIM_LAYER =
			new ModelLayerLocation(Industrialization.id("shift_worker"), "slim");
	public static final ModelLayerLocation GEAR_LAYER =
			new ModelLayerLocation(Industrialization.id("shift_worker"), "gear");
	public static final ModelLayerLocation GLASS_LAYER =
			new ModelLayerLocation(Industrialization.id("shift_worker"), "glass");

	/** Skins used when the worker's look is unknown to this client. */
	public static final Identifier FALLBACK_CLASSIC =
			Industrialization.id("textures/entity/shift_worker/open_m1.png");
	public static final Identifier FALLBACK_SLIM =
			Industrialization.id("textures/entity/shift_worker/open_f1.png");

	private final ShiftWorkerModel classic;
	private final ShiftWorkerModel slim;

	public ShiftWorkerRenderer(EntityRendererProvider.Context context) {
		super(context, new ShiftWorkerModel(context.bakeLayer(CLASSIC_LAYER)), 0.5F);
		this.classic = this.model;
		this.slim = new ShiftWorkerModel(context.bakeLayer(SLIM_LAYER));
		addLayer(new ShiftWorkerGearLayer(this, context.bakeLayer(GEAR_LAYER)));
		addLayer(new ShiftWorkerNumberLayer(this, context.getFont()));
		addLayer(new ShiftWorkerGlassLayer(this, context.bakeLayer(GLASS_LAYER)));
	}

	@Override
	public ShiftWorkerRenderState createRenderState() {
		return new ShiftWorkerRenderState();
	}

	@Override
	public void extractRenderState(ShiftWorker worker, ShiftWorkerRenderState state, float partialTicks) {
		super.extractRenderState(worker, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(worker, state, partialTicks, itemModelResolver);
		state.number = worker.number();
		ShiftWorkerLook look = look(worker);
		if (look != null) {
			state.texture = look.textureLocation();
			state.slim = look.slim();
			state.helmet = look.helmet();
			state.hair = look.hair();
		} else {
			state.slim = worker.slim();
			state.texture = state.slim ? FALLBACK_SLIM : FALLBACK_CLASSIC;
			state.helmet = ShiftWorkerLook.Helmet.OPEN;
			state.hair = ShiftWorkerLook.Hair.NONE;
		}
	}

	/** The worker's look from the client's copy of the synced registry, or null when it does not have it. */
	private static @Nullable ShiftWorkerLook look(ShiftWorker worker) {
		Identifier id = Identifier.tryParse(worker.lookId());
		if (id == null) {
			return null;
		}
		return worker.level().registryAccess().lookup(ModDataRegistries.SHIFT_WORKER_LOOK)
				.map(registry -> registry.getValue(id)).orElse(null);
	}

	@Override
	public void submit(ShiftWorkerRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		this.model = state.slim ? slim : classic;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(ShiftWorkerRenderState state) {
		return state.texture;
	}
}
