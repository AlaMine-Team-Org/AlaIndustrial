package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The reception capsule (MOD-833; epic MOD-824, decisions R6, R36): three blocks tall, a steel floor and a glass
 * flask open at the front, in which the archive restores a town's shift worker. The work — the price, the
 * conditions, handing the worker over — is in {@link ReceptionCapsuleBlockEntity}; this class is the body.
 *
 * <p><b>One block, three cells</b>, as a door with a cell more: the item places all three at once
 * ({@link #setPlacedBy}), a cell that loses a neighbour of its own capsule turns to air ({@link #updateShape}), so
 * breaking any of them takes the whole capsule, and only the lower cell drops the item. Only the lower cell is a
 * machine — it alone has a block entity, so the slots, the buffer and the menu exist in one place and a hopper
 * aimed at the flask finds nothing; a click on the flask opens the lower cell's screen.
 *
 * <p><b>Why three cells (MOD-850).</b> A worker is a player's model drawn unscaled: {@link #WORKER_REACH_PX} above
 * the floor it stands on, taller than its 1.8-block hitbox, and with the floor and a roof that is more than two
 * blocks can hold — in a two-block flask the helmet came through the roof. The roof now starts half a block into the
 * top cell ({@link #ROOF_PX}), a hand's breadth above the highest the hat can reach; a small cap sits on it, kin of
 * the teleporter capsule's crown.
 *
 * <p><b>The flask is a room, not a solid block.</b> Its walls, pillars and roof collide; its inside is empty and
 * its front is open, so the worker who appears on the floor can walk out: twelve pixels between the walls, eleven
 * between the front pillars. The silhouettes are built once at class-init (ADR-023) and match the model
 * ({@code tools/gen_reception_capsule_assets.py}). The capsule hides none of its neighbours' faces
 * ({@code noOcclusion}), as glass does.
 *
 * <p><b>Who breaks it (R36).</b> As the settlement core: unbreakable by hardness, so explosions and area tools
 * skip it; only {@link #getDestroyProgress} lets it go, and only for the owner of the capsule's town with a tool
 * that mines it — any pickaxe — at the pace of a block of {@link #OWNER_HARDNESS}.
 */
public class ReceptionCapsuleBlock extends HorizontalMachineBlock implements HasMachineTooltip {

	/** Which cell of the capsule a block is, counted up from the floor. */
	public enum Part implements StringRepresentable {
		LOWER("lower"),
		MIDDLE("middle"),
		UPPER("upper");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		/** How many cells this one stands above the lower cell. */
		public int aboveLower() {
			return ordinal();
		}

		/** The cell of the same capsule one block up, or {@code null} from the top one. */
		@Nullable
		Part above() {
			return this == UPPER ? null : values()[ordinal() + 1];
		}

		/** The cell of the same capsule one block down, or {@code null} from the lower one. */
		@Nullable
		Part below() {
			return this == LOWER ? null : values()[ordinal() - 1];
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

	/** The hardness the town's owner mines the capsule at — the settlement core's. */
	public static final float OWNER_HARDNESS = SettlementCoreBlock.OWNER_HARDNESS;
	/** Vanilla's divisor of the destroy progress with the right tool in hand ({@code BlockBehaviour}, javap 26.3). */
	private static final float CORRECT_TOOL_DIVISOR = 30.0F;

	/** Height of the floor plate a restored worker stands on, in pixels of the lower cell. */
	public static final double FLOOR_PX = 1.0;
	/** Where the flask's roof starts, in pixels of the upper cell: 40 px above the lower cell's base. */
	public static final double ROOF_PX = 8.0;
	/** The roof's thickness, and above it the cap's, in pixels. */
	private static final double ROOF_THICKNESS_PX = 2.0;
	private static final double CAP_THICKNESS_PX = 2.0;

	/**
	 * The highest a worker's body reaches above the floor it stands on, in pixels. A worker is a player's model
	 * drawn without the player's 15/16 scale ({@code MobRenderer}; {@code LivingEntityRenderer.scale} does nothing,
	 * javap 26.3): its neck is 24 px up (the renderer lifts the model by 1.501 blocks), the head 8 px above it and
	 * the hat, which carries either helmet, half a pixel proud of the head — 32.5 px standing straight. A worker that
	 * looks at a player tips its head, and the hat's top corner, {@code hypot(8.5, 4.5)} from the neck, rises to
	 * 33.6 px at a tilt of 27.9°, well inside the 40° a mob's head may tip. Hair stays inside the hat. Measured in
	 * MOD-850 (research.md); the roof clears it by more than five pixels.
	 */
	public static final double WORKER_REACH_PX = 1.501 * 16.0 + Math.hypot(8.5, 4.5);

	/*
	 * The silhouette, authored facing north (the open front at z = 0) and turned for the other three facings.
	 * Pillars 1.5 px at the four corners, walls one pixel thick on the two sides and the back, the floor between
	 * them in the lower cell; the flask runs through the middle cell and on in the upper one up to the roof, with
	 * the cap on top.
	 */
	private static final Map<Direction, VoxelShape> LOWER = Shapes.rotateHorizontal(Shapes.or(
			pillarsAndWalls(16),
			Block.box(2, 0, 1, 14, FLOOR_PX, 14)));
	private static final Map<Direction, VoxelShape> MIDDLE = Shapes.rotateHorizontal(pillarsAndWalls(16));
	private static final Map<Direction, VoxelShape> UPPER = Shapes.rotateHorizontal(Shapes.or(
			pillarsAndWalls(ROOF_PX),
			Block.box(1, ROOF_PX, 1, 15, ROOF_PX + ROOF_THICKNESS_PX, 15),
			Block.box(3, ROOF_PX + ROOF_THICKNESS_PX, 3, 13, ROOF_PX + ROOF_THICKNESS_PX + CAP_THICKNESS_PX, 13)));

	private static VoxelShape pillarsAndWalls(double top) {
		return Shapes.or(
				Block.box(1, 0, 1, 2.5, top, 2.5),
				Block.box(13.5, 0, 1, 15, top, 2.5),
				Block.box(1, 0, 13.5, 2.5, top, 15),
				Block.box(13.5, 0, 13.5, 15, top, 15),
				Block.box(1, 0, 2.5, 2, top, 13.5),
				Block.box(14, 0, 2.5, 15, top, 13.5),
				Block.box(2.5, 0, 14, 13.5, top, 15));
	}

	public ReceptionCapsuleBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(PART, Part.LOWER));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PART);
	}

	/**
	 * A turned structure turns the capsule with it: the town hall's template (MOD-833, R55) places it facing its
	 * core, and a town laid out at another rotation must keep it so.
	 */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** The lower cell is the machine; the flask above it has no block entity (see the class comment). */
	@Override
	@Nullable
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(PART) == Part.LOWER ? new ReceptionCapsuleBlockEntity(pos, state) : null;
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return state.getValue(PART) == Part.LOWER ? machineTicker(level) : null;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Map<Direction, VoxelShape> shapes = switch (state.getValue(PART)) {
			case LOWER -> LOWER;
			case MIDDLE -> MIDDLE;
			case UPPER -> UPPER;
		};
		return shapes.get(state.getValue(FACING));
	}

	/** Facing the player, with room for the flask above: as a door, nothing is placed where the flask cannot go. */
	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() + Part.UPPER.aboveLower() > level.getMaxY()) {
			return null;
		}
		for (int up = 1; up <= Part.UPPER.aboveLower(); up++) {
			if (!level.getBlockState(pos.above(up)).canBeReplaced(context)) {
				return null;
			}
		}
		return super.getStateForPlacement(context).setValue(PART, Part.LOWER);
	}

	/** The item raises the flask together with the floor, then the machine base records the placer as usual. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		raiseFlask(level, pos, state);
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof ReceptionCapsuleBlockEntity capsule) {
			capsule.wake();
		}
	}

	/** The middle and upper cells over a lower cell at {@code lower}, in the order a door raises its upper half. */
	private static void raiseFlask(Level level, BlockPos lower, BlockState lowerState) {
		level.setBlock(lower.above(Part.MIDDLE.aboveLower()), lowerState.setValue(PART, Part.MIDDLE), Block.UPDATE_ALL);
		level.setBlock(lower.above(Part.UPPER.aboveLower()), lowerState.setValue(PART, Part.UPPER), Block.UPDATE_ALL);
	}

	/**
	 * All three cells of a capsule standing at {@code lower}, facing {@code facing}: the path a structure, the demo
	 * stand or a game test takes, since a programmatic placement never runs {@link #setPlacedBy}.
	 */
	public static void placeWhole(Level level, BlockPos lower, Direction facing, Block block) {
		BlockState base = block.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LOWER);
		level.setBlock(lower, base, Block.UPDATE_ALL);
		raiseFlask(level, lower, base);
	}

	/**
	 * A cell whose neighbour of its own capsule is gone turns to air — a broken floor takes the flask with it, a
	 * broken roof the flask and the floor, a broken middle both. Each cell checks the neighbour it needs above and
	 * below, so the loss runs through the whole capsule one cell at a time.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		Part part = state.getValue(PART);
		Part needed = directionToNeighbour == Direction.UP ? part.above()
				: directionToNeighbour == Direction.DOWN ? part.below() : null;
		if (needed != null && !(neighbourState.is(this) && neighbourState.getValue(PART) == needed)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	/**
	 * A creative player breaking the flask takes the floor without a drop, as vanilla does for a door; in survival
	 * the floor goes through its loot table when the flask is broken (the lower cell is the one that drops).
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(PART) != Part.LOWER) {
			BlockPos below = lowerPos(state, pos);
			BlockState lower = level.getBlockState(below);
			if (lower.is(this) && lower.getValue(PART) == Part.LOWER) {
				level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, below, Block.getId(lower));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Nothing for anyone but the town's owner with a pickaxe; for them, a block of {@link #OWNER_HARDNESS}. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (!(level.getBlockEntity(lowerPos(state, pos)) instanceof ReceptionCapsuleBlockEntity capsule)
				|| !capsule.mayBeBrokenBy(player)) {
			return 0.0F;
		}
		return player.getDestroySpeed(state) / OWNER_HARDNESS / CORRECT_TOOL_DIVISOR;
	}

	/** A click on any cell opens the machine, which lives in the lower one. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.getBlockEntity(lowerPos(state, pos)) instanceof MenuProvider provider) {
			if (!level.isClientSide()) {
				player.openMenu(provider);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Only the lower cell takes EU; the flask draws no cable arm. */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return state.getValue(PART) == Part.LOWER && super.isCableConnectable(state, side);
	}

	/** Where the machine lives for a cell at {@code pos}. */
	public static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.getBlock() instanceof ReceptionCapsuleBlock ? pos.below(state.getValue(PART).aboveLower()) : pos;
	}

	/** Hover tooltip of the item (MOD-716, ADR-040): the EU one worker costs, the buffer and the restoring time. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.reception_capsule.purpose",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.stat("reception_capsule.eu_per_worker",
								ServerBalance::capsuleEuPerWorker)),
				List.of(MachineTooltipSpec.statValue("buffer", () -> 2L * ServerBalance.capsuleEuPerWorker()),
						MachineTooltipSpec.plain("duration_ticks", ServerBalance::capsuleRestoreTicks)));
	}
}
