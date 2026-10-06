package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.core.environment.MobWheelProfile;
import dev.alaindustrial.core.environment.MobWheelStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the mob wheel's drive (MOD-763): the feeder slot and what the screen shows — the occupant's
 * species, its stamina, the wheel's status and the EU/t it makes. No upgrade panel: the drive takes no chips.
 */
public class MobWheelMenu extends MachineMenu {
	/** Where the feeder slot sits in the GUI texture (left/top pixel of the 16×16 item area). */
	public static final int FEED_SLOT_X = 26;
	public static final int FEED_SLOT_Y = 35;

	/** Server side. */
	public MobWheelMenu(int syncId, Inventory playerInventory, MobWheelBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.MOB_WHEEL_CONTROLLER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.MOB_WHEEL_CONTROLLER.get());
	}

	/** Client side; no upgrade slots on this machine, so the stub is the feeder slot alone. */
	public MobWheelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.MOB_WHEEL_CONTROLLER_MENU.get(), syncId, playerInventory,
				clientStub(MobWheelBlockEntity.SLOT_COUNT, MobWheelBlockEntity.DATA_COUNT),
				ModContent.MOB_WHEEL_CONTROLLER.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, MobWheelBlockEntity.FEED_SLOT, FEED_SLOT_X, FEED_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(MobWheelBlockEntity.FEED_SLOT, stack);
			}
		});
	}

	/** Effective EU/t the wheel makes now (after the global rate multiplier). */
	public int getProductionRate() {
		return channel(MobWheelBlockEntity.Channel.RATE);
	}

	/** Occupant stamina in thousandths, 0 when the wheel is empty. */
	public int getStaminaPermille() {
		return channel(MobWheelBlockEntity.Channel.STAMINA);
	}

	/** Wheel speed in percent of nominal (0..120; a fast runner turns the wheel twice as fast, up to 200). */
	public int getSpeedPercent() {
		return getProgress();
	}

	public MobWheelStatus getStatus() {
		return MobWheelStatus.byOrdinal(getMaxProgress());
	}

	/** The occupant's entity type id ({@code minecraft:zombie_villager}), or {@code null} when nobody is in. */
	@Nullable
	public String getRunnerType() {
		return MobWheelProfile.runnerTypeId(channel(MobWheelBlockEntity.Channel.SPECIES) - 1);
	}

	/** The occupant's species (a zombie villager runs as {@link MobWheelProfile#ZOMBIE}), or {@code null}. */
	@Nullable
	public MobWheelProfile getSpecies() {
		String type = getRunnerType();
		return type == null ? null : MobWheelProfile.byEntityTypeId(type);
	}
}
