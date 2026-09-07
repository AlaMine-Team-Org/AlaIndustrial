package dev.alaindustrial.block;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** One description of the actual underground column, shared by harvesting and inspection. */
public final class KokSagyzRoots {
	private KokSagyzRoots() { }

	public record Column(boolean upper, boolean lower, boolean upperTip, boolean lowerTip, boolean growing) {
		public int depth() { return lower ? 2 : upper ? 1 : 0; }
		public boolean harvestable() { return upperTip || lowerTip; }
		public BlockPos harvestPos(BlockPos flower) {
			return lowerTip ? flower.below(2) : upperTip ? flower.below() : null;
		}
	}

	public static Column inspect(BlockGetter level, BlockPos flower) {
		BlockState one = level.getBlockState(flower.below());
		BlockState two = level.getBlockState(flower.below(2));
		boolean upper = one.is(ModContent.KOK_SAGYZ_ROOT.get());
		boolean lower = two.is(ModContent.KOK_SAGYZ_ROOT.get());
		boolean upperTip = upper && one.getValue(KokSagyzRootBlock.TIP);
		boolean lowerTip = lower && two.getValue(KokSagyzRootBlock.TIP);
		return new Column(upper, lower, upperTip, lowerTip,
				upper && !upperTip && !lower && KokSagyzBlock.isRootableSoil(two));
	}
}
