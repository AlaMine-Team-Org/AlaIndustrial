package dev.alaindustrial.core.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block with no energy of its own that lends another block's energy port through some of its faces
 * (MOD-608) — a cell of a multiblock whose buffer lives in one place.
 *
 * <p><b>One rule, two readers.</b> The cable arm ({@code CableBlock.connectsTo}) and the network's port
 * lookup ({@link EnergyLookup}) both ask {@link #energyHost}, so a face can neither draw an arm the
 * network never uses nor feed a network while showing no arm. The two used to be decided separately,
 * and a cell that was taught only one of them looked connected and carried nothing, or the reverse.
 *
 * <p><b>The endpoint stays where the cable touches.</b> The lookup lends the HOST's port, but the network
 * records the endpoint at this block's own position. Every rule that finds an endpoint's cables as its
 * six neighbours — the distance fields, charging the line, drawing from it — keeps working unchanged and
 * measures from the real point of contact. Recording the host's position instead would strand every
 * cable that touches only this block.
 *
 * <p><b>One host, one supply.</b> A host reached through several cells is still one machine. The network
 * counts its supply and caps its packet once per host ({@link #hostOf}), so a machine touched through
 * three cells behaves like a one-block machine with three cables on it — not like three machines
 * sharing one buffer, which would report triple the supply and push three packets a tick.
 *
 * <p><b>Limits of the contract.</b> The host is asked through the same world face, which is right only
 * for a host whose role does not depend on the face; an implementation with a face-sensitive host must
 * answer {@code null} for the faces the host would refuse. Only the supply side is counted per host: a
 * multi-cell CONSUMER would still have its demand counted once per touching cell.
 */
public interface EnergyHostRedirect {

	/**
	 * The position of the block whose energy port {@code face} of this block exposes, or {@code null}
	 * when this face exposes none. Decided from the block state alone, with no block entity, so the cable
	 * arm is right the instant the block changes — the reason {@code AbstractMachineBlock} decides its
	 * own connectability at the block level too.
	 *
	 * @param face the world face of THIS block the cable touches, pointing from this block toward it
	 */
	@Nullable
	BlockPos energyHost(BlockState state, BlockPos pos, Direction face);

	/**
	 * Where the energy seen at {@code pos} through {@code face} really lives: the host for a redirecting
	 * block, {@code pos} itself for any other block, and {@code null} when a redirecting face exposes
	 * nothing or its host is not loaded — a structure can straddle a chunk border.
	 */
	@Nullable
	static BlockPos hostOf(Level level, BlockPos pos, Direction face) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof EnergyHostRedirect redirect)) {
			return pos;
		}
		BlockPos host = redirect.energyHost(state, pos, face);
		return host != null && level.isLoaded(host) ? host : null;
	}
}
