package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.registry.ModTags;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a builder drone does with the ground of a house a player laid on free ground (MOD-877; epic MOD-824, decision
 * R109): the nature it takes off the site goes into the dispatcher's buffer, and the pits under the floor are filled
 * from the buffer with earth and stone ({@link ModTags.Items#TOWN_SITE_FILL}).
 */
final class SiteWork {

	/** The material a pit asks for, as a «no materials» reason names it: the fill tag's name. */
	static final String FILL_NAME = "tag.item.alaindustrial.town_site_fill";

	private SiteWork() {
	}

	/**
	 * Puts what {@code world} at {@code pos} drops into {@code buffer}, before the drone takes the block away; what
	 * does not fit falls where the block stood.
	 */
	static void keepSpoil(ServerLevel level, BlockPos pos, BlockState world, DispatcherBuffer buffer) {
		for (ItemStack drop : Block.getDrops(world, level, pos, null)) {
			ItemStack rest = buffer.insert(drop);
			if (!rest.isEmpty()) {
				Block.popResource(level, pos, rest);
			}
		}
	}

	/** The block the first fill material in {@code buffer} places, or empty when the buffer holds none. */
	static Optional<BlockState> fillState(DispatcherBuffer buffer) {
		for (int i = 0; i < buffer.size(); i++) {
			ItemStack stack = buffer.get(i);
			if (!stack.isEmpty() && stack.is(ModTags.Items.TOWN_SITE_FILL)
					&& stack.getItem() instanceof BlockItem item) {
				return Optional.of(item.getBlock().defaultBlockState());
			}
		}
		return Optional.empty();
	}

	/** How many fill items {@code buffer} holds, of every kind. */
	static int fillCount(DispatcherBuffer buffer) {
		int count = 0;
		for (int i = 0; i < buffer.size(); i++) {
			ItemStack stack = buffer.get(i);
			if (!stack.isEmpty() && stack.is(ModTags.Items.TOWN_SITE_FILL) && stack.getItem() instanceof BlockItem) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Takes one item of {@code fill}'s block out of {@code buffer}; whether there was one. */
	static boolean takeFill(DispatcherBuffer buffer, BlockState fill) {
		return buffer.consume(BuiltInRegistries.ITEM.getKey(fill.getBlock().asItem()).toString(), 1);
	}
}
