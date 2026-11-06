package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.quarry.QuarryFrameLayout;
import dev.alaindustrial.block.entity.quarry.QuarryFramePlacements;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.core.quarry.QuarryMotion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The formed quarry frame (MOD-950), drawn from the controller's block entity in the designer's model: four towers,
 * the two paths, the bridge with its saddles and the cart with its drill.
 *
 * <p>Once the frame forms every cell of it is invisible ({@code QuarryFramePart}), so the whole frame is this
 * renderer's geometry ({@link QuarryGeometry}, generated from the designer's {@code .bbmodel}). A loose frame keeps its
 * own block models and this renderer draws nothing. Where the parts stand comes from the controller's
 * {@link QuarryFrameLayout}, which reaches the client in the block entity's update tag.
 *
 * <p><b>Placing a part.</b> Where each part stands and how it turns is {@link QuarryFramePlacements}, the list the
 * frame's collision is cut from as well; this renderer applies it as
 * {@code translate(centre) · R_Y(yaw) · translate(-local)}.
 *
 * <p><b>Moving parts</b> (MOD-952). The bridge with its saddles, the cart and the drill stand where the quarry's work
 * cycle has them at this frame's moment ({@code QuarryDigger#pose}): the bridge along the paths, the cart along the
 * bridge, and the drill lowered by growing its pipe one block-long section at a time under the cart, its pipe and
 * head turning while it drills.
 *
 * <p><b>Texture.</b> The designer's 512 x 512 atlas under {@code textures/entity/quarry/}, stitched into the block
 * atlas by a directory source in {@code atlases/blocks.json}, as the mob wheel's sheets are.
 */
