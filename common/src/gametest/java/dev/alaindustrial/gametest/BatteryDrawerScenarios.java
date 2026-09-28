package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Loader-neutral gametest bodies for the battery drawer (MOD-679): a consumer machine drains the battery
 * in its drawer slot into its own buffer, so it can run with no cable at all.
 *
 * <p>Each scenario pins one property the drawer could lose silently: energy conserved across a stack, the
 * drain surviving the machine's idle sleep, the slot staying out of automation's reach, the drawer
 * existing only where it means something, and the old save layout staying where it was.
 */
public final class BatteryDrawerScenarios {

	private BatteryDrawerScenarios() {}

	private static final BlockPos MACHINE = new BlockPos(1, 2, 1);

	private static <T extends MachineBlockEntity> T place(GameTestHelper helper, Block block, Class<T> type) {
		helper.setBlock(MACHINE, block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		if (helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE)) == null) {
			helper.fail("block entity missing at " + MACHINE);
			return null;
		}
		return helper.getBlockEntity(MACHINE, type);
	}

	private static ItemStack batteries(int count) {
		ItemStack stack = new ItemStack(ModContent.BATTERY.get(), count);
		ItemEnergy.set(stack, Config.batteryBuffer);
		return stack;
	}

	private static void tick(MachineBlockEntity be, GameTestHelper helper) {
		be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());
	}

	/**
	 * DRAWER-01 — a full stack of 16 batteries feeds an empty furnace, one LV packet per tick, and every EU
	 * the buffer gained left the stack. A per-item drain (sixteen packets a tick) or a stack paid for as one
	 * battery breaks either the ceiling or the conservation check.
	 */
	public static void drawer01DrainsStackIntoBuffer(GameTestHelper helper) {
		ElectricFurnaceBlockEntity be = place(helper, ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		if (be == null) {
			return;
		}
		if (!be.hasBatterySlot()) {
			helper.fail("the electric furnace has no battery drawer");
			return;
		}
		int count = 16;
		be.getEnergyStorage().setAmountUntracked(0);
		be.setItem(be.batterySlotIndex(), batteries(count));
		tick(be, helper);

		long gained = be.getEnergyStorage().getAmount();
		long lost = (Config.batteryBuffer - ItemEnergy.get(be.getItem(be.batterySlotIndex()))) * count;
		if (gained <= 0) {
			helper.fail("the drawer moved no EU out of a full stack");
			return;
		}
		if (gained != lost) {
			helper.fail("EU not conserved: the stack lost " + lost + " but the buffer gained " + gained);
			return;
		}
		if (gained > EnergyTier.LV.maxVoltage()) {
			helper.fail("the drawer beat the LV ceiling in one tick: " + gained);
			return;
		}
		helper.succeed();
	}

	/**
	 * DRAWER-02 — an idle furnace still drinks from its drawer while it sleeps. With nothing to smelt it
	 * sleeps between checks; a drain that ran only on waking ticks would feed it once per sleep interval,
	 * and five ticks would add one packet instead of five.
	 */
	public static void drawer02DrainsWhileTheMachineSleeps(GameTestHelper helper) {
		ElectricFurnaceBlockEntity be = place(helper, ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		if (be == null) {
			return;
		}
		be.getEnergyStorage().setAmountUntracked(0);
		be.setItem(be.batterySlotIndex(), batteries(1));
		int ticks = 5;
		for (int i = 0; i < ticks; i++) {
			tick(be, helper);
		}
		long expected = Math.min((long) EnergyTier.LV.maxVoltage() * ticks, Config.batteryBuffer);
		long gained = be.getEnergyStorage().getAmount();
		if (gained != expected) {
			helper.fail("after " + ticks + " ticks the drawer delivered " + gained + " EU, expected " + expected
					+ " (a sleeping machine is not being fed)");
			return;
		}
		helper.succeed();
	}

	/**
	 * DRAWER-03 — the drawer slot is the LAST index and no face offers it to automation. Last, because a
	 * slot anywhere else would shift the upgrade chips of every saved machine by one (the MOD-083 trap);
	 * hidden, because a hopper that could fill or empty it would bypass the player's choice.
	 */
	public static void drawer03SlotIsLastAndHiddenFromAutomation(GameTestHelper helper) {
		ElectricFurnaceBlockEntity be = place(helper, ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		if (be == null) {
			return;
		}
		int expectedSize = ElectricFurnaceBlockEntity.SLOT_COUNT + MachineBlockEntity.UPGRADE_SLOT_COUNT + 1;
		if (be.getContainerSize() != expectedSize || be.batterySlotIndex() != expectedSize - 1) {
			helper.fail("drawer slot must be the last index: size " + be.getContainerSize()
					+ ", drawer at " + be.batterySlotIndex());
			return;
		}
		if (be.upgradeSlotStart() != ElectricFurnaceBlockEntity.SLOT_COUNT) {
			helper.fail("the upgrade block moved: starts at " + be.upgradeSlotStart());
			return;
		}
		for (Direction side : Direction.values()) {
			for (int slot : be.getSlotsForFace(side)) {
				if (slot == be.batterySlotIndex()) {
					helper.fail("face " + side + " offers the drawer slot to automation");
					return;
				}
			}
		}
		helper.succeed();
	}

	/**
	 * DRAWER-04 — only a consumer gets a drawer. A generator would pour the battery straight into the wire,
	 * and the Battery Box already has a discharge slot of its own; the block-level answer the client menu
	 * relies on must agree with the block entity.
	 */
	public static void drawer04OnlyConsumersHaveADrawer(GameTestHelper helper) {
		GeneratorBlockEntity generator = place(helper, ModContent.GENERATOR.get(), GeneratorBlockEntity.class);
		if (generator == null) {
			return;
		}
		if (generator.hasBatterySlot() || ContentManifest.isBatteryFed(ModContent.GENERATOR.get())) {
			helper.fail("the generator has a battery drawer");
			return;
		}
		BatteryBoxBlockEntity store = place(helper, ModContent.BATTERY_BOX.get(), BatteryBoxBlockEntity.class);
		if (store == null) {
			return;
		}
		if (store.hasBatterySlot() || ContentManifest.isBatteryFed(ModContent.BATTERY_BOX.get())) {
			helper.fail("the Battery Box got a second discharge slot");
			return;
		}
		if (!ContentManifest.isBatteryFed(ModContent.ELECTRIC_FURNACE.get())) {
			helper.fail("the manifest does not know the electric furnace has a drawer");
			return;
		}
		helper.succeed();
	}

	/**
	 * DRAWER-05 — a crystal blank is refused and a full machine leaves the battery alone. The blank only
	 * ever fills up, so a drawer that took it would hold it forever; a full buffer must not nibble charge
	 * it has no room for.
	 */
	public static void drawer05BlankRefusedAndFullMachineWaits(GameTestHelper helper) {
		if (ItemEnergy.canDischarge(new ItemStack(ModContent.ENERGY_CRYSTAL_BLANK.get()))) {
			helper.fail("a crystal blank counts as dischargeable");
			return;
		}
		ElectricFurnaceBlockEntity be = place(helper, ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		if (be == null) {
			return;
		}
		be.getEnergyStorage().setAmountUntracked(be.getEnergyStorage().getCapacity());
		be.setItem(be.batterySlotIndex(), batteries(1));
		tick(be, helper);
		long left = ItemEnergy.get(be.getItem(be.batterySlotIndex()));
		if (left != Config.batteryBuffer) {
			helper.fail("a full machine drained the battery to " + left);
			return;
		}
		helper.succeed();
	}
}
