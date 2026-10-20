package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.FluidTapBlockEntity;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.registry.ModContent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Tap (MOD-837): a pipe-style fitting that lets any fluid out of whatever it is screwed onto — a tank, a
 * pump, a machine, the mob trap, the end of a fluid pipe — back into the world. It takes no energy: it is a
 * valve, not a machine.
 *
 * <p><b>Two forms, one facing.</b> Clicked onto the side of a block, the tap hangs on it ({@link #CEILING}
 * false): a horizontal tube leaves the source, an elbow turns it down, the nozzle is at the bottom of the
 * cell. Clicked onto the underside of a block it hangs from it ({@link #CEILING} true): the tube comes down,
 * the elbow turns it toward {@link #FACING}, and the nozzle is on the front edge. {@link #FACING} is always
 * where the nozzle points; the one face it takes fluid through is {@link #sourceFace}.
 *
 * <p><b>Open or closed.</b> A right click toggles it, and a redstone signal holds it open as well:
 * {@link #OPEN} = toggled or powered, and it turns the red lever. The toggle lives in the block entity.
 *
 * <p><b>Shape.</b> The pipes of the model, 6 px boxes, assembled once per form and facing at class
 * initialisation and only read in {@link #getShape} (ADR-023).
 */
public class FluidTapBlock extends BaseEntityBlock implements HasMachineTooltip {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

	/** Wall form facing SOUTH, source to the north: the tube to the elbow, then the drop to the nozzle. */
	private static final VoxelShape WALL_SOUTH = Shapes.or(
			Block.box(5, 5, 0, 11, 11, 11),
			Block.box(5, 1, 5, 11, 5, 11));
	/** Ceiling form facing SOUTH, source above: the drop from the top, then the tube out to the front. */
	private static final VoxelShape CEILING_SOUTH = Shapes.or(
			Block.box(5, 5, 5, 11, 16, 11),
			Block.box(5, 5, 11, 11, 11, 16));
	private static final Map<Direction, VoxelShape> WALL_SHAPES = rotations(WALL_SOUTH);
	private static final Map<Direction, VoxelShape> CEILING_SHAPES = rotations(CEILING_SOUTH);

	public FluidTapBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(CEILING, false)
				.setValue(OPEN, false));
	}

	/**
	 * The codec Minecraft 26.2 asks every block for ({@code BaseEntityBlock.codec()} is abstract there; 26.3
	 * removed block codecs): the unit codec of this very instance, as the mod's own block bases answer it
	 * (MOD-703). No {@code @Override} on purpose: on 26.2 it overrides, on 26.3 it is a method of its own that
	 * nothing calls, so this source is the same on both lines. 26.2 reads block codecs only in the datagen
	 * block-list report ({@code BlockTypes.CODEC} -> {@code BlockListReport}, javap).
	 */
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return MapCodec.unit(this);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, CEILING, OPEN);
	}

	/**
	 * Clicked under a block: the ceiling form, nozzle toward the player. Clicked on a side: the wall form,
	 * nozzle away from that block. Clicked on top of a floor: the wall form too, nozzle toward the player
	 * and the back to whatever stands behind it.
	 */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		boolean ceiling = clicked == Direction.DOWN;
		Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
		boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
		return defaultBlockState().setValue(FACING, facing).setValue(CEILING, ceiling).setValue(OPEN, powered);
	}

	/** The one face the tap takes fluid through: the block it is screwed onto. */
	public static Direction sourceFace(BlockState state) {
		return state.getValue(CEILING) ? Direction.UP : state.getValue(FACING).getOpposite();
	}

	/**
	 * The cell the nozzle pours into: below the cell for the wall form, whose nozzle points down at the
	 * floor of its own cell; in front for the ceiling form, whose mouth is on the front edge.
	 */
	public static BlockPos pourCell(BlockPos pos, BlockState state) {
		return state.getValue(CEILING) ? pos.relative(state.getValue(FACING)) : pos.below();
	}

	/**
	 * Where the orbs appear, as an offset from the block's corner: just past the nozzle, clear of the tap's
	 * own shape so a fresh orb is not pushed out of a box it was spawned in.
	 */
	public static Vec3 nozzle(BlockState state) {
		// In the SOUTH-facing frame: under the wall form's nozzle ring, at floor level, a hair in front of the
		// ring; in front of the ceiling form's mouth, at its height.
		double[] local = state.getValue(CEILING) ? new double[] {0.5, 0.3, 1.27} : new double[] {0.5, 0.02, 0.96};
		double[] turned = turn(local[0], local[2], state.getValue(FACING));
		return new Vec3(turned[0], local[1], turned[1]);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(CEILING) ? CEILING_SHAPES : WALL_SHAPES).get(state.getValue(FACING));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FluidTapBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModContent.FLUID_TAP_BE.get()) {
			return null;
		}
		return (lvl, pos, st, be) -> ((FluidTapBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
	}

	/** Right click: open or close. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FluidTapBlockEntity tap) {
			tap.toggle(server, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		refreshOpen(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		refreshOpen(level, pos);
	}

	/**
	 * Set {@link #OPEN} to "toggled open or powered". Clients only need it for the lever, and nothing around
	 * the tap changes shape, so the update goes to clients alone.
	 */
	public static void refreshOpen(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof FluidTapBlock)) {
			return;
		}
		boolean toggled = level.getBlockEntity(pos) instanceof FluidTapBlockEntity tap && tap.isToggledOpen();
		boolean open = toggled || level.hasNeighborSignal(pos);
		if (state.getValue(OPEN) != open) {
			level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040): one line, and three short ones under Shift. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.fluid_tap.what", MachineTooltipSpec.Tone.GRAY)),
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.fluid_tap.use", MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.note("tooltip.alaindustrial.fluid_tap.redstone",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.plain("tank_mb", () -> (int) FluidTapBlockEntity.CAPACITY_MB)));
	}

	// --- geometry in the SOUTH-facing frame of the model, turned to each facing like the blockstate ---

	/**
	 * A point of the SOUTH-facing frame (in blocks) turned to {@code facing}, the way the blockstate's Y
	 * rotation turns the model: west 90, north 180, east 270 degrees.
	 */
	public static double[] turn(double x, double z, Direction facing) {
		return switch (facing) {
			case WEST -> new double[] {1 - z, x};
			case NORTH -> new double[] {1 - x, 1 - z};
			case EAST -> new double[] {z, 1 - x};
			default -> new double[] {x, z};
		};
	}

	private static Map<Direction, VoxelShape> rotations(VoxelShape south) {
		Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			VoxelShape[] out = {Shapes.empty()};
			south.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
				double[] a = turn(x0, z0, facing);
				double[] b = turn(x1, z1, facing);
				out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]),
						Math.max(a[0], b[0]), y1, Math.max(a[1], b[1])));
			});
			map.put(facing, out[0].optimize());
		}
		return map;
	}
}
