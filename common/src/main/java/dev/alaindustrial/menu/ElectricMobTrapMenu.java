package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ElectricMobTrapBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.network.ZonePreviewPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the Electric Mob Trap (MOD-818): the nine-slot buffer as a 3×3 grid of take-only slots, the energy
 * bar, the liquid-experience gauge, the status line and the reach-preview button. No upgrade panel: the trap
 * has nothing for a chip to speed up that its pulse period does not already set.
 */
public class ElectricMobTrapMenu extends MachineMenu {

	/** Item position of the grid's top-left slot; the atlas draws the wells one pixel around. */
	public static final int GRID_X = 44;
	public static final int GRID_Y = 17;
	/** Side of one slot well, and the step between two. */
	public static final int SLOT_STEP = 18;

	/** Button id of the reach preview: the server answers with the trap's own zone. */
	public static final int BUTTON_TOGGLE_ZONE = 0;

	@Nullable
	private final ElectricMobTrapBlockEntity trap;

	/** Server side. */
	public ElectricMobTrapMenu(int syncId, Inventory playerInventory, ElectricMobTrapBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.ELECTRIC_MOB_TRAP_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.ELECTRIC_MOB_TRAP.get());
		this.trap = be;
	}

	/** Client side: nine slots and no upgrade block, matching the {@code NoUpgradePanel} marker. */
	public ElectricMobTrapMenu(int syncId, Inventory playerInventory) {
		super(ModContent.ELECTRIC_MOB_TRAP_MENU.get(), syncId, playerInventory,
				clientStub(ElectricMobTrapBlockEntity.SLOT_COUNT, ElectricMobTrapBlockEntity.DATA_COUNT),
				ModContent.ELECTRIC_MOB_TRAP.get());
		this.trap = null;
	}

	/**
	 * The reach preview: answer the asking player — and only them — with the trap's position and its
	 * server-side reach; their client toggles the zone ({@code ZonePreviewRenderer}).
	 */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != BUTTON_TOGGLE_ZONE || trap == null || !(player instanceof ServerPlayer)) {
			return super.clickMenuButton(player, buttonId);
		}
		ZonePreviewPayload.send(player, trap.getLevel(), trap.getBlockPos(),
				ElectricMobTrapBlockEntity.reach(), true);
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

	/** Liquid experience as a permille (0..1000) — scaled because the sync channel is a signed short. */
	public int getXpPermille() {
		return channel(ElectricMobTrapBlockEntity.Channel.XP_PERMILLE);
	}

	/** Registry id of the fluid in the tank, or {@link ElectricMobTrapBlockEntity#FLUID_ID_NONE}. */
	public int getXpFluidId() {
		return channel(ElectricMobTrapBlockEntity.Channel.XP_FLUID_ID);
	}

	public ElectricMobTrapBlockEntity.Status getStatus() {
		return ElectricMobTrapBlockEntity.Status.byOrdinal(channel(ElectricMobTrapBlockEntity.Channel.STATUS));
	}
}
