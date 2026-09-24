package dev.alaindustrial.core.fluid;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.ReinforcedFluidPipeBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns the steam lines of an old world into steam pipes (MOD-662).
 *
 * <p><b>Why a migration at all.</b> Before the steam pipes existed a reactor vented its steam through
 * ordinary fluid pipe. Fluid pipes no longer take steam, so without this every reactor built before the
 * update would lose its exhaust on the first tick, stop boiling and overheat — a world that loads and
 * then melts down is a world the update broke. The owner chose an automatic swap.
 *
 * <p><b>The rule, for a fluid pipe loaded from an old save.</b>
 * <ul>
 * <li>It holds steam: it is a steam pipe.</li>
 * <li>It holds anything else: it stays.</li>
 * <li>It is empty: its open faces decide. A face toward a steam port (a column's top, a steam nozzle)
 * or toward a pipe that is already a steam pipe is steam evidence; a face toward a column's side is
 * water evidence. Steam evidence and no water evidence makes it a steam pipe; both kinds leave it a
 * fluid pipe, with a warning naming the position so an admin can look.</li>
 * </ul>
 * The grade is kept: a reinforced fluid pipe becomes a reinforced steam pipe.
 *
 * <p><b>The wave.</b> A steam line is mostly pipes that touch only other pipes, and those have no
 * evidence of their own until their neighbour changes. So every swap asks its legacy neighbours to look
 * again on their next tick, and a line converts outward from its column or nozzle one segment per tick.
 * It walks nothing itself — no recursion, no chunk loads; a part of the line in an unloaded chunk catches
 * up when that chunk loads, because its segments are still legacy there.
 *
 * <p><b>The swap keeps the segment.</b> {@link FluidPipeBlock} tells the chunk to keep the block entity
 * when one pipe replaces another, so the buffer, the wrenched face modes and the registration survive;
 * only the block changes. It is one-way: a world opened afterwards in an older build finds a block it
 * does not know.
 */
public final class SteamLineMigration {
	private SteamLineMigration() {
	}

	/**
	 * Check one legacy segment. Returns {@code true} when the question is settled — swapped or left as
	 * it is — and {@code false} when a neighbour is not loaded yet, so the caller asks again next tick.
	 */
	public static boolean check(FluidPipeBlockEntity pipe, Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel server)) {
			return true;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof FluidPipeBlock block) || block.family() != PipeFamily.FLUID) {
			return true;
		}
		FluidTank buffer = pipe.fluidBuffer;
		if (buffer.amount > 0 && !buffer.fluid.isEmpty()) {
			if (PipeFamily.isSteam(buffer.fluid)) {
				swap(server, pos, state);
			}
			return true;
		}
		int steamFaces = 0;
		int waterFaces = 0;
		for (Direction dir : Direction.values()) {
			if (pipe.faceMode(dir) == PipeFaceMode.DISABLED) {
				continue;
			}
			BlockPos neighbour = pos.relative(dir);
			if (!level.isLoaded(neighbour)) {
				return false;
			}
			if (level.getBlockEntity(neighbour) instanceof FluidPipeBlockEntity other) {
				if (other.family() == PipeFamily.STEAM
						&& other.faceMode(dir.getOpposite()) != PipeFaceMode.DISABLED) {
					steamFaces++;
				}
				continue;
			}
			PipeFamily served = PipeFamily.portFamily(level, neighbour, dir.getOpposite());
			if (served == PipeFamily.STEAM) {
				steamFaces++;
			} else if (served == PipeFamily.FLUID) {
				waterFaces++;
			}
		}
		if (steamFaces == 0) {
			return true;
		}
		if (waterFaces > 0) {
			Industrialization.LOGGER.warn("[MOD-662] fluid pipe at {} touches both a steam outlet and a water"
					+ " inlet; left as a fluid pipe — lay its steam side in steam pipe by hand", pos);
			return true;
		}
		swap(server, pos, state);
		return true;
	}

	/** Replace the block, keep the segment, and wake the legacy neighbours for the next wave. */
	private static void swap(ServerLevel level, BlockPos pos, BlockState old) {
		Block target = old.getBlock() instanceof ReinforcedFluidPipeBlock
				? ModContent.REINFORCED_STEAM_PIPE.get()
				: ModContent.STEAM_PIPE.get();
		level.setBlock(pos, target.withPropertiesOf(old), Block.UPDATE_ALL);
		FluidPipeBlock.refreshConnections(level, pos);
		FluidNetworkManager.topologyChanged(level, pos);
		if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity kept) {
			kept.setChanged();
		}
		for (Direction dir : Direction.values()) {
			BlockPos neighbour = pos.relative(dir);
			if (level.isLoaded(neighbour) && level.getBlockEntity(neighbour) instanceof FluidPipeBlockEntity other
					&& other.isLegacy()) {
				other.recheckMigration();
			}
		}
	}
}
