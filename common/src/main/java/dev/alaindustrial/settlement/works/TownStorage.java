package dev.alaindustrial.settlement.works;

import dev.alaindustrial.loot.PendingLoot;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.SettlementRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The storage of an ALA town (MOD-888; epic MOD-824, decisions R89, R152, R157): every container of a building the town
 * has restored once — the chests of the restored hall, of a repaired house, the shelves of a restored warehouse. A
 * container counts where the building's intact template has one ({@link ModTags.Blocks#TOWN_STORAGE}) and the world
 * still holds one; a chest in a ruin never counts, and neither does a chest a player stood somewhere else.
 *
 * <p><b>Reading a container is looking (ADR-010).</b> A container whose loot table has not been rolled yet — the
 * ruin's find, still sealed — is skipped, never opened: its contents do not exist yet. The workplace buildings
 * (the warehouse) come first, so the storekeeper fills the shelves before the hall's chests.
 */
public final class TownStorage {

	/**
	 * One container of the storage.
	 *
	 * @param pos       where it stands
	 * @param container its slots
	 * @param building  the index of its building among the town's buildings
	 */
	public record Box(BlockPos pos, Container container, int building) {
	}

	private TownStorage() {
	}

	/** The containers of town {@code town}'s storage that stand loaded now, the workplaces' first. */
	public static List<Box> boxes(ServerLevel level, int town) {
		Optional<TownWorks> found = SettlementRegistry.get(level.getServer()).works().find(town);
		if (found.isEmpty()) {
			return List.of();
		}
		TownWorks works = found.get();
		List<Box> first = new ArrayList<>();
		List<Box> rest = new ArrayList<>();
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			if (!StorekeeperRules.isStorage(building.everRestored(), building.withdrawn())) {
				continue;
			}
			Optional<BuildingPlan> plan = TownWorksScanner.plan(level, town, i);
			if (plan.isEmpty()) {
				continue;
			}
			List<Box> into = TownWorkplaces.tradeOf(building).isPresent() ? first : rest;
			for (int c = 0; c < plan.get().size(); c++) {
				if (!plan.get().expected(c).is(ModTags.Blocks.TOWN_STORAGE)) {
					continue;
				}
				at(level, plan.get().pos(c), i).ifPresent(into::add);
			}
		}
		first.addAll(rest);
		return first;
	}

	/** The container of the storage at {@code pos}, if one stands there loaded and sealed by no loot table. */
	static Optional<Box> at(ServerLevel level, BlockPos pos, int building) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModTags.Blocks.TOWN_STORAGE)) {
			return Optional.empty();
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof Container container) || PendingLoot.isPending(entity)) {
			return Optional.empty();
		}
		return Optional.of(new Box(pos.immutable(), container, building));
	}

	/** What {@code container} holds, by item id, in the order its slots first show each item. */
	public static Map<String, Integer> contents(Container container) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				out.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** How many empty slots {@code container} has. */
	public static int freeSlots(Container container) {
		int free = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	/**
	 * Takes up to {@code count} of the plain item with id {@code item} (no components of its own) out of
	 * {@code container}, from its last slots first; returns what it took, in stacks.
	 */
	public static List<ItemStack> take(Container container, String item, int count) {
		Optional<Item> found = byId(item);
		List<ItemStack> out = new ArrayList<>();
		if (found.isEmpty() || count <= 0) {
			return out;
		}
		int left = count;
		for (int i = container.getContainerSize() - 1; i >= 0 && left > 0; i--) {
			ItemStack stack = container.getItem(i);
			if (stack.is(found.get()) && stack.getComponentsPatch().isEmpty()) {
				int moved = Math.min(left, stack.getCount());
				out.add(stack.split(moved));
				if (stack.isEmpty()) {
					container.setItem(i, ItemStack.EMPTY);
				}
				left -= moved;
			}
		}
		if (!out.isEmpty()) {
			container.setChanged();
		}
		return out;
	}

	/** How many of {@code stack} {@code container} can take now. */
	public static int room(Container container, ItemStack stack) {
		int room = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack slot = container.getItem(i);
			int max = Math.min(container.getMaxStackSize(stack), stack.getMaxStackSize());
			if (slot.isEmpty()) {
				room += container.canPlaceItem(i, stack) ? max : 0;
			} else if (ItemStack.isSameItemSameComponents(slot, stack)) {
				room += Math.max(0, max - slot.getCount());
			}
		}
		return room;
	}

	/** Puts {@code stack} into {@code container}, topping up its own stacks first; returns what did not fit. */
	public static ItemStack insert(Container container, ItemStack stack) {
		ItemStack rest = stack.copy();
		int max = Math.min(container.getMaxStackSize(rest), rest.getMaxStackSize());
		for (int i = 0; i < container.getContainerSize() && !rest.isEmpty(); i++) {
			ItemStack slot = container.getItem(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest) && slot.getCount() < max) {
				int moved = Math.min(max - slot.getCount(), rest.getCount());
				slot.grow(moved);
				rest.shrink(moved);
			}
		}
		for (int i = 0; i < container.getContainerSize() && !rest.isEmpty(); i++) {
			if (container.getItem(i).isEmpty() && container.canPlaceItem(i, rest)) {
				container.setItem(i, rest.split(Math.min(max, rest.getCount())));
			}
		}
		if (rest.getCount() != stack.getCount()) {
			container.setChanged();
		}
		return rest;
	}

	/** The item with id {@code item}, if this game knows it. */
	public static Optional<Item> byId(String item) {
		Identifier id = Identifier.tryParse(item);
		return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
	}

	/** How many items of id {@code item} make a full stack (64 for an item this game does not know). */
	public static int maxStack(String item) {
		return byId(item).map(found -> found.getDefaultInstance().getMaxStackSize()).orElse(64);
	}
}
