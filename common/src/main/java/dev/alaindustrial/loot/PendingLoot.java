package dev.alaindustrial.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Whether a container still owes its contents to a loot table (MOD-524).
 *
 * <p><b>Ask this before reading any container the mod did not place.</b> On a vanilla chest or
 * barrel, {@code Container.getItem} is not a read: {@code RandomizableContainerBlockEntity.getItem}
 * calls {@code unpackLootTable(null)}, which clears the pending {@code LootTable} tag and rolls the
 * contents right there — with no player in the loot context. So does {@code isEmpty()},
 * {@code removeItem}, {@code removeItemNoUpdate} and {@code setItem}; only {@code getLootTable()},
 * {@code getLootTableSeed()} and {@code canOpen} leave the container alone. A passive sweep that
 * merely looks at the world therefore OPENS every unopened loot chest it passes, permanently.
 *
 * <p>That is not a cosmetic difference. A loot table may size itself from the player who opens the
 * chest, and with no player the providers that read the player's score return zero: Mine Treasure's
 * chests roll {@code binomial(20, p)} where {@code p} comes from a scoreboard value, so an unpack
 * with no player yields exactly zero items and the tag is gone, leaving the chest empty for good.
 * That shipped in 0.1.115 and was reported from a live world.
 *
 * <p><b>The rule:</b> a passive observer — a periodic scan, a comparator readout, a predicate —
 * must skip a container that is still pending, because its contents do not exist yet and there is
 * nothing to observe. An ACTIVE interaction is different and stays as it is: a pipe extracting
 * items or a player opening the chest is entitled to generate the loot, exactly as a vanilla hopper
 * does.
 */
public final class PendingLoot {

	private PendingLoot() {
	}

	/**
	 * Whether this block entity still holds an ungenerated loot table. Null-tolerant, because the
	 * callers get their block entity straight from the level.
	 */
	public static boolean isPending(@Nullable BlockEntity blockEntity) {
		return blockEntity instanceof RandomizableContainer randomizable
				&& randomizable.getLootTable() != null;
	}

	/**
	 * Whether the container at this position is still pending, counting BOTH halves of a double
	 * chest.
	 *
	 * <p>The second half matters because {@code ChestBlock.getContainer} hands back a
	 * {@code CompoundContainer} for a double chest, and that wrapper is not a
	 * {@link RandomizableContainer} and keeps its two halves private — so a caller holding the
	 * combined container cannot tell that one half is still pending, and reading slot 0 would unpack
	 * whichever half owns it. Asking positionally, before the halves are combined, is the only way
	 * to see it.
	 */
	public static boolean isPendingAt(BlockGetter level, BlockPos pos, BlockState state) {
		if (isPending(level.getBlockEntity(pos))) {
			return true;
		}
		if (!(state.getBlock() instanceof ChestBlock)
				|| !state.hasProperty(ChestBlock.TYPE)
				|| state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
			return false;
		}
		return isPending(level.getBlockEntity(pos.relative(ChestBlock.getConnectedDirection(state))));
	}
}
