package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.block.entity.FermenterStatus;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Six-slot Fermenter menu (MOD-146): the organic input, a container pair for the water tank, another
 * for the biofuel tank, and the biomass result.
 *
 * <p>{@code mayPlace} here is the client-side half of the slot rules; the server-side half is
 * {@code FermenterBlockEntity.canPlaceItem}. Both are needed — this one refuses a wrong item under
 * the player's cursor, that one is what automation and the fluid-container exchange consult.
 *
 * <p>The organic slot deliberately accepts anything: what may be fermented is a datapack question
 * answered by the recipe, and a menu that pre-judged it would go stale the moment a pack added a
 * plant. A wrong item simply sits there and the status line says "no recipe".
 */
public final class FermenterMenu extends MachineMenu {
	public FermenterMenu(int syncId, Inventory playerInventory, FermenterBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.FERMENTER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.FERMENTER.get());
	}

	public FermenterMenu(int syncId, Inventory playerInventory) {
		super(ModContent.FERMENTER_MENU.get(), syncId, playerInventory,
				new SimpleContainer(FermenterBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(FermenterBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.FERMENTER.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		// These coordinates ARE the GUI art: the background paints a trough and a slot frame at each
		// of them, and they were measured off that picture programmatically rather than read off by
		// eye. Move one without the other and the frame starts lying about where things go.
		addSlot(new Slot(container, FermenterBlockEntity.WATER_FILL_INPUT_SLOT, 24, 21) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemFluidBridge.get().isFluidContainer(stack);
			}
		});
		// Machine-filled: the emptied container lands here through the exchange, not by hand.
		addSlot(new OutputSlot(container, FermenterBlockEntity.WATER_FILL_OUTPUT_SLOT, 24, 51));
		addSlot(new Slot(container, FermenterBlockEntity.ORGANIC_SLOT, 47, 35));
		addSlot(new OutputSlot(container, FermenterBlockEntity.OUTPUT_SLOT, 91, 35));
		addSlot(new Slot(container, FermenterBlockEntity.BIOFUEL_DRAIN_INPUT_SLOT, 133, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemFluidBridge.get().isFluidContainer(stack);
			}
		});
		addSlot(new OutputSlot(container, FermenterBlockEntity.BIOFUEL_DRAIN_OUTPUT_SLOT, 133, 47));
	}

	/** Water level as a permille (0..1000) — scaled because the sync channel is a signed short. */
	public int getWaterPermille() {
		return data.get(FermenterBlockEntity.CH_WATER_PERMILLE);
	}

	/** Registry id of the fluid in the water tank, or {@link FermenterBlockEntity#FLUID_ID_NONE}. */
	public int getWaterFluidId() {
		return data.get(FermenterBlockEntity.CH_WATER_FLUID_ID);
	}

	/** Biofuel level as a permille (0..1000). */
	public int getBiofuelPermille() {
		return data.get(FermenterBlockEntity.CH_BIOFUEL_PERMILLE);
	}

	/** Registry id of the fluid in the biofuel tank, or {@link FermenterBlockEntity#FLUID_ID_NONE}. */
	public int getBiofuelFluidId() {
		return data.get(FermenterBlockEntity.CH_BIOFUEL_FLUID_ID);
	}

	/** Why the machine is idle, for the screen's status line. */
	public FermenterStatus getStatus() {
		return FermenterStatus.byOrdinal(data.get(FermenterBlockEntity.CH_STATUS));
	}
}
