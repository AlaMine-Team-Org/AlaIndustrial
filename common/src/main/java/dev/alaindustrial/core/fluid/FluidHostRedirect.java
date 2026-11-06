package dev.alaindustrial.core.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block with no tank of its own that lends another block's tank to automation (MOD-952) — the fluid twin of
 * {@link dev.alaindustrial.core.item.ItemHostRedirect}. The quarry's socket foundation is the first: pipes at the foot
 * of the main tower fill the controller's water tank, seven blocks up.
 *
 * <p>Both loaders publish the lent tank as the block's fluid capability for every block implementing this
 * ({@code BlockCapabilityRoster.fluidLenders}). The host's own tank rules hold — what goes in, what comes out —
 * because it is the host's port that is lent, not a copy.
 */
public interface FluidHostRedirect {

	/**
	 * The position of the block whose tank {@code face} of this block lends, or {@code null} when it lends none.
	 *
	 * @param face the world face of THIS block automation touches; {@code null} for a query with no side
	 */
	@Nullable
	BlockPos fluidHost(BlockState state, BlockPos pos, @Nullable Direction face);

	/** The tank {@code state} at {@code pos} lends through {@code face}, or {@code null} for none. */
	static @Nullable FluidPort lent(BlockGetter level, BlockPos pos, BlockState state, @Nullable Direction face) {
		if (!(state.getBlock() instanceof FluidHostRedirect redirect)) {
			return null;
		}
		BlockPos host = redirect.fluidHost(state, pos, face);
		if (host == null) {
			return null;
		}
		return level.getBlockEntity(host) instanceof FluidPortHost tank ? tank.fluidPortForLookup(face) : null;
	}
}
