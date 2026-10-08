package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.DistillationColumnBlock;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

/**
 * What one operation costs on the four machines that run their own cycle instead of
 * {@code ProcessingCycle} — the assembler, the incubator, the distillation column and the thermal
 * centrifuge (MOD-712, owner decision D4).
 *
 * <p>D4 extends the Mechanic skills Precise Draw and Resilient Cycle to these four machines, which is a
 * balance change for every player who owns them. The scenarios here pin the price of one operation
 * for a player who does NOT own them, so the change cannot shift the base balance on the way: the
 * expected numbers are the MOD-712 snapshot table in {@code docs/PERFORMANCE.md} (the skill-tree
 * section), rebuilt from the same knobs.
 *
 * <p><b>How a tick is attributed.</b> The buffer is topped up before every tick and the drop is read
 * after it, so nothing a tick spends can hide behind a later delivery. A tick that moves the
 * progress bar (or completes the operation) is an OPERATION tick; every other paid tick — the
 * column's warm-up, the centrifuge's spin-up — is a PRE-STAGE tick, and its cost is asserted
 * separately, because the skills promise a cheaper operation, not a cheaper preparation.
 *
 * <p><b>The owner is real.</b> Each machine belongs to a survival player who is in the player list,
 * the only kind of owner the skills ever read (a machine with no owner, or an offline one, counts
 * base numbers). Here that owner holds no skill at all, which is the case these scenarios fix.
 */
