package dev.alaindustrial.compat;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * Block properties whose spelling differs between the Minecraft lines (MOD-703, ADR-036). Every line
 * has a twin of this class with the same names and signatures; only the bodies differ. The block
 * declarations ({@code ContentManifest}'s {@code BlockDef} entries, {@code ModBlockProperties}) call these instead of
 * naming a line's own enum value or setter, so their sources are the same on every line.
 *
 * <p><b>This twin: Minecraft 26.3.</b> 26.3 renamed the push reactions ({@code DESTROY} became
 * {@link PushReaction#POPPED}, {@code BLOCK} became {@link PushReaction#IMMOVEABLE}) and decides whether
 * flowing fluid washes a block away from the {@code #minecraft:washed_away_by_fluids} block tag rather
 * than from its collision box.
 */
public final class LineBlockProps {

	private LineBlockProps() {
	}

	/** A piston breaks the block and drops it instead of moving it. 26.3: {@link PushReaction#POPPED}. */
	public static BlockBehaviour.Properties popsOnPush(BlockBehaviour.Properties properties) {
		return properties.pushReaction(PushReaction.POPPED);
	}

	/** A piston can neither push nor pull the block. 26.3: {@link PushReaction#IMMOVEABLE}. */
	public static BlockBehaviour.Properties pinnedAgainstPistons(BlockBehaviour.Properties properties) {
		return properties.pushReaction(PushReaction.IMMOVEABLE);
	}

	/**
	 * Flowing fluid washes the block away whatever shape it has taken (MOD-661). 26.3: nothing to set on the
	 * properties — the line asks the {@code #minecraft:washed_away_by_fluids} tag, which the mod's
	 * {@code data/minecraft/tags/block/washed_away_by_fluids.json} fills.
	 */
	public static BlockBehaviour.Properties washedAwayByFluids(BlockBehaviour.Properties properties) {
		return properties;
	}
}
