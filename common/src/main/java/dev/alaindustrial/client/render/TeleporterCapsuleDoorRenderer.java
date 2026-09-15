package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.CapsuleGlass;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.TeleporterCapsuleBlock;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the teleporter capsule's door wherever its travel has got to (MOD-112).
 *
 * <p>The door is the three front facets of the glass barrel and their steel trims and jambs. Opening,
 * they sink straight down and vanish into the floor at the deck — the approved design, the reactor
 * airlock's gesture. A baked model can show only the two ends of that, so everything else about the
 * capsule is chunk geometry (tools/gen_teleporter_capsule_assets.py) and only the door is drawn here.
 *
 * <p><b>Bound to the station, drawn across all three blocks.</b> The door runs from the deck of the
 * station's block to the crown in the top cell. Hanging it off the station keeps one renderer, one
 * clock and no block entity on the glass cells; the price is {@link #shouldRenderOffScreen} and lighting
 * sampled at one cell, the middle one, which is where the glass mostly is.
 *
 * <p><b>The pieces are the design's own numbers.</b> {@code build_industrial_capsule.py} builds each
 * facet as a box tangent to an octagon and turns it with a model rotation; the pieces below repeat that
 * construction, and each is turned by the same matrix a block model element gets in 26.2
 * ({@code CuboidRotation.SingleAxisRotation}: a rotation about +Y by the element's angle, around its
 * origin). That is what makes the moving facets meet the fixed ones without a seam.
 *
 * <p><b>The texture slides, it does not stretch.</b> A clipped piece keeps the top of its texture and
 * loses the bottom, as the design's own clip does.
 */
public final class TeleporterCapsuleDoorRenderer
		implements BlockEntityRenderer<TeleporterBlockEntity, TeleporterCapsuleDoorRenderer.State> {

	// --- geometry, in pixels of the station's block, for a capsule facing north ---
	/** The deck: the door disappears below this height. */
	private static final float DECK = 10.0f;
	private static final float DOOR_TOP = 40.0f;
	/** How far the door sinks when fully open — its whole height. */
	private static final float TRAVEL = DOOR_TOP - DECK;
	private static final float FACET_RADIUS = 6.6f;
	private static final float FACET_WIDTH = 5.47f;
	private static final float GLASS_THICKNESS = 0.2f;
	private static final float TRIM_THICKNESS = 0.4f;
	private static final float TRIM_HEIGHT = 1.0f;
	private static final float JAMB_RADIUS = 7.0f;
	private static final float JAMB_HALF = 0.4f;
	/** The door facets, counted in eighths of a turn clockwise from north. */
	private static final int[] DOOR_FACETS = {0, 1, 7};
	private static final float[] JAMB_ANGLES = {-67.5f, 67.5f};

	// --- texture sampling, shared with tools/gen_teleporter_capsule_assets.py ---
	/** Designer pixels across the body palette. */
	private static final float PALETTE_SIZE = 64.0f;
	/** The steel "edge" tile the trims and jambs are painted with. */
	private static final float EDGE_TILE_X = 32.0f;
	private static final float EDGE_TILE_Y = 0.0f;
	/** Half a texel of the upscaled palette, in designer pixels (bbmodel_to_block_model.DEFAULT_UV_INSET). */
	private static final float UV_INSET = 0.125f;
	/** Glass textures are sampled inside their one-pixel frame (GLASS_UV_MARGIN). */
	private static final float GLASS_UV_MARGIN = 1.0f;
	/**
	 * One plain pixel of the steel tile (PLAIN_TEXEL in the generator). The trims overlap at the
	 * facets' corners and the jambs share their end planes, so two end faces can occupy the same plane
	 * facing the same way; painted one flat colour, it no longer matters which the depth buffer keeps.
	 */
	private static final float PLAIN_TEXEL_X = 7.0f;
	private static final float PLAIN_TEXEL_Y = 9.0f;

	private static final float EPSILON = 1.0e-4f;

	private static final SpriteId BODY = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("teleporter_capsule_body"));
	private static final RenderType FRAME_TYPE = BODY.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	private static final RenderType GLASS_TYPE = Sheets.translucentBlockItemSheet();
	private static final Map<CapsuleGlass, SpriteId> GLASS_SPRITES = glassSprites();

	/** One box of the door, as the design builds it. */
	private record Piece(float x0, float y0, float z0, float x1, float y1, float z1, boolean glass,
			Matrix4f[] byFacing) {
	}

	private static final List<Piece> PIECES = buildPieces();

	private final SpriteGetter sprites;

	public TeleporterCapsuleDoorRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	private static Map<CapsuleGlass, SpriteId> glassSprites() {
		Map<CapsuleGlass, SpriteId> out = new EnumMap<>(CapsuleGlass.class);
		for (CapsuleGlass glass : CapsuleGlass.values()) {
			Identifier id = switch (glass) {
				case CLEAR -> Industrialization.id("teleporter_capsule_glass");
				case TINTED -> Identifier.withDefaultNamespace("tinted_glass");
				default -> Identifier.withDefaultNamespace(glass.getSerializedName() + "_stained_glass");
			};
			out.put(glass, Sheets.BLOCKS_MAPPER.apply(id));
		}
		return out;
	}

	private static List<Piece> buildPieces() {
		List<Piece> pieces = new ArrayList<>();
		for (int facet : DOOR_FACETS) {
			float theta = facet * 45.0f;
			float cx = 8.0f + (float) Math.sin(Math.toRadians(theta)) * FACET_RADIUS;
			float cz = 8.0f - (float) Math.cos(Math.toRadians(theta)) * FACET_RADIUS;
			// A model element turned by -theta: the design writes the facet's rotation as [0, -theta, 0].
			pieces.add(box(cx, cz, FACET_WIDTH, GLASS_THICKNESS, DECK, DOOR_TOP, -theta, true));
			pieces.add(box(cx, cz, FACET_WIDTH, TRIM_THICKNESS, DECK, DECK + TRIM_HEIGHT, -theta, false));
			pieces.add(box(cx, cz, FACET_WIDTH, TRIM_THICKNESS, DOOR_TOP - TRIM_HEIGHT, DOOR_TOP, -theta, false));
		}
		for (float angle : JAMB_ANGLES) {
			float x = 8.0f + (float) Math.sin(Math.toRadians(angle)) * JAMB_RADIUS;
			float z = 8.0f - (float) Math.cos(Math.toRadians(angle)) * JAMB_RADIUS;
			pieces.add(box(x, z, 2.0f * JAMB_HALF, 2.0f * JAMB_HALF, DECK, DOOR_TOP, 0.0f, false));
		}
		return List.copyOf(pieces);
	}

	/** A box centred on ({@code cx}, {@code cz}), {@code width} along X and {@code depth} along Z. */
	private static Piece box(float cx, float cz, float width, float depth, float y0, float y1, float angle,
			boolean glass) {
		Matrix4f element = new Matrix4f()
				.translation(cx, 0.0f, cz)
				.rotateY((float) Math.toRadians(angle))
				.translate(-cx, 0.0f, -cz);
		Matrix4f[] byFacing = new Matrix4f[4];
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			// A blockstate "y": R turns the north-facing model to its facing; that is a rotation about +Y
			// by -R around the block centre. R for a facing is (toYRot + 180) mod 360.
			float variantY = (facing.toYRot() + 180.0f) % 360.0f;
			byFacing[facing.get2DDataValue()] = new Matrix4f()
					.translation(8.0f, 0.0f, 8.0f)
					.rotateY((float) Math.toRadians(-variantY))
					.translate(-8.0f, 0.0f, -8.0f)
					.mul(element);
		}
		return new Piece(cx - width / 2.0f, y0, cz - depth / 2.0f, cx + width / 2.0f, y1, cz + depth / 2.0f,
				glass, byFacing);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TeleporterBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		Level level = entity.getLevel();
		BlockState station = entity.getBlockState();
		if (level == null || !TeleporterBlock.isFormed(station)) {
			state.visible = false;
			return;
		}
		BlockState middle = level.getBlockState(entity.getBlockPos().above());
		if (!(middle.getBlock() instanceof TeleporterCapsuleBlock)) {
			state.visible = false;
			return;
		}
		state.visible = true;
		state.sunk = TRAVEL * entity.doorOpenness(level.getGameTime(), partialTicks);
		state.facing = station.getValue(TeleporterBlock.FACING);
		state.glass = middle.getValue(TeleporterCapsuleBlock.GLASS);
		state.lightCoords = LightCoordsUtil.getLightCoords(level, entity.getBlockPos().above());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.visible || state.sunk >= TRAVEL - EPSILON) {
			return;
		}
		TextureAtlasSprite glass = sprites.get(GLASS_SPRITES.get(state.glass));
		TextureAtlasSprite body = sprites.get(BODY);
		collector.submitCustomGeometry(poseStack, GLASS_TYPE,
				(pose, consumer) -> drawPieces(pose, consumer, state, glass, true));
		collector.submitCustomGeometry(poseStack, FRAME_TYPE,
				(pose, consumer) -> drawPieces(pose, consumer, state, body, false));
	}

	/** The door reaches two blocks above the station's own. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer (MOD-632).
	 *
	 * <p>NeoForge culls every block entity renderer by this box — globally rendered ones too, before it
	 * looks at {@link #shouldRenderOffScreen} — and its default is the station's own block. From inside
	 * the capsule that block is under the player's feet and out of view whenever they look ahead, so the
	 * whole door, which is all the glass in front of them, vanished until they looked down. The whole
	 * capsule instead: the arriving player stands inside it, and a box the camera is in is never culled.
	 *
	 * <p>No {@code @Override}: the method is declared only by NeoForge's {@code BlockEntityRenderer}, which
	 * this class is compiled against there. On Fabric nothing calls it — vanilla does not test a block
	 * entity against the frustum at all.
	 */
	public AABB getRenderBoundingBox(TeleporterBlockEntity blockEntity) {
		return TeleporterBlock.capsuleBounds(blockEntity.getBlockPos());
	}

	private static void drawPieces(PoseStack.Pose pose, VertexConsumer out, State state, TextureAtlasSprite sprite,
			boolean glass) {
		for (Piece piece : PIECES) {
			if (piece.glass() != glass) {
				continue;
			}
			float top = piece.y1() - state.sunk;
			float bottom = Math.max(DECK, piece.y0() - state.sunk);
			if (top - bottom <= EPSILON) {
				continue;
			}
			float visible = (top - bottom) / (piece.y1() - piece.y0());
			Matrix4f matrix = piece.byFacing()[state.facing.get2DDataValue()];
			drawPiece(pose, out, piece, matrix, bottom, top, visible, sprite, glass, state.lightCoords);
		}
	}

	/**
	 * The faces of one clipped piece. Glass facets are panes and have only their two broad faces, as in
	 * the design; steel pieces are boxes and show every face but the bottom, which is on the deck or
	 * below it.
	 */
	private static void drawPiece(PoseStack.Pose pose, VertexConsumer out, Piece p, Matrix4f matrix, float y0,
			float y1, float visible, TextureAtlasSprite sprite, boolean glass, int light) {
		float x0 = p.x0();
		float x1 = p.x1();
		float z0 = p.z0();
		float z1 = p.z1();
		float fullHeight = p.y1() - p.y0();
		float[] across = uv(sprite, glass, x1 - x0, fullHeight, visible);
		// north (-Z) and south (+Z)
		face(pose, out, matrix, light, across, 0, 0, -1,
				x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
		face(pose, out, matrix, light, across, 0, 0, 1,
				x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
		if (glass) {
			return;
		}
		float[] side = uv(sprite, false, z1 - z0, fullHeight, visible);
		face(pose, out, matrix, light, side, -1, 0, 0,
				x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
		face(pose, out, matrix, light, side, 1, 0, 0,
				x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
		float[] cap = {
				sprite.getU((EDGE_TILE_X + PLAIN_TEXEL_X + UV_INSET) / PALETTE_SIZE),
				sprite.getV((EDGE_TILE_Y + PLAIN_TEXEL_Y + UV_INSET) / PALETTE_SIZE),
				sprite.getU((EDGE_TILE_X + PLAIN_TEXEL_X + 1.0f - UV_INSET) / PALETTE_SIZE),
				sprite.getV((EDGE_TILE_Y + PLAIN_TEXEL_Y + 1.0f - UV_INSET) / PALETTE_SIZE)};
		face(pose, out, matrix, light, cap, 0, 1, 0,
				x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
	}

	/**
	 * {u0, v0, u1, v1} in atlas coordinates for a face {@code width} by {@code height} design pixels, of
	 * which the top {@code visible} fraction is still above the deck.
	 *
	 * <p>The design gives each face a patch as big as the face, clamped to 1..16 pixels, from the corner
	 * of its tile. Glass then moves that patch onto the whole glass texture inside its frame.
	 */
	private static float[] uv(TextureAtlasSprite sprite, boolean glass, float width, float height, float visible) {
		float w = Math.max(1.0f, Math.min(16.0f, width));
		float h = Math.max(1.0f, Math.min(16.0f, height));
		float left = UV_INSET;
		float right = w - UV_INSET;
		float upper = UV_INSET;
		float lower = upper + (h - 2.0f * UV_INSET) * visible;
		if (glass) {
			return new float[] {
					sprite.getU(glassU(left)), sprite.getV(glassU(upper)),
					sprite.getU(glassU(right)), sprite.getV(glassU(lower))};
		}
		return new float[] {
				sprite.getU((EDGE_TILE_X + left) / PALETTE_SIZE), sprite.getV((EDGE_TILE_Y + upper) / PALETTE_SIZE),
				sprite.getU((EDGE_TILE_X + right) / PALETTE_SIZE), sprite.getV((EDGE_TILE_Y + lower) / PALETTE_SIZE)};
	}

	/** A position in the design's 16-pixel glass tile, moved inside the frame of a 16-pixel texture, as 0..1. */
	private static float glassU(float local) {
		return (GLASS_UV_MARGIN + local * (16.0f - 2.0f * GLASS_UV_MARGIN) / 16.0f) / 16.0f;
	}

	/**
	 * One quad, corners given clockwise from its top-left as seen from outside, emitted with both windings
	 * so that neither sheet's back-face culling hides it — a glass pane is looked at from both sides.
	 */
	private static void face(PoseStack.Pose pose, VertexConsumer out, Matrix4f matrix, int light, float[] uv,
			float nx, float ny, float nz, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		Vector3f normal = matrix.transformDirection(new Vector3f(nx, ny, nz)).normalize();
		Vector3f a = corner(matrix, ax, ay, az);
		Vector3f b = corner(matrix, bx, by, bz);
		Vector3f c = corner(matrix, cx, cy, cz);
		Vector3f d = corner(matrix, dx, dy, dz);
		vertex(pose, out, a, uv[0], uv[1], light, normal, 1.0f);
		vertex(pose, out, b, uv[2], uv[1], light, normal, 1.0f);
		vertex(pose, out, c, uv[2], uv[3], light, normal, 1.0f);
		vertex(pose, out, d, uv[0], uv[3], light, normal, 1.0f);

		vertex(pose, out, d, uv[0], uv[3], light, normal, -1.0f);
		vertex(pose, out, c, uv[2], uv[3], light, normal, -1.0f);
		vertex(pose, out, b, uv[2], uv[1], light, normal, -1.0f);
		vertex(pose, out, a, uv[0], uv[1], light, normal, -1.0f);
	}

	private static Vector3f corner(Matrix4f matrix, float x, float y, float z) {
		return matrix.transformPosition(new Vector3f(x, y, z)).div(16.0f);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vector3f p, float u, float v, int light,
			Vector3f normal, float sign) {
		out.addVertex(pose, p.x(), p.y(), p.z())
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, sign * normal.x(), sign * normal.y(), sign * normal.z());
	}

	public static final class State extends BlockEntityRenderState {
		private boolean visible;
		/** How far the door has sunk, in design pixels: 0 shut, {@link #TRAVEL} fully open. */
		private float sunk;
		private Direction facing = Direction.NORTH;
		private CapsuleGlass glass = CapsuleGlass.CLEAR;
		private int lightCoords;
	}
}
