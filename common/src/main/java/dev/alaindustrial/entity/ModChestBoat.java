package dev.alaindustrial.entity;

import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A boat carrying one of the mod's storage chests (MOD-785). Everything a vanilla chest boat does —
 * floating, rowing, one passenger, Shift+use to open, save/load, dropping the contents when broken,
 * loot tables — is inherited from {@link AbstractChestBoat}; this class only swaps the 27-slot
 * container for the chest tier's own size and opens that tier's menu instead of the vanilla
 * three-row {@code ChestMenu}.
 *
 * <p><b>Why the list is ours.</b> The vanilla item list is a private field sized 27 at construction,
 * but every container operation of {@code ContainerEntity} reaches it only through
 * {@link #getItemStacks()} and {@link #clearItemStacks()}; overriding both (and
 * {@link #getContainerSize()}) is the whole change. The list is created lazily because the super
 * constructor runs before this class's fields are assigned.
 *
 * <p>The wood and the chest tier are fixed by the entity type, exactly as vanilla fixes the wood:
 * {@code getDropItem()} and {@code getPickResult()} are final in {@code AbstractBoat} and return a bare
 * {@code new ItemStack(item)}, so each wood × chest pair is its own item and its own entity type.
 */
public class ModChestBoat extends AbstractChestBoat {
	/** Builds the chest tier's menu over this boat's container (server side). */
	@FunctionalInterface
	public interface ChestMenuFactory {
		AbstractContainerMenu create(int syncId, Inventory playerInventory, Container chest);
	}

	private final int containerSize;
	private final ChestMenuFactory menuFactory;
	private final boolean raft;
	private @Nullable NonNullList<ItemStack> items;

	public ModChestBoat(EntityType<? extends ModChestBoat> type, Level level, Supplier<Item> dropItem,
			int containerSize, ChestMenuFactory menuFactory, boolean raft) {
		super(type, level, dropItem);
		this.containerSize = containerSize;
		this.menuFactory = menuFactory;
		this.raft = raft;
	}

	/** Same seat height as the vanilla {@code ChestBoat}, or {@code ChestRaft} for the bamboo raft. */
	@Override
	protected double rideHeight(EntityDimensions dimensions) {
		return raft ? dimensions.height() * 0.8888889F : dimensions.height() / 3.0F;
	}

	@Override
	public int getContainerSize() {
		return containerSize;
	}

	@Override
	public NonNullList<ItemStack> getItemStacks() {
		if (items == null) {
			items = NonNullList.withSize(containerSize, ItemStack.EMPTY);
		}
		return items;
	}

	@Override
	public void clearItemStacks() {
		items = NonNullList.withSize(containerSize, ItemStack.EMPTY);
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		// Same guard as vanilla: a spectator must not roll a pending loot table by looking inside.
		if (getContainerLootTable() != null && player.isSpectator()) {
			return null;
		}
		unpackLootTable(inventory.player);
		return menuFactory.create(containerId, inventory, this);
	}
}
