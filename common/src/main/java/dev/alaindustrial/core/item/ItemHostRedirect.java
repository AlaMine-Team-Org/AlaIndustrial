package dev.alaindustrial.core.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block with no inventory of its own that lends another block's inventory to automation (MOD-951) — a cell of a
 * multiblock whose items live in one place, the item twin of {@link dev.alaindustrial.core.energy.EnergyHostRedirect}.
 * The quarry's socket foundation is the first: pipes and hoppers at the foot of the main tower reach the controller's
 * buffer and drill bit slot, seven blocks up.
 *
 * <p>Both loaders publish the lent inventory as the block's item capability for every block implementing this
 * ({@code BlockCapabilityRoster.itemLenders}), so the mod's pipes and other mods' see it; a block that also implements
 * {@code WorldlyContainerHolder} through {@link #lent} serves the vanilla hopper the same view. The host's own
 * slot rules hold — which slots a face reaches, what goes in, what comes out — because it is the host's container that
 * is lent, not a copy.
 */
public interface ItemHostRedirect {

	/**
	 * The position of the block whose inventory {@code face} of this block lends, or {@code null} when it lends none.
	 * Decided from the block state alone, the way the energy redirect is.
	 *
	 * @param face the world face of THIS block automation touches; {@code null} for a query with no side
	 */
	@Nullable
	BlockPos itemHost(BlockState state, BlockPos pos, @Nullable Direction face);

	/** The inventory {@code state} at {@code pos} lends through {@code face}, or {@code null} for none. */
	static @Nullable WorldlyContainer lent(BlockGetter level, BlockPos pos, BlockState state,
			@Nullable Direction face) {
		if (!(state.getBlock() instanceof ItemHostRedirect redirect)) {
			return null;
		}
		BlockPos host = redirect.itemHost(state, pos, face);
		if (host == null) {
			return null;
		}
		return level.getBlockEntity(host) instanceof WorldlyContainer container ? container : null;
	}
}
