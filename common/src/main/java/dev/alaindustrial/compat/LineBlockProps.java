package dev.alaindustrial.compat;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * Block properties whose spelling differs between the Minecraft lines (MOD-703, ADR-036). Every line
 * has a twin of this class with the same names and signatures; only the bodies differ. The block
 * declarations ({@code ContentManifest}'s {@code BlockDef} entries, {@code ModBlockProperties}) call these instead of
 * naming a line's own enum value or setter, so their sources are the same on every line.
 *
 * <p><b>This twin: Minecraft 26.2.</b> The push reactions are still {@link PushReaction#DESTROY} and
 * {@link PushReaction#BLOCK}, and flowing fluid washes away whatever does not block motion — read from
 * the collision box, so a block has to be forced non-solid to be washed away in every shape.
 */
public final class LineBlockProps {

	private LineBlockProps() {
	}

	/** A piston breaks the block and drops it instead of moving it. 26.2: {@link PushReaction#DESTROY}. */
	public static BlockBehaviour.Properties popsOnPush(BlockBehaviour.Properties properties) {
		return properties.pushReaction(PushReaction.DESTROY);
	}

	/** A piston can neither push nor pull the block. 26.2: {@link PushReaction#BLOCK}. */
	public static BlockBehaviour.Properties pinnedAgainstPistons(BlockBehaviour.Properties properties) {
		return properties.pushReaction(PushReaction.BLOCK);
	}

	/**
	 * Flowing fluid washes the block away whatever shape it has taken (MOD-661). 26.2: forced non-solid. A
	 * pipe with arms up and down is a full block tall, and this line would count it solid — it blocks
	 * motion — and leave it standing while the same pipe lying straight is washed away.
	 */
	public static BlockBehaviour.Properties washedAwayByFluids(BlockBehaviour.Properties properties) {
		return properties.forceSolidOff();
	}

	/**
	 * A see-through full block, the way vanilla glass is (MOD-795): mobs do not spawn on it, it carries no
	 * redstone, and it neither suffocates nor blocks the view of an entity whose head is inside it. 26.2: the
	 * view predicate takes three arguments.
	 */
	public static BlockBehaviour.Properties seeThrough(BlockBehaviour.Properties properties) {
		return properties.isValidSpawn((state, level, pos, type) -> false)
				.isRedstoneConductor((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
				.isViewBlocking((state, level, pos) -> false);
	}
}
