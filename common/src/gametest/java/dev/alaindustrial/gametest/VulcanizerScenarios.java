package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerStatus;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.block.entity.WorldHeatSources;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.skill.SkillSlot;
import java.util.Arrays;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import dev.alaindustrial.block.ElectricHeaterBlock;
import dev.alaindustrial.block.HeaterGlow;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral MOD-258 world scenarios. Fabric and NeoForge wrappers execute these same bodies, so
 * recipe registration, block entities and demand-driven heater behaviour cannot drift by loader.
 */
public final class VulcanizerScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(VulcanizerScenarios::fun01HeatLevelsScaleOutput, "vulcanizer_heat_levels_scale_output")
						.fabricId("VulcanizerGameTest", "tcVulc001Fun01_heatLevelsScaleOutput").ticks(700),
				RosterEntry.of(VulcanizerScenarios::fun02AllPassiveHeatSourcesResolve,
								"vulcanizer_all_passive_heat_sources_resolve")
						.fabricId("VulcanizerGameTest", "tcVulc001Fun02_allPassiveHeatSourcesResolve").ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::neg01NoHeatNoWork, "vulcanizer_no_heat_no_work")
						.fabricId("VulcanizerGameTest", "tcVulc001Neg01_noHeatNoWork").ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::neg02NoPowerNoWork, "vulcanizer_no_power_no_work")
						.fabricId("VulcanizerGameTest", "tcVulc001Neg02_noPowerNoWork").ticks(300),
				RosterEntry.of(VulcanizerScenarios::neg03InactiveHeatSourcesResolveAsNone,
								"vulcanizer_inactive_heat_sources_resolve_as_none")
						.fabricId("VulcanizerGameTest", "tcVulc001Neg03_inactiveHeatSourcesResolveAsNone")
						.ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::neg04PartialSulfurBatchDoesNotWork,
								"vulcanizer_partial_sulfur_batch_does_not_work")
						.fabricId("VulcanizerGameTest", "tcVulc001Neg04_partialSulfurBatchDoesNotWork").ticks(300),
				RosterEntry.of(VulcanizerScenarios::con01OutputJamFreezesBothConsumers,
								"vulcanizer_output_jam_freezes_both_consumers")
						.fabricId("VulcanizerGameTest", "tcVulc001Con01_outputJamFreezesBothConsumers").ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::con02HeaterIsDemandDriven, "vulcanizer_heater_is_demand_driven")
						.fabricId("VulcanizerGameTest", "tcVulc001Con02_heaterIsDemandDriven").ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::con03HeaterTariffIsAtomicAtThreshold,
								"vulcanizer_heater_tariff_is_atomic_at_threshold")
						.fabricId("VulcanizerGameTest", "tcVulc001Con03_heaterTariffIsAtomicAtThreshold")
						.ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::reg01HeatUpgradeFinishesBatchAtCapturedTier,
								"vulcanizer_heat_upgrade_finishes_batch_at_captured_tier")
						.fabricId("VulcanizerGameTest", "tcVulc001Reg01_heatUpgradeFinishesBatchAtCapturedTier")
						.ticks(300),
				RosterEntry.of(VulcanizerScenarios::fun04ColdHeaterProducesNothingUntilWarm,
								"electric_heater_cold_produces_nothing_until_warm")
						.fabricId("VulcanizerGameTest", "tcEheat001Fun04_coldHeaterProducesNothingUntilWarm")
						.ticks(700),
				RosterEntry.of(VulcanizerScenarios::fun05IdleHeaterCoolsAtHalfRate,
								"electric_heater_idle_cools_at_half_rate")
						.fabricId("VulcanizerGameTest", "tcEheat001Fun05_idleHeaterCoolsAtHalfRate").ticks(400),
				RosterEntry.of(VulcanizerScenarios::fun06LoneHeaterNeverWarmsAndSpendsNothing,
								"electric_heater_lone_never_warms")
						.fabricId("VulcanizerGameTest", "tcEheat001Fun06_loneHeaterNeverWarms").ticks(500),
				RosterEntry.of(VulcanizerScenarios::reg02AutomationKeepsInputsSeparated,
								"vulcanizer_automation_keeps_inputs_separated")
						.fabricId("VulcanizerGameTest", "tcVulc001Reg02_automationKeepsInputsSeparated").ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::reg03HeatDowngradeRestartsCycle,
								"vulcanizer_heat_downgrade_restarts_cycle")
						.fabricId("VulcanizerGameTest", "tcVulc001Reg03_heatDowngradeRestartsCycle").ticks(300),
				RosterEntry.of(VulcanizerScenarios::sta01RoundTripPreservesInFlightCycle,
								"vulcanizer_round_trip_preserves_in_flight_cycle")
						.fabricId("VulcanizerGameTest", "tcVulc001Sta01_roundTripPreservesInFlightCycle")
						.ticks(20, 40),
				RosterEntry.of(VulcanizerScenarios::fun03RubberProductionAdvancement,
								"vulcanizer_rubber_production_advancement")
						.fabricId("VulcanizerGameTest", "tcVulc001Fun03_rubberProductionAdvancement").ticks(300),
				RosterEntry.of(VulcanizerScenarios::tcHeater002Fun01_soundFollowsSpendingNotHeat,
								"vulcanizer_tc_heater002_fun01_sound_follows_spending_not_heat")
						.fabricId("VulcanizerGameTest", "tcHeater002Fun01_soundFollowsSpendingNotHeat").ticks(20, 200),
				RosterEntry.of(VulcanizerScenarios::skl01ResilientCycleTickPaysTheHeater,
						"mod751_vulcanizer_coasting_tick_pays_the_heater").ticks(100),
				RosterEntry.of(VulcanizerScenarios::skl02TrickleWithoutResilientCycleFreezes,
						"mod751_vulcanizer_trickle_without_skill_freezes").ticks(100));

		private Roster() {}
	}

	private static final BlockPos MACHINE = new BlockPos(1, 2, 1);
	private static final BlockPos HEAT = MACHINE.below();
	private static final long AMPLE_EU = 800L;

	private VulcanizerScenarios() {
	}

	private static VulcanizerBlockEntity placeMachine(GameTestHelper helper) {
		helper.setBlock(MACHINE, ModContent.VULCANIZER.get());
		VulcanizerBlockEntity be = helper.getBlockEntity(MACHINE, VulcanizerBlockEntity.class);
		if (be == null) {
			helper.fail("vulcanizer block entity missing after placement");
		}
		return be;
	}

	/**
	 * Per-operation input price from {@code recipe/vulcanizing/rubber.json} (MOD-271). The scenarios
	 * stock and assert in whole operations, so a future re-balance of the sulfur cost lands here and
	 * not in a dozen literals.
	 */
	private static final int RAW_RUBBER_PER_OPERATION = 1;
	private static final int SULFUR_PER_OPERATION = 4;

	/** Load the machine with exactly {@code operations} batches worth of both inputs. */
	private static void stock(VulcanizerBlockEntity be, int operations) {
		be.setItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), operations * RAW_RUBBER_PER_OPERATION));
		be.setItem(VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), operations * SULFUR_PER_OPERATION));
	}

	/** True when the two input slots hold exactly {@code operations} batches worth. */
	private static boolean holdsOperations(VulcanizerBlockEntity be, int operations) {
		return be.getItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT).getCount()
						== operations * RAW_RUBBER_PER_OPERATION
				&& be.getItem(VulcanizerBlockEntity.SULFUR_SLOT).getCount()
						== operations * SULFUR_PER_OPERATION;
	}

	private static int operationTicks() {
		return MachineRates.duration(Config.vulcanizerDuration, Config.globalMachineSpeedMultiplier) + 2;
	}

	private static void passiveHeat(GameTestHelper helper, Block block) {
		if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
			helper.setBlock(HEAT, block.defaultBlockState().setValue(CampfireBlock.LIT, true));
		} else {
			helper.setBlock(HEAT, block);
		}
	}

	private static ElectricHeaterBlockEntity poweredHeater(GameTestHelper helper, long energy) {
		helper.setBlock(HEAT, ModContent.ELECTRIC_HEATER.get());
		ElectricHeaterBlockEntity heater = helper.getBlockEntity(HEAT, ElectricHeaterBlockEntity.class);
		if (heater == null) {
			helper.fail("electric-heater block entity missing after placement");
		}
		heater.getEnergyStorage().setAmountUntracked(energy);
		return heater;
	}

	/** A heater buffer big enough for a full cold start plus a batch, whatever the config says. */
	private static long heaterEu() {
		return Config.electricHeaterBuffer;
	}

	/**
	 * Places a heater under {@code machine} and runs its warm-up to completion (MOD-418).
	 *
	 * <p>Warmed the way the game warms it — its own ticks, spending its own EU, while the machine above
	 * sits blocked on heat — and never by poking the field. The gate IS the mechanic here, so a scenario
	 * that wants tier-3 heat has to prove the gate can be passed at all. The machine must already be
	 * stocked when this is called, or the heater correctly refuses to light and stays cold.
	 *
	 * <p>The buffer is topped back up afterwards, so callers get a full heater and can still measure
	 * spending against it.
	 */
	private static ElectricHeaterBlockEntity hotHeater(GameTestHelper helper, VulcanizerBlockEntity machine) {
		ElectricHeaterBlockEntity heater = poweredHeater(helper, heaterEu());
		machine.onHeatNeighbourChanged();
		drive(machine, helper, 1); // the machine reports NO_HEAT, which is what lights the heater
		drive(heater, helper, Config.electricHeaterWarmupTicks + 2);
		if (!heater.isHot()) {
			helper.fail("heater is not hot after " + Config.electricHeaterWarmupTicks
					+ " warming ticks; permille=" + heater.heatPermille());
		}
		heater.getEnergyStorage().setAmountUntracked(heaterEu());
		return heater;
	}

	private static void assertOutput(GameTestHelper helper, VulcanizerBlockEntity be, int count) {
		ItemStack output = be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT);
		if (!output.is(ModContent.RUBBER.get()) || output.getCount() != count) {
			helper.fail("expected " + count + " rubber, got " + output);
		}
	}

	/** Campfire, lava and powered electric heat produce x1, x2 and x3 without extra ingredients. */
	public static void fun01HeatLevelsScaleOutput(GameTestHelper helper) {
		for (int expected = 1; expected <= 3; expected++) {
			helper.setBlock(MACHINE, Blocks.AIR);
			helper.setBlock(HEAT, Blocks.AIR);
			if (expected == 1) {
				passiveHeat(helper, Blocks.CAMPFIRE);
			} else if (expected == 2) {
				passiveHeat(helper, Blocks.LAVA);
			}
			VulcanizerBlockEntity be = placeMachine(helper);
			be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
			stock(be, 2);
			if (expected == 3) {
				// MOD-418: tier 3 is the WARMED heater, and lighting it needs the machine already
				// stocked above — so unlike the passive sources this one goes in after the machine.
				hotHeater(helper, be);
				be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
			}

			drive(be, helper, operationTicks());

			assertOutput(helper, be, expected);
			if (!holdsOperations(be, 1)) {
				helper.fail("heat x" + expected + " must consume exactly one operation's inputs");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The warm-up gate end to end (MOD-418): a cold heater is not a heat source, so the machine above
	 * produces NOTHING until the ramp finishes — and then runs at x3 from its first working tick.
	 *
	 * <p>The first half is the real assertion and the reason the scenario exists: it is exactly what
	 * separates "the stove has to be lit" from "the stove runs weaker while cold". Driving a full
	 * operation's worth of ticks and demanding an empty output slot would fail on any design that let a
	 * cold heater supply some lesser tier.
	 */
	public static void fun04ColdHeaterProducesNothingUntilWarm(GameTestHelper helper) {
		ElectricHeaterBlockEntity heater = poweredHeater(helper, heaterEu());
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);

		if (heater.isHot() || heater.heatPermille() != 0) {
			helper.fail("a freshly placed heater must start stone cold");
			return;
		}

		// A whole operation's worth of machine ticks against a cold heater: nothing may come out.
		drive(be, helper, operationTicks());
		if (!be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("a cold heater produced " + be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT)
					+ " — it must be no heat source at all until warm");
			return;
		}
		if (!holdsOperations(be, 1)) {
			helper.fail("waiting on heat must not consume inputs");
			return;
		}

		// Now let it light, and the very next batch is the full tier.
		drive(heater, helper, Config.electricHeaterWarmupTicks + 2);
		if (!heater.isHot()) {
			helper.fail("heater did not finish warming; permille=" + heater.heatPermille());
			return;
		}
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		heater.getEnergyStorage().setAmountUntracked(heaterEu());
		be.onHeatNeighbourChanged();
		drive(be, helper, operationTicks());
		assertOutput(helper, be, 3);
		helper.succeed();
	}

	/**
	 * Warming costs EU, and only while something is actually waiting on the heat (MOD-418).
	 *
	 * <p>This is the guard on the block's founding promise. The gate design gave the heater an idle draw
	 * for the first time, and the obvious implementation — "warm whenever powered" — would have a heater
	 * under an empty machine, or under no machine at all, quietly burning its buffer forever.
	 */
	public static void fun06LoneHeaterNeverWarmsAndSpendsNothing(GameTestHelper helper) {
		ElectricHeaterBlockEntity heater = poweredHeater(helper, heaterEu());

		// No machine above at all.
		drive(heater, helper, Config.electricHeaterWarmupTicks);
		if (heater.heatPermille() != 0 || heater.getEnergyStorage().getAmount() != heaterEu()) {
			helper.fail("a heater with nothing above it warmed or spent EU: permille="
					+ heater.heatPermille() + " energy=" + heater.getEnergyStorage().getAmount());
			return;
		}

		// A machine above, but with nothing to make: still nothing to heat.
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		drive(be, helper, 2);
		drive(heater, helper, Config.electricHeaterWarmupTicks);
		if (heater.heatPermille() != 0 || heater.getEnergyStorage().getAmount() != heaterEu()) {
			helper.fail("a heater under an EMPTY machine warmed or spent EU: permille="
					+ heater.heatPermille() + " energy=" + heater.getEnergyStorage().getAmount());
			return;
		}
		helper.succeed();
	}

	/**
	 * A heater with nothing to do cools at HALF the rate it warmed (MOD-418), so a hopper pausing between
	 * batches costs a little of the ramp rather than all of it.
	 *
	 * <p>The upper bound is the real assertion: cooling one step per idle tick — the obvious
	 * implementation — would drop ~40 here and fail, which is what makes this a control rather than a
	 * restatement of the code.
	 */
	public static void fun05IdleHeaterCoolsAtHalfRate(GameTestHelper helper) {
		int warmup = Config.electricHeaterWarmupTicks;
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);

		// Take the work away: an empty machine is not waiting on heat, so the heater is purely cooling.
		be.setItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT, ItemStack.EMPTY);
		be.setItem(VulcanizerBlockEntity.SULFUR_SLOT, ItemStack.EMPTY);
		drive(be, helper, 1);
		long energyBefore = heater.getEnergyStorage().getAmount();
		int before = heater.heatPermille();

		drive(heater, helper, 40);

		int droppedTicks = (before - heater.heatPermille()) * warmup / 1000;
		if (droppedTicks < 18 || droppedTicks > 22) {
			helper.fail("40 idle ticks must shed ~20 ticks of warm-up (half rate), shed " + droppedTicks);
			return;
		}
		if (heater.getEnergyStorage().getAmount() != energyBefore) {
			helper.fail("cooling must be free; heater spent "
					+ (energyBefore - heater.getEnergyStorage().getAmount()) + " EU");
			return;
		}
		helper.succeed();
	}

	/** The adapter recognizes every shipped passive source, including both campfires. */
	public static void fun02AllPassiveHeatSourcesResolve(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		Block[] sources = {
				Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.LAVA,
				Blocks.MAGMA_BLOCK, Blocks.LAVA_CAULDRON
		};
		HeatSource[] expected = {
				HeatSource.CAMPFIRE, HeatSource.CAMPFIRE, HeatSource.LAVA,
				HeatSource.MAGMA, HeatSource.LAVA_CAULDRON
		};
		for (int i = 0; i < sources.length; i++) {
			passiveHeat(helper, sources[i]);
			be.onHeatNeighbourChanged();
			if (be.heatSource() != expected[i]) {
				helper.fail(sources[i] + " resolved as " + be.heatSource() + ", expected " + expected[i]);
				return;
			}
		}
		helper.succeed();
	}

	/** Unlit campfires and underfunded electric heaters are not usable heat sources. */
	public static void neg03InactiveHeatSourcesResolveAsNone(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		be.onHeatNeighbourChanged();
		if (be.heatSource() != HeatSource.NONE) {
			helper.fail("an unlit campfire resolved as " + be.heatSource());
			return;
		}

		int cost = MachineRates.euPerTick(Config.electricHeaterEuPerTick, Config.globalMachineSpeedMultiplier);
		poweredHeater(helper, cost - 1L);
		be.onHeatNeighbourChanged();
		if (be.heatSource() != HeatSource.NONE) {
			helper.fail("an underfunded electric heater resolved as " + be.heatSource());
			return;
		}
		helper.succeed();
	}

	/** With no heat, neither progress, EU nor ingredients move. */
	public static void neg01NoHeatNoWork(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);

		drive(be, helper, 3);

		if (be.getDataAccess().get(2) != 0 || be.getEnergyStorage().getAmount() != AMPLE_EU
				|| !holdsOperations(be, 1)) {
			helper.fail("unheated vulcanizer changed progress, energy or inputs");
			return;
		}
		helper.succeed();
	}

	/** With no EU, passive heat cannot produce or consume ingredients. */
	public static void neg02NoPowerNoWork(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity be = placeMachine(helper);
		stock(be, 1);

		drive(be, helper, operationTicks());

		if (be.getDataAccess().get(2) != 0 || !be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT).isEmpty()
				|| !holdsOperations(be, 1)) {
			helper.fail("unpowered vulcanizer progressed, produced, or consumed inputs");
			return;
		}
		helper.succeed();
	}

	/** A full output freezes the operation before either the machine or electric heater spends EU. */
	public static void con01OutputJamFreezesBothConsumers(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(VulcanizerBlockEntity.OUTPUT_SLOT, new ItemStack(ModContent.RUBBER.get(), 64));

		drive(be, helper, 3);
		drive(heater, helper, 3);

		if (be.getDataAccess().get(2) != 0 || be.getEnergyStorage().getAmount() != AMPLE_EU
				|| heater.getEnergyStorage().getAmount() != heaterEu()) {
			helper.fail("blocked output spent progress or EU: progress=" + be.getDataAccess().get(2)
					+ " machine=" + be.getEnergyStorage().getAmount()
					+ " heater=" + heater.getEnergyStorage().getAmount());
			return;
		}
		helper.succeed();
	}

	/** The heater pays exactly one configured tariff only when the Vulcanizer advances. */
	public static void con02HeaterIsDemandDriven(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		int cost = MachineRates.euPerTick(Config.electricHeaterEuPerTick, Config.globalMachineSpeedMultiplier);

		drive(be, helper, 1);
		if (be.getDataAccess().get(2) != 1 || heater.getEnergyStorage().getAmount() != heaterEu() - cost) {
			helper.fail("one useful tick must drain exactly " + cost + " heater EU");
			return;
		}

		be.setItem(VulcanizerBlockEntity.OUTPUT_SLOT, new ItemStack(ModContent.RUBBER.get(), 64));
		long idleEnergy = heater.getEnergyStorage().getAmount();
		drive(be, helper, 3);
		drive(heater, helper, 3);
		if (heater.getEnergyStorage().getAmount() != idleEnergy) {
			helper.fail("idle/jammed heater consumed EU: " + idleEnergy + " -> "
					+ heater.getEnergyStorage().getAmount());
			return;
		}
		helper.succeed();
	}

	/** The electric heater accepts the exact tariff, commits it once, and rejects a second draw. */
	public static void con03HeaterTariffIsAtomicAtThreshold(GameTestHelper helper) {
		int cost = MachineRates.euPerTick(Config.electricHeaterEuPerTick, Config.globalMachineSpeedMultiplier);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		// Must be WARM before the tariff means anything: a cold heater is not a heat source (MOD-418),
		// so it would not be discoverable at any buffer level.
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);
		heater.getEnergyStorage().setAmountUntracked(cost);
		if (WorldHeatSources.resolve(helper.getLevel(), helper.absolutePos(MACHINE))
				!= HeatSource.ELECTRIC_HEATER) {
			helper.fail("warm heater with the exact tariff was not discoverable");
			return;
		}
		if (!heater.consumeHeatTick() || heater.getEnergyStorage().getAmount() != 0L) {
			helper.fail("heater did not atomically consume the exact tariff");
			return;
		}
		if (heater.consumeHeatTick() || heater.getEnergyStorage().getAmount() != 0L) {
			helper.fail("empty heater accepted a second heat draw");
			return;
		}
		helper.succeed();
	}

	/**
	 * Raising heat mid-batch keeps the batch running and finishes it at the tier it started on; the NEXT
	 * batch gets the better tier.
	 *
	 * <p>This scenario asserted the opposite until MOD-418 ("raising heat restarts the batch at zero").
	 * Restarting was harmless while every source was instantly present or instantly gone, and became a
	 * punishment the moment the Electric Heater grew a warm-up: crossing from x2 to x3 partway through
	 * would throw away up to a whole operation, so the block would have felt worse the better it got.
	 *
	 * <p>The anti-cheese half is what the {@code assertOutput(..., 1)} pins: upgrading the source on the
	 * batch's second tick must NOT turn that batch into x2 — it finishes at the captured tier, and only
	 * the following one is worth more.
	 */
	public static void reg01HeatUpgradeFinishesBatchAtCapturedTier(GameTestHelper helper) {
		// Campfire -> lava, because that is now the only upgrade that can happen mid-batch: an electric
		// heater is either hot (tier 3) or not a heat source at all (MOD-418), so it has no partial tier
		// to climb out of while a batch is running.
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		drive(be, helper, 1);
		if (be.cycleHeatLevel() != 1) {
			helper.fail("first campfire tick did not snapshot heat level 1, got " + be.cycleHeatLevel());
			return;
		}

		passiveHeat(helper, Blocks.LAVA);
		be.onHeatNeighbourChanged();
		if (be.getDataAccess().get(2) != 1 || be.cycleHeatLevel() != 1) {
			helper.fail("heat upgrade threw away the in-flight cycle: progress="
					+ be.getDataAccess().get(2) + " tier=" + be.cycleHeatLevel());
			return;
		}

		// And the anti-cheese half: reaching tier 2 partway through must not pay this batch out at x2.
		drive(be, helper, operationTicks());
		assertOutput(helper, be, 1);
		helper.succeed();
	}

	/** Dropping from electric heat to lava restarts and completes at x2 without replacing the machine. */
	public static void reg03HeatDowngradeRestartsCycle(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		// Must be a WARMED heater: only tier 3 is a downgrade when it becomes lava's tier 2 (MOD-418).
		hotHeater(helper, be);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		drive(be, helper, 1);
		if (be.getDataAccess().get(2) != 1 || be.cycleHeatLevel() != 3) {
			helper.fail("electric heat did not start a tier-3 cycle");
			return;
		}

		passiveHeat(helper, Blocks.LAVA);
		be.onHeatNeighbourChanged();
		if (be.getDataAccess().get(2) != 0 || be.cycleHeatLevel() != 0) {
			helper.fail("heat downgrade did not restart the in-flight cycle");
			return;
		}
		drive(be, helper, operationTicks());
		assertOutput(helper, be, 2);
		helper.succeed();
	}

	/**
	 * A partial sulfur batch buys nothing (MOD-271). With heat, EU and raw rubber all present but one
	 * dust short of the recipe price, the machine must sit still and name the shortfall — the failure
	 * this guards against is a cycle that runs to completion and then quietly underpays, or a status
	 * line that blames a missing recipe when the recipe is right there.
	 */
	public static void neg04PartialSulfurBatchDoesNotWork(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), RAW_RUBBER_PER_OPERATION));
		be.setItem(VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), SULFUR_PER_OPERATION - 1));

		drive(be, helper, operationTicks());

		if (!be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("a short sulfur batch still produced rubber");
			return;
		}
		if (be.getDataAccess().get(2) != 0 || be.getEnergyStorage().getAmount() != AMPLE_EU) {
			helper.fail("a short sulfur batch moved progress or spent EU");
			return;
		}
		if (be.getItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT).getCount() != RAW_RUBBER_PER_OPERATION
				|| be.getItem(VulcanizerBlockEntity.SULFUR_SLOT).getCount() != SULFUR_PER_OPERATION - 1) {
			helper.fail("a short sulfur batch consumed inputs");
			return;
		}
		if (be.status() != VulcanizerStatus.NO_SULFUR) {
			helper.fail("expected status NO_SULFUR for a short batch, got " + be.status());
			return;
		}
		helper.succeed();
	}

	/** Both input slots have strict predicates and the bottom face is entirely reserved for heat. */
	public static void reg02AutomationKeepsInputsSeparated(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		ItemStack raw = new ItemStack(ModContent.RAW_RUBBER.get());
		ItemStack sulfur = new ItemStack(ModContent.SULFUR_DUST.get());
		ItemStack rubber = new ItemStack(ModContent.RUBBER.get());

		if (!be.canPlaceItemThroughFace(VulcanizerBlockEntity.RAW_RUBBER_SLOT, raw, Direction.UP)
				|| be.canPlaceItemThroughFace(VulcanizerBlockEntity.RAW_RUBBER_SLOT, sulfur, Direction.UP)
				|| !be.canPlaceItemThroughFace(VulcanizerBlockEntity.SULFUR_SLOT, sulfur, Direction.UP)
				|| be.canPlaceItemThroughFace(VulcanizerBlockEntity.SULFUR_SLOT, raw, Direction.UP)
				|| be.canPlaceItemThroughFace(VulcanizerBlockEntity.OUTPUT_SLOT, rubber, Direction.UP)) {
			helper.fail("vulcanizer input/output slot predicates are not separated");
			return;
		}
		// MOD-746: the rubber slot reads a tag that takes another mod's finished rubber, but never the
		// mod's own — or rubber would loop through the machine and triple on the electric heater.
		if (be.canPlaceItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT, rubber)
				|| be.canPlaceItemThroughFace(VulcanizerBlockEntity.RAW_RUBBER_SLOT, rubber, Direction.UP)) {
			helper.fail("vulcanizer accepts its own finished rubber as input: a dupe loop");
			return;
		}
		if (be.getSlotsForFace(Direction.DOWN).length != 0
				|| be.canTakeItemThroughFace(VulcanizerBlockEntity.OUTPUT_SLOT, rubber, Direction.DOWN)) {
			helper.fail("the heat-facing bottom must expose no automation slots");
			return;
		}
		if (!Arrays.stream(be.getSlotsForFace(Direction.UP))
				.anyMatch(slot -> slot == VulcanizerBlockEntity.RAW_RUBBER_SLOT)) {
			helper.fail("top face does not expose the raw-rubber input");
			return;
		}
		helper.succeed();
	}

	/** Energy, both inputs, progress and the active heat tier survive serialization. */
	public static void sta01RoundTripPreservesInFlightCycle(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity src = placeMachine(helper);
		src.getEnergyStorage().setAmountUntracked(321L);
		stock(src, 3);
		drive(src, helper, 1);

		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = src.saveCustomOnly(registries);
		VulcanizerBlockEntity restored = new VulcanizerBlockEntity(
				helper.absolutePos(MACHINE), helper.getLevel().getBlockState(helper.absolutePos(MACHINE)));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != 321L - MachineRates.euPerTick(Config.machineEuPerTick,
				Config.globalMachineSpeedMultiplier)
				|| restored.getDataAccess().get(2) != 1 || restored.cycleHeatLevel() != 1
				|| !holdsOperations(restored, 3)) {
			helper.fail("in-flight vulcanizer cycle did not round-trip");
			return;
		}
		helper.succeed();
	}

	/** The existing inventory trigger still awards rubber_production for machine-made rubber. */
	public static void fun03RubberProductionAdvancement(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		drive(be, helper, operationTicks());
		assertOutput(helper, be, 1);

		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		player.setGameMode(GameType.SURVIVAL);
		AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
				.get(Industrialization.id("rubber_production"));
		if (advancement == null) {
			helper.fail("advancement alaindustrial:rubber_production is not loaded");
			return;
		}
		if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
			helper.fail("rubber_production was awarded before the player received rubber");
			return;
		}
		player.getInventory().add(be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT).copy());
		player.inventoryMenu.broadcastChanges();
		if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
			helper.fail("receiving Vulcanizer rubber did not award rubber_production");
			return;
		}
		helper.succeed();
	}

	/**
	 * TC-HEATER-002-FUN01 — the heater's SOUND flag follows spending, its LIGHT follows temperature.
	 *
	 * <p>MOD-577. Until then both read the same "are the coils hot" ladder, so a heater that was holding
	 * temperature for free, or coasting through its twenty-second cool-down, hummed exactly like one
	 * under load — and a block whose founding promise is "a heater with nothing to heat costs exactly
	 * zero" spent most of its audible life costing nothing.
	 *
	 * <p>Asserted on the blockstate rather than on the sound, because the blockstate is what the client
	 * reads: {@code MachineHumProvider#isWorking} is handed nothing else.
	 *
	 * @implements TC-HEATER-002-FUN01 — the heater's sound flag follows spending, its light temperature.
	 */
	public static void tcHeater002Fun01_soundFollowsSpendingNotHeat(GameTestHelper helper) {
		VulcanizerBlockEntity machine = placeMachine(helper);
		machine.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(machine, 1);
		ElectricHeaterBlockEntity heater = hotHeater(helper, machine);

		BlockState hot = helper.getBlockState(HEAT);
		if (hot.getValue(ElectricHeaterBlock.GLOW) == HeaterGlow.COLD) {
			helper.fail("the heater should be hot after its warm-up");
		}

		// Nothing waiting on it any more: it holds its heat for free, then cools — and must go quiet
		// while staying lit, because hot metal glows but a silent block is the honest signal for "free".
		helper.setBlock(MACHINE, Blocks.AIR);
		drive(heater, helper, 4);

		BlockState idle = helper.getBlockState(HEAT);
		if (idle.getValue(ElectricHeaterBlock.DRAWING)) {
			helper.fail("a heater that is spending nothing must not claim to be working");
		}
		if (idle.getValue(ElectricHeaterBlock.GLOW) == HeaterGlow.COLD) {
			helper.fail("the light follows temperature and must still be lit while the coils are hot");
		}
		helper.succeed();
	}

	// ── Resilient Cycle against the electric heater (MOD-751) ─────────────────────────────────────

	/**
	 * A stocked machine over a hot heater, owned by a survival player holding {@code slots} of the
	 * Mechanic branch, driven on a full buffer past the Resilient Cycle threshold; null (test failed) when
	 * the rig never got there.
	 */
	private static ElectricHeaterBlockEntity pastHalfwayOverHeater(GameTestHelper helper, VulcanizerBlockEntity be,
			SkillSlot... slots) {
		OperationEnergyScenarios.own(be, OperationEnergyScenarios.mechanic(helper, slots));
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		stock(be, 1);
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);
		if (!OperationEnergyScenarios.pastHalfway(new OperationEnergyScenarios.Rig(be, () -> { }), helper,
				operationTicks())) {
			helper.fail("the vulcanizer never passed the Resilient Cycle threshold (progress "
					+ be.getDataAccess().get(2) + ", status " + be.status() + ")");
			return null;
		}
		heater.getEnergyStorage().setAmountUntracked(heaterEu());
		return heater;
	}

	/**
	 * TC-VULC-001-SKL01 — a tick Resilient Cycle runs on a trickle still buys its heat (MOD-751).
	 *
	 * <p>The skill waives the machine's own supply, never the heater's product: past halfway the buffer is
	 * cut below one tick's draw, the heater below is left to pay from its own charge, and every operation
	 * tick must cost it exactly one tariff until the batch finishes. Before MOD-751 the heat was drawn
	 * only on a PAID tick, so the whole coasting half ran on free heat. Then the other half of the rule, on
	 * the heat gate's own refusal: the heater is still found as a source, but cannot pay an overclocked
	 * tick, so the coasting batch stands still and the heater spends nothing.
	 *
	 * @implements TC-VULC-001-SKL01 — a coasting tick pays the electric heater; no heat, no progress.
	 */
	public static void skl01ResilientCycleTickPaysTheHeater(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		ElectricHeaterBlockEntity heater = pastHalfwayOverHeater(helper, be, SkillSlot.CAP);
		if (heater == null) {
			return;
		}
		int tariff = OperationEnergyScenarios.heaterTariff();
		OperationEnergyScenarios.HeatLedger ledger = OperationEnergyScenarios.trickleAgainstHeater(be, heater,
				helper, operationTicks());
		if (!ledger.finished() || ledger.unpaidTicks() != 0
				|| ledger.heaterSpent() != (long) tariff * ledger.operationTicks()) {
			helper.fail("a coasting tick must pay the heater one tariff (" + tariff + " EU) and finish the "
					+ "batch; " + ledger.describe());
			return;
		}

		// The next batch, with one overclocker: the heater holds exactly one BASE tariff, so the machine still
		// finds it as a heat source (resolve asks for the base price) but the tick costs the overclocked price,
		// and consumeForProgress refuses. The batch must stand exactly where it was — not advance on heat
		// nobody paid for, and not restart either, because the heat level it was priced at is still there.
		be.setItem(be.upgradeSlotStart(), new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get()));
		heater = pastHalfwayOverHeater(helper, be, SkillSlot.CAP);
		if (heater == null) {
			return;
		}
		if (be.overclockerCount() != 1) {
			helper.fail("setup failed: the vulcanizer must run one overclocker, runs " + be.overclockerCount());
			return;
		}
		heater.getEnergyStorage().setAmountUntracked(tariff);
		if (WorldHeatSources.resolve(helper.getLevel(), helper.absolutePos(MACHINE)) != HeatSource.ELECTRIC_HEATER) {
			helper.fail("setup failed: a hot heater holding one base tariff must still resolve as a heat source");
			return;
		}
		int progress = be.getDataAccess().get(2);
		ItemStack output = be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT).copy();
		for (int i = 0; i < 5; i++) {
			be.getEnergyStorage().setAmountUntracked(1L);
			drive(be, helper, 1);
		}
		if (be.getDataAccess().get(2) != progress
				|| !ItemStack.matches(output, be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT))
				|| heater.getEnergyStorage().getAmount() != tariff) {
			helper.fail("a coasting tick the heater cannot pay for must leave the batch where it stood: progress "
					+ progress + " -> " + be.getDataAccess().get(2) + ", output " + output + " -> "
					+ be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT) + ", heater "
					+ heater.getEnergyStorage().getAmount() + " EU");
			return;
		}
		helper.succeed();
	}

	/**
	 * The control for {@link #skl01ResilientCycleTickPaysTheHeater}: without Resilient Cycle the same trickle
	 * past halfway freezes the batch (R-NRG-10) and the heater spends nothing — so the first scenario's
	 * heat bill is the skill's, not the rig's.
	 */
	public static void skl02TrickleWithoutResilientCycleFreezes(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		ElectricHeaterBlockEntity heater = pastHalfwayOverHeater(helper, be);
		if (heater == null) {
			return;
		}
		int progress = be.getDataAccess().get(2);
		OperationEnergyScenarios.HeatLedger ledger = OperationEnergyScenarios.trickleAgainstHeater(be, heater,
				helper, operationTicks());
		if (ledger.operationTicks() != 0 || be.getDataAccess().get(2) != progress || ledger.heaterSpent() != 0) {
			helper.fail("without the skill a trickle below one tick's draw must freeze the batch at " + progress
					+ " and leave the heater untouched; " + ledger.describe());
			return;
		}
		helper.succeed();
	}
}
