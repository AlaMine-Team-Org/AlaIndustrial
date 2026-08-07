package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.block.AbstractMachineBlock;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the accessories clamped onto a cable segment: the insulating stand underneath it (MOD-279)
 * and the maintenance breaker on top of it (MOD-276).
 *
 * <p><b>One renderer, two accessories, on purpose.</b> A {@code BlockEntityType} accepts exactly one
 * {@code BlockEntityRenderer}, and both accessories hang off the same {@link CableBlockEntity} — so
 * a second renderer class is not an option, it is a silent overwrite of the first registration.
 *
 * <p>The stand is a thin plate resting on the floor of the cable's own cell, textured with the exact
 * block the player installed.
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
public final class CableAccessoryBlockEntityRenderer
		implements BlockEntityRenderer<CableBlockEntity, CableAccessoryBlockEntityRenderer.State> {
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

	/**
	 * The breaker is a <b>collar around the wire</b>, not a box perched on top of it (MOD-276 revision).
	 *
	 * <p>The perched box had two faults the first in-world pass exposed: on a vertical run the cable
	 * core swallowed it (a 6-px core drawn over a box that only cleared the crown by 3 px), and it read
	 * as a crate sitting beside the cable rather than hardware clamped onto it. A collar fixes both by
	 * construction — it wraps the 6-px core on all four sides, so it is equally visible whichever way
	 * the run travels, and it cannot be hidden by the wire it encloses.
	 *
	 * <p>{@code ACROSS} is the half-thickness perpendicular to the run (5 px out from the block centre,
	 * i.e. a 10-px collar around a 6-px core), {@code ALONG} is its half-length on the run's own axis.
	 */
	private static final float ACROSS = 5.0F / 16.0F;
	private static final float ALONG = 3.5F / 16.0F;
	/** Flange rings at both ends of the collar: slightly wider, very short — the bolted end caps. */
	private static final float FLANGE_ACROSS = 6.0F / 16.0F;
	private static final float FLANGE_ALONG = 1.0F / 16.0F;
	/**
	 * Status lamp: a thin band standing a fifth of a pixel proud of the housing.
	 *
	 * <p>The first pass used 0.004 blocks (~1/16 px). That is enough on paper and not enough in
	 * practice: at distance the depth buffer cannot separate two surfaces that close and the lamp
	 * flickers through the body as the camera moves. 0.2 px is still invisible as a step, and stable.
	 */
	private static final float LAMP_PROUD = 0.2F / 16.0F;
	private static final float LAMP_ACROSS = ACROSS + LAMP_PROUD;
	private static final float LAMP_ALONG = 1.5F / 16.0F;

	/**
	 * Half-size of the junction housing used when the run does not travel on one clean axis — a corner,
	 * a tee, or the six-way star in the feedback screenshots.
	 *
	 * <p>A collar has an axis by definition, so on a star it has to pick one and looks wrong from the
	 * other four sides. A cube does not have that problem: it reads as the junction box the wiring
	 * passes through, and every arm leaves it the same way.
	 */
	private static final float JUNCTION = 5.5F / 16.0F;

	/**
	 * The severed ends shown while the switch is open: a short stub toward every side the wiring would
	 * have continued into, so the gap reads as "cut here on purpose" rather than as a missing block.
	 *
	 * <p>Emitted per <em>direction</em>, not per axis, which is what makes a corner or a tee show them
	 * too — the first pass drew a pair along one axis and left an elbow with none.
	 *
	 * <p>{@code STUB_IN} starts outside the flange's outer face rather than flush against it: two
	 * coplanar faces are exactly the z-fighting that made the housing shimmer from below.
	 */
	private static final float STUB_HALF = 1.5F / 16.0F;
	private static final float STUB_IN = ALONG + FLANGE_ALONG + 0.25F / 16.0F;
	private static final float STUB_OUT = 7.75F / 16.0F;

	private static final int LAMP_LIVE = 0xFF4ADC6E;
	private static final int LAMP_DEAD = 0xFFD1483C;

	private static final SpriteId BREAKER_BODY =
			Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath("alaindustrial", "cable_breaker_body"));
	private static final SpriteId BREAKER_LAMP =
			Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath("alaindustrial", "cable_breaker_lamp"));

	private final SpriteGetter sprites;

	public CableAccessoryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
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
		state.hasBreaker = entity.hasBreaker();
		state.breakerClosed = entity.isBreakerClosed();
		state.breakerAxis = state.hasBreaker ? runAxis(entity) : Direction.Axis.X;
		state.breakerJunction = state.hasBreaker && isJunction(entity);
		state.breakerStubs = state.hasBreaker ? runNeighbourMask(entity) : 0;
	}

	/**
	 * Bit per {@link Direction} the wiring continues into — the sides that need a severed end drawn
	 * while the switch is open. Read from the world for the same reason the axis is: an open breaker
	 * has already retracted its own connection flags.
	 */
	private static int runNeighbourMask(CableBlockEntity entity) {
		var level = entity.getLevel();
		if (level == null) {
			return 0;
		}
		BlockPos pos = entity.getBlockPos();
		int mask = 0;
		for (Direction dir : Direction.values()) {
			if (isRunNeighbour(level, pos, dir)) {
				mask |= 1 << dir.ordinal();
			}
		}
		return mask;
	}

	/**
	 * True when the wiring here leaves on more than one axis — a corner, a tee, or the six-way star.
	 *
	 * <p>Such a cell has no "the" axis, so a collar would have to pick one and would sit wrong against
	 * every arm on the other axes. Those cells get the cube housing instead.
	 */
	private static boolean isJunction(CableBlockEntity entity) {
		var level = entity.getLevel();
		if (level == null) {
			return false;
		}
		BlockPos pos = entity.getBlockPos();
		int axes = 0;
		for (Direction.Axis axis : Direction.Axis.values()) {
			boolean used = isRunNeighbour(level, pos, Direction.get(Direction.AxisDirection.POSITIVE, axis))
					|| isRunNeighbour(level, pos, Direction.get(Direction.AxisDirection.NEGATIVE, axis));
			if (used) {
				axes++;
			}
		}
		return axes > 1;
	}

	/**
	 * Which way the run travels through this cell, so the collar can be threaded onto it.
	 *
	 * <p>Read from the <em>world</em>, not from this cable's own connection flags: an open breaker has
	 * retracted every arm by design, so its own flags say "connected to nothing" exactly when the
	 * collar most needs an orientation. Vertical wins ties — a run that turns a corner here looks right
	 * with the collar on the upright leg, and a lone segment falls back to X.
	 */
	private static Direction.Axis runAxis(CableBlockEntity entity) {
		var level = entity.getLevel();
		if (level == null) {
			return Direction.Axis.X;
		}
		BlockPos pos = entity.getBlockPos();
		if (isRunNeighbour(level, pos, Direction.UP) || isRunNeighbour(level, pos, Direction.DOWN)) {
			return Direction.Axis.Y;
		}
		if (isRunNeighbour(level, pos, Direction.EAST) || isRunNeighbour(level, pos, Direction.WEST)) {
			return Direction.Axis.X;
		}
		if (isRunNeighbour(level, pos, Direction.NORTH) || isRunNeighbour(level, pos, Direction.SOUTH)) {
			return Direction.Axis.Z;
		}
		return Direction.Axis.X;
	}

	/** True if the neighbour on that side is something a cable run continues into. */
	private static boolean isRunNeighbour(BlockGetter level, BlockPos pos, Direction dir) {
		return level.getBlockState(pos.relative(dir)).getBlock() instanceof AbstractMachineBlock;
	}

	private static boolean connected(BlockState state, Direction direction) {
		var property = PipeBlock.PROPERTY_BY_DIRECTION.get(direction);
		return state.hasProperty(property) && state.getValue(property);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		submitBreaker(state, poseStack, submitNodeCollector);
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
	 * Emits the breaker housing, when one is installed. Cutout rather than translucent: the housing is
	 * solid metal, and the lever/lamp that tell the player the switch position are baked into the
	 * sprite, so the whole box is one six-quad draw with no per-face bookkeeping.
	 */
	private void submitBreaker(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (!state.hasBreaker) {
			return;
		}
		int light = state.lightCoords;
		Direction.Axis axis = state.breakerAxis;

		boolean junction = state.breakerJunction;
		boolean open = !state.breakerClosed;
		int stubs = state.breakerStubs;

		TextureAtlasSprite body = sprites.get(BREAKER_BODY);
		if (open && stubs != 0) {
			collector.submitCustomGeometry(poseStack, BREAKER_BODY.renderType(ignored -> Sheets.cutoutBlockItemSheet()),
					(pose, consumer) -> {
						for (Direction dir : Direction.values()) {
							if ((stubs & (1 << dir.ordinal())) != 0) {
								stub(pose, consumer, body, light, dir);
							}
						}
					});
		}
		collector.submitCustomGeometry(poseStack, BREAKER_BODY.renderType(ignored -> Sheets.cutoutBlockItemSheet()),
				(pose, consumer) -> {
					if (junction) {
						// No single axis to thread onto — draw the box the wiring passes through instead.
						cuboid(pose, consumer, body, light, -1,
								0.5F - JUNCTION, 0.5F - JUNCTION, 0.5F - JUNCTION,
								0.5F + JUNCTION, 0.5F + JUNCTION, 0.5F + JUNCTION);
						return;
					}
					// The collar itself, then a bolted flange straddling each end face. Straddling, not
					// sitting flush inside: a flange whose outer face lands exactly on the collar's own
					// face gives two coplanar surfaces, and that pair is what shimmered from below.
					box(pose, consumer, body, light, -1, axis, ACROSS, ALONG, 0.0F);
					box(pose, consumer, body, light, -1, axis, FLANGE_ACROSS, FLANGE_ALONG, ALONG);
					box(pose, consumer, body, light, -1, axis, FLANGE_ACROSS, FLANGE_ALONG, -ALONG);
				});

		TextureAtlasSprite lamp = sprites.get(BREAKER_LAMP);
		int tint = state.breakerClosed ? LAMP_LIVE : LAMP_DEAD;
		collector.submitCustomGeometry(poseStack, BREAKER_LAMP.renderType(ignored -> Sheets.cutoutBlockItemSheet()),
				(pose, consumer) -> {
					if (junction) {
						// A band on every side of the cube, so the state reads from any approach.
						float lamp0 = 0.5F - JUNCTION - LAMP_PROUD;
						float lamp1 = 0.5F + JUNCTION + LAMP_PROUD;
						float band = 1.5F / 16.0F;
						cuboid(pose, consumer, lamp, light, tint,
								lamp0, 0.5F - band, lamp0, lamp1, 0.5F + band, lamp1);
						return;
					}
					box(pose, consumer, lamp, light, tint, axis, LAMP_ACROSS, LAMP_ALONG, 0.0F);
				});
	}

	/**
	 * A box centred on the cell, sized {@code across} perpendicular to {@code axis} and {@code along}
	 * on it, shifted by {@code offset} along that axis. Only the four faces that ring the wire are
	 * emitted — the two caps face straight into the cable core and are never visible, and skipping them
	 * also keeps the lamp band from glowing out of the ends.
	 *
	 * <p>Writing it axis-relative is what makes one call site serve all three orientations: the collar
	 * on a vertical run is the same geometry with y and x swapped, not a second model.
	 */
	private static void box(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
			int color, Direction.Axis axis, float across, float along, float offset) {
		float c = 0.5F;
		float a0 = c - across;
		float a1 = c + across;
		float l0 = c + offset - along;
		float l1 = c + offset + along;
		switch (axis) {
			case X -> cuboid(pose, consumer, sprite, light, color, l0, a0, a0, l1, a1, a1);
			case Y -> cuboid(pose, consumer, sprite, light, color, a0, l0, a0, a1, l1, a1);
			case Z -> cuboid(pose, consumer, sprite, light, color, a0, a0, l0, a1, a1, l1);
			default -> { }
		}
	}

	/**
	 * One severed end: a short square post from just outside the housing to near the cell edge, pointing
	 * the way the wiring used to continue.
	 */
	private static void stub(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
			Direction dir) {
		float c = 0.5F;
		float a0 = c - STUB_HALF;
		float a1 = c + STUB_HALF;
		switch (dir) {
			case UP -> cuboid(pose, consumer, sprite, light, -1, a0, c + STUB_IN, a0, a1, c + STUB_OUT, a1);
			case DOWN -> cuboid(pose, consumer, sprite, light, -1, a0, c - STUB_OUT, a0, a1, c - STUB_IN, a1);
			case EAST -> cuboid(pose, consumer, sprite, light, -1, c + STUB_IN, a0, a0, c + STUB_OUT, a1, a1);
			case WEST -> cuboid(pose, consumer, sprite, light, -1, c - STUB_OUT, a0, a0, c - STUB_IN, a1, a1);
			case SOUTH -> cuboid(pose, consumer, sprite, light, -1, a0, a0, c + STUB_IN, a1, a1, c + STUB_OUT);
			case NORTH -> cuboid(pose, consumer, sprite, light, -1, a0, a0, c - STUB_OUT, a1, a1, c - STUB_IN);
			default -> { }
		}
	}

	/**
	 * All six faces of an axis-aligned box.
	 *
	 * <p><b>Six, not four.</b> The first collar emitted only the ring of faces around the wire, on the
	 * reasoning that the two caps point straight into the cable core and are never seen. They are: the
	 * moment the breaker is open the arms retract and both caps face open air, and even closed, looking
	 * along the run (or from directly below) showed straight through the housing — the "sharp corners"
	 * and see-through gaps in the feedback screenshots. A closed solid also means every viewing angle
	 * gets a lit, opaque surface instead of a back face culled away.
	 */
	private static void cuboid(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
			int color, float x0, float y0, float z0, float x1, float y1, float z1) {
		// Up / down
		quad(pose, consumer, sprite, light, color, 0.0F, 1.0F, 0.0F,
				x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0.0F, 0.0F, 1.0F, 1.0F);
		quad(pose, consumer, sprite, light, color, 0.0F, -1.0F, 0.0F,
				x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0.0F, 0.0F, 1.0F, 1.0F);
		// North / south
		quad(pose, consumer, sprite, light, color, 0.0F, 0.0F, -1.0F,
				x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0.0F, 0.0F, 1.0F, 1.0F);
		quad(pose, consumer, sprite, light, color, 0.0F, 0.0F, 1.0F,
				x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0.0F, 0.0F, 1.0F, 1.0F);
		// West / east
		quad(pose, consumer, sprite, light, color, -1.0F, 0.0F, 0.0F,
				x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, 0.0F, 0.0F, 1.0F, 1.0F);
		quad(pose, consumer, sprite, light, color, 1.0F, 0.0F, 0.0F,
				x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 0.0F, 0.0F, 1.0F, 1.0F);
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
		quad(pose, consumer, sprite, light, -1, 0.0F, 1.0F, 0.0F,
				x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
				x0, z0, x1, z1);
		quad(pose, consumer, sprite, light, -1, 0.0F, -1.0F, 0.0F,
				x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
				x0, z0, x1, z1);

		// The four sides: as wide as the plate, two pixels tall, sampled from the top of the sprite.
		quad(pose, consumer, sprite, light, -1, 0.0F, 0.0F, -1.0F,
				x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
				x0, 0.0F, x1, THICKNESS);
		quad(pose, consumer, sprite, light, -1, 0.0F, 0.0F, 1.0F,
				x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1,
				x0, 0.0F, x1, THICKNESS);
		quad(pose, consumer, sprite, light, -1, -1.0F, 0.0F, 0.0F,
				x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0,
				z0, 0.0F, z1, THICKNESS);
		quad(pose, consumer, sprite, light, -1, 1.0F, 0.0F, 0.0F,
				x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
				z0, 0.0F, z1, THICKNESS);
	}

	/** One quad, wound in the order given, with its UV rectangle taken as fractions of the sprite. */
	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
			int color, float normalX, float normalY, float normalZ,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float uMin, float vMin, float uMax, float vMax) {
		float u0 = sprite.getU(uMin);
		float u1 = sprite.getU(uMax);
		float v0 = sprite.getV(vMin);
		float v1 = sprite.getV(vMax);
		vertex(pose, consumer, ax, ay, az, u0, v1, light, color, normalX, normalY, normalZ);
		vertex(pose, consumer, bx, by, bz, u0, v0, light, color, normalX, normalY, normalZ);
		vertex(pose, consumer, cx, cy, cz, u1, v0, light, color, normalX, normalY, normalZ);
		vertex(pose, consumer, dx, dy, dz, u1, v1, light, color, normalX, normalY, normalZ);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
			float u, float v, int light, int color, float normalX, float normalY, float normalZ) {
		consumer.addVertex(pose, x, y, z)
				.setColor(color)
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
		/** Breaker presence, switch position and the axis its collar is threaded onto (MOD-276). */
		private boolean hasBreaker;
		private boolean breakerClosed;
		private Direction.Axis breakerAxis = Direction.Axis.X;
		/** True when the wiring leaves on more than one axis — draw the cube housing, not a collar. */
		private boolean breakerJunction;
		/** Bitmask of directions that get a severed-end stub while the switch is open. */
		private int breakerStubs;

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
