package dev.alaindustrial.block;

import dev.alaindustrial.core.fluid.PipeFamily;

/**
 * The steam pipe (MOD-662): the fluid pipe's geometry, block entity and network, carrying steam and
 * nothing else.
 *
 * <p><b>One rule, and it is the family.</b> A steam pipe joins only steam pipes, reaches only for ports
 * that give or take steam — a reactor column's top, a steam nozzle, a two-way inlet — and its segment
 * refuses every other fluid. Everything else, from the wrench ladder to the 50 mB/t segment buffer, is
 * inherited, so a steam line behaves like a water line the player already knows.
 *
 * <p>Not final: the {@link ReinforcedSteamPipeBlock reinforced grade} is this pipe in a shielding jacket.
 */
public class SteamPipeBlock extends FluidPipeBlock {

	public SteamPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public PipeFamily family() {
		return PipeFamily.STEAM;
	}
}
