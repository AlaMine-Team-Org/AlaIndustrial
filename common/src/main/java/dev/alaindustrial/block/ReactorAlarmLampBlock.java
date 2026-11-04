package dev.alaindustrial.block;

import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The alarm lamp (MOD-945): a red beacon in an iron cage that glows and flashes while it is lit.
 *
 * <p><b>Two things light it, and either is enough.</b> Redstone, anywhere — a reactor antenna's boom, a pressure
 * plate, a mob farm: it behaves as a redstone lamp does, lit at once and dark {@link #OFF_DELAY_TICKS} ticks after the
 * signal goes ({@link #LIT}). And its own reactor (owner decision 2026-11-04): pressed with its base to the shell of a
 * sealed reactor room, or to a bare reactor's controller or rack, it lights by that reactor's alarm with no wire at
 * all ({@link #ALARM}). The controller writes that flag as the last step of its tick, only on a change; a lamp showing
 * it checks every {@link #ALARM_CHECK_TICKS} ticks that it still stands on a working reactor and goes dark when not.
 *
 * <p>It is placed on any of the six faces: its base on the clicked block, the cage pointing away ({@link #FACING}).
 * No block entity: the flashing is the lens texture's own animation, and the light of the block stays a steady
 * {@link #LIT_LIGHT}, so the world never relights on a flash.
 */
public class ReactorAlarmLampBlock extends Block implements HasMachineTooltip {

	/** The way the cage points: away from the block its base is pressed to. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** Lit by redstone. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** Lit by the alarm of the reactor its base is pressed to. */
	public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

	/** Light of a lit lamp: as bright as a torch. */
	public static final int LIT_LIGHT = 14;

	/** Ticks a lit lamp stays lit once its signal is gone, as a redstone lamp does. */
	public static final int OFF_DELAY_TICKS = 4;

	/** How often a lamp lit by its reactor checks that it still stands on one: about one second. */
	public static final int ALARM_CHECK_TICKS = 20;

	/** The beacon and its cage, 14 x 15 x 14 px standing on its base, one shape per facing (ADR-023). */
	private static final VoxelShape[] SHAPES = buildShapes();

	public ReactorAlarmLampBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(LIT, false)
				.setValue(ALARM, false));
	}

	private static VoxelShape[] buildShapes() {
		VoxelShape[] shapes = new VoxelShape[Direction.values().length];
		shapes[Direction.UP.ordinal()] = Block.box(1, 0, 1, 15, 15, 15);
		shapes[Direction.DOWN.ordinal()] = Block.box(1, 1, 1, 15, 16, 15);
		shapes[Direction.NORTH.ordinal()] = Block.box(1, 1, 1, 15, 15, 16);
		shapes[Direction.SOUTH.ordinal()] = Block.box(1, 1, 0, 15, 15, 15);
		shapes[Direction.EAST.ordinal()] = Block.box(0, 1, 1, 15, 15, 15);
		shapes[Direction.WEST.ordinal()] = Block.box(1, 1, 1, 16, 15, 15);
		return shapes;
	}

	/** The block's light: {@link #LIT_LIGHT} while lit by either, none while dark. */
	public static int lightLevel(BlockState state) {
		return isOn(state) ? LIT_LIGHT : 0;
	}

	/** Whether the lamp shows lit: by redstone or by its reactor. */
	public static boolean isOn(BlockState state) {
		return state.getValue(LIT) || state.getValue(ALARM);
	}

	/** The cell the lamp's base is pressed to. */
	public static BlockPos supportOf(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING).getOpposite());
	}

	/**
	 * Shows its reactor's alarm on the lamp at {@code pos} (MOD-945): written only on a change, never on a lamp whose
	 * support is no longer a working reactor's, and a lit lamp keeps its self-check scheduled.
	 */
	public static void showReactorAlarm(Level level, BlockPos pos, boolean on) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ReactorAlarmLampBlock lamp)) {
			return;
		}
		// The same rule as the self-check, or a controller that has not yet noticed a breach would light again a
		// lamp the check had just put out.
		on = on && standsOnReactor(level.getBlockState(supportOf(pos, state)));
		if (state.getValue(ALARM) != on) {
			level.setBlock(pos, state.setValue(ALARM, on), Block.UPDATE_CLIENTS);
		}
		if (on) {
			// A pending check of the same block at the same cell is not scheduled twice.
			level.scheduleTick(pos, lamp, ALARM_CHECK_TICKS);
		}
	}

	/**
	 * Whether the block a lamp's base is pressed to is a working reactor's: a formed shell cell, a controller or a
	 * fuel rack. A breach, a broken controller or a taken support un-forms or removes it, and the lamp goes dark.
	 */
	private static boolean standsOnReactor(BlockState support) {
		Block block = support.getBlock();
		if (block instanceof ReactorShellBlock) {
			return support.getValue(ReactorShellBlock.FORMED);
		}
		return block instanceof ReactorControllerBlock || block instanceof FuelRodAssemblyBlock;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace())
				.setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
			@Nullable Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide()) {
			return;
		}
		boolean lit = state.getValue(LIT);
		boolean powered = level.hasNeighborSignal(pos);
		if (lit && !powered) {
			level.scheduleTick(pos, this, OFF_DELAY_TICKS);
		} else if (!lit && powered) {
			level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
		}
	}

	/** One tick serves both clocks: the redstone off-delay and the reactor self-check. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState next = state;
		if (state.getValue(LIT) && !level.hasNeighborSignal(pos)) {
			next = next.setValue(LIT, false);
		}
		if (state.getValue(ALARM)) {
			if (standsOnReactor(level.getBlockState(supportOf(pos, state)))) {
				level.scheduleTick(pos, this, ALARM_CHECK_TICKS);
			} else {
				next = next.setValue(ALARM, false);
			}
		}
		if (next != state) {
			level.setBlock(pos, next, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING).ordinal()];
	}

	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.reactor_alarm_lamp.purpose",
						MachineTooltipSpec.Tone.GRAY)),
				List.of(MachineTooltipSpec.text("tooltip.alaindustrial.reactor_alarm_lamp.reactor",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.text("tooltip.alaindustrial.reactor_alarm_lamp.antenna",
								MachineTooltipSpec.Tone.GRAY)));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, ALARM);
	}
}
