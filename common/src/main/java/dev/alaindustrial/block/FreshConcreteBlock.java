package dev.alaindustrial.block;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Fresh concrete (MOD-927): placed soft, it sets into industrial concrete in four visible stages.
 *
 * <p><b>Scheduled ticks, not random ones.</b> Each stage lasts exactly {@link Config#freshConcreteStageTicks}
 * ticks, so a slab poured at dusk is hard by the next dusk, also in a world with random ticks turned off.
 * A scheduled tick waits while its chunk is unloaded, which is the "only in loaded chunks" rule for free.
 *
 * <p><b>Water keeps it wet.</b> A tick that finds water on any of the six faces does not advance and waits a
 * whole stage again; the block keeps its stage, so taking the water away resumes where it stopped.
 *
 * <p>Breaking it returns the block item, whose state is the default one: the stage resets. A piston moves it
 * like any block and keeps the stage; the move places it anew, which schedules the next stage again.
 */
public class FreshConcreteBlock extends Block {

	/** The last stage; the tick after it turns the block into industrial concrete. */
	public static final int MAX_SETTING = 3;

	/** How far the concrete has set: 0 just poured and dark with water, 3 almost dry. */
	public static final IntegerProperty SETTING = IntegerProperty.create("setting", 0, MAX_SETTING);

	public FreshConcreteBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(SETTING, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SETTING);
	}

	/**
	 * Starts the clock when the block arrives from somewhere else; a stage change replaces the block by itself,
	 * and {@link #tick} has already scheduled the next one.
	 */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !oldState.is(this)) {
			level.scheduleTick(pos, this, stageTicks());
		}
	}

	/** One stage of setting, or none while water touches the block. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (touchesWater(level, pos)) {
			level.scheduleTick(pos, this, stageTicks());
			return;
		}
		int setting = state.getValue(SETTING);
		if (setting >= MAX_SETTING) {
			level.setBlockAndUpdate(pos, ModContent.INDUSTRIAL_CONCRETE.get().defaultBlockState());
			return;
		}
		level.setBlockAndUpdate(pos, state.setValue(SETTING, setting + 1));
		level.scheduleTick(pos, this, stageTicks());
	}

	/** Whether water stands on any of the six faces (a source, a flow or a waterlogged neighbour). */
	public static boolean touchesWater(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (level.getFluidState(pos.relative(direction)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static int stageTicks() {
		return Math.max(1, Config.freshConcreteStageTicks);
	}
}
