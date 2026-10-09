package dev.alaindustrial.block;

import net.minecraft.world.level.block.IronBarsBlock;

/**
 * Tempered iron bars (MOD-795): vanilla iron bars in the tempered iron look. The vanilla constructor is
 * protected, so the block needs a class of its own; behaviour is inherited unchanged.
 */
public class TemperedIronBarsBlock extends IronBarsBlock {

	public TemperedIronBarsBlock(Properties properties) {
		super(properties);
	}
}
