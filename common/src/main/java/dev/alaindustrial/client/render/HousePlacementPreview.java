package dev.alaindustrial.client.render;

import dev.alaindustrial.block.HouseInletBlock;
import dev.alaindustrial.network.HousePlacementPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The ghost of a house being laid (MOD-877, R108): the blocks of the house a restorer is choosing a place and a turn
 * for, as the server last surveyed them, drawn as the hall's schematic is — from the town's settlement core, by
 * {@link HallSchematicRenderer}, as translucent real block models ({@link HallSchematicGhosts}).
 *
 * <p>Shown from the first reply of the laying window and kept after the window closes, so the player can walk round
 * it; gone when the house is laid or called off, when the inlet is gone, on a change of dimension and on leaving the
 * world ({@code ClientDisconnectReset}).
 */
public final class HousePlacementPreview {

	@Nullable
	private static BlockPos inlet;
	@Nullable
	private static BlockPos core;
	@Nullable
	private static ResourceKey<Level> dimension;
	private static HallSchematicGhosts ghosts = HallSchematicGhosts.empty();

	private HousePlacementPreview() {
	}

	/** A reply of the laying window: the ghosts of the house as surveyed, or none once it is laid. */
	public static void receive(HousePlacementPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || payload.laid() || payload.ghosts().isEmpty()) {
			clear();
			return;
		}
		inlet = payload.inlet();
		core = payload.core();
		dimension = level.dimension();
		ghosts = HallSchematicGhosts.build(payload.core(), payload.ghosts(), level);
	}

	/** The ghosts drawn from the core at {@code at}, empty when no house of its town is being laid. */
	public static HallSchematicGhosts ghostsAt(BlockPos at) {
		if (core == null || !core.equals(at)) {
			return HallSchematicGhosts.empty();
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !level.dimension().equals(dimension) || inlet != null && level.isLoaded(inlet)
				&& !(level.getBlockState(inlet).getBlock() instanceof HouseInletBlock)) {
			clear();
			return HallSchematicGhosts.empty();
		}
		return ghosts;
	}

	/** Drops the ghost (laid, called off, leaving the world). */
	public static void clear() {
		inlet = null;
		core = null;
		dimension = null;
		ghosts = HallSchematicGhosts.empty();
	}
}
