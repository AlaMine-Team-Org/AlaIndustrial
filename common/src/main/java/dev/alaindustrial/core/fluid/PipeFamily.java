package dev.alaindustrial.core.fluid;

import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Which of the two pipe networks a pipe belongs to (MOD-662): liquids, or steam.
 *
 * <p><b>Two families that never meet.</b> A water line and a steam line can run side by side through a
 * reactor room without joining: pipes of different families do not connect, and each family's segment
 * refuses the other's fluid. Steam is recognised by the conventional {@code c:steam} tag rather than by
 * our own fluid, so another mod's steam rides the steam line too.
 *
 * <p><b>A port can belong to a family as well.</b> The reactor's column gives steam from its top and
 * drinks water on every other face, and the nozzle only ever takes steam; a pipe of the wrong family
 * does not reach for those faces, so it draws no arm there. Every other port — tanks, machines, the
 * reactor's two-way inlet — belongs to no family and fits either line.
 */
public enum PipeFamily {
	/** Every fluid except steam. */
	FLUID,
	/** Steam only. */
	STEAM;

	/** Whether a segment of this family may hold {@code fluid}. The empty holder is nobody's. */
	public boolean accepts(FluidHolder fluid) {
		if (fluid.isEmpty()) {
			return false;
		}
		return isSteam(fluid) == (this == STEAM);
	}

	/** Whether {@code fluid} is steam — {@code c:steam}, ours or another mod's. */
	public static boolean isSteam(FluidHolder fluid) {
		return !fluid.isEmpty() && fluid.fluid().defaultFluidState().is(ModTags.Fluids.C_STEAM);
	}

	/**
	 * The family the port on {@code face} of the block at {@code pos} serves, or {@code null} when it
	 * serves any family or there is no port there. Only the reactor's one-way vessels answer: the column
	 * (steam on top, water everywhere else) and the nozzle (steam on every face that has a port).
	 */
	public static PipeFamily portFamily(Level level, BlockPos pos, Direction face) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof FuelRodAssemblyBlockEntity column) {
			if (column.fluidPort(face) == null) {
				return null;
			}
			return face == Direction.UP ? STEAM : FLUID;
		}
		if (be instanceof SteamNozzleBlockEntity nozzle) {
			return nozzle.fluidPort(face) == null ? null : STEAM;
		}
		return null;
	}
}
