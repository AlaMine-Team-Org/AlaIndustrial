package dev.alaindustrial.block;

import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The charging bed (MOD-876; epic MOD-824, decision R7): where a shift worker sleeps at night and charges its battery.
 * A low tempered iron plinth, a mattress, and a headboard console with copper contacts at the head.
 *
 * <p><b>Two cells, as a vanilla bed (owner's round 4 of 0.1.219, R4-9).</b> A worker lies along the bed, so it is a
 * {@link #PART head and a foot}: {@link #FACING} points from the foot to the head, as a vanilla bed's does, so the
 * builder drone's pairing of two-cell blocks ({@code PairedCells}) and the material count ({@code BuildMaterials},
 * the foot pays) take it as they take a bed. It is put down from the foot, the head one cell further the way the
 * player looks; a half whose other half is gone goes with it. It is not a vanilla bed: a player does not sleep in it,
 * and it keeps no «occupied» flag.
 *
 * <p><b>No buffer of its own.</b> The bed holds no EU and takes no cable. It charges a worker from its building's house
 * inlet ({@link dev.alaindustrial.settlement.HousePowerSource}); the house knows its inlet and its bed's head cell
 * ({@code TownHousing.bed}).
 */
public class ChargingBedBlock extends Block implements HasMachineTooltip {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

	/** The height of the mattress a worker lies on, in blocks. */
	public static final double SLEEP_HEIGHT = 6.0 / 16.0;

	/** The head cell, the head to the north: plinth and mattress, the headboard console at the north edge. */
	private static final Map<Direction, VoxelShape> HEAD_SHAPE = Shapes.rotateHorizontal(Shapes.or(
			Block.box(1, 0, 1, 15, 6, 16),
			Block.box(1, 0, 1, 15, 12, 3)));
	/** The foot cell, the head to the north: plinth and mattress, open at the south end. */
	private static final Map<Direction, VoxelShape> FOOT_SHAPE = Shapes.rotateHorizontal(Block.box(1, 0, 0, 15, 6, 15));

	public ChargingBedBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, PART);
	}

	/** The cell of the other half of a bed standing as {@code state} at {@code pos}. */
	public static BlockPos otherHalf(BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FACING);
		return pos.relative(state.getValue(PART) == BedPart.FOOT ? facing : facing.getOpposite());
	}

	/** The foot where the player clicked, the head one cell further the way the player looks; none if that is taken. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection();
		BlockPos head = context.getClickedPos().relative(facing);
		Level level = context.getLevel();
		return level.getBlockState(head).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(head)
				? defaultBlockState().setValue(FACING, facing) : null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		level.setBlockAndUpdate(pos.relative(state.getValue(FACING)), state.setValue(PART, BedPart.HEAD));
	}

	/** A half whose other half is gone goes too, as a vanilla bed's does; the head drops the item. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (neighborPos.equals(otherHalf(pos, state))) {
			return neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART)
					? state : Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
	}

	/** A creative player breaking the foot takes the head with it and gets no item, as with a vanilla bed. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(PART) == BedPart.FOOT) {
			BlockPos head = otherHalf(pos, state);
			BlockState headState = level.getBlockState(head);
			if (headState.is(this) && headState.getValue(PART) == BedPart.HEAD) {
				level.setBlock(head, Blocks.AIR.defaultBlockState(),
						Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, head, Block.getId(headState));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(PART) == BedPart.HEAD ? HEAD_SHAPE : FOOT_SHAPE).get(state.getValue(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Hover tooltip of the item (MOD-716, ADR-040): what it is for. Takes no EU itself, so no tier line. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.charging_bed.purpose",
						MachineTooltipSpec.Tone.GRAY)),
				List.of());
	}

	/** Its lines are whole sentences: broken to the width of a vanilla description (R4-3). */
	@Override
	public boolean wrapsTooltipLines() {
		return true;
	}
}
