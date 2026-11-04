package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.core.energy.EnergyHostRedirect;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The reactor antenna's boom (MOD-941) — the cell in front of the antenna that the boom stands in.
 *
 * <p><b>Why a block of its own.</b> The boom sticks 14 px out of the wall. Drawn by the antenna's model it was lit
 * from inside the solid wall (dark) and could be built over, walked through and not aimed at. As a block in the
 * cell it occupies, it has its own light, a collision box and an outline, and nothing can be placed there.
 *
 * <p>Like the head of a bed it is the antenna's other half: the antenna places it (and grows it back when the
 * cell is free), it goes when the antenna goes, and breaking it breaks the antenna, which drops the item. It has
 * no item and an empty loot table.
 *
 * <p><b>A cable joins it straight into the antenna.</b> A cable beside the boom's cell — on any side but the antenna's
 * and the Yagi elements' ({@link ReactorAntennaGeometry#isCableFace}) — gets a copper sleeve from its side of the cell
 * into the port painted in the antenna's face, and the boom lends it the antenna's energy port
 * ({@link EnergyHostRedirect}). The sleeve is drawn from the six connection flags, which follow the cable's own arm
 * toward the boom, so the two always agree.
 *
 * <p><b>It gives out the reactor's alarm as redstone (MOD-945)</b> — 0, 7 or 15, held in {@link #ALARM} and written by
 * the controller only when the level changes. A weak signal, like a daylight detector's: dust, a repeater or a lamp
 * beside the boom takes it, the solid block beside it is not powered. Only the boom gives it, never the antenna cube:
 * the controller runs while redstone reaches it, a cube may touch the controller, and an alarm that reached it would
 * start the reactor by itself. The boom never touches the controller. A boom showing an alarm checks every
 * {@link #ALARM_CHECK_TICKS} ticks that its antenna still transmits and goes dark when it does not — the controller is
 * gone, unloaded, or no longer serves it — since no block-removal hook may tell it (they deadlock on 26.2).
 */
public class ReactorAntennaMastBlock extends Block implements EnergyHostRedirect {

	/** The antenna's facing: the boom points this way, and the antenna is one cell the opposite way. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** The alarm the boom shows, and so its redstone level (MOD-945). */
	public static final EnumProperty<ReactorAlarmLevel> ALARM = EnumProperty.create("alarm", ReactorAlarmLevel.class);

	/** How often a boom showing an alarm checks that its antenna still transmits: about one second. */
	public static final int ALARM_CHECK_TICKS = 20;

	/** Whether a cable joins the boom's cell through each face (world directions). */
	private static final Map<Direction, BooleanProperty> CABLE = PipeBlock.PROPERTY_BY_DIRECTION;

	/** One shape per facing and set of joined faces (6 x 64), assembled once at class load (ADR-023). */
	private static final VoxelShape[] SHAPES = buildShapes();

	public ReactorAntennaMastBlock(Properties properties) {
		super(properties);
		BlockState initial = defaultBlockState().setValue(FACING, Direction.NORTH)
				.setValue(ALARM, ReactorAlarmLevel.NONE);
		for (BooleanProperty joined : CABLE.values()) {
			initial = initial.setValue(joined, false);
		}
		registerDefaultState(initial);
	}

	private static VoxelShape[] buildShapes() {
		VoxelShape[] shapes = new VoxelShape[Direction.values().length * 64];
		for (Direction facing : Direction.values()) {
			VoxelShape boom = Shapes.empty();
			for (double[] box : ReactorAntennaGeometry.MAST_BOXES) {
				boom = Shapes.or(boom, turned(facing, box));
			}
			VoxelShape sleeve = turned(facing, ReactorAntennaGeometry.SLEEVE_BOX);
			for (int mask = 0; mask < 64; mask++) {
				VoxelShape shape = boom;
				if (mask != 0) {
					shape = Shapes.or(shape, sleeve);
				}
				for (Direction face : Direction.values()) {
					if ((mask & (1 << face.ordinal())) != 0) {
						shape = Shapes.or(shape, turned(face, ReactorAntennaGeometry.ARM_BOX));
					}
				}
				shapes[facing.ordinal() * 64 + mask] = shape.optimize();
			}
		}
		return shapes;
	}

	private static VoxelShape turned(Direction facing, double[] box) {
		double[] a = ReactorAntennaGeometry.rotate(facing, box[0], box[1], box[2]);
		double[] b = ReactorAntennaGeometry.rotate(facing, box[3], box[4], box[5]);
		return Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
				Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
	}

	private static int joinedMask(BlockState state) {
		int mask = 0;
		for (Direction face : Direction.values()) {
			if (state.getValue(CABLE.get(face))) {
				mask |= 1 << face.ordinal();
			}
		}
		return mask;
	}

	/**
	 * Whether the cable at {@code neighbour}, on the boom's {@code face}, joins it: it draws its arm toward the boom.
	 */
	private static boolean joins(BlockState state, Direction face, BlockState neighbour) {
		return ReactorAntennaGeometry.isCableFace(state.getValue(FACING), face)
				&& neighbour.getBlock() instanceof CableBlock
				&& neighbour.getValue(CABLE.get(face.getOpposite()));
	}

	/** The antenna this boom belongs to. */
	public static BlockPos antennaOf(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING).getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ALARM);
		CABLE.values().forEach(builder::add);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING).ordinal() * 64 + joinedMask(state)];
	}

	/** A boom whose antenna is gone goes too, as the head of a bed does; a cable beside it is joined or let go. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (neighborPos.equals(antennaOf(pos, state))) {
			return neighborState.getBlock() instanceof ReactorAntennaBlock
					&& neighborState.getValue(ReactorAntennaBlock.FACING) == state.getValue(FACING)
					? state : Blocks.AIR.defaultBlockState();
		}
		return state.setValue(CABLE.get(direction), joins(state, direction, neighborState));
	}

	/** Breaking the boom breaks the antenna: the player gets the antenna back, as from the antenna itself. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			BlockPos antenna = antennaOf(pos, state);
			if (level.getBlockState(antenna).getBlock() instanceof ReactorAntennaBlock) {
				level.destroyBlock(antenna, !player.preventsBlockDrops(), player);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * Shows the alarm {@code signal} on the boom of the antenna at {@code antennaPos} (MOD-945). The world is written
	 * only when the level changes, so a steady alarm costs no neighbour updates; a lit boom keeps its self-check
	 * scheduled.
	 */
	public static void showAlarm(Level level, BlockState antenna, BlockPos antennaPos, int signal) {
		BlockPos pos = antennaPos.relative(antenna.getValue(ReactorAntennaBlock.FACING));
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ReactorAntennaMastBlock)) {
			return;
		}
		ReactorAlarmLevel shown = ReactorAlarmLevel.of(signal);
		if (state.getValue(ALARM) != shown) {
			level.setBlock(pos, state.setValue(ALARM, shown), Block.UPDATE_ALL);
		}
		if (shown != ReactorAlarmLevel.NONE) {
			// A pending check of the same block at the same cell is not scheduled twice.
			level.scheduleTick(pos, state.getBlock(), ALARM_CHECK_TICKS);
		}
	}

	/** The self-check: a lit boom whose antenna no longer transmits goes dark; one that still does checks again. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(ALARM) == ReactorAlarmLevel.NONE) {
			return;
		}
		if (level.getBlockEntity(antennaOf(pos, state)) instanceof ReactorAntennaBlockEntity antenna
				&& antenna.isTransmitting(level.getGameTime())) {
			level.scheduleTick(pos, this, ALARM_CHECK_TICKS);
			return;
		}
		level.setBlock(pos, state.setValue(ALARM, ReactorAlarmLevel.NONE), Block.UPDATE_ALL);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	/** Weak only, read from its own state: no strong signal, so the solid block beside the boom is not powered. */
	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(ALARM).signal();
	}

	/** The antenna's port, through any face a cable may join ({@link ReactorAntennaGeometry#isCableFace}). */
	@Override
	public @Nullable BlockPos energyHost(BlockState state, BlockPos pos, Direction face) {
		return ReactorAntennaGeometry.isCableFace(state.getValue(FACING), face) ? antennaOf(pos, state) : null;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		BlockState turned = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
		for (Direction face : Direction.values()) {
			turned = turned.setValue(CABLE.get(rotation.rotate(face)), state.getValue(CABLE.get(face)));
		}
		return turned;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