public final class OperationEnergyScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(OperationEnergyScenarios::assemblerOperationCostsTheSnapshotWithoutSkills,
								"operation_energy_assembler_snapshot_without_skills")
						.fabricId("OperationEnergyGameTest", "assemblerOperationCostsTheSnapshotWithoutSkills")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::incubatorOperationCostsTheSnapshotWithoutSkills,
								"operation_energy_incubator_snapshot_without_skills")
						.fabricId("OperationEnergyGameTest", "incubatorOperationCostsTheSnapshotWithoutSkills")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::distillationColumnOperationCostsTheSnapshotWithoutSkills,
								"operation_energy_distillation_column_snapshot_without_skills")
						.fabricId("OperationEnergyGameTest",
								"distillationColumnOperationCostsTheSnapshotWithoutSkills")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::thermalCentrifugeOperationCostsTheSnapshotWithoutSkills,
								"operation_energy_thermal_centrifuge_snapshot_without_skills")
						.fabricId("OperationEnergyGameTest",
								"thermalCentrifugeOperationCostsTheSnapshotWithoutSkills")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::assemblerPreciseDrawSkipsEveryTenthTick,
								"operation_energy_assembler_precise_draw")
						.fabricId("OperationEnergyGameTest", "assemblerPreciseDrawSkipsEveryTenthTick").ticks(100),
				RosterEntry.of(OperationEnergyScenarios::incubatorPreciseDrawSkipsEveryTenthTick,
								"operation_energy_incubator_precise_draw")
						.fabricId("OperationEnergyGameTest", "incubatorPreciseDrawSkipsEveryTenthTick").ticks(100),
				RosterEntry.of(OperationEnergyScenarios::distillationColumnPreciseDrawSkipsEveryTenthTick,
								"operation_energy_distillation_column_precise_draw")
						.fabricId("OperationEnergyGameTest", "distillationColumnPreciseDrawSkipsEveryTenthTick")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::thermalCentrifugePreciseDrawSkipsEveryTenthTick,
								"operation_energy_thermal_centrifuge_precise_draw")
						.fabricId("OperationEnergyGameTest", "thermalCentrifugePreciseDrawSkipsEveryTenthTick")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::assemblerResilientCycleFinishesOnATrickle,
								"operation_energy_assembler_resilient_cycle")
						.fabricId("OperationEnergyGameTest", "assemblerResilientCycleFinishesOnATrickle").ticks(100),
				RosterEntry.of(OperationEnergyScenarios::incubatorResilientCycleFinishesOnATrickle,
								"operation_energy_incubator_resilient_cycle")
						.fabricId("OperationEnergyGameTest", "incubatorResilientCycleFinishesOnATrickle").ticks(100),
				RosterEntry.of(OperationEnergyScenarios::distillationColumnResilientCycleFinishesOnATrickle,
								"operation_energy_distillation_column_resilient_cycle")
						.fabricId("OperationEnergyGameTest", "distillationColumnResilientCycleFinishesOnATrickle")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::thermalCentrifugeResilientCycleFinishesOnATrickle,
								"operation_energy_thermal_centrifuge_resilient_cycle")
						.fabricId("OperationEnergyGameTest", "thermalCentrifugeResilientCycleFinishesOnATrickle")
						.ticks(100),
				RosterEntry.of(OperationEnergyScenarios::thermalCentrifugeResilientCycleTickPaysTheHeater,
								"mod751_thermal_centrifuge_coasting_tick_pays_the_heater").ticks(100));

		private Roster() {}
	}

	private OperationEnergyScenarios() {
	}

	/** Where the rig under test stands. */
	private static final BlockPos RIG = new BlockPos(1, 2, 1);

	/** Ticks a measurement may take beyond the expected pre-stage and operation before it gives up. */
	private static final int SLACK_TICKS = 10;

	// ── the owner ────────────────────────────────────────────────────────────────────────────────

	/** A survival player in the player list who holds exactly {@code slots} of the Mechanic branch. */
	static ServerPlayer mechanic(GameTestHelper helper, SkillSlot... slots) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		SkillBuild build = SkillBuild.EMPTY;
		for (SkillSlot slot : slots) {
			build = build.with(SkillBranch.MECH, slot);
		}
		SkillStore.set(owner, new PlayerSkills(build));
		return owner;
	}

	static void own(MachineBlockEntity be, ServerPlayer owner) {
		be.setOwner(owner.getUUID(), owner.getGameProfile().name());
	}

	// ── one measured operation ───────────────────────────────────────────────────────────────────

	/** What one run cost, split by kind of tick. */
	private record Spend(long operationEu, long preStageEu, int operationTicks, boolean finished) {
	}

	/**
	 * Drive {@code be} until it completes one operation, with its buffer (and, through {@code topUp},
	 * any neighbour it depends on) refilled before every tick.
	 */
	private static Spend runOneOperation(MachineBlockEntity be, GameTestHelper helper, Runnable topUp,
			int maxTicks) {
		long operationEu = 0;
		long preStageEu = 0;
		int operationTicks = 0;
		long doneBefore = be.totalItemsProcessed();
		for (int i = 0; i < maxTicks; i++) {
			topUp.run();
			be.getEnergyStorage().setAmountUntracked(be.getEnergyStorage().getCapacity());
			long charge = be.getEnergyStorage().getAmount();
			int progress = progressOf(be);
			drive(be, helper, 1);
			long spent = charge - be.getEnergyStorage().getAmount();
			boolean finished = be.totalItemsProcessed() != doneBefore;
			if (progressOf(be) != progress || finished) {
				operationEu += spent;
				operationTicks++;
			} else {
				preStageEu += spent;
			}
			if (finished) {
				return new Spend(operationEu, preStageEu, operationTicks, true);
			}
		}
		return new Spend(operationEu, preStageEu, operationTicks, false);
	}

	private static int progressOf(MachineBlockEntity be) {
		return be.getDataAccess().get(2);
	}

	/**
	 * The price of one operation: {@code rate × ticks}, less one tick in
	 * {@code skillPreciseDrawEveryTicks} when the owner holds Precise Draw — the same arithmetic
	 * {@code SkillMachine.freeDrainTick} applies over progress 0..ticks-1.
	 */
	private static long operationPrice(int rate, int ticks, boolean preciseDraw) {
		int every = Math.max(1, Config.skillPreciseDrawEveryTicks);
		return (long) rate * (ticks - (preciseDraw ? ticks / every : 0));
	}

	private static boolean checkOperation(GameTestHelper helper, String machine, Spend spend, int rate,
			int ticks, boolean preciseDraw) {
		if (!spend.finished()) {
			helper.fail(machine + ": no operation completed — the rig cannot measure what it claims to "
					+ "(spent " + spend.operationEu() + " EU over " + spend.operationTicks() + " working ticks)");
			return false;
		}
		long expected = operationPrice(rate, ticks, preciseDraw);
		if (spend.operationEu() != expected) {
			helper.fail(machine + ": one operation must cost " + expected + " EU (" + rate + " EU/t x "
					+ ticks + " ticks" + (preciseDraw ? ", every " + Config.skillPreciseDrawEveryTicks
							+ "th tick free" : "") + "), spent " + spend.operationEu() + " EU");
			return false;
		}
		return true;
	}

	private static boolean checkPreStage(GameTestHelper helper, String machine, String stage, long spent,
			long expected) {
		if (spent != expected) {
			helper.fail(machine + ": the " + stage + " must cost " + expected
					+ " EU — it is preparation, not the operation — spent " + spent + " EU");
			return false;
		}
		return true;
	}

	// ── rigs ─────────────────────────────────────────────────────────────────────────────────────

	/** An assembler with one operation's worth of planks in the warehouse beside it. */
	static AssemblerBlockEntity assembler(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
		AssemblerBlockEntity be = AlaGameTestHelper.place(helper, pos, ModContent.ASSEMBLER.get(),
				AssemblerBlockEntity.class);
		own(be, owner);
		helper.setBlock(pos.east(), ModContent.STORAGE_MODULE.get());
		StorageModuleBlockEntity store = helper.getBlockEntity(pos.east(), StorageModuleBlockEntity.class);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		List<ItemStack> cells = new ArrayList<>(Collections.nCopies(BlueprintPattern.GRID_SIZE, ItemStack.EMPTY));
		cells.set(0, new ItemStack(Items.OAK_PLANKS));
		cells.set(3, new ItemStack(Items.OAK_PLANKS));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, AssemblyBlueprintItem.record(
				new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()), BlueprintPattern.of(cells)));
		return be;
	}

	/** A formed incubator on a lapis-to-redstone transform, with a dry nutrient bath. */
	static IncubatorBlockEntity incubator(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
		IncubatorBlockEntity be = AlaGameTestHelper.place(helper, pos, ModContent.INCUBATOR.get(),
				IncubatorBlockEntity.class);
		own(be, owner);
		helper.setBlock(pos.above(), Blocks.GLASS);
		be.setItem(IncubatorBlockEntity.CHIP_SLOT, new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()));
		be.setItem(IncubatorBlockEntity.FUEL_SLOT, new ItemStack(ModContent.URANIUM_INGOT.get(), 8));
		be.setItem(IncubatorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAPIS_LAZULI, 8));
		return be;
	}

	/** A cold, formed column holding one bucket of crude oil. */
	static DistillationColumnBlockEntity column(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
		DistillationColumnBlock.placeTower(helper.getLevel(), helper.absolutePos(pos));
		DistillationColumnBlockEntity be = helper.getBlockEntity(pos, DistillationColumnBlockEntity.class);
		own(be, owner);
		be.oilTank.fluid = FluidHolder.of(ModContent.OIL.get());
		be.oilTank.amount = FluidAmounts.BUCKET;
		return be;
	}

	/** A switched-on centrifuge over a heater, fed uranium dust; the rotor is not spun up yet. */
	record Centrifuge(ThermalCentrifugeBlockEntity be, ElectricHeaterBlockEntity heater) {
		void topUp() {
			heater.getEnergyStorage().setAmountUntracked(Config.electricHeaterBuffer);
		}
	}

	static Centrifuge centrifuge(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
		ThermalCentrifugeBlockEntity be = AlaGameTestHelper.place(helper, pos,
				ModContent.THERMAL_CENTRIFUGE.get(), ThermalCentrifugeBlockEntity.class);
		own(be, owner);
		be.setItem(ThermalCentrifugeBlockEntity.INPUT_SLOT, new ItemStack(ModContent.URANIUM_DUST.get(), 2));
		helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
		ElectricHeaterBlockEntity heater = AlaGameTestHelper.place(helper, pos.below(),
				ModContent.ELECTRIC_HEATER.get(), ElectricHeaterBlockEntity.class);
		heater.getEnergyStorage().setAmountUntracked(Config.electricHeaterBuffer);
		// The heater warms only on demand: one machine tick (with an empty buffer, so the rotor does not
		// move) reports the wait, then the heater runs its own warm-up — the path ThermalCentrifugeScenarios
		// proves, not a poked field.
		be.onHeatNeighbourChanged();
		drive(be, helper, 1);
		drive(heater, helper, Config.electricHeaterWarmupTicks + 2);
		Centrifuge rig = new Centrifuge(be, heater);
		rig.topUp();
		return rig;
	}

	/**
	 * Spin the rotor up to working speed on a funded buffer and return what the ramp cost; fails the
	 * test (returning -1) when the rig does not reach speed with an untouched progress bar.
	 */
	private static long spinUp(GameTestHelper helper, Centrifuge rig) {
		int spinupTicks = Math.max(1, Config.thermalCentrifugeSpinupTicks);
		long spent = 0;
		for (int i = 0; i < spinupTicks; i++) {
			rig.topUp();
			rig.be().getEnergyStorage().setAmountUntracked(rig.be().getEnergyStorage().getCapacity());
			long charge = rig.be().getEnergyStorage().getAmount();
			drive(rig.be(), helper, 1);
			spent += charge - rig.be().getEnergyStorage().getAmount();
		}
		if (!rig.be().isAtSpeed() || progressOf(rig.be()) != 0) {
			helper.fail("setup failed: the rotor should be at speed with no progress after " + spinupTicks
					+ " funded ticks (atSpeed=" + rig.be().isAtSpeed() + ", progress=" + progressOf(rig.be())
					+ ", status=" + rig.be().status() + ")");
			return -1;
		}
		return spent;
	}

	// ── one operation, priced ────────────────────────────────────────────────────────────────────

	private static void priceAssembler(GameTestHelper helper, ServerPlayer owner, boolean preciseDraw) {
		AssemblerBlockEntity be = assembler(helper, RIG, owner);
		int rate = be.effectiveEuPerTick(Config.assemblerEuPerTick);
		int ticks = be.effectiveDuration(Config.assemblerDuration);
		Spend spend = runOneOperation(be, helper, () -> { }, ticks + 2 + SLACK_TICKS);
		if (checkOperation(helper, "assembler", spend, rate, ticks, preciseDraw)
				&& checkPreStage(helper, "assembler", "planning tick", spend.preStageEu(), 0)) {
			helper.succeed();
		}
	}

	private static void priceIncubator(GameTestHelper helper, ServerPlayer owner, boolean preciseDraw) {
		IncubatorBlockEntity be = incubator(helper, RIG, owner);
		int rate = be.effectiveEuPerTick(Config.incubatorEuPerTick);
		int ticks = be.effectiveDuration(Config.mutationDurationTransform);
		Spend spend = runOneOperation(be, helper, () -> { }, ticks + SLACK_TICKS);
		if (checkOperation(helper, "incubator", spend, rate, ticks, preciseDraw)
				&& checkPreStage(helper, "incubator", "idle", spend.preStageEu(), 0)) {
			helper.succeed();
		}
	}

	private static void priceColumn(GameTestHelper helper, ServerPlayer owner, boolean preciseDraw) {
		DistillationColumnBlockEntity be = column(helper, RIG, owner);
		int rate = be.effectiveEuPerTick(Config.machineEuPerTick);
		int ticks = be.effectiveDuration(Config.distillationColumnDuration);
		int warmup = Math.max(1, Config.distillationColumnWarmupTicks);
		Spend spend = runOneOperation(be, helper, () -> { }, warmup + ticks + SLACK_TICKS);
		if (checkOperation(helper, "distillation column", spend, rate, ticks, preciseDraw)
				&& checkPreStage(helper, "distillation column", "warm-up", spend.preStageEu(),
						(long) warmup * rate)) {
			helper.succeed();
		}
	}

	private static void priceCentrifuge(GameTestHelper helper, ServerPlayer owner, boolean preciseDraw) {
		Centrifuge rig = centrifuge(helper, RIG, owner);
		int rate = rig.be().effectiveEuPerTick(Config.thermalCentrifugeEuPerTick);
		int ticks = rig.be().effectiveDuration(Config.thermalCentrifugeDuration);
		long ramp = spinUp(helper, rig);
		if (ramp < 0) {
			return;
		}
		Spend spend = runOneOperation(rig.be(), helper, rig::topUp, ticks + SLACK_TICKS);
		if (checkPreStage(helper, "thermal centrifuge", "spin-up", ramp,
						(long) Math.max(1, Config.thermalCentrifugeSpinupTicks) * rate)
				&& checkOperation(helper, "thermal centrifuge", spend, rate, ticks, preciseDraw)
				&& checkPreStage(helper, "thermal centrifuge", "idle", spend.preStageEu(), 0)) {
			helper.succeed();
		}
	}

	// ── characterization: no skill, the snapshot price ───────────────────────────────────────────

	/**
	 * Without Mechanic skills one assembler operation costs {@code assemblerEuPerTick ×
	 * assemblerDuration} = 480 EU; the planning tick and the finishing tick cost nothing.
	 */
	public static void assemblerOperationCostsTheSnapshotWithoutSkills(GameTestHelper helper) {
		priceAssembler(helper, mechanic(helper), false);
	}

	/**
	 * Without Mechanic skills one dry transform attempt costs {@code incubatorEuPerTick ×
	 * mutationDurationTransform} = 2400 EU.
	 */
	public static void incubatorOperationCostsTheSnapshotWithoutSkills(GameTestHelper helper) {
		priceIncubator(helper, mechanic(helper), false);
	}

	/**
	 * Without Mechanic skills one distillation costs {@code machineEuPerTick ×
	 * distillationColumnDuration} = 400 EU, after a cold start of {@code distillationColumnWarmupTicks}
	 * paid ticks at the same rate.
	 */
	public static void distillationColumnOperationCostsTheSnapshotWithoutSkills(GameTestHelper helper) {
		priceColumn(helper, mechanic(helper), false);
	}

	/**
	 * Without Mechanic skills one centrifuging costs {@code thermalCentrifugeEuPerTick ×
	 * thermalCentrifugeDuration} = 800 EU, after a spin-up of {@code thermalCentrifugeSpinupTicks} paid
	 * ticks at the same rate.
	 */
	public static void thermalCentrifugeOperationCostsTheSnapshotWithoutSkills(GameTestHelper helper) {
		priceCentrifuge(helper, mechanic(helper), false);
	}

	// ── Precise Draw: one tick in ten free, the pre-stage at full price ──────────────────────────

	/** With Precise Draw the assembler's 40-tick operation costs 36 ticks' worth: 432 EU instead of 480. */
	public static void assemblerPreciseDrawSkipsEveryTenthTick(GameTestHelper helper) {
		priceAssembler(helper, mechanic(helper, SkillSlot.B1), true);
	}

	/** With Precise Draw a dry transform costs 270 ticks' worth: 2160 EU instead of 2400. */
	public static void incubatorPreciseDrawSkipsEveryTenthTick(GameTestHelper helper) {
		priceIncubator(helper, mechanic(helper, SkillSlot.B1), true);
	}

	/**
	 * With Precise Draw a distillation costs 360 EU instead of 400 — and the warm-up still costs its full
	 * 400: it buys heat, not progress.
	 */
	public static void distillationColumnPreciseDrawSkipsEveryTenthTick(GameTestHelper helper) {
		priceColumn(helper, mechanic(helper, SkillSlot.B1), true);
	}

	/**
	 * With Precise Draw a centrifuging costs 720 EU instead of 800 — and the spin-up still costs its full
	 * 1600: it buys revolutions, not progress.
	 */
	public static void thermalCentrifugePreciseDrawSkipsEveryTenthTick(GameTestHelper helper) {
		priceCentrifuge(helper, mechanic(helper, SkillSlot.B1), true);
	}

	// ── Resilient Cycle: past halfway an operation finishes on a trickle below one tick's draw ──────

	/** Where the control rig stands — far enough from {@link #RIG} that neither touches the other. */
	private static final BlockPos CONTROL = new BlockPos(1, 2, 4);

	/** The trickle: a charge that cannot pay one working tick of any of the four machines. */
	private static final long TRICKLE_EU = 1;

	/** A machine and whatever its neighbour needs to keep working. */
	record Rig(MachineBlockEntity be, Runnable topUp) {
	}

	/** Funded ticks until progress passes the Resilient Cycle threshold, stopping short of the end. */
	static boolean pastHalfway(Rig rig, GameTestHelper helper, int maxTicks) {
		for (int i = 0; i < maxTicks; i++) {
			rig.topUp().run();
			rig.be().getEnergyStorage().setAmountUntracked(rig.be().getEnergyStorage().getCapacity());
			drive(rig.be(), helper, 1);
			int progress = progressOf(rig.be());
			int duration = rig.be().getDataAccess().get(3);
			if (duration > 0 && progress * 100 >= duration * Config.skillResilientFromPercent
					&& progress < duration) {
				return true;
			}
		}
		return false;
	}

	/** {@link #TRICKLE_EU} before every tick; whether an operation completed within {@code ticks}. */
	private static boolean finishesOnTrickle(Rig rig, GameTestHelper helper, int ticks) {
		long done = rig.be().totalItemsProcessed();
		for (int i = 0; i < ticks; i++) {
			rig.topUp().run();
			rig.be().getEnergyStorage().setAmountUntracked(TRICKLE_EU);
			drive(rig.be(), helper, 1);
			if (rig.be().totalItemsProcessed() != done) {
				return true;
			}
		}
		return false;
	}

	/** What a trickle against a heater that is never refilled bought: operation ticks, how many went unbilled. */
	record HeatLedger(int operationTicks, int unpaidTicks, long heaterSpent, boolean finished) {
		String describe() {
			return operationTicks + " operation ticks, " + unpaidTicks + " of them without the heater's tariff, "
					+ "heater spent " + heaterSpent + " EU, finished=" + finished;
		}
	}

	/** One heat tick's price under a machine with no overclockers — the heater's bill per operation tick. */
	static int heaterTariff() {
		return MachineRates.euPerTick(Config.electricHeaterEuPerTick, Config.globalMachineSpeedMultiplier);
	}

	/**
	 * {@link #TRICKLE_EU} before every tick, the heater below NOT refilled, until the operation completes
	 * (MOD-751).
	 *
	 * <p>A tick that moves the bar (or completes the operation) is an operation tick, and it must cost the heater
	 * exactly {@link #heaterTariff()}: the heat is the heater's product, and Resilient Cycle waives only the
	 * machine's own supply. The heater is read before and after every tick, so a tick that runs on heat
	 * nobody paid for is counted when it happens rather than hidden in a total. Refilling it each tick —
	 * what the plain trickle rigs do — would leave the heat gate's question unasked.
	 */
	static HeatLedger trickleAgainstHeater(MachineBlockEntity be, ElectricHeaterBlockEntity heater,
			GameTestHelper helper, int maxTicks) {
		long done = be.totalItemsProcessed();
		long heaterStart = heater.getEnergyStorage().getAmount();
		int operationTicks = 0;
		int unpaid = 0;
		for (int i = 0; i < maxTicks; i++) {
			be.getEnergyStorage().setAmountUntracked(TRICKLE_EU);
			long heaterBefore = heater.getEnergyStorage().getAmount();
			int progress = progressOf(be);
			drive(be, helper, 1);
			boolean finished = be.totalItemsProcessed() != done;
			if (progressOf(be) != progress || finished) {
				operationTicks++;
				if (heaterBefore - heater.getEnergyStorage().getAmount() != heaterTariff()) {
					unpaid++;
				}
			}
			if (finished) {
				break;
			}
		}
		return new HeatLedger(operationTicks, unpaid, heaterStart - heater.getEnergyStorage().getAmount(),
				be.totalItemsProcessed() != done);
	}

	/**
	 * The pair that pins the rule, as {@code MachineScenarios.mod576CoastingFinishesOnlySupplyGaps} does
	 * for the machines on {@code ProcessingCycle}: two identical rigs are driven past halfway and then fed
	 * a trickle no working tick can be paid from. The owner with Resilient Cycle must finish; the owner
	 * without it must stand still with its progress frozen (R-NRG-10). Either half alone would pass on a
	 * broken rule — the first if coasting finished everything, the second if it were never wired.
	 */
	private static void assertCoasting(GameTestHelper helper, String machine, Rig skilled, Rig control,
			int fundedTicks) {
		if (!pastHalfway(skilled, helper, fundedTicks) || !pastHalfway(control, helper, fundedTicks)) {
			helper.fail(machine + ": never passed the Resilient Cycle threshold — the rig cannot test what it "
					+ "claims to (progress " + progressOf(skilled.be()) + " / " + progressOf(control.be()) + ")");
			return;
		}
		int trickleTicks = fundedTicks + SLACK_TICKS;
		int frozenAt = progressOf(control.be());
		if (!finishesOnTrickle(skilled, helper, trickleTicks)) {
			helper.fail(machine + ": with Resilient Cycle an operation past halfway must finish on what the "
					+ "buffer holds, but it stood at progress " + progressOf(skilled.be()));
			return;
		}
		if (finishesOnTrickle(control, helper, trickleTicks) || progressOf(control.be()) != frozenAt) {
			helper.fail(machine + ": without the skill a trickle below one tick's draw must freeze the "
					+ "operation, but progress went " + frozenAt + " -> " + progressOf(control.be()));
			return;
		}
		helper.succeed();
	}

	/** With Resilient Cycle a half-built assembler operation finishes on a trickle; without it, it waits. */
	public static void assemblerResilientCycleFinishesOnATrickle(GameTestHelper helper) {
		AssemblerBlockEntity skilled = assembler(helper, RIG, mechanic(helper, SkillSlot.CAP));
		AssemblerBlockEntity control = assembler(helper, CONTROL, mechanic(helper));
		assertCoasting(helper, "assembler", new Rig(skilled, () -> { }), new Rig(control, () -> { }),
				skilled.effectiveDuration(Config.assemblerDuration) + 2);
	}

	/** With Resilient Cycle a half-done incubator attempt finishes on a trickle; without it, it waits. */
	public static void incubatorResilientCycleFinishesOnATrickle(GameTestHelper helper) {
		IncubatorBlockEntity skilled = incubator(helper, RIG, mechanic(helper, SkillSlot.CAP));
		IncubatorBlockEntity control = incubator(helper, CONTROL, mechanic(helper));
		assertCoasting(helper, "incubator", new Rig(skilled, () -> { }), new Rig(control, () -> { }),
				skilled.effectiveDuration(Config.mutationDurationTransform));
	}

	/**
	 * With Resilient Cycle a half-done distillation finishes on a trickle; without it, it waits (and
	 * cools). The warm-up is not an operation and is paid in full on the way there.
	 */
	public static void distillationColumnResilientCycleFinishesOnATrickle(GameTestHelper helper) {
		DistillationColumnBlockEntity skilled = column(helper, RIG, mechanic(helper, SkillSlot.CAP));
		DistillationColumnBlockEntity control = column(helper, CONTROL, mechanic(helper));
		assertCoasting(helper, "distillation column", new Rig(skilled, () -> { }), new Rig(control, () -> { }),
				Math.max(1, Config.distillationColumnWarmupTicks)
						+ skilled.effectiveDuration(Config.distillationColumnDuration));
	}

	/**
	 * With Resilient Cycle a half-done centrifuging finishes on a trickle — the heater below still paid
	 * for every tick; without the skill the rotor idles and the operation waits.
	 */
	public static void thermalCentrifugeResilientCycleFinishesOnATrickle(GameTestHelper helper) {
		Centrifuge skilled = centrifuge(helper, RIG, mechanic(helper, SkillSlot.CAP));
		Centrifuge control = centrifuge(helper, CONTROL, mechanic(helper));
		if (spinUp(helper, skilled) < 0 || spinUp(helper, control) < 0) {
			return;
		}
		assertCoasting(helper, "thermal centrifuge", new Rig(skilled.be(), skilled::topUp),
				new Rig(control.be(), control::topUp),
				skilled.be().effectiveDuration(Config.thermalCentrifugeDuration));
	}

	/**
	 * Every tick Resilient Cycle runs on a trickle is a tick of heat the heater sells (MOD-712, pinned by
	 * MOD-751): past halfway the buffer is cut to a trickle and the heater is left to pay from its own
	 * charge, and each operation tick must cost it exactly one tariff. The scenario above refills the heater
	 * before every tick, so a heat gate that asked "can the machine pay?" instead of "does the tick run?"
	 * finished there as well; here it counts its unbilled ticks.
	 */
	public static void thermalCentrifugeResilientCycleTickPaysTheHeater(GameTestHelper helper) {
		Centrifuge rig = centrifuge(helper, RIG, mechanic(helper, SkillSlot.CAP));
		int duration = rig.be().effectiveDuration(Config.thermalCentrifugeDuration);
		if (spinUp(helper, rig) < 0) {
			return;
		}
		if (!pastHalfway(new Rig(rig.be(), rig::topUp), helper, duration)) {
			helper.fail("thermal centrifuge: never passed the Resilient Cycle threshold (progress "
					+ progressOf(rig.be()) + ")");
			return;
		}
		rig.topUp();
		HeatLedger ledger = trickleAgainstHeater(rig.be(), rig.heater(), helper, duration + SLACK_TICKS);
		if (!ledger.finished() || ledger.unpaidTicks() != 0
				|| ledger.heaterSpent() != (long) heaterTariff() * ledger.operationTicks()) {
			helper.fail("thermal centrifuge: a coasting tick must pay the heater one tariff ("
					+ heaterTariff() + " EU) and finish the operation; " + ledger.describe());
			return;
		}
		helper.succeed();
	}
}
