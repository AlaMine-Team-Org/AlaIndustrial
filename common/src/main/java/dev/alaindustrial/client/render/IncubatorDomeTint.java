package dev.alaindustrial.client.render;

import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Colours the incubator dome with the glass the player built it from (MOD-118).
 *
 * <p>The dome block itself carries no colour: assembling the multiblock swaps the player's glass for
 * one shared {@code alaindustrial:incubator_dome} state and remembers the original in the base's
 * block entity. This tint source reads that memory back, so tint layer 0 of the dome model — the
 * glass panes only, never the metal frame — takes the dye colour of the block that was placed.
 *
 * <p>Registered per loader: Fabric through {@code BlockColorRegistry.register}, NeoForge through
 * {@code RegisterColorHandlersEvent.BlockTintSources}. Both take the same
 * {@code List<BlockTintSource>} layer list, so the behaviour here is shared verbatim.
 */
public final class IncubatorDomeTint implements BlockTintSource {

	/** Opaque white: the multiply leaves the glass texture exactly as it is. */
	public static final int UNTINTED = 0xFFFFFFFF;

	public static final IncubatorDomeTint INSTANCE = new IncubatorDomeTint();

	private IncubatorDomeTint() {
	}

	/**
	 * The tint for a dome built from {@code glass}.
	 *
	 * <p>Only the sixteen {@link StainedGlassBlock} variants carry a {@link net.minecraft.world.item.DyeColor},
	 * and their {@code getTextureDiffuseColor()} is already opaque ARGB — the same constant vanilla
	 * uses to paint the dyed glass texture, so the dome reads as the block the player spent.
	 *
	 * <p>Plain glass has no colour, and neither does tinted glass: it is not a stained-glass block,
	 * it carries no dye, and vanilla renders it from its own dark texture rather than from a tint.
	 * Inventing a grey for it would be a made-up number backed by nothing in the game data, so both
	 * fall through to {@link #UNTINTED}. The eighteen accepted blocks therefore give the seventeen
	 * distinct chamber looks the spec promises: sixteen dye colours plus one uncoloured.
	 */
	public static int colorOf(BlockState glass) {
		return glass.getBlock() instanceof StainedGlassBlock stained
				? stained.getColor().getTextureDiffuseColor()
				: UNTINTED;
	}

	/**
	 * Out-of-world colour (inventory icon, no position to look up). The dome is never held as an
	 * item — it has no loot table and no creative-tab entry — so plain glass is the honest answer.
	 */
	@Override
	public int color(BlockState state) {
		return UNTINTED;
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		// The base sits directly below the dome and owns the remembered glass state.
		return level.getBlockEntity(pos.below()) instanceof IncubatorBlockEntity base
				? colorOf(base.domeSource())
				: UNTINTED;
	}
}
