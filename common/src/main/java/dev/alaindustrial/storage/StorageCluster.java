package dev.alaindustrial.storage;

import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.menu.StorageModuleMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The set of storage modules that behave as one warehouse (MOD-287).
 *
 * <p>A cluster is <b>derived, never stored</b>. It is walked fresh from a starting module every time
 * a player opens the window, which is why nothing in the world holds a list of member positions —
 * the mod has no precedent for persisting {@code BlockPos} sets, and the energy and item networks
 * likewise keep no topology on disk. Placing or breaking a module therefore needs no bookkeeping: the
 * next walk simply finds a different set.
 *
 * <p><b>Unloaded chunks are skipped, not forced.</b> A module whose chunk is away is left out of the
 * walk instead of being force-loaded — the same rule the stock display frame already follows. Its
 * items are neither lost nor duplicated; that part of the warehouse is temporarily not there. Forcing
 * the chunk instead would let a player keep a chunk alive by leaving a GUI open.
 */
public final class StorageCluster {
	/** Cap on modules in one cluster: 4 × 27 = 108 slots. */
	public static final int MAX_MODULES = 4;
	/** Rows of nine contributed by each module. */
	public static final int ROWS_PER_MODULE = 3;
	/**
	 * Rows the window shows at once; anything beyond is scrolled.
	 *
	 * <p>Six because the panel is {@code rows × 18 + 112} px tall and the game does not give a window
	 * more than that. At the default "auto" GUI scale, {@code Window.calculateScale} raises the scale
	 * while {@code height / (scale + 1) >= 240}, so the usable height lands between 240 and about 270
	 * px on every common resolution — 240 on 1440p and 4K, 270 on 1080p, 256 on 1366×768. Twelve rows
	 * come to 328 px and are cut off at the top AND the bottom, which is what the player photographed;
	 * six rows come to 220, which is exactly the vanilla double chest — the tallest container window
	 * the game itself ships, and therefore the honest ceiling rather than a guess.
	 */
	public static final int MAX_VISIBLE_ROWS = 6;

	private final List<StorageModuleBlockEntity> modules;

	private StorageCluster(List<StorageModuleBlockEntity> modules) {
		this.modules = modules;
	}

	/**
	 * Walk the cluster containing {@code origin}.
	 *
	 * <p>Breadth-first over face-adjacent modules — face only, so modules touching at an edge or a
	 * corner are separate warehouses and the player can build two side by side. Neighbours are visited
	 * in a fixed {@link Direction} order and the walk stops at {@link #MAX_MODULES}, so an
	 * over-large blob always yields the same members no matter which module was clicked... with one
	 * exception worth knowing: the walk starts at {@code origin}, so clicking a different module of an
	 * over-large blob can select a different four. That is intentional and visible — the window shows
	 * how many modules are connected.
	 */
	public static StorageCluster of(Level level, BlockPos origin) {
		List<StorageModuleBlockEntity> found = new ArrayList<>(MAX_MODULES);
		List<BlockPos> frontier = new ArrayList<>();
		List<BlockPos> seen = new ArrayList<>();
		frontier.add(origin);
		seen.add(origin);

		while (!frontier.isEmpty() && found.size() < MAX_MODULES) {
			BlockPos pos = frontier.remove(0);
			StorageModuleBlockEntity module = moduleAt(level, pos);
			if (module == null) {
				continue;
			}
			found.add(module);
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (!containsPos(seen, next)) {
					seen.add(next);
					frontier.add(next);
				}
			}
		}
		return new StorageCluster(found);
	}

	/** The module at {@code pos}, or {@code null} if the chunk is away or the block is something else. */
	private static StorageModuleBlockEntity moduleAt(Level level, BlockPos pos) {
		if (level == null || !level.isLoaded(pos)) {
			return null;
		}
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof StorageModuleBlockEntity module ? module : null;
	}

	private static boolean containsPos(List<BlockPos> list, BlockPos pos) {
		for (BlockPos p : list) {
			if (p.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/** How many modules make up this warehouse (1–{@value #MAX_MODULES}). */
	public int moduleCount() {
		return modules.size();
	}

	/** Rows of nine the warehouse holds: three per module. */
	public int rows() {
		return Math.max(1, modules.size()) * ROWS_PER_MODULE;
	}

	/** Rows of nine the window shows at once — the rest are reached by scrolling. */
	public int visibleRows() {
		return Math.min(rows(), MAX_VISIBLE_ROWS);
	}

	/** The modules' inventories presented as one container, in walk order. */
	public Container container() {
		return new ClusterContainer(List.copyOf(modules));
	}

	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return StorageModuleMenu.server(syncId, playerInventory, this);
	}

	/**
	 * A read/write view over the member inventories. Slot {@code i} maps to module {@code i / 27},
	 * local slot {@code i % 27} — so the first module always owns the top three rows and the order on
	 * screen matches the walk order.
	 *
	 * <p>Holds the modules themselves rather than copies of their contents: every read and write goes
	 * straight to the owning block entity, so there is no intermediate copy that could drift from the
	 * world and no second place an item could exist.
	 */
	private record ClusterContainer(List<StorageModuleBlockEntity> modules) implements Container {

		@Override
		public int getContainerSize() {
			return modules.size() * StorageModuleBlockEntity.CONTAINER_SIZE;
		}

		@Override
		public boolean isEmpty() {
			for (StorageModuleBlockEntity module : modules) {
				if (!module.isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getItem(int slot) {
			return valid(slot) ? owner(slot).getItem(local(slot)) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			return valid(slot) ? owner(slot).removeItem(local(slot), amount) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return valid(slot) ? owner(slot).removeItemNoUpdate(local(slot)) : ItemStack.EMPTY;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (valid(slot)) {
				owner(slot).setItem(local(slot), stack);
			}
		}

		@Override
		public void setChanged() {
			for (StorageModuleBlockEntity module : modules) {
				module.setChanged();
			}
		}

		@Override
		public boolean stillValid(Player player) {
			// The walk is redone on every open, so a cluster only has to stay valid while the window is
			// up: every member must still be there and in reach.
			for (StorageModuleBlockEntity module : modules) {
				if (module.isRemoved() || !Container.stillValidBlockEntity(module, player)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public void clearContent() {
			for (StorageModuleBlockEntity module : modules) {
				module.clearContent();
			}
		}

		private boolean valid(int slot) {
			return slot >= 0 && slot < getContainerSize();
		}

		private StorageModuleBlockEntity owner(int slot) {
			return modules.get(slot / StorageModuleBlockEntity.CONTAINER_SIZE);
		}

		private static int local(int slot) {
			return slot % StorageModuleBlockEntity.CONTAINER_SIZE;
		}
	}
}
