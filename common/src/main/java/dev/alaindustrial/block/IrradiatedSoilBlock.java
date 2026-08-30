package dev.alaindustrial.block;

import dev.alaindustrial.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Ground a reactor accident poisoned (MOD-471) — the scar an explosion leaves once the fire is out.
 *
 * <p><b>It fades on its own, and the player can hurry it along with water.</b> Contamination that
 * never lifts is a permanent hole in someone's world, and this mod's standing rule is not to break a
 * player's world; contamination that lifts instantly is scenery. So it decays on the vanilla random
 * tick through four visible steps, and a block of water resting on top halves the wait — which is
 * both real decontamination practice and, more usefully here, the one gesture a reactor operator
 * already knows. The reactor asked for water all along.
 *
 * <p><b>No block item, on purpose.</b> There is nothing to craft it from and nowhere to put it: it is
 * a consequence, like fire. The same choice the oil and distillate blocks make — registered in
 * {@code ContentManifest.BLOCKS} with no entry in {@code ITEM_FACTORIES}. Its loot table is empty for
 * the same reason: digging up a contaminated patch should remove it, not hand you a portable one.
 *
 * <p><b>The intensity is not decoration.</b> It is the decay countdown, the texture variant and what
 * the Geiger counter (MOD-475) reads; radiation strength scales with it, so a field that is nearly
 * clean reads as nearly clean rather than switching off all at once.
 */
public class IrradiatedSoilBlock extends Block {

	public static final int MAX_INTENSITY = 3;

	/** How poisoned this cell still is: 3 fresh from the blast, 0 one step from being ordinary dirt. */
	public static final IntegerProperty INTENSITY = IntegerProperty.create("intensity", 0, MAX_INTENSITY);

	public IrradiatedSoilBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(INTENSITY, MAX_INTENSITY));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(INTENSITY);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	/**
	 * One step of decay, or none this time.
	 *
	 * <p>The chance is halved-in-reverse by water: {@code reactorFalloutDecayChancePercent} is the dry
	 * rate and washing doubles it. Doubling rather than "always" keeps the water useful without making
	 * it a delete button — a player pouring a bucket over a crater should shorten the cleanup, not skip
	 * it.
	 */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int chance = Math.max(1, Config.reactorFalloutDecayChancePercent);
		if (level.getFluidState(pos.above()).is(net.minecraft.world.level.material.Fluids.WATER)) {
			chance = Math.min(100, chance * 2);
		}
		if (random.nextInt(100) >= chance) {
			return;
		}
		int intensity = state.getValue(INTENSITY);
		if (intensity <= 0) {
			// Back to ordinary ground rather than to air: the crater keeps its shape, it just stops
			// being dangerous. Turning it to air would quietly deepen every crater over time.
			level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
			return;
		}
		level.setBlockAndUpdate(pos, state.setValue(INTENSITY, intensity - 1));
	}

	/** Radiation strength of one cell at this intensity, as a share of the configured per-block dose. */
	public static int doseFor(BlockState state) {
		if (!(state.getBlock() instanceof IrradiatedSoilBlock)) {
			return 0;
		}
		int intensity = state.getValue(INTENSITY);
		return Math.max(0, Config.reactorFalloutDosePerBlock) * intensity / MAX_INTENSITY;
	}
}
