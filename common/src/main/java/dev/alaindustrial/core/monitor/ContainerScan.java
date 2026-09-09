package dev.alaindustrial.core.monitor;

import dev.alaindustrial.block.AbstractModChestBlock;
import dev.alaindustrial.loot.PendingLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Counting somebody else's storage without touching it (MOD-480). The resolution order is lifted
 * from the stock display frame (MOD-066), because the same three hazards apply and each of them
 * has already cost this mod a bug:
 *
 * <ol>
 *   <li><b>Pending loot first.</b> Every read path below ends in {@code Container.getItem}, and on a
 *       container whose loot table has not been rolled yet that unpacks the table with no player in
 *       context — emptying another mod's loot chest for good (ADR-010, MOD-524). A container in that
 *       state reads as "nothing here", which is also the honest answer: until somebody opens it, it
 *       holds nothing to count.</li>
 *   <li><b>Double chests are resolved positionally, before combining.</b> {@code CompoundContainer}
 *       hides which half is which, so the pending-loot question has to be asked first.</li>
 *   <li><b>Vanilla {@code Container} is not the whole world.</b> Mod storages that expose only their
 *       loader's capability never implement it, so the fallback goes through
 *       {@link ItemViewLookup}.</li>
 * </ol>
 */
public final class ContainerScan {

	private ContainerScan() {
	}

	/**
	 * Add the contents at {@code pos} into {@code sink}.
	 *
	 * <p>An unloaded chunk is skipped rather than loaded: forcing a synchronous chunk load once per
	 * scan per wired container is how a monitoring wall would turn into a server stall.
	 *
	 * @return whether anything readable was found there
	 */
	public static boolean tallyAt(ServerLevel level, BlockPos pos, StockTally sink) {
		if (sink.isEmpty() || !level.isLoaded(pos)) {
			return false;
		}
		Container container = resolveContainer(level, pos);
		if (container != null) {
			tallyContainer(container, sink);
			return true;
		}
		ItemView view = ItemViewLookup.get().find(level, pos, null);
		if (view == null) {
			return false;
		}
		view.tally(sink);
		return true;
	}

	/** Whether {@code pos} holds anything this system can read at all — used to draw wire arms. */
	public static boolean isReadable(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		return resolveContainer(level, pos) != null
				|| ItemViewLookup.get().find(level, pos, null) != null;
	}

	private static void tallyContainer(Container container, StockTally sink) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			int index = sink.indexOf(stack);
			if (index >= 0) {
				sink.add(index, stack.getCount());
			}
		}
	}

	private static @Nullable Container resolveContainer(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (PendingLoot.isPendingAt(level, pos, state)) {
			return null;
		}
		if (state.getBlock() instanceof ChestBlock chest) {
			return ChestBlock.getContainer(chest, state, level, pos, false);
		}
		if (state.getBlock() instanceof AbstractModChestBlock chest) {
			return chest.combinedContainer(state, level, pos);
		}
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof Container container ? container : null;
	}
}
