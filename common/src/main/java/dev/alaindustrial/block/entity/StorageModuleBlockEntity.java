package dev.alaindustrial.block.entity;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One block of the modular storage (MOD-287). Holds {@value #CONTAINER_SIZE} slots of its own and
 * nothing else — the "one shared warehouse" the player sees is assembled at open time by
 * {@link StorageCluster}, which walks the face-adjacent modules and presents their inventories as a
 * single container.
 *
 * <p><b>Items live in the module they were put in.</b> There is no master inventory and no migration
 * between modules. That is what makes breaking a module a local event (only its own contents drop)
 * and what keeps an unloaded module from silently voiding or duplicating anything — it is simply not
 * part of the cluster while its chunk is away.
 *
 * <p>Built on the vanilla {@link BaseContainerBlockEntity} like the mod's chests, not on
 * {@code MachineBlockEntity}: the module has no energy, no processing and no tick.
 * Unlike the chests it has no lid animation — it renders as a plain cube, so none of the
 * {@code ChestLidController} / {@code ContainerOpenersCounter} machinery of
 * {@link AbstractChestBlockEntity} applies.
 */
public class StorageModuleBlockEntity extends BaseContainerBlockEntity {
	/** Slots per module — exactly three rows of nine, so every cluster size is a whole number of rows. */
	public static final int CONTAINER_SIZE = 27;

	private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

	public StorageModuleBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.STORAGE_MODULE_BE.get(), pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.alaindustrial.storage_module");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return CONTAINER_SIZE;
	}

	/**
	 * Never called: {@link dev.alaindustrial.block.StorageModuleBlock} opens the cluster menu itself,
	 * because the menu type depends on how many modules are attached and that is not known to the
	 * block entity alone. Implemented only to satisfy {@link BaseContainerBlockEntity}.
	 */
	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return StorageCluster.of(level, worldPosition).createMenu(syncId, playerInventory);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}
}
