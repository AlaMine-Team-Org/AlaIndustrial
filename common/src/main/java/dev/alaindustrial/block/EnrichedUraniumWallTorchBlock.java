package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;

/**
 * Wall-mounted Enriched Uranium Torch (MOD-085): the wall variant of {@link EnrichedUraniumTorchBlock},
 * inheriting {@link WallTorchBlock}'s behaviour (FACING, placement, survival, rotate/mirror) and adding
 * the same enriched flame particles and the same {@link OilLoggedBlock} water/oil logging as the
 * standing torch. It has no BlockItem of its own — its drop and display name come from the standing torch via the
 * block {@code Properties.overrideLootTable(...)}/{@code overrideDescription(...)} set at registration.
 *
 * <p>The codec is typed {@code MapCodec<WallTorchBlock>} to satisfy the invariant return type of
 * {@link WallTorchBlock#codec()}. The particle getter downcasts to this concrete type because
 * {@code flameParticle} is {@code protected} in {@code TorchBlock} and this subclass lives in a different
 * package, so it may be read only through a reference of this class.
 */
public class EnrichedUraniumWallTorchBlock extends WallTorchBlock implements OilLoggedBlock {

	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	public static final MapCodec<WallTorchBlock> CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					PARTICLE_OPTIONS_FIELD.forGetter(b -> ((EnrichedUraniumWallTorchBlock) b).flameParticle),
					propertiesCodec()).apply(i, EnrichedUraniumWallTorchBlock::new));

	public EnrichedUraniumWallTorchBlock(SimpleParticleType flameParticle, BlockBehaviour.Properties properties) {
		super(flameParticle, properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
				.setValue(WATERLOGGED, false).setValue(OILLOGGED, false));
	}

	@Override
	public MapCodec<WallTorchBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder); // adds FACING
		builder.add(WATERLOGGED, OILLOGGED);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return OilLoggedBlock.fluidState(state, super.getFluidState(state));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context); // WallTorchBlock: FACING-oriented state, or null if no wall
		if (state == null) {
			return null;
		}
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		return OilLoggedBlock.loggedForPlacement(state, fluid);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		OilLoggedBlock.scheduleFluidTick(state, level, ticks, pos);
		// super (WallTorchBlock) still pops the torch off when its wall support is removed.
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	/**
	 * MOD-085 enhanced flame (wall): same enriched burst as the standing torch, but at the wall-mounted
	 * particle origin — the vanilla {@code WallTorchBlock.animateTick} offset ({@code 0.27} out from the
	 * wall along {@code -FACING}, {@code y+0.22}).
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Direction opposite = state.getValue(FACING).getOpposite();
		double x = pos.getX() + 0.5 + 0.27 * opposite.getStepX();
		double y = pos.getY() + 0.7 + 0.22;
		double z = pos.getZ() + 0.5 + 0.27 * opposite.getStepZ();
		EnrichedUraniumTorchBlock.spawnFx(level, random, x, y, z, this.flameParticle);
	}
}
