package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.DistillationColumnBlock;
import dev.alaindustrial.block.DistillationColumnOutline;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.menu.DistillationColumnMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

/**
 * Loader-neutral gametest bodies for the Distillation Column (MOD-251, suite TC-DIST-001). Wrapped
 * by the Fabric {@code DistillationColumnGameTest} suite and registered on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}, {@code distillation_column_*}), so both
 * loaders run the SAME bodies — the mod's first 1×1×3 multiblock, first face-per-segment fluid
 * ports, first warm-up gate and first fluid-preserving drop.
 */
public final class DistillationColumnScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(DistillationColumnScenarios::fun01OilSplitsIntoFractions,
								"distillation_column_oil_splits_into_fractions")
						.fabricId("DistillationColumnGameTest", "tcDist001Fun01_oilSplitsIntoFractions").ticks(600),
				RosterEntry.of(DistillationColumnScenarios::fun02WarmupGatesDistillation,
								"distillation_column_warmup_gates_distillation")
						.fabricId("DistillationColumnGameTest", "tcDist001Fun02_warmupGatesDistillation").ticks(400),
				RosterEntry.of(DistillationColumnScenarios::con01FullDieselTankFreezes,
								"distillation_column_full_diesel_tank_freezes")
						.fabricId("DistillationColumnGameTest", "tcDist001Con01_fullDieselTankFreezes").ticks(600),
				RosterEntry.of(DistillationColumnScenarios::fun03DrainSlotBottlesDiesel,
								"distillation_column_drain_slot_bottles_diesel")
						.fabricId("DistillationColumnGameTest", "tcDist001Fun03_drainSlotBottlesDiesel").ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::seg01BreakMiddleDegradesTower,
								"distillation_column_break_middle_degrades_tower")
						.fabricId("DistillationColumnGameTest", "tcDist001Seg01_breakMiddleDegradesTower").ticks(100),
				RosterEntry.of(DistillationColumnScenarios::frm01ThreeBlanksFormTheTower,
								"distillation_column_three_blanks_form_tower")
						.fabricId("DistillationColumnGameTest", "tcDist001Frm01_threeBlanksFormTheTower")
						.ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::prt01SegmentPortsMatchLayout,
								"distillation_column_segment_ports_match_layout")
						.fabricId("DistillationColumnGameTest", "tcDist001Prt01_segmentPortsMatchLayout")
						.ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::prt02NullSidePortIsOilTank,
								"distillation_column_null_side_port_is_oil_tank")
						.fabricId("DistillationColumnGameTest", "tcDist001Prt02_nullSidePortIsOilTank").ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::sta01NbtRoundTripPreservesTanksAndHeat,
								"distillation_column_nbt_round_trip")
						.fabricId("DistillationColumnGameTest", "tcDist001Sta01_nbtRoundTripPreservesTanksAndHeat")
						.ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::crk01FuelOilCracksIntoDiesel,
								"distillation_column_fuel_oil_cracks_into_diesel")
						.fabricId("DistillationColumnGameTest", "tcDist001Crk01_fuelOilCracksIntoDiesel").ticks(700),
				RosterEntry.of(DistillationColumnScenarios::sec01SectionBoostsDiesel,
								"distillation_column_section_boosts_diesel")
						.fabricId("DistillationColumnGameTest", "tcDist001Sec01_sectionBoostsDiesel").ticks(600),
				RosterEntry.of(DistillationColumnScenarios::sec02SectionClickOpensColumnScreen,
								"distillation_column_section_click_opens_column_screen")
						.fabricId("DistillationColumnGameTest", "tcDist001Sec02_sectionClickOpensColumnScreen")
						.ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::sec03LoneSectionClickPasses,
								"distillation_column_lone_section_click_passes")
						.fabricId("DistillationColumnGameTest", "tcDist001Sec03_loneSectionClickPasses")
						.ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::sec04WrenchOnSectionCleansColumn,
								"distillation_column_wrench_on_section_cleans_column")
						.fabricId("DistillationColumnGameTest", "tcDist001Sec04_wrenchOnSectionCleansColumn")
						.ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::out01OutlineSpansTheWholeTower,
								"distillation_column_outline_spans_the_whole_tower")
						.fabricId("DistillationColumnGameTest", "tcDist001Out01_outlineSpansTheWholeTower")
						.ticks(20, 60),
				RosterEntry.of(DistillationColumnScenarios::fou01FouledStopsUntilCleaned,
								"distillation_column_fouled_stops_until_cleaned")
						.fabricId("DistillationColumnGameTest", "tcDist001Fou01_fouledStopsUntilCleaned").ticks(1000));

		private Roster() {}
	}

	private DistillationColumnScenarios() {
	}

	private static final BlockPos BASE = new BlockPos(1, 2, 1);
	/**
	 * "Fill the buffer" for a fixture. Larger than the column's own buffer on purpose and CLAMPED to
	 * it by {@code setAmountUntracked} (MOD-400) — before that the field was public and would accept a
	 * charge no machine can hold. Assertions therefore compare against the buffer's capacity, never
	 * against this number.
	 */
	private static final long AMPLE_EU = 8000L;

	/** One warm-up plus one full run plus slack for the scaled-duration knob. */
	private static int driveTicks() {
		return Config.distillationColumnWarmupTicks
				+ MachineRates.duration(Config.distillationColumnDuration, Config.globalMachineSpeedMultiplier) + 30;
	}

	/** Raise the full tower (placeTower — helper.setBlock never calls setPlacedBy, MOD-015). */
	private static DistillationColumnBlockEntity place(GameTestHelper helper) {
		DistillationColumnBlock.placeTower(helper.getLevel(), helper.absolutePos(BASE));
		DistillationColumnBlockEntity be =
				helper.getBlockEntity(BASE, DistillationColumnBlockEntity.class);
		if (be == null) {
			helper.fail("distillation column master block entity missing after placement");
		}
		return be;
	}

	private static void fillOil(DistillationColumnBlockEntity be, long mb) {
		be.oilTank.fluid = FluidHolder.of(ModContent.OIL.get());
		be.oilTank.amount = mb;
	}

	// ── FUN01: one bucket of crude becomes 700 diesel + 200 fuel oil, the oil tank ends empty ──────

	/**
	 * The signature split: 1000 mB oil + EU → 700 mB diesel (top tank) + 200 mB fuel oil (bottom).
	 *
	 * @implements TC-DIST-001-FUN01 — 1000 mB crude + EU → 700 mB diesel (top tank) + 200 mB fuel
	 * oil (bottom tank); the oil tank ends empty with its fluid identity cleared.
	 */
	public static void fun01OilSplitsIntoFractions(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fillOil(be, FluidAmounts.BUCKET);

		drive(be, helper, driveTicks());

		if (be.dieselTank.amount != 700 || !be.dieselTank.fluid.is(ModContent.DIESEL.get())) {
			helper.fail("expected 700 mB diesel in the top tank, got " + be.dieselTank.amount
					+ " mB of " + be.dieselTank.fluid);
			return;
		}
		if (be.fuelOilTank.amount != 200 || !be.fuelOilTank.fluid.is(ModContent.FUEL_OIL.get())) {
			helper.fail("expected 200 mB fuel oil in the bottom tank, got " + be.fuelOilTank.amount
					+ " mB of " + be.fuelOilTank.fluid);
			return;
		}
		if (be.oilTank.amount != 0 || !be.oilTank.fluid.isEmpty()) {
			helper.fail("expected the oil tank drained and cleared, got " + be.oilTank.amount
					+ " mB of " + be.oilTank.fluid);
			return;
		}
		helper.succeed();
	}

	// ── FUN02: the warm-up gates the first run — EU burns, no oil is consumed while heating ────────

	/**
	 * Half a warm-up in: progress still 0, oil untouched, but EU visibly spent (heating is not free).
	 *
	 * @implements TC-DIST-001-FUN02 — the warm-up gates the first run: EU burns while heating, but
	 * no oil is consumed and no progress accrues.
	 */
	public static void fun02WarmupGatesDistillation(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fillOil(be, FluidAmounts.BUCKET);

		drive(be, helper, Math.max(2, Config.distillationColumnWarmupTicks / 2));

		if (be.oilTank.amount != FluidAmounts.BUCKET) {
			helper.fail("warm-up must not consume oil, tank is " + be.oilTank.amount + " mB");
			return;
		}
		if (be.dieselTank.amount != 0 || be.fuelOilTank.amount != 0) {
			helper.fail("warm-up must not produce fractions yet");
			return;
		}
		if (be.getEnergyStorage().getAmount() >= be.getEnergyStorage().getCapacity()) {
			helper.fail("warm-up must draw EU, buffer still at " + be.getEnergyStorage().getAmount());
			return;
		}
		if (be.getDataAccess().get(2) != 0) {
			helper.fail("progress must stay 0 while heating, got " + be.getDataAccess().get(2));
			return;
		}
		helper.succeed();
	}

	// ── CON01: a full output tank freezes the run — no EU spent, no oil consumed, nothing destroyed ─

	/**
	 * With the diesel tank full the column freezes: oil and EU both stay exactly where they were.
	 *
	 * @implements TC-DIST-001-CON01 — a full diesel tank freezes the column: no EU spent, no oil
	 * consumed, and no half-run delivers the other fraction alone.
	 */
	public static void con01FullDieselTankFreezes(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fillOil(be, FluidAmounts.BUCKET);
		be.dieselTank.fluid = FluidHolder.of(ModContent.DIESEL.get());
		be.dieselTank.amount = DistillationColumnBlockEntity.TANK_CAPACITY;

		drive(be, helper, driveTicks());

		if (be.oilTank.amount != FluidAmounts.BUCKET) {
			helper.fail("a blocked output must not consume oil, tank is " + be.oilTank.amount + " mB");
			return;
		}
		if (be.getEnergyStorage().getAmount() != be.getEnergyStorage().getCapacity()) {
			helper.fail("a frozen column must not burn EU, buffer at " + be.getEnergyStorage().getAmount());
			return;
		}
		if (be.fuelOilTank.amount != 0) {
			helper.fail("no run may complete while ANY output tank is blocked (both fractions ship together)");
			return;
		}
		helper.succeed();
	}

	// ── FUN03: the drain pair bottles a fraction — empty bucket in, diesel bucket out ───────────────

	/**
	 * An empty bucket in the diesel drain slot leaves as a diesel bucket; the tank drops 1000 mB.
	 *
	 * @implements TC-DIST-001-FUN03 — an empty bucket in the diesel drain slot is bottled into a
	 * diesel bucket; the tank drops exactly one bucket's volume.
	 */
	public static void fun03DrainSlotBottlesDiesel(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		be.dieselTank.fluid = FluidHolder.of(ModContent.DIESEL.get());
		be.dieselTank.amount = 2 * FluidAmounts.BUCKET;
		be.setItem(DistillationColumnBlockEntity.DIESEL_DRAIN_INPUT_SLOT,
				new ItemStack(net.minecraft.world.item.Items.BUCKET));

		drive(be, helper, 2);

		if (!be.getItem(DistillationColumnBlockEntity.DIESEL_DRAIN_OUTPUT_SLOT)
				.is(ModContent.DIESEL_BUCKET.get())) {
			helper.fail("expected a diesel bucket in the drain-output slot, got "
					+ be.getItem(DistillationColumnBlockEntity.DIESEL_DRAIN_OUTPUT_SLOT));
			return;
		}
		if (be.dieselTank.amount != FluidAmounts.BUCKET) {
			helper.fail("bottling must take exactly one bucket, tank at " + be.dieselTank.amount + " mB");
			return;
		}
		helper.succeed();
	}

	// ── SEG01 (round 3): breaking a segment DEGRADES the tower — one segment drop, base keeps fluids ─

	/**
	 * Destroy the MIDDLE segment: one segment item drops, the base degrades to a blank with tanks intact.
	 *
	 * @implements TC-DIST-001-SEG01 — round 3: breaking the MIDDLE segment degrades the tower (one
	 * segment item drops, the base becomes a blank keeping its tanks, the top becomes a blank).
	 */
	public static void seg01BreakMiddleDegradesTower(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		fillOil(be, 3 * FluidAmounts.BUCKET);
		be.dieselTank.fluid = FluidHolder.of(ModContent.DIESEL.get());
		be.dieselTank.amount = 1400;

		ServerLevel level = helper.getLevel();
		BlockPos absBase = helper.absolutePos(BASE);
		level.destroyBlock(absBase.above(), true); // the middle segment, WITH its loot (a segment item)

		helper.runAfterDelay(5, () -> {
			// The base degrades to a blank in place — same block, formed=false, block entity intact.
			BlockState baseState = level.getBlockState(absBase);
			if (!(baseState.getBlock() instanceof dev.alaindustrial.block.DistillationColumnBlock)
					|| baseState.getValue(dev.alaindustrial.block.DistillationColumnBlock.FORMED)) {
				helper.fail("the base must degrade to an unformed blank, got " + baseState);
				return;
			}
			if (!(level.getBlockEntity(absBase) instanceof DistillationColumnBlockEntity degraded)
					|| degraded.oilTank.amount != 3 * FluidAmounts.BUCKET
					|| degraded.dieselTank.amount != 1400) {
				helper.fail("the degraded base must keep its tank contents");
				return;
			}
			// The top segment degrades into a lone blank too.
			BlockState topState = level.getBlockState(absBase.above(2));
			if (!(topState.getBlock() instanceof dev.alaindustrial.block.DistillationColumnBlock)
					|| topState.getValue(dev.alaindustrial.block.DistillationColumnBlock.FORMED)) {
				helper.fail("the top segment must degrade to a blank, got " + topState);
				return;
			}
			// Exactly one segment item dropped (the broken middle's loot).
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
					new AABB(absBase).inflate(2.5, 3.5, 2.5),
					e -> e.getItem().is(ModContent.DISTILLATION_COLUMN_ITEM.get()));
			long count = drops.stream().mapToLong(e -> e.getItem().getCount()).sum();
			if (count != 1) {
				helper.fail("expected exactly 1 segment drop, got " + count);
				return;
			}
			helper.succeed();
		});
	}

	// ── FRM01 (round 3): three hand-placed blanks self-assemble into the tower ──────────────────────

	/**
	 * Stack three blanks, drive assembly: base forms (master), the two above become segments.
	 *
	 * @implements TC-DIST-001-FRM01 — round 3: three hand-placed blanks self-assemble into the
	 * formed tower (base master + middle + top), scanning down from any of the three.
	 */
	public static void frm01ThreeBlanksFormTheTower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos absBase = helper.absolutePos(BASE);
		BlockState blank = ModContent.DISTILLATION_COLUMN.get().defaultBlockState();
		level.setBlockAndUpdate(absBase, blank);
		level.setBlockAndUpdate(absBase.above(), blank);
		level.setBlockAndUpdate(absBase.above(2), blank);
		// helper.setBlock never calls setPlacedBy (MOD-015) — drive the assembly hook directly,
		// from the TOP block, proving the scan walks down to the lowest blank.
		dev.alaindustrial.block.DistillationColumnBlock.tryFormTower(level, absBase.above(2));

		BlockState baseState = level.getBlockState(absBase);
		if (!baseState.getValue(dev.alaindustrial.block.DistillationColumnBlock.FORMED)) {
			helper.fail("the bottom blank must become the formed base, got " + baseState);
			return;
		}
		if (!(level.getBlockState(absBase.above()).getBlock()
				instanceof dev.alaindustrial.block.DistillationColumnMiddleBlock)) {
			helper.fail("the second blank must become the middle segment");
			return;
		}
		if (!(level.getBlockState(absBase.above(2)).getBlock()
				instanceof dev.alaindustrial.block.DistillationColumnTopBlock)) {
			helper.fail("the third blank must become the top segment");
			return;
		}
		if (!(level.getBlockEntity(absBase) instanceof DistillationColumnBlockEntity)) {
			helper.fail("the formed base must carry the master block entity");
			return;
		}
		helper.succeed();
	}

	// ── PRT01: the port layout is the tower — oil in the middle, diesel on top, fuel oil below ──────

	/**
	 * Per-segment ports via FluidLookup: middle accepts oil; top and base give the right fraction.
	 *
	 * @implements TC-DIST-001-PRT01 — the fixed port layout: the middle segment accepts crude into
	 * the base's intake, the top exposes diesel, the base exposes fuel oil.
	 */
	public static void prt01SegmentPortsMatchLayout(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		be.dieselTank.fluid = FluidHolder.of(ModContent.DIESEL.get());
		be.dieselTank.amount = FluidAmounts.BUCKET;
		be.fuelOilTank.fluid = FluidHolder.of(ModContent.FUEL_OIL.get());
		be.fuelOilTank.amount = FluidAmounts.BUCKET;

		ServerLevel level = helper.getLevel();
		BlockPos absBase = helper.absolutePos(BASE);

		FluidPort middle = FluidLookup.get().find(level, absBase.above(), Direction.NORTH);
		if (middle == null) {
			helper.fail("no fluid port on the middle segment — the proxy BE registration is missing");
			return;
		}
		long[] moved = {0};
		EnergyTransactions.get().runCommitting(txn ->
				moved[0] = middle.insert(FluidHolder.of(ModContent.OIL.get()), FluidAmounts.BUCKET, txn));
		if (moved[0] != FluidAmounts.BUCKET || be.oilTank.amount != FluidAmounts.BUCKET) {
			helper.fail("the middle segment must accept oil into the base's intake tank, moved " + moved[0]);
			return;
		}

		FluidPort top = FluidLookup.get().find(level, absBase.above(2), Direction.NORTH);
		if (top == null || !top.fluid().is(ModContent.DIESEL.get())) {
			helper.fail("the top segment must expose the diesel tank, got "
					+ (top == null ? "null" : top.fluid()));
			return;
		}
		FluidPort bottom = FluidLookup.get().find(level, absBase, Direction.NORTH);
		if (bottom == null || !bottom.fluid().is(ModContent.FUEL_OIL.get())) {
			helper.fail("the base must expose the fuel-oil tank, got "
					+ (bottom == null ? "null" : bottom.fluid()));
			return;
		}
		helper.succeed();
	}

	// ── PRT02: fluidPort(null) survives (Jade/WTHIT/foreign pipes probe without a direction) ───────

	/**
	 * The task's explicit null contract: master → the oil tank; no NPE anywhere.
	 *
	 * @implements TC-DIST-001-PRT02 — {@code fluidPort(null)} answers the oil tank, never NPEs.
	 */
	public static void prt02NullSidePortIsOilTank(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		FluidPort port = be.fluidPort(null);
		if (port != be.oilPort()) {
			helper.fail("fluidPort(null) must answer the oil tank (the column's identity fluid)");
			return;
		}
		helper.succeed();
	}

	// ── CRK01 (round 2): the column cracks its own residue — fuel oil in, diesel out ────────────────

	/**
	 * 1000 mB fuel oil in the intake + EU → 600 mB diesel; the intake ends empty.
	 *
	 * @implements TC-DIST-001-CRK01 — cracking (round 2): 1000 mB fuel oil in the intake + EU →
	 * 600 mB diesel, single result, intake drained.
	 */
	public static void crk01FuelOilCracksIntoDiesel(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.oilTank.fluid = FluidHolder.of(ModContent.FUEL_OIL.get());
		be.oilTank.amount = FluidAmounts.BUCKET;

		drive(be, helper, Config.distillationColumnWarmupTicks + 350);

		if (be.dieselTank.amount != 600 || !be.dieselTank.fluid.is(ModContent.DIESEL.get())) {
			helper.fail("expected 600 mB diesel from cracking, got " + be.dieselTank.amount
					+ " mB of " + be.dieselTank.fluid);
			return;
		}
		if (be.oilTank.amount != 0) {
			helper.fail("cracking must drain the intake, got " + be.oilTank.amount + " mB");
			return;
		}
		if (be.fuelOilTank.amount != 0) {
			helper.fail("cracking has a single result — the fuel-oil tank must stay empty");
			return;
		}
		helper.succeed();
	}

	// ── SEC01 (round 2): a Rectification Section on top adds +50 mB diesel per run ──────────────────

	/**
	 * With the 4th storey installed: 750 mB diesel + 200 mB fuel oil from one bucket of crude.
	 *
	 * @implements TC-DIST-001-SEC01 — a Rectification Section on the tower adds +50 mB diesel per
	 * run (losses 10 % → 5 %), fuel oil unchanged.
	 */
	public static void sec01SectionBoostsDiesel(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		helper.setBlock(BASE.above(3), ModContent.RECTIFICATION_SECTION.get());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fillOil(be, FluidAmounts.BUCKET);

		drive(be, helper, driveTicks());

		if (be.dieselTank.amount != 700 + DistillationColumnBlockEntity.SECTION_DIESEL_BONUS_MB) {
			helper.fail("expected 750 mB diesel with a Rectification Section, got " + be.dieselTank.amount);
			return;
		}
		if (be.fuelOilTank.amount != 200) {
			helper.fail("the section bonus is diesel-only; fuel oil must stay 200, got "
					+ be.fuelOilTank.amount);
			return;
		}
		helper.succeed();
	}

	// ── SEC02–SEC04 (MOD-778): the section is a storey of the tower — its click belongs to the tower ────

	/** The empty-hand click as vanilla delivers it: the block's state, the player, a hit on its east face. */
	private static InteractionResult click(GameTestHelper helper, BlockPos rel, ServerPlayer player) {
		BlockPos abs = helper.absolutePos(rel);
		BlockState state = helper.getLevel().getBlockState(abs);
		return state.useWithoutItem(helper.getLevel(), player,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.EAST, abs, false));
	}

	/**
	 * A click on the Rectification Section opens the column's screen, as a click on the middle or top does.
	 *
	 * @implements TC-DIST-001-SEC02 — empty-hand RMB on the section of an assembled tower returns
	 * {@code SUCCESS} and opens the base's {@code DistillationColumnMenu}.
	 */
	public static void sec02SectionClickOpensColumnScreen(GameTestHelper helper) {
		place(helper);
		helper.setBlock(BASE.above(3), ModContent.RECTIFICATION_SECTION.get());
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);

		InteractionResult result = click(helper, BASE.above(3), player);

		if (result != InteractionResult.SUCCESS) {
			helper.fail("a click on the section of an assembled tower must be SUCCESS, was " + result);
			return;
		}
		if (!(player.containerMenu instanceof DistillationColumnMenu)) {
			helper.fail("a click on the section must open the column's screen, the open menu is "
					+ player.containerMenu.getClass().getSimpleName());
			return;
		}
		helper.succeed();
	}

	/**
	 * A section with no tower under it opens nothing and passes the click on.
	 *
	 * @implements TC-DIST-001-SEC03 — empty-hand RMB on a section standing without a tower returns
	 * {@code PASS}.
	 */
	public static void sec03LoneSectionClickPasses(GameTestHelper helper) {
		helper.setBlock(BASE.above(3), ModContent.RECTIFICATION_SECTION.get());
		InteractionResult result = click(helper, BASE.above(3), AlaGameTestHelper.mockPlayerInLevel(helper));
		if (result != InteractionResult.PASS) {
			helper.fail("a click on a section with no tower under it must be PASS, was " + result);
			return;
		}
		helper.succeed();
	}

	/**
	 * A wrench on the section cleans the column, as it does on any segment.
	 *
	 * @implements TC-DIST-001-SEC04 — RMB with a wrench on the section of a fouled tower returns
	 * {@code SUCCESS} and resets the base's fouling to 0.
	 */
	public static void sec04WrenchOnSectionCleansColumn(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		helper.setBlock(BASE.above(3), ModContent.RECTIFICATION_SECTION.get());
		ServerLevel level = helper.getLevel();
		CompoundTag tag = be.saveCustomOnly(level.registryAccess());
		tag.putInt("Fouling", DistillationColumnBlockEntity.FOULING_MAX);
		be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		BlockPos section = helper.absolutePos(BASE.above(3));

		InteractionResult result = level.getBlockState(section).useItemOn(new ItemStack(ModContent.WRENCH.get()),
				level, player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(section), Direction.EAST, section, false));

		if (result != InteractionResult.SUCCESS) {
			helper.fail("a wrench on the section of a fouled tower must be SUCCESS, was " + result);
			return;
		}
		if (be.getFouling() != 0) {
			helper.fail("a wrench on the section must clean the column, fouling is " + be.getFouling());
			return;
		}
		helper.succeed();
	}

	// ── OUT01 (MOD-778): every storey of an assembled tower outlines the whole tower ─────────────────

	/**
	 * The hover outline's shape: from each storey, the contour reaches from the base's floor to the top of
	 * the highest storey; a lone blank and a section without a tower keep their own outline.
	 *
	 * @implements TC-DIST-001-OUT01 — the outline of any storey of an assembled tower spans the whole
	 * tower (four storeys with the section, three without); a lone blank or section gets none.
	 */
	public static void out01OutlineSpansTheWholeTower(GameTestHelper helper) {
		place(helper);
		helper.setBlock(BASE.above(3), ModContent.RECTIFICATION_SECTION.get());
		for (int storey = 0; storey < 4; storey++) {
			if (!spans(helper, BASE.above(storey), -storey, 4 - storey)) {
				return;
			}
		}
		helper.setBlock(BASE.above(3), Blocks.AIR);
		for (int storey = 0; storey < 3; storey++) {
			if (!spans(helper, BASE.above(storey), -storey, 3 - storey)) {
				return;
			}
		}
		BlockPos blank = new BlockPos(3, 2, 1);
		helper.setBlock(blank, ModContent.DISTILLATION_COLUMN.get());
		BlockPos loneSection = new BlockPos(3, 2, 3);
		helper.setBlock(loneSection, ModContent.RECTIFICATION_SECTION.get());
		for (BlockPos lone : List.of(blank, loneSection)) {
			BlockPos abs = helper.absolutePos(lone);
			BlockState state = helper.getLevel().getBlockState(abs);
			if (DistillationColumnOutline.shape(helper.getLevel(), abs, state) != null) {
				helper.fail("a lone " + state.getBlock() + " must keep its own outline");
				return;
			}
		}
		helper.succeed();
	}

	/** Whether the outline at {@code rel} reaches from {@code minY} to {@code maxY} (blocks, relative). */
	private static boolean spans(GameTestHelper helper, BlockPos rel, double minY, double maxY) {
		BlockPos abs = helper.absolutePos(rel);
		BlockState state = helper.getLevel().getBlockState(abs);
		VoxelShape shape = DistillationColumnOutline.shape(helper.getLevel(), abs, state);
		if (shape == null) {
			helper.fail(state.getBlock() + " of an assembled tower has no tower outline");
			return false;
		}
		AABB box = shape.bounds();
		if (Math.abs(box.minY - minY) > 1e-6 || Math.abs(box.maxY - maxY) > 1e-6) {
			helper.fail(state.getBlock() + ": outline spans y " + box.minY + ".." + box.maxY + ", expected "
					+ minY + ".." + maxY);
			return false;
		}
		return true;
	}

	// ── FOU01 (round 2): coked up at 100 % — the column stops; a wrench cleaning restarts it ────────

	/**
	 * At max fouling nothing runs (no EU, no oil consumed); cleanFouling() yields coal and unblocks.
	 *
	 * @implements TC-DIST-001-FOU01 — at 100 % fouling the column freezes without consuming
	 * anything; a wrench cleaning yields 1..3 coal, resets fouling and distilling resumes.
	 */
	public static void fou01FouledStopsUntilCleaned(GameTestHelper helper) {
		DistillationColumnBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fillOil(be, FluidAmounts.BUCKET);
		// Coke it up via the same NBT path the save uses (fouling has no public setter by design).
		ServerLevel level = helper.getLevel();
		CompoundTag tag = be.saveCustomOnly(level.registryAccess());
		tag.putInt("Fouling", DistillationColumnBlockEntity.FOULING_MAX);
		be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));

		drive(be, helper, driveTicks());

		if (be.oilTank.amount != FluidAmounts.BUCKET
				|| be.getEnergyStorage().getAmount() != be.getEnergyStorage().getCapacity()) {
			helper.fail("a fouled column must freeze: oil " + be.oilTank.amount + " mB, EU "
					+ be.getEnergyStorage().getAmount());
			return;
		}
		int coal = be.cleanFouling();
		if (coal < 1 || coal > 3) {
			helper.fail("cleaning a fully fouled column must yield 1..3 coal, got " + coal);
			return;
		}
		if (be.getFouling() != 0) {
			helper.fail("cleaning must reset fouling, got " + be.getFouling());
			return;
		}
		drive(be, helper, driveTicks());
		if (be.dieselTank.amount == 0) {
			helper.fail("a cleaned column must resume distilling");
			return;
		}
		helper.succeed();
	}

	// ── STA01: three tanks + heat survive an NBT round-trip ─────────────────────────────────────────

	/**
	 * All three tanks (amount + fluid identity) and the warm-up state survive save/load.
	 *
	 * @implements TC-DIST-001-STA01 — all three tanks (amount + fluid identity) and the warm-up heat
	 * survive an NBT round-trip.
	 */
	public static void sta01NbtRoundTripPreservesTanksAndHeat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(BASE);
		DistillationColumnBlockEntity src = place(helper);

		src.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fillOil(src, 2 * FluidAmounts.BUCKET);
		src.dieselTank.fluid = FluidHolder.of(ModContent.DIESEL.get());
		src.dieselTank.amount = 700;
		src.fuelOilTank.fluid = FluidHolder.of(ModContent.FUEL_OIL.get());
		src.fuelOilTank.amount = 200;
		// Bake some heat in by running the powered, fed column for a few ticks.
		drive(src, helper, 10);

		CompoundTag tag = src.saveCustomOnly(registries);
		DistillationColumnBlockEntity restored =
				new DistillationColumnBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.oilTank.amount != 2 * FluidAmounts.BUCKET
				|| !restored.oilTank.fluid.is(ModContent.OIL.get())) {
			helper.fail("oil tank round-trip mismatch: " + restored.oilTank.amount);
			return;
		}
		if (restored.dieselTank.amount != 700 || !restored.dieselTank.fluid.is(ModContent.DIESEL.get())) {
			helper.fail("diesel tank round-trip mismatch: " + restored.dieselTank.amount);
			return;
		}
		if (restored.fuelOilTank.amount != 200
				|| !restored.fuelOilTank.fluid.is(ModContent.FUEL_OIL.get())) {
			helper.fail("fuel-oil tank round-trip mismatch: " + restored.fuelOilTank.amount);
			return;
		}
		CompoundTag reTag = restored.saveCustomOnly(registries);
		if (reTag.getIntOr("Heat", 0) <= 0) {
			helper.fail("warm-up heat must survive the round-trip, got " + reTag.getIntOr("Heat", 0));
			return;
		}
		helper.succeed();
	}
}
