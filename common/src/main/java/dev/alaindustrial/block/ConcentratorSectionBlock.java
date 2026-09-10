package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Concentrator Section (MOD-603) — the block a player crafts seven of to grow a Mirror Concentrator
 * from one block into a two-by-two-by-two machine.
 *
 * <p>Loose, it is an inert casing: no block entity, no energy, no inventory. Claimed by a structure,
 * it becomes one of seven eighths of the concentrator's model and nothing else — the machine's
 * buffer, its ticking and its energy face all stay on the core, exactly as the Workstation's upper
 * half is energy-inert. That is why there is no block entity here at all: seven of them would be
 * seven objects with nothing to remember.
 *
 * <p>Assembly and the rule that takes it apart both live in {@link ConcentratorStructure}; this
 * class only wires the three hooks every way of changing the world arrives through.
 */
public class ConcentratorSectionBlock extends Block {
	public static final MapCodec<ConcentratorSectionBlock> CODEC =
			simpleCodec(ConcentratorSectionBlock::new);

	/**
	 * Which cell of a structure this block is, {@link ConcentratorPart#LOOSE} when none.
	 *
	 * <p>{@link ConcentratorPart#CORE} is filtered out rather than merely unused: the core cell is
	 * the grown panel itself, so a section carrying that value would be a second, energy-less copy of
	 * the machine's heart. Excluding it here makes that state impossible instead of merely wrong.
	 */
	public static final EnumProperty<ConcentratorPart> PART = EnumProperty.create(
			"part", ConcentratorPart.class, part -> part != ConcentratorPart.CORE);

	/** Which way the structure this cell belongs to faces; meaningless while loose. */
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public ConcentratorSectionBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(PART, ConcentratorPart.LOOSE)
				.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART, FACING);
	}

	/**
	 * A freshly placed section is always loose and always faces north.
	 *
	 * <p>Deliberately NOT the player's facing. A loose section looks the same from every side, and
	 * the facing it will really carry is decided by assembly, from the box the player filled — taking
	 * it from the placer here would put a value in the world that the very next tick overwrites, and
	 * anyone reading the state mid-build would believe it meant something.
	 */
	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return state.getValue(PART).shape(state.getValue(FACING));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		ConcentratorStructure.tryAssemble(level, pos);
	}

	/**
	 * A neighbour changed — the section that completes the box may just have arrived, or the grown
	 * panel next door may just have finished evolving. {@link ConcentratorStructure#tryAssemble}
	 * returns immediately unless this block is still loose, which is what stops this recursing
	 * through the neighbour updates assembly itself sets off.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		ConcentratorStructure.tryAssemble(level, pos);
	}

	/**
	 * A cell that has lost a neighbour it needed becomes a loose section again, and the loss travels:
	 * the eight cells are face-connected, so one broken cell reaches all seven others through
	 * ordinary neighbour updates. Nothing is dropped here — each block is left standing in the form
	 * the player originally put down.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		BlockState answer = ConcentratorStructure.afterNeighbourChange(state, directionToNeighbour,
				neighbourState);
		if (answer != state) {
			return answer;
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
				neighbourState, random);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
