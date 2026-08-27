package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.menu.UpgradeSlotKind;
import dev.alaindustrial.registry.ContentManifest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * L2 suite for the overclocker arm of the upgrade panel (MOD-392).
 *
 * <p>The bug these guard: a generator accepted an overclocker chip and then ignored it. Nothing was
 * broken in the effect — {@code supportsOverclock()} was correctly false — but the slot did not know,
 * so the player parked a chip in a solar panel and waited for a speed-up that was never coming. A dead
 * upgrade reads as a broken item.
 */
public final class OverclockerPanelScenarios {

	private OverclockerPanelScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final BlockPos CONTROL = new BlockPos(3, 2, 1);

	/** The panel arm index the overclocker lives on. */
	private static final int OVERCLOCK_ARM = 1;

	/**
	 * A generator's overclock arm refuses the chip; a processing machine's takes it.
	 *
	 * <p>The control half is the point. "Solar panel refuses the chip" passes just as well on a build
	 * where NO arm accepts anything — a typo in the tag, a slot wired to the wrong kind — so the same
	 * test asserts a macerator still accepts one. And the mute arm is checked on the very same solar
	 * panel: refusing the overclocker must not cost generators their mute chip, which is the one upgrade
	 * they are supposed to take.
	 */
	public static void overclocker_generatorRefusesTheChip(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		ItemStack chip = new ItemStack(dev.alaindustrial.registry.ModContent.OVERCLOCKER_CHIP_I.get());
		ItemStack mute = new ItemStack(dev.alaindustrial.registry.ModContent.MUTE_CHIP.get());

		MachineBlockEntity panel =
				AlaGameTestHelper.place(helper, POS, dev.alaindustrial.registry.ModContent.SOLAR_PANEL.get());
		MachineMenu panelMenu = new dev.alaindustrial.menu.SolarPanelMenu(1, player.getInventory(), panel,
				ContainerLevelAccess.create(helper.getLevel(), panel.getBlockPos()));
		Slot panelOverclockArm = armSlot(helper, panelMenu, panel, OVERCLOCK_ARM);
		if (panelOverclockArm.mayPlace(chip)) {
			helper.fail("a solar panel accepted an overclocker chip — it cannot use one, so the chip "
					+ "would sit there doing nothing and read as broken");
		}
		Slot panelMuteArm = armSlot(helper, panelMenu, panel, 0);
		if (!panelMuteArm.mayPlace(mute)) {
			helper.fail("a solar panel refused a MUTE chip — locking the overclock arm must not cost "
					+ "generators the one upgrade they do take");
		}

		MachineBlockEntity macerator =
				AlaGameTestHelper.place(helper, CONTROL, dev.alaindustrial.registry.ModContent.MACERATOR.get());
		MachineMenu machineMenu = new dev.alaindustrial.menu.MaceratorMenu(2, player.getInventory(), macerator,
				ContainerLevelAccess.create(helper.getLevel(), macerator.getBlockPos()));
		Slot machineOverclockArm = armSlot(helper, machineMenu, macerator, OVERCLOCK_ARM);
		if (!machineOverclockArm.mayPlace(chip)) {
			helper.fail("control failed: a macerator must still accept the overclocker chip");
		}
		helper.succeed();
	}

	/**
	 * The arm survives a panel drag.
	 *
	 * <p>The panel's arm layout is built in two places — docked and after a drag — and the task log
	 * records the last time only one copy was updated: the slots behaved until the player moved the
	 * panel and then quietly changed behaviour. So the refusal is re-checked on the rebuilt slot.
	 */
	public static void overclocker_refusalSurvivesAPanelDrag(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		MachineBlockEntity panel =
				AlaGameTestHelper.place(helper, POS, dev.alaindustrial.registry.ModContent.SOLAR_PANEL.get());
		MachineMenu menu = new dev.alaindustrial.menu.SolarPanelMenu(1, player.getInventory(), panel,
				ContainerLevelAccess.create(helper.getLevel(), panel.getBlockPos()));
		menu.repositionUpgradeSlots(7, 5);

		ItemStack chip = new ItemStack(dev.alaindustrial.registry.ModContent.OVERCLOCKER_CHIP_I.get());
		Slot arm = armSlot(helper, menu, panel, OVERCLOCK_ARM);
		if (arm.mayPlace(chip)) {
			helper.fail("after dragging the panel the solar panel's overclock arm accepted a chip again "
					+ "— the arm layout is built in two places and only one was fixed");
		}
		if (!(arm instanceof MachineMenu.UpgradeSlot upgrade)
				|| upgrade.kind() != UpgradeSlotKind.OVERCLOCK_UNSUPPORTED) {
			helper.fail("the dragged arm lost its locked kind, so the screen would draw it as open");
		}
		helper.succeed();
	}

	/**
	 * The manifest's answer matches the block entity's, on a machine of each kind.
	 *
	 * <p>Not a tautology despite both sides reading {@link dev.alaindustrial.block.entity.Overclockable}:
	 * the block entity here is a REAL one, instantiated by the world from the block, while the manifest
	 * answers from its declared block→class mapping. They disagree exactly when a block is listed under
	 * the wrong {@code BlockEntityDef} — a copy-paste in the manifest that nothing else would catch, and
	 * that would show up as a slot which accepts a chip the machine ignores.
	 */
	public static void overclocker_manifestMatchesTheBlockEntity(GameTestHelper helper) {
		Block[] blocks = {
				dev.alaindustrial.registry.ModContent.MACERATOR.get(),
				dev.alaindustrial.registry.ModContent.ASSEMBLER.get(),
				dev.alaindustrial.registry.ModContent.SOLAR_PANEL.get(),
				dev.alaindustrial.registry.ModContent.BATTERY_BOX.get(),
				dev.alaindustrial.registry.ModContent.ENERGY_CONDENSER.get(),
		};
		boolean sawTrue = false;
		boolean sawFalse = false;
		for (int i = 0; i < blocks.length; i++) {
			BlockPos at = new BlockPos(1 + i, 2, 1);
			MachineBlockEntity be = AlaGameTestHelper.place(helper, at, blocks[i]);
			boolean fromEntity = be.supportsOverclock();
			boolean fromManifest = ContentManifest.isOverclockable(blocks[i]);
			if (fromEntity != fromManifest) {
				helper.fail("block " + blocks[i] + ": the block entity says supportsOverclock=" + fromEntity
						+ " but the manifest says " + fromManifest + " — the slot and the effect disagree");
			}
			sawTrue |= fromEntity;
			sawFalse |= !fromEntity;
		}
		// Without this the loop would pass on a build where the answer is a constant, either way.
		if (!sawTrue || !sawFalse) {
			helper.fail("control failed: the sample must contain both an overclockable machine and one "
					+ "that is not, or the comparison above proves nothing");
		}
		helper.succeed();
	}

	/** The menu slot standing on panel arm {@code arm} of {@code machine}. */
	private static Slot armSlot(GameTestHelper helper, MachineMenu menu, MachineBlockEntity machine, int arm) {
		int index = machine.upgradeSlotStart() + arm;
		for (Slot slot : menu.slots) {
			if (slot.container == machine && slot.getContainerSlot() == index) {
				return slot;
			}
		}
		helper.fail("no menu slot bound to upgrade arm " + arm + " (container index " + index + ")");
		throw new IllegalStateException("unreachable");
	}
}
