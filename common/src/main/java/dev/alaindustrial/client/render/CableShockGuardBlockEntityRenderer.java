package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.core.energy.ShockGuardMaterial;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the insulating stand under a bare cable segment (MOD-279): a thin plate resting on the floor
 * of the cable's own cell, textured with the exact block the player installed.
 *
 * <p>The stand reaches the client for free. {@link CableBlockEntity} writes the block's id in
 * {@code saveAdditional}, and the inherited {@code getUpdateTag} is {@code saveWithoutMetadata} — so
 * the same slim NBT that persists the stand also ships it in the block-entity update packet, and this
 * renderer just reads the field off the client-side entity. No bespoke payload, and no new
 * {@code ContainerData} index (widening that array would touch every machine in the mod).
 *
 * <p><b>The plate grows to meet its neighbours.</b> A 12×12 pad in every cell left visible gaps along
 * a straight run, which read as holes in what is meant to look like one continuous board under the
 * wire. The plate therefore extends to the cell edge on each side where the cable has a horizontal
 * connection, so consecutive stood segments meet flush, while a run's end still stops short and keeps
 * the pad silhouette. UVs are world-aligned (the sprite is sampled at the plate's own coordinates), so
 * the grain continues across the seam instead of restarting in every cell.
 */
public final class CableShockGuardBlockEntityRenderer
		implements BlockEntityRenderer<CableBlockEntity, CableShockGuardBlockEntityRenderer.State> {
	/**
	 * Sprite per stand block, resolved once and reused. A base can hold hundreds of stood segments and
	 * this runs per frame per segment, so the registry lookup and id juggling below must not repeat.
	 * Concurrent because render-state extraction is not guaranteed to be single-threaded.
	 */
	private static final Map<Block, SpriteId> SPRITE_CACHE = new ConcurrentHashMap<>();

	/** Inset of the plate on a side with no cable connection, in block units (2 px). */
	private static final float INSET = 2.0F / 16.0F;
	/** Plate thickness, in block units (2 px). */
	private static final float THICKNESS = 2.0F / 16.0F;

	/**
	 * Lifts the plate a hair off the cell floor. A stand and a {@code DOWN} connection are mutually
	 * exclusive, but nothing stops a cable from sitting on a plain stone floor — and a plate at exactly
	 * y=0 would z-fight with that block's top face.
	 */
	private static final float FLOOR_GAP = 0.005F;

	private final SpriteGetter sprites;

	public CableShockGuardBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CableBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		Block block = entity.shockGuardBlock();
		state.sprite = block == null ? null : SPRITE_CACHE.computeIfAbsent(block, State::spriteFor);
		state.translucent = entity.shockGuard() == ShockGuardMaterial.GLASS;
		BlockState blockState = entity.getBlockState();
		state.extendNorth = connected(blockState, Direction.NORTH);
		state.extendSouth = connected(blockState, Direction.SOUTH);
		state.extendWest = connected(blockState, Direction.WEST);
		state.extendEast = connected(blockState, Direction.EAST);
	}

	private static boolean connected(BlockState state, Direction direction) {
		var property = PipeBlock.PROPERTY_BY_DIRECTION.get(direction);
		return state.hasProperty(property) && state.getValue(property);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		SpriteId spriteId = state.sprite;
		if (spriteId == null) {
			return;
		}
		// Stained and plain glass have real alpha; drawing them on the cutout sheet would slam every
		// pixel to fully opaque and lose the tint that point 4 of the feedback is about.
		RenderType renderType = spriteId.renderType(ignored ->
				state.translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet());
		TextureAtlasSprite sprite = sprites.get(spriteId);
		int light = state.lightCoords;
		float x0 = state.extendWest ? 0.0F : INSET;
		float x1 = state.extendEast ? 1.0F : 1.0F - INSET;
		float z0 = state.extendNorth ? 0.0F : INSET;
		float z1 = state.extendSouth ? 1.0F : 1.0F - INSET;
		submitNodeCollector.submitCustomGeometry(poseStack, renderType,
				(pose, consumer) -> renderPlate(pose, consumer, sprite, light, x0, x1, z0, z1));
	}

	/**
	 * Emits the six faces of the plate. Every face samples the sprite at its own world-aligned
	 * coordinates, so the texture tiles continuously across adjoining cells and the plate's proportions
	 * stay block-scale rather than being stretched to fit each face.
	 */
	private static void renderPlate(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			int light, float x0, float x1, float z0, float z1) {
		float y0 = FLOOR_GAP;
		float y1 = FLOOR_GAP + THICKNESS;

		// Top and bottom, textured with the slice of the sprite matching the plate's footprint.
		quad(pose, consumer, sprite, light, 0.0F, 1.0F, 0.0F,
				x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
				x0, z0, x1, z1);
		quad(pose, consumer, sprite, light, 0.0F, -1.0F, 0.0F,
				x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
				x0, z0, x1, z1);

		// The four sides: as wide as the plate, two pixels tall, sampled from the top of the sprite.
		quad(pose, consumer, sprite, light, 0.0F, 0.0F, -1.0F,
				x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
				x0, 0.0F, x1, THICKNESS);
		quad(pose, consumer, sprite, light, 0.0F, 0.0F, 1.0F,
				x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1,
				x0, 0.0F, x1, THICKNESS);
		quad(pose, consumer, sprite, light, -1.0F, 0.0F, 0.0F,
				x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0,
				z0, 0.0F, z1, THICKNESS);
		quad(pose, consumer, sprite, light, 1.0F, 0.0F, 0.0F,
				x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
				z0, 0.0F, z1, THICKNESS);
	}

	/** One quad, wound in the order given, with its UV rectangle taken as fractions of the sprite. */
	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
			float normalX, float normalY, float normalZ,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float uMin, float vMin, float uMax, float vMax) {
		float u0 = sprite.getU(uMin);
		float u1 = sprite.getU(uMax);
		float v0 = sprite.getV(vMin);
		float v1 = sprite.getV(vMax);
		vertex(pose, consumer, ax, ay, az, u0, v1, light, normalX, normalY, normalZ);
		vertex(pose, consumer, bx, by, bz, u0, v0, light, normalX, normalY, normalZ);
		vertex(pose, consumer, cx, cy, cz, u1, v0, light, normalX, normalY, normalZ);
		vertex(pose, consumer, dx, dy, dz, u1, v1, light, normalX, normalY, normalZ);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
			float u, float v, int light, float normalX, float normalY, float normalZ) {
		consumer.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, normalX, normalY, normalZ);
	}

	public static final class State extends BlockEntityRenderState {
		@Nullable
		private SpriteId sprite;
		private boolean translucent;
		private boolean extendNorth;
		private boolean extendSouth;
		private boolean extendWest;
		private boolean extendEast;

		/**
		 * The block texture that represents {@code block}, by the vanilla naming convention
		 * {@code <namespace>:block/<path>}.
		 *
		 * <p>Two families break that convention because they reuse another block's texture rather than
		 * shipping their own, and both are accepted as stands: the all-bark {@code *_wood} variants draw
		 * the matching {@code *_log}, and the nether {@code *_hyphae} draw {@code *_stem}. They are
		 * rewritten here. Anything else that somehow slipped past the tag checks resolves to whatever
		 * the convention gives and, if that sprite does not exist, renders as the atlas's missing
		 * texture — visibly wrong but harmless, never a crash.
		 */
		private static SpriteId spriteFor(Block block) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			String path = id.getPath();
			if (path.endsWith("_wood")) {
				path = path.substring(0, path.length() - "_wood".length()) + "_log";
			} else if (path.endsWith("_hyphae")) {
				path = path.substring(0, path.length() - "_hyphae".length()) + "_stem";
			}
			return Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath(id.getNamespace(), path));
		}
	}
}
