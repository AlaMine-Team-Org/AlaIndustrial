package dev.alaindustrial.worldgen.town;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Block-entity placeholders a town leaves behind where no block entity lives (MOD-846).
 *
 * <p>World generation places a template into a chunk that is not yet a level chunk, and for every block state
 * whose block is an {@link EntityBlock} the game writes a {@code DUMMY} block-entity tag in place of the real one;
 * when the chunk is loaded into the level the tag is turned into the block's block entity. A block that makes no
 * block entity for some of its states still counts as an entity block for all of them — the reception capsule's
 * middle and upper cells are such ({@code ReceptionCapsuleBlock.newBlockEntity} returns {@code null} there) — so
 * their placeholders have nothing to become, and the game logs "Tried to load a block entity for block … but
 * failed" for every town. The template itself carries no block data on those cells; the placeholder is the game's
 * own.
 *
 * <p>So after a town's pieces are down in a chunk, a placeholder whose block makes no block entity for its state
 * is dropped. Anything else — a real block entity, a placeholder that will become one, a block with data — is
 * left alone.
 */
public final class TownBlockEntities {

	/** The id the game gives the placeholder of a block entity placed during world generation. */
	static final String PLACEHOLDER = "DUMMY";

	private TownBlockEntities() {
	}

	/**
	 * Drops the placeholders inside {@code area} of {@code chunk} whose block makes no block entity for its state.
	 *
	 * @return how many were dropped
	 */
	public static int dropEmptyPlaceholders(ChunkAccess chunk, BoundingBox area) {
		int dropped = 0;
		// getBlockEntitiesPos is a copy, so removing while walking it is safe
		for (BlockPos pos : chunk.getBlockEntitiesPos()) {
			if (!area.isInside(pos)) {
				continue;
			}
			CompoundTag tag = chunk.getBlockEntityNbt(pos);
			if (tag == null || !PLACEHOLDER.equals(tag.getStringOr("id", ""))) {
				continue;
			}
			BlockState state = chunk.getBlockState(pos);
			if (state.getBlock() instanceof EntityBlock block && block.newBlockEntity(pos, state) == null) {
				chunk.removeBlockEntity(pos);
				dropped++;
			}
		}
		return dropped;
	}
}