public final class QuarryFrameRenderer
		implements BlockEntityRenderer<QuarryControllerBlockEntity, QuarryFrameRenderer.State> {

	private static final float PIXEL = 1.0F / 16.0F;

	private static final AffineCubeMesh TOWER = new AffineCubeMesh(QuarryGeometry.TOWER);
	private static final AffineCubeMesh PATH = new AffineCubeMesh(QuarryGeometry.PATH);
	private static final AffineCubeMesh SADDLE = new AffineCubeMesh(QuarryGeometry.SADDLE);
	private static final AffineCubeMesh BRIDGE = new AffineCubeMesh(QuarryGeometry.BRIDGE);
	private static final AffineCubeMesh CART = new AffineCubeMesh(QuarryGeometry.CART);
	/** By {@link QuarryFramePlacements.Part} ordinal; the drill ({@code null}) is drawn from its own parts. */
	private static final AffineCubeMesh[] MESHES = {TOWER, PATH, SADDLE, BRIDGE, CART, null};
	private static final AffineCubeMesh DRILL_MOUNT = new AffineCubeMesh(QuarryGeometry.DRILL_MOUNT);
	private static final AffineCubeMesh DRILL_SEGMENT = new AffineCubeMesh(QuarryGeometry.DRILL_SEGMENT);
	private static final AffineCubeMesh DRILL_PIPE = new AffineCubeMesh(QuarryGeometry.DRILL_PIPE);
	private static final AffineCubeMesh DRILL_HEAD_FRAME = new AffineCubeMesh(QuarryGeometry.DRILL_HEAD_FRAME);
	private static final AffineCubeMesh DRILL_HEAD = new AffineCubeMesh(QuarryGeometry.DRILL_HEAD);
	/** How fast the pipe and the head turn while drilling: one turn a second. */
	private static final float SPIN_DEGREES_PER_TICK = 18.0F;
	/**
	 * How far the bridge with its saddles, the cart and the drill ride below the model's own height, in blocks (owner,
	 * 2026-11-06): the saddle's top lies in the plane of the head's top plate, which reaches a little over the path, so
	 * at the towers the two flickered into each other; two pixels lower the saddle slides under the plate's edge.
	 */
	private static final float RIDE_LOWER = 2.0F * PIXEL;
	/** The top of the pipe's block-long section in the model, pixels: under the mount plate. */
	private static final float SECTION_TOP = 96.0F;


	private static final SpriteId SPRITE = Sheets.BLOCK_ENTITIES_MAPPER.apply(Industrialization.id("quarry/frame"));
	/** The stub of cable into the socket foundation ({@link QuarryCableJoint}). */
	private static final SpriteId CABLE = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("copper_cable"));
	/**
	 * Back faces culled: where two parts meet, their end faces lie in one plane facing each other, and a sheet that
	 * drew both flickered between them at every joint of the bridge and the paths.
	 */
	private static final RenderType TYPE = RenderTypes.entityCutoutCull(Sheets.BLOCKS_MAPPER.sheet());

	private final SpriteGetter sprites;

	public QuarryFrameRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** One part where it stands: which mesh, its centre relative to the controller block, its turn and light. */
	record Placement(int mesh, float x, float y, float z, float yaw, int light) {
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		final List<Placement> placements = new ArrayList<>();
		/** Faces of the socket foundation a cable touches, its height from the controller, and its light. */
		int cableFaces;
		/** Whether a drill bit is in the slot: without one the cart hangs no drill (owner, 2026-11-05). */
		boolean drill;
		/** How far the drill hangs below its resting length, blocks; its turn; the light of each pipe section. */
		float drop;
		float spin;
		int[] sectionLights = new int[0];
		int headLight;
		/** How red-hot the head glows, 0 to 1: from half the heat scale to its top (MOD-952). */
		float glow;
		float socketY;
		int socketLight;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(QuarryControllerBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		state.placements.clear();
		state.cableFaces = 0;
		state.drill = entity.hasDrillBit();
		state.glow = Math.max(0.0F, Math.min(1.0F, (entity.cooling.syncedHeatPermille() - 500) / 500.0F));
		QuarryFrameLayout layout = entity.layout();
		Level level = entity.getLevel();
		if (layout == null || level == null) {
			return;
		}
		float bridge = layout.bridgeAt() + 0.5F;
		float cart = layout.cartAt() + 0.5F;
		float tip = layout.headY() + 3.0F;
		boolean spinning = false;
		double now = level.getGameTime() + (double) partialTicks;
		if (entity.digger().placed()) {
			QuarryMotion.Pose pose = entity.digger().pose(now);
			bridge = pose.along();
			cart = pose.across();
			tip = pose.tip();
			spinning = pose.spinning();
		}
		place(layout, entity.getBlockPos(), level, state.placements, bridge, cart);
		float restingTip = layout.baseY() + QuarryGeometry.DRILL_TIP * PIXEL - RIDE_LOWER;
		state.drop = QuarryMotion.drop(restingTip, tip);
		state.spin = spinning ? (float) (now * SPIN_DEGREES_PER_TICK % 360.0D) : 0.0F;
		lightTheDrill(layout, level, state, bridge, cart, tip);
		BlockPos socket = new BlockPos(entity.getBlockPos().getX(), layout.baseY(), entity.getBlockPos().getZ());
		state.cableFaces = QuarryCableJoint.cabledFaces(level, socket);
		state.socketY = layout.baseY() - entity.getBlockPos().getY();
		state.socketLight = lightAbove(level, socket);
	}

	/**
	 * Every part of the frame ({@link QuarryFramePlacements}) with the bridge and the cart at {@code bridge} and
	 * {@code cart}, relative to the controller block and lit.
	 */
	static void place(QuarryFrameLayout layout, BlockPos controller, Level level, List<Placement> out, float bridge,
			float cart) {
		float baseY = layout.baseY() - controller.getY();
		for (QuarryFramePlacements.Placement part : QuarryFramePlacements.of(layout, bridge, cart)) {
			int light = lightAbove(level, BlockPos.containing(part.x(), part.lightY(), part.z()));
			out.add(new Placement(part.part().ordinal(), part.x() - controller.getX(), baseY,
					part.z() - controller.getZ(), part.yaw(), light));
		}
	}

	/**
	 * The light of each block-long section of the drill's pipe, from the cart down, and of its head: the pipe runs
	 * from the sunlit top of the frame into a dark pit, and one light for all of it drew either end wrong.
	 */
	private static void lightTheDrill(QuarryFrameLayout layout, Level level, State state, float bridge, float cart,
			float tip) {
		boolean alongX = layout.alongX();
		double x = alongX ? bridge : cart;
		double z = alongX ? cart : bridge;
		int sections = (int) Math.floor(state.drop) + 2;
		if (state.sectionLights.length != sections) {
			state.sectionLights = new int[sections];
		}
		float sectionTop = layout.baseY() + SECTION_TOP * PIXEL - RIDE_LOWER;
		for (int k = 0; k < sections; k++) {
			state.sectionLights[k] = lightAbove(level, BlockPos.containing(x, sectionTop - k - 0.5F, z));
		}
		state.headLight = lightAbove(level, BlockPos.containing(x, tip + 0.5F, z));
	}

	/** How many cells above a part's light cell are read: a player's block on the frame must not black the model. */
	private static final int LIGHT_CELLS = 4;

	/**
	 * The brightest light of the open cells above a part: its own light cell and the few over it, sky and block light
	 * each at their brightest. One cell alone went dark the moment a player set a block on the frame, and the model
	 * under it was drawn black.
	 */
	static int lightAbove(Level level, BlockPos cell) {
		int light = 0;
		for (int up = 0; up < LIGHT_CELLS; up++) {
			light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, cell.above(up)));
		}
		return light;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.placements.isEmpty()) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		for (Placement placement : state.placements) {
			QuarryFramePlacements.Part part = QuarryFramePlacements.Part.values()[placement.mesh()];
			AffineCubeMesh mesh = MESHES[placement.mesh()];
			if (part == QuarryFramePlacements.Part.DRILL ? !state.drill : mesh.isEmpty()) {
				continue;
			}
			poseStack.pushPose();
			boolean riding = part == QuarryFramePlacements.Part.SADDLE || part == QuarryFramePlacements.Part.BRIDGE
					|| part == QuarryFramePlacements.Part.CART || part == QuarryFramePlacements.Part.DRILL;
			poseStack.translate(placement.x(), placement.y() - (riding ? RIDE_LOWER : 0.0F), placement.z());
			Poses.rotate(poseStack, Axis.YP.rotationDegrees(placement.yaw()));
			poseStack.translate(-part.localX() * PIXEL, 0.0F, -part.localZ() * PIXEL);
			if (part == QuarryFramePlacements.Part.DRILL) {
				drill(state, poseStack, collector, sprite, placement.light());
			} else {
				emit(poseStack, collector, mesh, sprite, placement.light());
			}
			poseStack.popPose();
		}
		if (state.cableFaces != 0) {
			TextureAtlasSprite cable = this.sprites.get(CABLE);
			int faces = state.cableFaces;
			int light = state.socketLight;
			poseStack.pushPose();
			poseStack.translate(0.0F, state.socketY, 0.0F);
			collector.submitCustomGeometry(poseStack, TYPE,
					(pose, consumer) -> QuarryCableJoint.emit(pose, consumer, cable, light, faces));
			poseStack.popPose();
		}
	}

	/**
	 * The drill in the cart's frame: the mount under the cart, one pipe section for every block the drill hangs below
	 * its resting length and one more, then the head's cage and the head lowered by the same. Everything under the
	 * mount turns as one about the pipe's axis, which is the cart frame's own: the rods, the cage and the augers are
	 * one drill string, and turning only some of them drove the augers through the cage (owner, 2026-11-06).
	 */
	private static void drill(State state, PoseStack poseStack, SubmitNodeCollector collector,
			TextureAtlasSprite sprite, int mountLight) {
		emit(poseStack, collector, DRILL_MOUNT, sprite, mountLight);
		poseStack.pushPose();
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(state.spin));
		// Whole sections down to the cage, then one cut to the length that is left: a whole one reached into the cage
		// and its rods lay over the cage's own (owner, 2026-11-06).
		int whole = (int) Math.floor(state.drop);
		for (int k = 0; k <= whole && k < state.sectionLights.length; k++) {
			int light = state.sectionLights[k];
			poseStack.pushPose();
			poseStack.translate(0.0F, -k, 0.0F);
			emit(poseStack, collector, DRILL_SEGMENT, sprite, light);
			emit(poseStack, collector, DRILL_PIPE, sprite, light);
			poseStack.popPose();
		}
		float rest = state.drop - whole;
		if (rest > 0.01F && whole + 1 < state.sectionLights.length) {
			int light = state.sectionLights[whole + 1];
			float sectionBottom = SECTION_TOP * PIXEL - 1.0F - whole;
			poseStack.pushPose();
			// The section's top (SECTION_TOP) onto the last whole one's bottom, squeezed to the length left.
			poseStack.translate(0.0F, sectionBottom, 0.0F);
			poseStack.scale(1.0F, rest, 1.0F);
			poseStack.translate(0.0F, -SECTION_TOP * PIXEL, 0.0F);
			emit(poseStack, collector, DRILL_SEGMENT, sprite, light);
			emit(poseStack, collector, DRILL_PIPE, sprite, light);
			poseStack.popPose();
		}
		poseStack.translate(0.0F, -state.drop, 0.0F);
		// A hot head glows: it keeps its red and loses green and blue, and it lights itself the hotter it is.
		int green = Math.round(255 - 170 * state.glow);
		int blue = Math.round(255 - 205 * state.glow);
		int light = state.glow > 0.5F ? LightCoordsUtil.max(state.headLight, LightCoordsUtil.pack(10, 0))
				: state.headLight;
		collector.submitCustomGeometry(poseStack, TYPE,
				(pose, consumer) -> DRILL_HEAD_FRAME.emit(pose, consumer, sprite, light, 255, green, blue));
		collector.submitCustomGeometry(poseStack, TYPE,
				(pose, consumer) -> DRILL_HEAD.emit(pose, consumer, sprite, light, 255, green, blue));
		poseStack.popPose();
	}

	private static void emit(PoseStack poseStack, SubmitNodeCollector collector, AffineCubeMesh mesh,
			TextureAtlasSprite sprite, int light) {
		collector.submitCustomGeometry(poseStack, TYPE, (pose, consumer) -> mesh.emit(pose, consumer, sprite, light));
	}

	/** The frame stands up to fifteen blocks from the controller in every direction it is built. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer (MOD-633): the whole frame, its
	 * heads' beams and the cart's drill included. A loose controller draws nothing, so its own block is enough.
	 *
	 * <p>No {@code @Override}: only NeoForge's {@code BlockEntityRenderer} declares this method; Fabric never tests a
	 * block entity against the frustum. {@code OffScreenRendererBoxTest} on the NeoForge lane fails if it stops
	 * overriding.
	 */
	public AABB getRenderBoundingBox(QuarryControllerBlockEntity blockEntity) {
		QuarryFrameLayout layout = blockEntity.layout();
		if (layout == null) {
			return new AABB(blockEntity.getBlockPos());
		}
		// Down to the bottom of the world: the drill goes into the pit as deep as the quarry digs.
		Level level = blockEntity.getLevel();
		int bottom = level == null ? layout.baseY() : level.getMinY();
		return new AABB(layout.minX() - 1, bottom, layout.minZ() - 1, layout.maxX() + 2, layout.headY() + 4,
				layout.maxZ() + 2);
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
