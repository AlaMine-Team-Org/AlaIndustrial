package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Standing Enriched Uranium Torch (MOD-085): a vanilla-behaviour torch (thin shape, no collision,
 * instabreak, light level 14 — identical to the vanilla torch) that spawns the mod's green flame plus a
 * richer particle burst (see {@link #spawnFx}), and — unlike a vanilla torch — is <b>waterloggable</b>:
 * it can be placed underwater and keeps burning ("sealed uranium" perk).
 *
 * <p>Behaviour is inherited from {@link TorchBlock}/{@code BaseTorchBlock}; this subclass adds its own
 * {@link MapCodec}, the enhanced {@code animateTick}, and the {@link SimpleWaterloggedBlock} wiring
 * (WATERLOGGED property + fluid state + placement/updateShape), following the vanilla {@code LadderBlock}
 * pattern. The particle is supplied at construction from the loader-neutral
 * {@code ModParticles.ENRICHED_URANIUM_FLAME} facade by each loader's registry.
 */
public class EnrichedUraniumTorchBlock extends TorchBlock implements SimpleWaterloggedBlock {

	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	public static final MapCodec<EnrichedUraniumTorchBlock> CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(PARTICLE_OPTIONS_FIELD.forGetter(b -> b.flameParticle), propertiesCodec())
					.apply(i, EnrichedUraniumTorchBlock::new));

	public EnrichedUraniumTorchBlock(SimpleParticleType flameParticle, BlockBehaviour.Properties properties) {
		super(flameParticle, properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
	}

	@Override
	public MapCodec<? extends TorchBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WATERLOGGED);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		return this.defaultBlockState().setValue(WATERLOGGED, fluid.is(Fluids.WATER));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		// super (BaseTorchBlock) still pops the torch off when its floor support is removed.
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	/**
	 * MOD-085 enhanced flame (standing): a livelier green flame plus an occasional radioactive-green
	 * shimmer, at the standing-torch particle origin (block centre, {@code y+0.7}) — same origin the
	 * vanilla {@code TorchBlock.animateTick} uses. Overrides the inherited base so the torch reads as an
	 * "enriched" light, not a plain torch.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		spawnFx(level, random, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, this.flameParticle);
	}

	/**
	 * Shared enriched-torch client particle burst, reused by the wall variant at its own offset origin.
	 * Base torch feel (smoke + one green flame) + a second jittered flame for a livelier fire + an
	 * occasional {@link ParticleTypes#HAPPY_VILLAGER} green sparkle drifting up — the "radioactive glow".
	 * Client-only display tick, so a little extra particle work here is cheap.
	 */
	public static void spawnFx(Level level, RandomSource random, double x, double y, double z, SimpleParticleType flame) {
		level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
		level.addParticle(flame, x, y, z, 0.0, 0.0, 0.0);
		double jitter = (random.nextDouble() - 0.5) * 0.10;
		double jitterZ = (random.nextDouble() - 0.5) * 0.10;
		level.addParticle(flame, x + jitter, y + 0.02, z + jitterZ, 0.0, 0.006, 0.0);
		if (random.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.HAPPY_VILLAGER,
					x + (random.nextDouble() - 0.5) * 0.32, y + 0.10 + random.nextDouble() * 0.22,
					z + (random.nextDouble() - 0.5) * 0.32, 0.0, 0.0, 0.0);
		}
	}
}
