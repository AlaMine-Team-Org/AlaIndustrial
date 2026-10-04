package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.MachineRig.AMPLE_EU;
import static dev.alaindustrial.gametest.MachineRig.DRIVE_TICKS;
import static dev.alaindustrial.gametest.MachineRig.compressor;
import static dev.alaindustrial.gametest.MachineRig.extractor;
import static dev.alaindustrial.gametest.MachineRig.furnace;
import static dev.alaindustrial.gametest.MachineRig.macerator;
import static dev.alaindustrial.gametest.MachineRig.place;
import static dev.alaindustrial.gametest.MachineRig.processing;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AbstractProcessingMachineBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * World scenarios for a processing machine's operation cycle (MOD-446): the exact E_op and E_op−1
 * boundaries, the lit state, the status channel that says why a machine is stalled (MOD-458), and coasting
 * across a power gap with the Resilient Cycle skill (MOD-576).

 * <p>Split by mechanic in MOD-717 (TST-2, the {@code ReactorScenarios} pattern): the lanes run the names in
 * {@link MachineScenarios}, which delegates here; a new processing-machine scenario is written in the class of
 * its mechanic and declared in that class's roster.
 */
public final class MachineOperationScenarios {

	private MachineOperationScenarios() {
	}

	/**
	 * TC-MACH-001-PRF: the data-driven maceration recipe for an iron ore block yields ×2 and its EU
	 * cost equals the shared E_op (machineEuPerTick × maceratorDuration), keeping the JSON recipe and
	 * {@link Config} in sync. Ported from {@code IndustrializationSelfTest} MACERATOR_MULTIPLIER.
	 *
	 * @implements TC-MACH-001-PRF — the data-driven maceration recipe for an iron ore block yields ×2
	 *     and its EU cost equals the shared E_op (machineEuPerTick × maceratorDuration), keeping the JSON
	 *     recipe and {@link dev.alaindustrial.Config} in sync. Ported from
	 *     {@code IndustrializationSelfTest} MACERATOR_MULTIPLIER. @covers R-NRG-04 (E_op)
	 */
	public static void tcMach001Prf_maceratorEopMatchesConfig(GameTestHelper helper) {
		// Looked up through the vanilla RecipeManager (R-14); iron_ore resolves via the
		// #alaindustrial:macerable_iron tag (R-15), proving tag ingredients match. Ore blocks and
		// raw_iron both macerate to ×2 dust (MOD-095, Mekanism/IC2 model); only the ingot path is ×1.
		ProcessingRecipeInput input = new ProcessingRecipeInput(new ItemStack(Items.IRON_ORE));
		AlaProcessingRecipe ironRecipe = ModRecipes.MACERATION.newCheck()
				.getRecipeFor(input, helper.getLevel()).map(RecipeHolder::value).orElse(null);
		if (ironRecipe == null) {
			helper.fail("no maceration recipe for iron_ore (datapack not loaded?)");
			return;
		}
		int count = ironRecipe.assemble(input).getCount();
		if (count != 2) {
			helper.fail("iron_ore maceration count expected 2 but got " + count);
		}
		int eOp = Config.machineEuPerTick * Config.maceratorDuration;
		if (ironRecipe.energy() / Config.machineEuPerTick != Config.maceratorDuration) {
			helper.fail("raw_iron maceration E_op mismatch: energy=" + ironRecipe.energy()
					+ " but machineEuPerTick(" + Config.machineEuPerTick + ")×maceratorDuration("
					+ Config.maceratorDuration + ")=" + eOp);
		}
		helper.succeed();
	}

