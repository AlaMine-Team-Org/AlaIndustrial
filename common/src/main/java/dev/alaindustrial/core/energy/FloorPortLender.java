package dev.alaindustrial.core.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A thin block that lends its energy port THROUGH the block it rests on (MOD-764, ADR-046): a cable or a
 * machine placed directly under the floor block sees the lender's port on the floor's bottom face, so the
 * wire can stay hidden. Implemented by a {@code Block} — the decision is made from block states alone, like
 * {@link EnergyHostRedirect}, so the energy lookup and the network's endpoint scan agree by construction.
 *
 * <p>The floor qualifies only while it is a plain block — one without a block entity. A machine, a cable or a
 * chest under the lender keeps its own faces; nothing about them is borrowed.
 */
public interface FloorPortLender {

	/**
	 * Where the port lent through the floor at {@code floorPos} lives, for a lookup of the floor's
	 * {@code face}: the lender above the floor when {@code face} is the floor's bottom face, the floor holds no
	 * block entity and the block above it is a lender; otherwise {@code null}.
	 */
	@Nullable
	static BlockPos lenderThroughFloor(Level level, BlockPos floorPos, BlockState floorState, Direction face) {
		if (face != Direction.DOWN || floorState.hasBlockEntity()) {
			return null;
		}
		BlockPos above = floorPos.above();
		return level.getBlockState(above).getBlock() instanceof FloorPortLender ? above : null;
	}
}
