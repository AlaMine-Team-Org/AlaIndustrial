package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.BlockBreakerBlockEntity;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity.Channel;
import dev.alaindustrial.block.entity.quarry.QuarryFrameLayout;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * The quarry controller's window (MOD-951): the drill bit's slot, the 27-slot buffer the haul goes into, and the
 * channels the screen writes the work from — the cycle's state, the frame's first broken rule, the layer and how far
 * it is done. The panel is taller than a machine's (222 pixels) to hold the buffer above the player's inventory.
 */
public class QuarryControllerMenu extends MachineMenu {

	public static final int BIT_SLOT_X = 32;
	public static final int BIT_SLOT_Y = 41;
	public static final int BUFFER_X = 8;
	public static final int BUFFER_Y = 72;
	public static final int INVENTORY_Y = 140;
	public static final int HOTBAR_Y = 198;
	public static final int IMAGE_HEIGHT = 222;
	/** The button that steps the redstone mode (MOD-952). */
	public static final int BUTTON_REDSTONE_MODE = 0;
	/** How far from its frame the player may stand with the screen open, in blocks. */
	private static final double REACH_FROM_FRAME = 8.0D;

	/** The controller on the server side; {@code null} in the client's stub. */
	private final QuarryControllerBlockEntity controller;

	public QuarryControllerMenu(int syncId, Inventory playerInventory, QuarryControllerBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.QUARRY_CONTROLLER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.QUARRY_CONTROLLER.get());
		this.controller = be;
	}

	public QuarryControllerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.QUARRY_CONTROLLER_MENU.get(), syncId, playerInventory,
				clientStub(QuarryControllerBlockEntity.SLOT_COUNT, QuarryControllerBlockEntity.DATA_COUNT),
				ModContent.QUARRY_CONTROLLER.get());
		this.controller = null;
	}

	/**
	 * The screen opens from any block of the formed frame (MOD-951), and the controller sits in a head seven blocks
	 * up: the vanilla rule, a few blocks from the controller itself, shut the screen the moment a player standing at
	 * the frame's foot opened it. It stays open while the player is within reach of the frame instead.
	 */
	@Override
	public boolean stillValid(Player player) {
		if (controller == null) {
			return super.stillValid(player);
		}
		QuarryFrameLayout frame = controller.layout();
		if (controller.isRemoved() || frame == null) {
			return super.stillValid(player);
		}
		AABB reach = new AABB(frame.minX(), frame.baseY(), frame.minZ(), frame.maxX() + 1, frame.headY() + 4,
				frame.maxZ() + 1).inflate(REACH_FROM_FRAME);
		return reach.contains(player.position());
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != BUTTON_REDSTONE_MODE || controller == null || !(player instanceof ServerPlayer)) {
			return super.clickMenuButton(player, buttonId);
		}
		controller.setRedstoneMode(controller.redstoneMode().next());
		return true;
	}

	public BlockBreakerBlockEntity.RedstoneMode redstoneMode() {
		return BlockBreakerBlockEntity.RedstoneMode.byOrdinal(channel(Channel.REDSTONE));
	}

	/** The drill's heat and the water tank, per mille of their scale. */
	public int heatPermille() {
		return channel(Channel.HEAT_PERMILLE);
	}

	public int waterPermille() {
		return channel(Channel.WATER_PERMILLE);
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, QuarryControllerBlockEntity.BIT_SLOT, BIT_SLOT_X, BIT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine instanceof QuarryControllerBlockEntity be
						? be.canPlaceItem(QuarryControllerBlockEntity.BIT_SLOT, stack)
						: stack.is(ModContent.QUARRY_DRILL_BIT.get());
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int slot = QuarryControllerBlockEntity.BUFFER_START + row * 9 + col;
				addSlot(new Slot(machine, slot, BUFFER_X + col * 18, BUFFER_Y + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false; // the buffer is output only
					}
				});
			}
		}
	}

	@Override
	protected int playerInventoryY() {
		return INVENTORY_Y;
	}

	@Override
	protected int hotbarY() {
		return HOTBAR_Y;
	}

	public QuarryWorkStatus workStatus() {
		return QuarryWorkStatus.byOrdinal(channel(Channel.STATUS));
	}

	public QuarryFrameScan.Status frameStatus() {
		int ordinal = channel(Channel.FRAME_STATUS);
		QuarryFrameScan.Status[] all = QuarryFrameScan.Status.values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : QuarryFrameScan.Status.UNSCANNED;
	}

	public int layer() {
		return channel(Channel.LAYER);
	}

	public int layers() {
		return channel(Channel.LAYERS);
	}

	public int layerPermille() {
		return channel(Channel.LAYER_PERMILLE);
	}
}
