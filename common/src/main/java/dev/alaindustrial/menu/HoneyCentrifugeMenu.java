package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.HoneyCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.HoneyCentrifugeStatus;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Four-slot Honey Centrifuge menu (MOD-924): the input, the container a recipe hands back, and a container
 * pair that fills an empty bucket from the honey tank.
 *
 * <p>The input slot accepts anything: what may be spun is the recipe's business (a datapack may add a
 * comb), and a wrong item simply sits there while the status says "no recipe". {@code mayPlace} on the
 * container slot is the client half of {@code HoneyCentrifugeBlockEntity.canPlaceItem}.
 */
public final class HoneyCentrifugeMenu extends MachineMenu {
	public HoneyCentrifugeMenu(int syncId, Inventory playerInventory, HoneyCentrifugeBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.HONEY_CENTRIFUGE_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.HONEY_CENTRIFUGE.get());
	}

	public HoneyCentrifugeMenu(int syncId, Inventory playerInventory) {
		super(ModContent.HONEY_CENTRIFUGE_MENU.get(), syncId, playerInventory,
				clientStub(HoneyCentrifugeBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						HoneyCentrifugeBlockEntity.DATA_COUNT),
				ModContent.HONEY_CENTRIFUGE.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		// These coordinates ARE the GUI art (tools/gen_honey_centrifuge_textures.py): the input and the
		// result frame sit on either side of the arrow, the container pair beside the honey gauge.
		addSlot(new Slot(container, HoneyCentrifugeBlockEntity.INPUT_SLOT, 21, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return mayEnterInput(container, stack);
			}
		});
		addSlot(new OutputSlot(container, HoneyCentrifugeBlockEntity.REMAINDER_SLOT, 65, 35));
		addSlot(new Slot(container, HoneyCentrifugeBlockEntity.BUCKET_INPUT_SLOT, 133, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return !stack.is(Items.GLASS_BOTTLE) && !stack.is(Items.HONEY_BOTTLE)
						&& ItemFluidBridge.get().isFluidContainer(stack);
			}
		});
		addSlot(new OutputSlot(container, HoneyCentrifugeBlockEntity.BUCKET_OUTPUT_SLOT, 133, 47));
	}

	/**
	 * Whether {@code stack} may go into the input slot. The input comes first in the menu, so without this a
	 * shift-clicked empty bucket landed there instead of in the container slot. On the server the block entity
	 * answers by its recipes; the client stub has no recipes, so it only keeps fluid containers out — a honey
	 * bottle excepted, which is a recipe input — and the server's answer corrects the rest.
	 */
	static boolean mayEnterInput(Container container, ItemStack stack) {
		if (container instanceof HoneyCentrifugeBlockEntity be) {
			return be.canPlaceItem(HoneyCentrifugeBlockEntity.INPUT_SLOT, stack);
		}
		return stack.is(Items.HONEY_BOTTLE) || !ItemFluidBridge.get().isFluidContainer(stack);
	}

	/** Honey level as a permille (0..1000) — scaled because the sync channel is a signed short. */
	public int getFluidPermille() {
		return channel(HoneyCentrifugeBlockEntity.Channel.LEVEL_PERMILLE);
	}

	/** Registry id of the fluid in the tank, or {@link HoneyCentrifugeBlockEntity#FLUID_ID_NONE}. */
	public int getFluidRegistryId() {
		return channel(HoneyCentrifugeBlockEntity.Channel.FLUID_ID);
	}

	/** Why the machine is idle, for the screen's tooltip over the arrow. */
	public HoneyCentrifugeStatus getStatus() {
		return HoneyCentrifugeStatus.byOrdinal(channel(HoneyCentrifugeBlockEntity.Channel.STATUS));
	}
}
