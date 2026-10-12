package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.VacuumCollectorBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.network.ZonePreviewPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the Vacuum Collector (MOD-802): the nine-slot buffer as a 3×3 grid of take-only slots, the energy
 * bar, the status line and the reach-preview button. No upgrade panel: the collector has nothing for a chip
 * to speed up that its pulse period does not already set.
 */
public class VacuumCollectorMenu extends MachineMenu {

	/** Item position of the grid's top-left slot; the atlas draws the wells one pixel around. */
	public static final int GRID_X = 44;
	public static final int GRID_Y = 17;
	/** Side of one slot well, and the step between two. */
	public static final int SLOT_STEP = 18;

	/** Button id of the reach preview: the server answers with the collector's own zone. */
	public static final int BUTTON_TOGGLE_ZONE = 0;

	@Nullable
	private final VacuumCollectorBlockEntity collector;

	/** Server side. */
	public VacuumCollectorMenu(int syncId, Inventory playerInventory, VacuumCollectorBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.VACUUM_COLLECTOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.VACUUM_COLLECTOR.get());
		this.collector = be;
	}

	/** Client side: nine slots and no upgrade block, matching the {@code NoUpgradePanel} marker. */
	public VacuumCollectorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.VACUUM_COLLECTOR_MENU.get(), syncId, playerInventory,
				clientStub(VacuumCollectorBlockEntity.SLOT_COUNT, VacuumCollectorBlockEntity.DATA_COUNT),
				ModContent.VACUUM_COLLECTOR.get());
		this.collector = null;
	}

	/**
	 * The reach preview: answer the asking player — and only them — with the collector's position and its
	 * server-side reach; their client toggles the zone ({@code ZonePreviewRenderer}).
	 */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != BUTTON_TOGGLE_ZONE || collector == null || !(player instanceof ServerPlayer)) {
			return super.clickMenuButton(player, buttonId);
		}
		ZonePreviewPayload.send(player, collector.getLevel(), collector.getBlockPos(),
				VacuumCollectorBlockEntity.reach(), true);
		return true;
	}

	@Override
	protected void addMachineSlots() {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				addSlot(new OutputSlot(machine, row * 3 + col, GRID_X + col * SLOT_STEP, GRID_Y + row * SLOT_STEP));
			}
		}
	}

	public VacuumCollectorBlockEntity.Status getStatus() {
		return VacuumCollectorBlockEntity.Status.byOrdinal(channel(VacuumCollectorBlockEntity.Channel.STATUS));
	}
}
