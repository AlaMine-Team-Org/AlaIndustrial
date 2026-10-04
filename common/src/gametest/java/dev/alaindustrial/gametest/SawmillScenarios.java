package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.MachineRig.AMPLE_EU;
import static dev.alaindustrial.gametest.MachineRig.DRIVE_TICKS;
import static dev.alaindustrial.gametest.MachineRig.place;
import static dev.alaindustrial.gametest.MachineRig.sawmill;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.SawmillMode;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * World scenarios for the sawmill (MOD-150): its four switchable modes, per-species yield and mode
 * persistence.

 * <p>Split by mechanic in MOD-717 (TST-2, the {@code ReactorScenarios} pattern): the lanes run the names in
 * {@link MachineScenarios}, which delegates here; a new processing-machine scenario is written in the class of
 * its mechanic and declared in that class's roster.
 */
public final class SawmillScenarios {

	private SawmillScenarios() {
	}

	// ── Sawmill (MOD-150): four switchable modes, per-species yield, mode persistence ──────────────

	/**
	 * Place a powered sawmill in the given mode, feed {@code input}, drive exactly ONE operation
	 * ({@code sawmillDuration} ticks), and assert the output is exactly {@code count} of {@code expected}
	 * and exactly one input item was consumed. Driving a single op (not {@link MachineRig#DRIVE_TICKS}) keeps the
	 * per-op yield assertion exact rather than accumulating across the whole input stack.
	 */
	private static void assertSawsInMode(GameTestHelper helper, SawmillMode mode, ItemStack input, Item expected,
			int count) {
		MachineBlockEntity be = place(helper, sawmill());
		((SawmillBlockEntity) be).setMode(mode);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		int startCount = input.getCount();
		be.setItem(0, input.copy());
		drive(be, helper, Config.sawmillDuration);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(expected) || out.getCount() != count) {
			helper.fail("sawmill[" + mode + "]: expected exactly " + count + "× " + expected + " but got "
					+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
		}
		ItemStack in = be.getItem(0);
		if (in.getCount() != startCount - 1) {
			helper.fail("sawmill[" + mode + "]: expected exactly one input consumed (" + (startCount - 1)
					+ " left) but got " + in.getCount());
		}
		helper.succeed();
	}

	/**
	 * TC-SAW-001-FUN01: PLANKS mode (default), oak log → 6 oak planks (+50% over vanilla 4).
	 *
	 * @implements TC-SAW-001-FUN01 — PLANKS mode (default): oak log → 6 oak planks (+50% over vanilla 4).
	 */
	public static void tcSaw001Fun01_planksMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.PLANKS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_PLANKS, 6);
	}

	/**
	 * TC-SAW-001-FUN02: PLANKS mode, bamboo block → 3 bamboo planks (halved, per vanilla 2/block).
	 *
	 * @implements TC-SAW-001-FUN02 — PLANKS mode, bamboo: bamboo block → 3 bamboo planks (halved, per vanilla 2/block).
	 */
	public static void tcSaw001Fun02_bambooHalfYield(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.PLANKS, new ItemStack(Items.BAMBOO_BLOCK, 4), Items.BAMBOO_PLANKS, 3);
	}

	/**
	 * TC-SAW-001-FUN03: STICKS mode, oak log (#minecraft:logs) → 18 sticks (MOD-215: 1.5× the free 12).
	 *
	 * @implements TC-SAW-001-FUN03 — STICKS mode: oak log (#minecraft:logs) → 18 sticks (MOD-215: 1.5× the 12 a
	 * player gets free from PLANKS mode + a workbench, so the mode is worth its own operation).
	 */
	public static void tcSaw001Fun03_sticksMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.STICKS, new ItemStack(Items.OAK_LOG, 4), Items.STICK, 18);
	}

	/**
	 * TC-SAW-001-FUN04: SLABS mode, oak log → 18 oak slabs (MOD-215: 1.5× the 12 from PLANKS + a workbench).
	 *
	 * @implements TC-SAW-001-FUN04 — SLABS mode: oak log → 18 oak slabs (MOD-215: 1.5× the 12 from PLANKS
	 * mode + a workbench).
	 */
	public static void tcSaw001Fun04_slabsMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.SLABS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_SLAB, 18);
	}

	/**
	 * TC-SAW-001-FUN05: STAIRS mode, oak log → 6 oak stairs.
	 *
	 * @implements TC-SAW-001-FUN05 — STAIRS mode: oak log → 6 oak stairs.
	 */
	public static void tcSaw001Fun05_stairsMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.STAIRS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_STAIRS, 6);
	}

	/**
	 * TC-SAW-001-CON01: the machine saws ONLY in the active mode. A regression guard: with the mode set
	 * to STICKS, an oak log must produce sticks — never planks (which the default mode would make).
	 * Fails if {@code resolveInput} ignored the selected mode.
	 *
	 * @implements TC-SAW-001-CON01 — the machine saws ONLY in the active mode. A regression guard: with the
	 *     mode set to STICKS, an oak log must produce sticks — never planks (which the default mode would
	 *     make). Fails if {@code resolveInput} ignored the selected mode.
	 */
	public static void tcSaw001Con01_onlyActiveModeSaws(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, sawmill());
		((SawmillBlockEntity) be).setMode(SawmillMode.STICKS);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.OAK_LOG, 4));
		drive(be, helper, DRIVE_TICKS);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(Items.STICK)) {
			helper.fail("sawmill in STICKS mode did not produce sticks from a log: "
					+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
		}
		if (out.is(Items.OAK_PLANKS)) {
			helper.fail("sawmill ignored the active mode and produced planks");
		}
		helper.succeed();
	}

	/**
	 * TC-SAW-001-CON02: the active mode survives an NBT round-trip (relog). Set STAIRS, save the block
	 * entity to NBT, reload into a fresh instance, and assert the mode is still STAIRS.
	 *
	 * @implements TC-SAW-001-CON02 — the active mode survives an NBT round-trip (relog). Set STAIRS, save
	 *     the block entity to NBT, reload into a fresh instance, and assert the mode is still STAIRS.
	 */
	public static void tcSaw001Con02_modePersistsThroughNbt(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, sawmill());
		((SawmillBlockEntity) be).setMode(SawmillMode.STAIRS);

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = be.saveCustomOnly(registries);
		SawmillBlockEntity restored = new SawmillBlockEntity(be.getBlockPos(),
				helper.getLevel().getBlockState(be.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getMode() != SawmillMode.STAIRS) {
			helper.fail("sawmill mode lost on NBT round-trip: expected STAIRS but got " + restored.getMode());
		}
		helper.succeed();
	}

	/**
	 * TC-SAW-001-CON03: switching the mode mid-operation resets progress to 0 (the new mode saws a
	 * different product, so carrying progress over would be wrong).
	 *
	 * @implements TC-SAW-001-CON03 — switching the mode mid-operation resets progress to 0 (the new mode
	 *     saws a different product, so carrying progress over would be wrong).
	 */
	public static void tcSaw001Con03_modeSwitchResetsProgress(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, sawmill());
		SawmillBlockEntity saw = (SawmillBlockEntity) be;
		saw.setMode(SawmillMode.PLANKS);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.OAK_LOG, 4));
		drive(be, helper, Config.sawmillDuration / 2);
		if (be.getDataAccess().get(2) <= 0) {
			helper.fail("sawmill made no progress before the mode switch");
		}
		saw.setMode(SawmillMode.SLABS);
		if (be.getDataAccess().get(2) != 0) {
			helper.fail("sawmill progress did not reset to 0 after switching mode");
		}
		helper.succeed();
	}
}
