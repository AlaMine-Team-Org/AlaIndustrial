package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.network.HallSchematicPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The town hall's schematic as the frame draws it (MOD-857, owner's round 3, point 6): every ghost cell's own block
 * model — the baked quads of the block that belongs there, in its state (a stair turned and halved, a door's half, a
 * carpet) — ready to be written into one translucent buffer by {@link HallSchematicRenderer}. Built once per reply of
 * the server, never per frame: a frame only walks the arrays.
 *
 * <p><b>What a ghost looks like.</b> The real block's quads at {@link #ALPHA} alpha, coloured by the block's own tint
 * (grass, leaves) and then by {@link #PLAN_TINT}, a pale blue that tells a plan from a built block, lit full bright so
 * it reads at night. A ghost is {@link #INFLATE} larger than its cell, about its centre: a wrong block standing in the
 * cell shows tinted rather than flickering against the ghost, and a ghost face that touches a real block lies inside
 * that block, hidden.
 *
 * <p><b>Fewer quads.</b> A quad with a cull face is left out where the neighbour on that side is a full opaque cube —
 * the ghost that belongs there, or the real block when no ghost does: that face could not be seen in the built hall
 * either, and a wall of ghosts then reads as one wall, not a stack of glass boxes.
 *
 * <p><b>A block with no model of its own</b> (a chest: its block-entity renderer draws it, the block model is empty)
 * becomes a box of the block's shape, textured with its particle sprite.
 *
 * <p>The quads of a state are cached per block state and dropped when the client reloads its models, when the
 * {@link BlockStateModelSet} instance changes.
 */
public final class HallSchematicGhosts {

	/** The ghost's alpha: 45 %. */
	static final int ALPHA = 0x73;
	/** The plan tint multiplied into every ghost colour: a pale blue. */
	static final int PLAN_TINT = 0xC8E0FF;
	/** How much larger than the cell a ghost is, on each side, in blocks. */
	static final float INFLATE = 0.01F;
	/** Ghosts farther than this from the eye are not drawn. */
	static final double DRAW_RADIUS = 96.0;

	private static final Direction[] DIRECTIONS = Direction.values();
	private static final Identifier BLOCK_ATLAS = Sheets.BLOCKS_MAPPER.sheet();

	@Nullable
	private static BlockStateModelSet cachedFor;
	private static final Map<BlockState, StateQuads> CACHE = new IdentityHashMap<>();

	private static final HallSchematicGhosts EMPTY = new HallSchematicGhosts(BlockPos.ZERO, new Cell[0], null, 0);

	private final BlockPos core;
	private final Cell[] cells;
	@Nullable
	private final AABB bounds;
	private final int quadCount;
	private final PoseStack.Pose scratch = new PoseStack.Pose();
	private final QuadInstance instance = new QuadInstance();

	private HallSchematicGhosts(BlockPos core, Cell[] cells, @Nullable AABB bounds, int quadCount) {
		this.core = core;
		this.cells = cells;
		this.bounds = bounds;
		this.quadCount = quadCount;
	}

	/** No ghost to draw. */
	public static HallSchematicGhosts empty() {
		return EMPTY;
	}

	/** The core the schematic belongs to; offsets are from its block. */
	public BlockPos core() {
		return core;
	}

	/** Whether there is nothing to draw. */
	public boolean isEmpty() {
		return cells.length == 0;
	}

	/** How many ghost cells are drawn (air and model-less cells of no shape are left out). */
	public int cellCount() {
		return cells.length;
	}

	/** How many quads one frame writes with every ghost in range — the cost of the schematic. */
	public int quadCount() {
		return quadCount;
	}

	/** The world box every ghost lies in, or {@code null} when there is none. */
	@Nullable
	public AABB bounds() {
		return bounds;
	}

	/**
	 * Bakes the reply: the quads to draw per cell, coloured. Run on the client thread, where the level, the models and
	 * the block colours live.
	 */
	public static HallSchematicGhosts build(BlockPos core, List<HallSchematicPayload.Ghost> ghosts,
			@Nullable ClientLevel level) {
		Minecraft minecraft = Minecraft.getInstance();
		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
		if (models != cachedFor) {
			CACHE.clear();
			cachedFor = models;
		}
		BlockColors colors = minecraft.getBlockColors();
		Map<Long, BlockState> wanted = new HashMap<>();
		for (HallSchematicPayload.Ghost ghost : ghosts) {
			wanted.put(ghost.pos(), ghost.state());
		}
		List<Cell> cells = new ArrayList<>(ghosts.size());
		AABB bounds = null;
		int quadCount = 0;
		for (HallSchematicPayload.Ghost ghost : ghosts) {
			BlockState state = ghost.state();
			if (state.isAir()) {
				continue;
			}
			BlockPos pos = BlockPos.of(ghost.pos());
			StateQuads model = CACHE.computeIfAbsent(state, s -> StateQuads.of(models, s));
			Cell cell = model.isEmpty() ? boxCell(core, pos, state, models)
					: modelCell(core, pos, state, model, wanted, level, colors);
			if (cell == null) {
				continue;
			}
			cells.add(cell);
			quadCount += cell.quadCount();
			AABB box = new AABB(pos);
			bounds = bounds == null ? box : bounds.minmax(box);
		}
		return new HallSchematicGhosts(core.immutable(), cells.toArray(Cell[]::new),
				bounds == null ? null : bounds.inflate(INFLATE), quadCount);
	}

	/**
	 * Writes every ghost within {@link #DRAW_RADIUS} of {@code eye}. {@code pose} is the core block's corner seen from
	 * the camera, as a block entity renderer receives it.
	 */
	void emit(PoseStack.Pose pose, VertexConsumer consumer, Vec3 eye) {
		instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
		instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
		double radius2 = DRAW_RADIUS * DRAW_RADIUS;
		for (Cell cell : cells) {
			double dx = core.getX() + cell.dx + 0.5 - eye.x;
			double dy = core.getY() + cell.dy + 0.5 - eye.y;
			double dz = core.getZ() + cell.dz + 0.5 - eye.z;
			if (dx * dx + dy * dy + dz * dz > radius2) {
				continue;
			}
			scratch.set(pose);
			scratch.translate(cell.dx + 0.5F, cell.dy + 0.5F, cell.dz + 0.5F);
			scratch.scale(1.0F + 2.0F * INFLATE, 1.0F + 2.0F * INFLATE, 1.0F + 2.0F * INFLATE);
			scratch.translate(-0.5F, -0.5F, -0.5F);
			if (cell.box != null) {
				emitBox(scratch, consumer, cell.box, cell.sprite, cell.colours[0]);
				continue;
			}
			for (int i = 0; i < cell.quads.length; i++) {
				instance.setColor(cell.colours[i]);
				consumer.putBakedQuad(scratch, cell.quads[i], instance);
			}
		}
	}

	private static @Nullable Cell modelCell(BlockPos core, BlockPos pos, BlockState state, StateQuads model,
			Map<Long, BlockState> wanted, @Nullable ClientLevel level, BlockColors colors) {
		List<BakedQuad> quads = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		Map<Integer, Integer> tints = new HashMap<>();
		for (int side = 0; side <= DIRECTIONS.length; side++) {
			List<BakedQuad> sideQuads = model.quads[side];
			if (sideQuads.isEmpty()) {
				continue;
			}
			if (side < DIRECTIONS.length && hiddenBehind(pos.relative(DIRECTIONS[side]), wanted, level)) {
				continue;
			}
			for (BakedQuad quad : sideQuads) {
				int rgb = 0xFFFFFF;
				if (quad.materialInfo().isTinted()) {
					int index = quad.materialInfo().tintIndex();
					rgb = tints.computeIfAbsent(index, i -> tint(colors, state, i, level, pos));
				}
				quads.add(quad);
				colours.add(colour(rgb));
			}
		}
		if (quads.isEmpty()) {
			return null;
		}
		int[] packed = new int[colours.size()];
		for (int i = 0; i < packed.length; i++) {
			packed[i] = colours.get(i);
		}
		return new Cell(pos.getX() - core.getX(), pos.getY() - core.getY(), pos.getZ() - core.getZ(),
				quads.toArray(BakedQuad[]::new), packed, null, null);
	}

	/** A block whose model is empty: its shape's box with its particle sprite, or nothing when it has no shape. */
	private static @Nullable Cell boxCell(BlockPos core, BlockPos pos, BlockState state, BlockStateModelSet models) {
		var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		if (shape.isEmpty()) {
			return null;
		}
		AABB box = shape.bounds();
		TextureAtlasSprite sprite = models.getParticleMaterial(state).sprite();
		if (!BLOCK_ATLAS.equals(sprite.atlasLocation())) {
			return null;
		}
		float[] bounds = {(float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY,
				(float) box.maxZ};
		return new Cell(pos.getX() - core.getX(), pos.getY() - core.getY(), pos.getZ() - core.getZ(),
				new BakedQuad[0], new int[] {colour(0xFFFFFF)}, bounds, sprite);
	}

	/**
	 * Whether a face toward {@code neighbour} would be hidden in the built hall: the block that belongs there, or the
	 * real one when nothing is planned there, is a full opaque cube.
	 */
	private static boolean hiddenBehind(BlockPos neighbour, Map<Long, BlockState> wanted, @Nullable ClientLevel level) {
		BlockState planned = wanted.get(neighbour.asLong());
		if (planned != null) {
			return planned.isSolidRender();
		}
		return level != null && level.isLoaded(neighbour) && level.getBlockState(neighbour).isSolidRender();
	}

	private static int tint(BlockColors colors, BlockState state, int index, @Nullable ClientLevel level,
			BlockPos pos) {
		BlockTintSource source = colors.getTintSource(state, index);
		if (source == null) {
			return 0xFFFFFF;
		}
		int argb = level != null ? source.colorInWorld(state, level, pos) : source.color(state);
		return argb == -1 ? 0xFFFFFF : argb & 0xFFFFFF;
	}

	/** {@code rgb} times {@link #PLAN_TINT}, at {@link #ALPHA}. */
	static int colour(int rgb) {
		int r = (rgb >> 16 & 0xFF) * (PLAN_TINT >> 16 & 0xFF) / 255;
		int g = (rgb >> 8 & 0xFF) * (PLAN_TINT >> 8 & 0xFF) / 255;
		int b = (rgb & 0xFF) * (PLAN_TINT & 0xFF) / 255;
		return ALPHA << 24 | r << 16 | g << 8 | b;
	}

	/** The six faces of a box, each textured with the whole sprite, normals out. */
	private static void emitBox(PoseStack.Pose pose, VertexConsumer consumer, float[] b, TextureAtlasSprite sprite,
			int colour) {
		float x0 = b[0];
		float y0 = b[1];
		float z0 = b[2];
		float x1 = b[3];
		float y1 = b[4];
		float z1 = b[5];
		face(pose, consumer, sprite, colour, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
		face(pose, consumer, sprite, colour, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		face(pose, consumer, sprite, colour, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
		face(pose, consumer, sprite, colour, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
		face(pose, consumer, sprite, colour, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
		face(pose, consumer, sprite, colour, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
	}

	private static void face(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int colour,
			float nx, float ny, float nz, float... corners) {
		float[] u = {sprite.getU0(), sprite.getU0(), sprite.getU1(), sprite.getU1()};
		float[] v = {sprite.getV0(), sprite.getV1(), sprite.getV1(), sprite.getV0()};
		for (int i = 0; i < 4; i++) {
			consumer.addVertex(pose, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2]).setColor(colour)
					.setUv(u[i], v[i]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
					.setNormal(pose, nx, ny, nz);
		}
	}

	/**
	 * One ghost: its offset from the core, and either its model's quads with a colour each, or a box with a sprite.
	 */
	private record Cell(int dx, int dy, int dz, BakedQuad[] quads, int[] colours, float @Nullable [] box,
			@Nullable TextureAtlasSprite sprite) {

		int quadCount() {
			return box != null ? 6 : quads.length;
		}
	}

	/**
	 * A state's model quads by cull face — index {@link Direction#ordinal()}, then the last slot for the quads with
	 * none — keeping only quads on the block atlas, the one the ghost's render type samples.
	 */
	private record StateQuads(List<BakedQuad>[] quads) {

		// collectParts(RandomSource, List) is deprecated only by NeoForge's patch, in favour of an overload taking a
		// level and a position that vanilla, and so Fabric, does not have. A ghost has no level: it is the plan.
		@SuppressWarnings({"unchecked", "deprecation"})
		static StateQuads of(BlockStateModelSet models, BlockState state) {
			List<BlockStateModelPart> parts = new ArrayList<>();
			models.get(state).collectParts(RandomSource.create(42L), parts);
			List<BakedQuad>[] bySide = new List[DIRECTIONS.length + 1];
			for (int side = 0; side <= DIRECTIONS.length; side++) {
				List<BakedQuad> out = new ArrayList<>();
				for (BlockStateModelPart part : parts) {
					for (BakedQuad quad : part.getQuads(side < DIRECTIONS.length ? DIRECTIONS[side] : null)) {
						if (BLOCK_ATLAS.equals(quad.materialInfo().sprite().atlasLocation())) {
							out.add(quad);
						}
					}
				}
				bySide[side] = List.copyOf(out);
			}
			return new StateQuads(bySide);
		}

		boolean isEmpty() {
			for (List<BakedQuad> side : quads) {
				if (!side.isEmpty()) {
					return false;
				}
			}
			return true;
		}
	}
}
