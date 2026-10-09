package dev.alaindustrial.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stairs cut from one of the mod's metal blocks (MOD-796).
 *
 * <p>Vanilla stairs with nothing added: the class exists only because {@link StairBlock}'s constructor is
 * {@code protected}, so the content manifest cannot name {@code StairBlock::new}. The base state is the
 * metal block the stairs are cut from — vanilla reads it for the explosion resistance and the particles.
 */
public class MetalStairBlock extends StairBlock {
	public MetalStairBlock(BlockState base, Properties properties) {
		super(base, properties);
	}
}
