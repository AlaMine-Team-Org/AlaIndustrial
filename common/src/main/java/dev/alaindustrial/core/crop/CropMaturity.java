package dev.alaindustrial.core.crop;

import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The single source of truth for "is this crop ready to be taken?" — shared by the
 * {@link dev.alaindustrial.item.tool.ScytheItem} (manual harvest) and the Garden Drone Station
 * (automated harvest, MOD-277).
 *
 * <p>Extracted from {@code ScytheItem.isCropTarget} in MOD-277 rather than reimplemented: the rules
 * below are a set of hard-won edge cases (stems, stalk bases, berry bushes), and two copies would
 * drift apart the moment either side gained a new crop rule. Both callers now answer the question
 * with the same code, so a crop the scythe refuses to cut is also a crop the drone refuses to
 * harvest.
 */
public final class CropMaturity {

	/** Sweet berry bush: ripe from age 2 (drops berries); age 0/1 are left to grow. */
	private static final int SWEET_BERRY_RIPE_AGE = 2;

	private CropMaturity() {
	}

	/**
	 * Whether the block at {@code pos} is a <b>mature</b> crop in {@link ModTags.Blocks#SCYTHE_CROPS}.
	 * Immature crops are left to keep growing, and decorative foliage is untouched (it is not in the
	 * crops tag).
	 *
	 * <p>The tag pulls {@code #minecraft:crops}, whose 26.2 membership is broader than
	 * {@link CropBlock}: wheat/carrot/potato/beetroot/torchflower_crop are {@code CropBlock}, but
	 * {@code melon_stem}/{@code pumpkin_stem} are {@code StemBlock} and {@code pitcher_crop} is a
	 * {@code DoublePlantBlock}. Each type needs its own maturity rule, and stems are deliberately
	 * never harvested (they are not a crop you pick — they spawn a fruit on a neighbour and keep
	 * growing), matching MOD-098 decision 2.
	 *
	 * <p>Cactus / sugar cane grow as a vertical column from a single base block on the ground. Only
	 * the blocks <b>above the base</b> are harvested — i.e. this block is a target only when the
	 * block below it is the same type (it is part of the stalk, not the root). That leaves the base
	 * alive to regrow, exactly like hand-picking the top of a cane/cactus (MOD-098 decision 1).
	 *
	 * <p>Takes a {@link BlockGetter} rather than a {@code Level}: the only world access is reading
	 * the block below for the stalk-base check, and the wider type lets a caller pass any block view
	 * (the drone station scans through the level, the scythe through the player's level).
	 *
	 * @param level the block view, to read the block below for the cactus/cane base check
	 * @param pos   the candidate position
	 * @param state the candidate state
	 */
	public static boolean isHarvestable(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.is(ModTags.Blocks.SCYTHE_CROPS)) {
			return false;
		}
		Block block = state.getBlock();
		// CropBlock covers wheat, carrots, potatoes, beetroots, torchflower_crop (all its subclasses).
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof SweetBerryBushBlock) {
			return state.getValue(SweetBerryBushBlock.AGE) >= SWEET_BERRY_RIPE_AGE;
		}
		// Cactus / sugar cane: harvest only above the base. The base block sits on ground with nothing
		// of the same type beneath it; any same-typed block above the base is the pickable stalk.
		if (isStalkBlock(block)) {
			return level.getBlockState(pos.below()).is(block);
		}
		// Melon/pumpkin stems (StemBlock) are never harvested: they are not a pickable crop — they grow
		// a fruit on a neighbour and keep growing. Explicit guard before the AGE fallback, because a
		// ripe stem DOES carry AGE at max and would otherwise be caught there (MOD-098 decision 2).
		if (block instanceof StemBlock) {
			return false;
		}
		// Pitcher crop (DoublePlantBlock, not CropBlock): mature at its max AGE. Same for any other
		// future crop that carries a vanilla AGE property — harvest at the property's top value, so new
		// crops added to the tag by Mojang or mods work without a code change.
		IntegerProperty age = findAgeProperty(state);
		if (age != null) {
			int max = age.getPossibleValues().stream().max(Integer::compare).orElse(0);
			return state.getValue(age) >= max;
		}
		// Unknown tagged block with no AGE rule and no specific handler: never auto-harvest. A block we
		// cannot prove ripe is safer left untouched than broken blind.
		return false;
	}

	/**
	 * Whether the block at {@code pos} is a crop this mod knows how to grow but which is <b>not yet
	 * ripe</b> — i.e. a legitimate target for fertilizer (MOD-277). Deliberately defined as "in the
	 * crops tag, and {@link #isHarvestable} says no", so the fertilize and harvest sets can never
	 * overlap or leave a gap: every tagged crop is exactly one of the two on any given tick.
	 *
	 * <p>Note this returns {@code true} for stems, which are tagged but never harvestable — that is
	 * intended: a stem is a growing crop worth fertilizing even though its fruit, not the stem
	 * itself, is what gets picked.
	 */
	public static boolean isFertilizable(BlockGetter level, BlockPos pos, BlockState state) {
		return !state.isAir()
				&& state.is(ModTags.Blocks.SCYTHE_CROPS)
				&& !isHarvestable(level, pos, state);
	}

	/** {@code true} for the cactus / sugar cane columns that regrow from a base (the stalk crops). */
	private static boolean isStalkBlock(Block block) {
		return block == net.minecraft.world.level.block.Blocks.CACTUS
				|| block == net.minecraft.world.level.block.Blocks.SUGAR_CANE;
	}

	/**
	 * The block's {@code AGE} {@link IntegerProperty} if it has one (most growing blocks do), else
	 * {@code null}. Used as the maturity fallback for tagged crops that are not {@link CropBlock} and
	 * not handled by a specific branch (e.g. {@code pitcher_crop}).
	 */
	private static IntegerProperty findAgeProperty(BlockState state) {
		for (var property : state.getProperties()) {
			if (property instanceof IntegerProperty ip && "age".equals(property.getName())) {
				return ip;
			}
		}
		return null;
	}
}