	/**
	 * Coasting finishes an operation ONLY when the supply is what went missing (MOD-576).
	 *
	 * <p>The Resilient Cycle skill is meant to carry a started operation across a power gap. Until
	 * MOD-145 it carried it across ANYTHING: {@code canWork} is the conjunction of every condition a
	 * machine has — recipe, input, room for the result, energy — and the coasting branch simply
	 * ignored the lot. Since {@code addOutput} deliberately does not re-check its precondition
	 * ("the caller already asked {@code canOutput}"), finishing into a full slot grew the stack past
	 * its limit: the recycler's ash bin reached 66 of a maximum 64.
	 *
	 * <p><b>Why no test caught it.</b> A machine placed in a rig has {@code owner == null}, so
	 * {@code SkillMachine.has} answers false and the coasting branch never executed — not once, in
	 * any scenario. The fix shipped in 0.1.148 with no test able to reach the line it changed. This
	 * scenario is that rig: a survival player who is IN the player list (creative is excluded by
	 * {@code OwnerPresence.eligible}, and {@code hasInfiniteMaterials} reads
	 * {@code abilities.instabuild}), the skill granted, and the machine's owner set to them.
	 *
	 * <p><b>Both halves are asserted together on purpose.</b> Half A alone would pass if coasting
	 * were deleted outright, and deleting it would silently remove a skill players paid a fragment
	 * for. Half B alone would pass on the old, broken behaviour. Only the pair pins the rule.
	 *
	 * @implements R-MACH-30 — see docs/testing/RULES.md
	 */
	public static void mod576CoastingFinishesOnlySupplyGaps(GameTestHelper helper) {
		// ── A: the output is full — the operation must NOT finish, whatever the skill says ──
		MachineBlockEntity jammed = ownedMacerator(helper);
		jammed.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		jammed.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		driveUntilPastCoastThreshold(jammed, helper);
		jammed.setItem(1, new ItemStack(ModContent.IRON_DUST.get(), 64));
		drive(jammed, helper, DRIVE_TICKS);
		int jammedCount = jammed.getItem(1).getCount();
		if (jammedCount != 64) {
			helper.fail("coasting finished into a full slot: " + jammedCount
					+ " items in a stack of 64 — addOutput does not re-check, so this is an overflow");
		}

		// ── B: only the supply is missing — the operation MUST finish on the machine's own charge ──
		MachineBlockEntity starved = ownedMacerator(helper);
		starved.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		starved.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		driveUntilPastCoastThreshold(starved, helper);
		// A positive charge that cannot pay a tick: coasting requires amount > 0, and canWork
		// requires amount >= euPerTick. One EU sits between the two on purpose.
		starved.getEnergyStorage().setAmountUntracked(1);
		drive(starved, helper, DRIVE_TICKS);
		if (starved.getItem(1).isEmpty()) {
			helper.fail("the skill did not carry the operation across a power gap — coasting is dead, "
					+ "and a fragment was spent on nothing");
		}
		helper.succeed();
	}

	/** A macerator owned by a survival player in the player list who has bought MECH/CAP. */
	private static MachineBlockEntity ownedMacerator(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		SkillStore.set(owner, new PlayerSkills(SkillBuild.EMPTY.with(SkillBranch.MECH, SkillSlot.CAP)));
		MachineBlockEntity be = place(helper, macerator());
		be.setOwner(owner.getUUID(), owner.getGameProfile().name());
		return be;
	}

	/**
	 * Ticks until progress passes the skill's threshold, then stops.
	 *
	 * <p>Driven by the machine's own numbers rather than a fixed tick count: the threshold is a
	 * config percentage of a duration that recipes and overclockers both move, and a hardcoded
	 * "drive 40 ticks" would quietly stop covering the branch the day either changed.
	 */
	private static void driveUntilPastCoastThreshold(MachineBlockEntity be, GameTestHelper helper) {
		for (int i = 0; i < DRIVE_TICKS; i++) {
			drive(be, helper, 1);
			int progress = be.getDataAccess().get(2);
			int duration = be.getDataAccess().get(3);
			if (duration > 0 && progress * 100 >= duration * Config.skillResilientFromPercent
					&& progress < duration) {
				return;
			}
		}
		helper.fail("never reached the coasting threshold — the rig cannot test what it claims to");
	}

	// ── Status channel (MOD-458): the machine says WHY it is stalled ───────────────────────────────

