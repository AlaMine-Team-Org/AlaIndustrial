package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.BlockBreakerBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the Block Breaker (MOD-787): the tool slot, the energy bar, the break progress, the status
 * line and the redstone-mode button.
 *
 * <p>The button rides the vanilla container-button channel, like the sawmill's mode buttons: the screen
 * sends {@link #BUTTON_REDSTONE_MODE} and the server cycles the mode; the new mode comes back on the
 * {@code REDSTONE} sync channel.
 */
public class BlockBreakerMenu extends MachineMenu {

	/** Tool slot: the Macerator atlas's input well, which tools/gen_block_breaker_gui.py keeps. */
	public static final int TOOL_SLOT_X = 56;
	public static final int TOOL_SLOT_Y = 35;

	/** Button id of the redstone-mode button: cycles ignore → with signal → without signal. */
	public static final int BUTTON_REDSTONE_MODE = 0;

	@Nullable
	private final BlockBreakerBlockEntity breaker;

	/** Server side. */
	public BlockBreakerMenu(int syncId, Inventory playerInventory, BlockBreakerBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.BLOCK_BREAKER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.BLOCK_BREAKER.get());
		this.breaker = be;
	}

	/** Client side. */
	public BlockBreakerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.BLOCK_BREAKER_MENU.get(), syncId, playerInventory,
				clientStub(BlockBreakerBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT, BlockBreakerBlockEntity.DATA_COUNT),
				ModContent.BLOCK_BREAKER.get());
		this.breaker = null;
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, BlockBreakerBlockEntity.TOOL_SLOT, TOOL_SLOT_X, TOOL_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// Server: the block entity's verdict; the client stub sees the tag the same way.
				return machine instanceof BlockBreakerBlockEntity be
						? be.canPlaceItem(BlockBreakerBlockEntity.TOOL_SLOT, stack)
						: stack.is(BlockBreakerBlockEntity.TOOLS);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId == BUTTON_BATTERY_DRAWER) {
			return super.clickMenuButton(player, buttonId); // the battery drawer (MOD-679)
		}
		if (buttonId != BUTTON_REDSTONE_MODE || breaker == null || !(player instanceof ServerPlayer)) {
			return false;
		}
		breaker.setRedstoneMode(breaker.redstoneMode().next());
		return true;
	}

	public BlockBreakerBlockEntity.Status getStatus() {
		return BlockBreakerBlockEntity.Status.byOrdinal(channel(BlockBreakerBlockEntity.Channel.STATUS));
	}

	public BlockBreakerBlockEntity.RedstoneMode getRedstoneMode() {
		return BlockBreakerBlockEntity.RedstoneMode.byOrdinal(channel(BlockBreakerBlockEntity.Channel.REDSTONE));
	}
}
