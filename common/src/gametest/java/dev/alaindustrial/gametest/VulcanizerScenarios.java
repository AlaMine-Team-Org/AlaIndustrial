package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerStatus;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.core.heat.WorldHeatSources;
import dev.alaindustrial.registry.ModContent;
import java.util.Arrays;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral MOD-258 world scenarios. Fabric and NeoForge wrappers execute these same bodies, so
 * recipe registration, block entities and demand-driven heater behaviour cannot drift by loader.
 */
public final class VulcanizerScenarios {
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
		return Config.scaledDuration(Config.vulcanizerDuration) + 2;
	}

	private static void drive(MachineBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
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

		int cost = Config.electricHeaterEuPerTickEffective();
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
		int cost = Config.electricHeaterEuPerTickEffective();

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
		int cost = Config.electricHeaterEuPerTickEffective();
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

		if (restored.getEnergyStorage().getAmount() != 321L - Config.machineEuPerTickEffective()
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
}
