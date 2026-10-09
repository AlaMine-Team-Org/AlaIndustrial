package dev.alaindustrial.block;

import net.minecraft.world.level.block.LadderBlock;

/**
 * Tempered iron ladder (MOD-795): climbs exactly like the vanilla ladder; only its look, sound and
 * hardness differ, and those live in its properties. The vanilla constructor is protected.
 */
public class TemperedIronLadderBlock extends LadderBlock {

	public TemperedIronLadderBlock(Properties properties) {
		super(properties);
	}
}