	/**
	 * Assert BOTH halves of the readout every time. {@code status()} is the server's own verdict; the
	 * {@code STATUS} channel is what a screen actually reads — and the two can part company, because
	 * a subclass that appends channels of its own supplies its own bridge (the Sawmill does, for its mode).
	 * Checking only the field would leave that bridge untested and the caption blank in game.
	 */
	private static void assertStatus(GameTestHelper helper, AbstractProcessingMachineBlockEntity be,
			ProcessingMachineStatus expected, String what) {
		if (be.status() != expected) {
			helper.fail(what + ": expected " + expected + " but the machine reports " + be.status());
		}
		int wire = be.getDataAccess().get(AbstractProcessingMachineBlockEntity.Channel.STATUS.ordinal());
		if (wire != expected.ordinal()) {
			helper.fail(what + ": readout channel carries ordinal " + wire + " ("
					+ ProcessingMachineStatus.byOrdinal(wire) + ") instead of " + expected);
		}
	}

	/**
	 * TC-COMP-001-GUI06: a partial batch names itself — and stops the moment it is topped up.
	 *
	 * @implements TC-COMP-001-GUI06 — a partial batch (3 of 4 dust, 1 of 9 redstone) reports
	 *     NOT_ENOUGH_INPUT on the readout channel, and clears to READY the moment it is topped up.
	 * @covers R-GUI-03
	 */
	public static void tcComp001Gui06_compressorReportsPartialBatch(GameTestHelper helper) {
		AbstractProcessingMachineBlockEntity be = processing(helper, compressor());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);

		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NO_INPUT, "an empty compressor");

		be.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 3));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NOT_ENOUGH_INPUT, "3 of a 4-dust batch");

		// The redstone leftover: 64 / 9 parks exactly one item in the slot after every full stack, which
		// without this caption is the single most jam-looking state the compressor can reach.
		be.setItem(0, new ItemStack(Items.REDSTONE, 1));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NOT_ENOUGH_INPUT, "1 of a 9-redstone batch");

		be.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 4));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.READY, "a full 4-dust batch");
		helper.succeed();
	}

	/**
	 * TC-COMP-001-GUI07: the two stalls that are not about the batch — a wrong item and a jammed output.
	 *
	 * @implements TC-COMP-001-GUI07 — an item with no compressing recipe reports NO_RECIPE, and a full
	 *     output slot reports OUTPUT_BLOCKED, on both the field and the synced channel.
	 * @covers R-GUI-03
	 */
	public static void tcComp001Gui07_compressorReportsWrongItemAndJammedOutput(GameTestHelper helper) {
		AbstractProcessingMachineBlockEntity be = processing(helper, compressor());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);

		// setItem bypasses canPlaceItem deliberately: a datapack reload or a /setblock can leave an item in
		// the slot that no longer resolves, and that is exactly the state worth captioning.
		be.setItem(0, new ItemStack(Items.DIAMOND, 1));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NO_RECIPE, "an item the compressor cannot press");

		be.setItem(0, new ItemStack(Items.CLAY_BALL, 8));
		be.setItem(1, new ItemStack(Items.BRICK, 64));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.OUTPUT_BLOCKED, "a full output slot");
		helper.succeed();
	}

	/**
	 * TC-COMP-001-GUI08: an empty buffer is captioned only once it means something.
	 *
	 * <p>The second half is the regression this case exists for. A machine fed just under its draw works
	 * every other tick; a bare {@code buffer < cost} test would strobe "No energy" at 10 Hz while the arrow
	 * visibly advances. One LV solar panel against one LV machine is exactly that setup, so this is an
	 * ordinary early-game base, not a contrived one.
	 *
	 * @implements TC-COMP-001-GUI08 — a machine with input and no power reports NO_ENERGY, while one
	 *     trickling just under its draw never does, because it is still making progress.
	 * @covers R-GUI-03
	 */
	public static void tcComp001Gui08_compressorReportsStarvationButNotTrickle(GameTestHelper helper) {
		AbstractProcessingMachineBlockEntity be = processing(helper, compressor());
		be.setItem(0, new ItemStack(Items.CLAY_BALL, 8));
		be.getEnergyStorage().setAmountUntracked(0);
		// Two unpaid evaluations, and an idle machine sleeps 40 ticks between them (R-29).
		drive(be, helper, 90);
		assertStatus(helper, be, ProcessingMachineStatus.NO_ENERGY, "input but no power at all");

		int cost = Config.machineEuPerTick;
		int supply = cost - 1;
		BlockPos pos = be.getBlockPos();
		boolean hasWorked = false;
		int lastProgress = be.getDataAccess().get(2);
		for (int tick = 0; tick < 200; tick++) {
			be.getEnergyStorage().setAmountUntracked(be.getEnergyStorage().getAmount() + supply);
			be.wake(); // an arriving packet wakes the machine, the way the buffer's commit hook does in world
			be.serverTick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos));
			int progress = be.getDataAccess().get(2);
			hasWorked |= progress > lastProgress;
			lastProgress = progress;
			// Assertions start once the machine has visibly worked. The invariant being pinned is about the
			// STEADY state of a trickle-fed machine; the one transition tick out of a dead buffer is not part
			// of it, and there "no energy" is simply true — the machine has not managed a paid tick in ninety.
			if (hasWorked && be.status() == ProcessingMachineStatus.NO_ENERGY) {
				helper.fail("trickling at " + supply + " EU/t against " + cost + " EU/t reported NO_ENERGY on"
						+ " tick " + tick + ", after the machine had already resumed making progress");
			}
		}
		// Without this the loop above is vacuous: a machine that never worked also never says NO_ENERGY.
		if (!hasWorked) {
			helper.fail("the trickle half advanced no progress at all — it proves nothing");
		}
		helper.succeed();
	}

	// ── STA: lit blockstate tracks active/idle, no light emission (parametric) ─────────────────────

	/**
	 * Positive/negative pair: the block's {@code lit} property switches on while an operation is
	 * progressing (powered + valid input) and switches back off once the machine has no work left
	 * (input exhausted). Mirrors {@code GeneratorGameTest#tcGen001Sta01_litStateTracksBurning} but for
	 * a processing machine's EU-driven progress instead of a burning generator.
	 */
	private static void assertLitTracksActive(GameTestHelper helper, Block block, ItemStack singleInput) {
		MachineBlockEntity be = place(helper, block);
		BlockPos abs = be.getBlockPos();
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, singleInput.copy());
		drive(be, helper, 3);
		if (!helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail(block + ": must be LIT while actively processing");
		}
		// Drain the input so the machine has nothing left to process; give it a tick to notice and
		// clear LIT via updateLit(false).
		be.setItem(0, ItemStack.EMPTY);
		drive(be, helper, 3);
		if (helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail(block + ": must not stay LIT once there is no input left to process");
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-STA01: macerator lit tracks active/idle, no light emission.
	 *
	 * @implements TC-MACH-001-STA01 — macerator lit tracks active/idle, no light emission. @covers R-VIS-01
	 */
	public static void tcMach001Sta01_maceratorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, macerator(), new ItemStack(Items.RAW_IRON, 1));
	}

	/**
	 * TC-MACH-002-STA01: electric furnace lit tracks active/idle, no light emission.
	 *
	 * @implements TC-MACH-002-STA01 — electric furnace lit tracks active/idle, no light emission. @covers R-VIS-01
	 */
	public static void tcMach002Sta01_furnaceLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1));
	}

	/**
	 * TC-COMP-001-STA01 / TC-MACH-003-STA01: compressor lit tracks active/idle, no light emission.
	 *
	 * @implements TC-COMP-001-STA01 / TC-MACH-003-STA01 — compressor lit tracks active/idle, no light emission. @covers
	 *     R-VIS-01
	 */
	public static void tcMach003Sta01_compressorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, compressor(), new ItemStack(Items.CLAY_BALL, 1));
	}

	/**
	 * TC-MACH-004-STA01: extractor lit tracks active/idle, no light emission.
	 *
	 * @implements TC-MACH-004-STA01 — extractor lit tracks active/idle, no light emission. @covers R-VIS-01
	 */
	public static void tcMach004Sta01_extractorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1));
	}

	// ── PRF: E_op exact & E_op−1 (BVA), parametric across all 4 machines ────────────────────────────

	/** BVA: exactly E_op available → operation completes and EU is fully spent (amount==0). */
	private static void assertEopExactCompletes(GameTestHelper helper, Block block, ItemStack input, int durationTicks,
			int euPerTick, Item expectedOutput) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked((long) durationTicks * euPerTick);
		be.setItem(0, input);
		drive(be, helper, durationTicks);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(expectedOutput)) {
			helper.fail(block + ": E_op exact (" + (durationTicks * euPerTick) + " EU) did not complete the operation");
		}
		if (be.getEnergyStorage().getAmount() != 0) {
			helper.fail(block + ": E_op exact should leave amount==0 but got " + be.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/** BVA: E_op−1 available → operation never completes; progress freezes one tick short. */
	private static void assertEopMinusOneStalls(GameTestHelper helper, Block block, ItemStack input, int durationTicks,
			int euPerTick) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked((long) durationTicks * euPerTick - 1);
		be.setItem(0, input);
		drive(be, helper, DRIVE_TICKS);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": E_op−1 (one EU short) must not produce any output");
		}
		int progress = be.getDataAccess().get(2);
		if (progress != durationTicks - 1) {
			helper.fail(block + ": E_op−1 progress expected " + (durationTicks - 1) + " but got " + progress);
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-PRF04: macerator, E_op=300 exactly → output + amount==0 (BVA).
	 *
	 * @implements TC-MACH-001-PRF04 — macerator: E_op=300 exactly → output + amount==0 (BVA). @covers R-NRG-04
	 */
	public static void tcMach001Prf04_maceratorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				Config.maceratorDuration, Config.machineEuPerTick, ModContent.IRON_DUST.get());
	}

	/**
	 * TC-MACH-001-PRF03: macerator, E_op−1=299 → no output, progress=149/150 (BVA).
	 *
	 * @implements TC-MACH-001-PRF03 — macerator: E_op−1=299 → no output, progress=149/150 (BVA). @covers R-NRG-04
	 */
	public static void tcMach001Prf03_maceratorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				Config.maceratorDuration, Config.machineEuPerTick);
	}

	/**
	 * TC-EFURN-001-PRF01: electric furnace, E_op=200 exactly → output + amount==0 (BVA).
	 *
	 * @implements TC-EFURN-001-PRF01 — electric furnace: E_op=200 exactly → output + amount==0 (BVA). @covers R-NRG-04
	 */
	public static void tcEfurn001Prf01_furnaceEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.electricFurnaceDuration, Config.machineEuPerTick, Items.IRON_INGOT);
	}

	/**
	 * TC-EFURN-001-PRF02: electric furnace, E_op−1=199 → no output, progress=99/100 (BVA).
	 *
	 * @implements TC-EFURN-001-PRF02 — electric furnace: E_op−1=199 → no output, progress=99/100 (BVA). @covers
	 *     R-NRG-04
	 */
	public static void tcEfurn001Prf02_furnaceEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.electricFurnaceDuration, Config.machineEuPerTick);
	}

	/**
	 * TC-COMP-001-PRF01: compressor, E_op=260 exactly → output + amount==0 (BVA).
	 *
	 * @implements TC-COMP-001-PRF01 — compressor: E_op=260 exactly → output + amount==0 (BVA). @covers R-NRG-04
	 */
	public static void tcComp001Prf01_compressorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, compressor(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.compressorDuration, Config.machineEuPerTick, Items.IRON_INGOT);
	}

	/**
	 * TC-COMP-001-PRF02: compressor, E_op−1=259 → no output, progress=129/130 (BVA).
	 *
	 * @implements TC-COMP-001-PRF02 — compressor: E_op−1=259 → no output, progress=129/130 (BVA). @covers R-NRG-04
	 */
	public static void tcComp001Prf02_compressorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, compressor(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.compressorDuration, Config.machineEuPerTick);
	}

	/**
	 * TC-EXTR-001-PRF01: extractor, E_op=240 exactly → output + amount==0 (BVA).
	 *
	 * @implements TC-EXTR-001-PRF01 — extractor: E_op=240 exactly → output + amount==0 (BVA). @covers R-NRG-04
	 */
	public static void tcExtr001Prf01_extractorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1),
				Config.extractorDuration, Config.machineEuPerTick, Items.BLAZE_POWDER);
	}

	/**
	 * TC-EXTR-001-PRF02: extractor, E_op−1=239 → no output, progress=119/120 (BVA).
	 *
	 * @implements TC-EXTR-001-PRF02 — extractor: E_op−1=239 → no output, progress=119/120 (BVA). @covers R-NRG-04
	 */
	public static void tcExtr001Prf02_extractorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1),
				Config.extractorDuration, Config.machineEuPerTick);
	}
}
