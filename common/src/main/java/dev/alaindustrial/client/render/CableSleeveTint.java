package dev.alaindustrial.client.render;

import dev.alaindustrial.block.CableColors;
import dev.alaindustrial.block.entity.CableBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Colours the rubber sleeve of an insulated cable with the dye stored in its block entity (MOD-666).
 *
 * <p>Tint layer 0 of the insulated cable models is the grey sleeve only; the grade's conductor flecks
 * are a separate, untinted layer, so a dyed copper cable keeps its copper flecks and stays readable as
 * copper. A tint rather than a renderer, because cables are placed by the hundred.
 */
public final class CableSleeveTint implements BlockTintSource {

	public static final CableSleeveTint INSTANCE = new CableSleeveTint();

	private CableSleeveTint() {
	}

	/** Out of world (a falling-block or particle context with no position): plain rubber. */
	@Override
	public int color(BlockState state) {
		return CableColors.UNDYED;
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof CableBlockEntity cable
				? CableColors.sleeve(cable.color())
				: CableColors.UNDYED;
	}
}
