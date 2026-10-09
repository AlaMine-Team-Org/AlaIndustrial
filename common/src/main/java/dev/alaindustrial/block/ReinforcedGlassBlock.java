package dev.alaindustrial.block;

import net.minecraft.world.level.block.TransparentBlock;

/**
 * Reinforced glass (MOD-795): vanilla glass behaviour — a shared face between two panes is not drawn,
 * light passes, full brightness inside — with the hardness and blast resistance of its properties. The
 * vanilla constructor is protected, so the block needs a class of its own.
 */
public class ReinforcedGlassBlock extends TransparentBlock {

	public ReinforcedGlassBlock(Properties properties) {
		super(properties);
	}
}
