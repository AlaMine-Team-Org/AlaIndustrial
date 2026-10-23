package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.TownRoadBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The town road (MOD-861; epic MOD-824, decisions R90, R111-R115): a full block laid flush with the ground, a metal
 * grate with a copper wire under it. People walk on it as on any floor; the wire is a real segment of the energy
 * network (ADR-001, ADR-049), joined to every cable, machine and house inlet it touches exactly as a cable is.
 *
 * <p><b>A cable, not a pipe network of its own.</b> Its block entity is a {@link
 * dev.alaindustrial.core.energy.CableNode} of the {@link TownRoadBlockEntity#GRADE insulated copper} grade, so the
 * network counts it as one more segment: a road and a player's cable that touch are one line, and a building is
 * connected to its town when its inlet shares that line with the core (R112).
 *
 * <p><b>The glow.</b> {@link #POWERED} is set by the block entity while energy has moved through the segment in the
 * last second, and the block then gives off a faint light (R115). It is the wire's look only; nothing reads it.
 *
 * <p><b>The look (owner's round 2 of 0.1.219, R2-8).</b> A box frame with a cavity under a grate, the wire running
 * through the middle of it. Where the wire goes down through the floor, the floor is a grate too (round 4, R4-11): a
 * road on a step shows its joint below it. The six arm properties are a cable's
 * ({@link PipeBlock#PROPERTY_BY_DIRECTION}), worked out by the very rule a cable's arms use
 * ({@link CableBlock#shouldConnectTo}), so the wire reaches every road, cable, machine and house inlet the network
 * joins it to. A step of a road course is a vertical arm: the drones lay a hidden joint under the higher road
 * ({@code RoadWork.slots}). The arms are the look only; the network keys on the block entity.
 *
 * <p><b>The riser.</b> The last road of a course standing on that joint in front of a house inlet would look like a
 * block in the air. {@link #RISER} marks it (a road on a road, no road beside it, joined sideways to something else),
 * and it draws no frame: the wire rises out of the grate below and enters the inlet. Its shape is the wire's, not a
 * cube. Every shape is built once at class init (ADR-023).
 */
public class TownRoadBlock extends AbstractMachineBlock implements HasMachineTooltip {

	/** Energy moved through the wire a moment ago: the copper under the grate glows. */
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/** The road is the wire's last bend up into an inlet: no frame, the shape of the wire alone. */
	public static final BooleanProperty RISER = BooleanProperty.create("riser");

	/** The six arms, a cable's own properties. */
	private static final Map<Direction, BooleanProperty> ARMS = PipeBlock.PROPERTY_BY_DIRECTION;

	/** A riser's shape by its four side arms (bit = {@code Direction.get2DDataValue()}), built once (ADR-023). */
	private static final VoxelShape[] RISER_SHAPES = new VoxelShape[16];

	static {
		VoxelShape stem = Block.box(5, 0, 5, 11, 11, 11);
		for (int bits = 0; bits < 16; bits++) {
			VoxelShape shape = stem;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				if ((bits & (1 << dir.get2DDataValue())) != 0) {
					shape = Shapes.or(shape, switch (dir) {
						case NORTH -> Block.box(5, 5, 0, 11, 11, 5);
						case SOUTH -> Block.box(5, 5, 11, 11, 11, 16);
						case WEST -> Block.box(0, 5, 5, 5, 11, 11);
						default -> Block.box(11, 5, 5, 16, 11, 11);
					});
				}
			}
			RISER_SHAPES[bits] = shape.optimize();
		}
	}

	/** The light a powered road gives off: faint, a wire under a grate, not a lamp. */
	public static final int GLOW_LIGHT = 4;

	public TownRoadBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any().setValue(POWERED, false).setValue(RISER, false);
		for (BooleanProperty arm : ARMS.values()) {
			state = state.setValue(arm, false);
		}
		registerDefaultState(state);
	}

	/** The light level of {@code state}, for the block's properties. */
	public static int lightOf(BlockState state) {
		return state.hasProperty(POWERED) && state.getValue(POWERED) ? GLOW_LIGHT : 0;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(POWERED, RISER);
		ARMS.values().forEach(builder::add);
	}

	/**
	 * {@code state} with its arms and riser flag worked out from the world around {@code pos}. Read by placement, by a
	 * neighbour's shape update and by the block entity's first tick (a drone or a structure places the bare default).
	 */
	public static BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
		BlockState out = state;
		for (Map.Entry<Direction, BooleanProperty> arm : ARMS.entrySet()) {
			out = out.setValue(arm.getValue(), CableBlock.shouldConnectTo(level, pos, arm.getKey()));
		}
		return out.setValue(RISER, isRiser(level, pos, out));
	}

	/** A road on a road, wired sideways only to something that is not a road, and nothing above. */
	private static boolean isRiser(LevelReader level, BlockPos pos, BlockState state) {
		if (!state.getValue(ARMS.get(Direction.DOWN)) || state.getValue(ARMS.get(Direction.UP))
				|| !(level.getBlockState(pos.below()).getBlock() instanceof TownRoadBlock)) {
			return false;
		}
		boolean side = false;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (state.getValue(ARMS.get(dir))) {
				if (level.getBlockState(pos.relative(dir)).getBlock() instanceof TownRoadBlock) {
					return false;
				}
				side = true;
			}
		}
		return side;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		// The riser flag reads all four sides, so every arm is worked out again, not only the changed one.
		return withConnections(level, pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (!state.getValue(RISER)) {
			return Shapes.block();
		}
		int bits = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (state.getValue(ARMS.get(dir))) {
				bits |= 1 << dir.get2DDataValue();
			}
		}
		return RISER_SHAPES[bits];
	}

	/**
	 * The grate shows the cavity, so no face of the road hides a neighbour's: the ground beside it is drawn behind the
	 * bars. It still occludes in the registry sense (a full collision cube, R-PHY-05) and stops light like the solid
	 * block it was, except the riser, which is a wire in the air (the MOD-776 pattern of {@code IncubatorBlock}).
	 */
	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return state.getValue(RISER) ? 0 : 15;
	}

	/**
	 * No corner shadow on the neighbours (owner's round 4 of 0.1.219, R4-4). A full collision cube makes the smooth
	 * lighting darken the ground round a block, as under a wall; the road is an open grate, not a wall, so it shades
	 * nothing, as glass does not ({@code VacuumCollectorBlock}'s pattern).
	 */
	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	/** Two framed roads side by side are one channel: the walls between them are not drawn, as between panes. */
	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
		return adjacent.is(this) && !state.getValue(RISER) && !adjacent.getValue(RISER)
				|| super.skipRendering(state, adjacent, direction);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TownRoadBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/** A road joins the line the moment it is laid, as a cable does. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TownRoadBlockEntity road) {
			road.ensureRegistered();
		}
	}

	/** A machine placed or broken next to the road: the line looks for its endpoints again, as for a cable. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			NetworkManager.onNeighbourChanged(serverLevel, pos);
		}
	}

	/**
	 * Hover tooltip of the item (MOD-716, ADR-040): what it is, and the wire inside it. No numbers: the client would
	 * read them from its own config (MOD-761); the wire's are the insulated copper cable's.
	 */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.town_road.purpose",
						MachineTooltipSpec.Tone.GRAY)),
				List.of(MachineTooltipSpec.text("tooltip.alaindustrial.town_road.wire", MachineTooltipSpec.Tone.GRAY)));
	}

	/** Its lines are whole sentences: broken to the width of a vanilla description (R4-3). */
	@Override
	public boolean wrapsTooltipLines() {
		return true;
	}
}
