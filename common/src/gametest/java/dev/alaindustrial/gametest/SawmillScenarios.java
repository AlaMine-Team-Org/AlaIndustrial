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
import dev.alaindustrial.registry.ModContent;
import java.util.List;
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

	/**
	 * The scenarios declared here rather than through {@link MachineScenarios} (ADR-038): the sawdust mode
	 * (MOD-655). Nested so reading the roster does not initialise this class.
	 */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SawmillScenarios::tcSaw001Fun06_sawdustModeLog, "sawmill_sawdust_mode_log_gives_six")
						.ticks(40),
				RosterEntry.of(SawmillScenarios::tcSaw001Fun07_sawdustModeBamboo,
								"sawmill_sawdust_mode_bamboo_block_gives_three")
						.ticks(40),
				RosterEntry.of(SawmillScenarios::tcSaw001Con04_savedModeOrdinalsLoadUnchanged,
								"sawmill_saved_mode_ordinals_load_unchanged")
						.ticks(40),
				RosterEntry.of(SawmillScenarios::tcSaw001Con05_netherStemGivesNoSawdust,
								"sawmill_sawdust_mode_nether_stem_gives_none")
						.ticks(40));

		private Roster() {}
	}

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
	 * TC-SAW-001-FUN06 (MOD-655): SAWDUST mode, oak log → 6 sawdust — one log burns 300 ticks, six sawdust 6 × 50,
	 * so sawing it first never beats burning it.
	 *
	 * @implements TC-SAW-001-FUN06 — SAWDUST mode: oak log (#minecraft:logs_that_burn) → exactly 6 sawdust.
	 */
	public static void tcSaw001Fun06_sawdustModeLog(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.SAWDUST, new ItemStack(Items.OAK_LOG, 4), ModContent.SAWDUST.get(), 6);
	}

	/**
	 * TC-SAW-001-FUN07 (MOD-655): SAWDUST mode, bamboo block → 3 sawdust — half a log, as in PLANKS mode.
	 *
	 * @implements TC-SAW-001-FUN07 — SAWDUST mode: bamboo block → exactly 3 sawdust (half of a log).
	 */
	public static void tcSaw001Fun07_sawdustModeBamboo(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.SAWDUST, new ItemStack(Items.BAMBOO_BLOCK, 4),
				ModContent.SAWDUST.get(), 3);
	}

	/**
	 * TC-SAW-001-CON05 (MOD-655): a crimson stem gives no sawdust. Nether stems do not burn, and sawdust is fuel, so
	 * sawing one would make fuel out of something that is not; the recipe takes {@code #minecraft:logs_that_burn},
	 * which leaves the stems out, rather than {@code #minecraft:logs}, which holds them.
	 *
	 * @implements TC-SAW-001-CON05 — SAWDUST mode: a crimson stem is not sawn; no output, the input untouched.
	 */
	public static void tcSaw001Con05_netherStemGivesNoSawdust(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, sawmill());
		((SawmillBlockEntity) be).setMode(SawmillMode.SAWDUST);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.CRIMSON_STEM, 4));
		drive(be, helper, Config.sawmillDuration);
		ItemStack out = be.getItem(1);
		if (!out.isEmpty()) {
			helper.fail("sawmill[SAWDUST] sawed a crimson stem into " + out.getCount() + "× " + out.getItem()
					+ " — Nether stems do not burn and must not become fuel");
		}
		if (be.getItem(0).getCount() != 4) {
			helper.fail("sawmill[SAWDUST] consumed a crimson stem: " + be.getItem(0).getCount() + " left of 4");
		}
		helper.succeed();
	}

	/**
	 * TC-SAW-001-CON04 (MOD-655): the saved {@code "Mode"} is the enum ordinal, so a mode added anywhere but last
	 * turns every saved sawmill into another mode. Each ordinal is written into a saved block entity and loaded into
	 * a fresh one; the four ordinals worlds saved before MOD-655 must load as the mode they were, and 4 as SAWDUST.
	 *
	 * @implements TC-SAW-001-CON04 — saved modes 0..3 load as PLANKS, STICKS, SLABS, STAIRS and 4 as SAWDUST.
	 */
	public static void tcSaw001Con04_savedModeOrdinalsLoadUnchanged(GameTestHelper helper) {
		// The order worlds were saved in; written out by name, not read from SawmillMode.values().
		List<String> saved = List.of("PLANKS", "STICKS", "SLABS", "STAIRS", "SAWDUST");
		MachineBlockEntity be = place(helper, sawmill());
		var registries = helper.getLevel().registryAccess();
		for (int ordinal = 0; ordinal < saved.size(); ordinal++) {
			CompoundTag tag = be.saveCustomOnly(registries);
			tag.putInt("Mode", ordinal);
			SawmillBlockEntity restored = new SawmillBlockEntity(be.getBlockPos(),
					helper.getLevel().getBlockState(be.getBlockPos()));
			restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
			if (!restored.getMode().name().equals(saved.get(ordinal))) {
				helper.fail("sawmill saved with Mode " + ordinal + " loaded as " + restored.getMode() + ", expected "
						+ saved.get(ordinal) + " — a mode was inserted before the end of SawmillMode");
			}
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
