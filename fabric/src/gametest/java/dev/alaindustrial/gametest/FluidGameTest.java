package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the fluid-fed geothermal generator. Covers the bucket-fed path: a lava
 * bucket is consumed for EU and the empty bucket returned. Migrated from legacy {@code GEOTHERMAL}.
 * (Pump→tank fluid transport is a follow-up suite.)
 *
 * <p>MOD-445/MOD-323: the loader-neutral bodies live in {@code FluidMachineScenarios} (common) and
 * this class keeps the Fabric {@code @GameTest} wrappers. The bodies that read a Fabric-only
 * capability surface (Transfer API droplet boundary via {@code FluidStorage.SIDED}, foreign
 * {@code OddDropletStorage} adapters, and the {@code EnergyStorage.SIDED} face probe) stay here by
 * construction — see the individual tests below.
 */
public class FluidGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static GeothermalGeneratorBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModBlocks.GEOTHERMAL_GENERATOR, GeothermalGeneratorBlockEntity.class);
	}

	private static void drive(GeothermalGeneratorBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	/** Place a pump facing EAST — the pump acquires fluid only from that face. */
	private static PumpBlockEntity placePump(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.PUMP.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.EAST));
		PumpBlockEntity pump = helper.getBlockEntity(pos, PumpBlockEntity.class);
		if (pump == null) {
			helper.fail("pump block entity missing after placement");
		}
		return pump;
	}

	/**
	 * @implements TC-GEO-001-FUN01 — a lava bucket is consumed for EU and the empty bucket returned, and
	 *     the buffer grows by the exact per-tick rate × ticks. The buffer (4000) is far from full at 5
	 *     ticks × 16 EU/t = 80 EU, so the rate is read cleanly with no cap masking; a regression that
	 *     halves or doubles {@code geothermalEuPerTick} (or drops the conversion factor) is caught here,
	 *     not just by the neighbouring PRF01.
	 * @covers R-NRG-15
	 */
	@GameTest
	public void tcGeo001Fun01_lavaBucketProducesEu(GameTestHelper helper) {
		GeneratorEnergyScenarios.geothermalLavaBucketRate(helper);
	}

	/** @implements TC-GEO-001-NEG01 — no lava → no EU. @covers R-NRG-15 */
	@GameTest
	public void tcGeo001Neg01_noLavaNoEu(GameTestHelper helper) {
		GeneratorEnergyScenarios.geothermalNoLavaNoEu(helper);
	}

	/**
	 * @implements TC-FLUID-001-PUMP — a lava SOURCE feeds an EU-powered pump, which moves the lava into
	 * an adjacent geothermal generator's fluid tank; the generator then produces EU from that fluid with
	 * NO bucket item involved. Ported from {@code IndustrializationSelfTest.runFluidPumpCheck}.
	 * @covers R-CON-01 (fluid), R-NRG-15
	 */
	@GameTest
	public void tcFluidPump_lavaSourceToGeothermal(GameTestHelper helper) {
		FluidMachineScenarios.lavaSourceToGeothermal(helper);
	}

	// ============================================================================================
	// Geothermal generator — additional L2 cases (PHY/FUN/NEG/STA/PRF/CON), PER skipped per scope.
	// ============================================================================================

	/**
	 * @implements TC-GEO-001-PHY02 — the generator's collision shape is a full cube (16^3).
	 * @covers R-PHY-02
	 */
	@GameTest
	public void tcGeo001Phy02_hitboxIsFullCube(GameTestHelper helper) {
		FluidMachineScenarios.geothermalHitboxIsFullCube(helper);
	}

	/**
	 * @implements TC-GEO-001-FUN02 — a pump feeds lava into the generator's fluid tank; the generator
	 *     burns from the tank and its item slots stay empty (no bucket item involved).
	 * @covers R-NRG-15, R-CON-01
	 */
	@GameTest
	public void tcGeo001Fun02_pumpFillsTankAndBurnsWithoutBucket(GameTestHelper helper) {
		FluidMachineScenarios.geothermalPumpFillsTankAndBurnsWithoutBucket(helper);
	}

	/**
	 * @implements TC-GEO-001-FUN03 — the tank holds exactly 10 buckets' worth of burn ticks
	 *     (10 * geothermalBurnTicks), matching maxProgress/tankCapacity(); no overflow.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGeo001Fun03_tankHoldsTenBucketsOfBurnTicks(GameTestHelper helper) {
		FluidMachineScenarios.geothermalTankHoldsTenBucketsOfBurnTicks(helper);
	}

	/**
	 * @implements TC-GEO-001-FUN04 — the buffer grows to geothermalBuffer (4000 EU) and pauses there,
	 *     regardless of the lava-tick source (bucket or tank).
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcGeo001Fun04_bufferCapsAtGeothermalMax(GameTestHelper helper) {
		FluidMachineScenarios.geothermalBufferCapsAtGeothermalMax(helper);
	}

	/**
	 * @implements TC-GEO-001-FUN05 — a directly-adjacent LV consumer receives EU, draining the buffer.
	 * @covers R-NRG-03, R-CON-11
	 */
	@GameTest
	public void tcGeo001Fun05_pushesToAdjacentConsumer(GameTestHelper helper) {
		FluidMachineScenarios.geothermalPushesToAdjacentConsumer(helper);
	}

	/** @implements TC-GEO-001-NEG02 — the input slot rejects a non-lava-bucket item. @covers R-GUI-02 */
	@GameTest
	public void tcGeo001Neg02_slotRejectsNonLavaBucket(GameTestHelper helper) {
		FluidMachineScenarios.geothermalSlotRejectsNonLavaBucket(helper);
	}

	/** @implements TC-GEO-001-NEG03 — the fluid tank rejects a non-lava fluid via canInsert. @covers R-GUI-02 */
	@GameTest
	public void tcGeo001Neg03_tankRejectsNonLava(GameTestHelper helper) {
		FluidMachineScenarios.geothermalTankRejectsNonLava(helper);
	}

	/**
	 * @implements TC-GEO-001-NEG04 — a full energy buffer pauses the lavaTicks→EU conversion so
	 *     lava-ticks are not wasted (R-NRG-11). Lava intake (bucket→lavaTicks) is intentionally
	 *     NOT blocked: the bucket slot is cleared before the frozen-check so the two concerns are
	 *     tested independently.
	 * @covers R-NRG-11
	 */
	@GameTest
	public void tcGeo001Neg04_fullBufferPausesBurn(GameTestHelper helper) {
		FluidMachineScenarios.geothermalFullBufferPausesBurn(helper);
	}

	/**
	 * @implements TC-GEO-001-FUN06 — a lava bucket is consumed into lavaTicks even when the energy
	 *     buffer is full; the lavaTicks→EU step stays paused (R-NRG-11). This verifies that intake
	 *     and burn are decoupled: you can pre-load the lava buffer independently of energy state.
	 * @covers R-NRG-11
	 */
	@GameTest
	public void tcGeo001Fun06_lavaBucketLoadedWhenEnergyFull(GameTestHelper helper) {
		FluidMachineScenarios.geothermalLavaBucketLoadedWhenEnergyFull(helper);
	}

	/** @implements TC-GEO-001-NEG05 — the generator never accepts external EU (producer only). @covers R-NRG-03 */
	@GameTest
	public void tcGeo001Neg05_rejectsExternalEu(GameTestHelper helper) {
		FluidMachineScenarios.geothermalRejectsExternalEu(helper);
	}

	/**
	 * @implements TC-GEO-001-NEG06 — the tank never lets a neighbour extract lava back out
	 *     ({@code canExtract} is always false). Probed via an actual {@code extract()} call: Fabric's
	 *     {@code Storage#supportsExtraction()} is a coarse capability flag that defaults to {@code true}
	 *     for any {@code SingleVariantStorage} regardless of its {@code canExtract} override — the real
	 *     per-variant gate lives inside {@code extract()} itself (it checks {@code canExtract} before
	 *     moving anything), so that is the only way to observe "never extractable" here.
	 * @covers R-CON-08
	 */
	@GameTest
	public void tcGeo001Neg06_tankNeverExtractable(GameTestHelper helper) {
		FluidMachineScenarios.geothermalTankNeverExtractable(helper);
	}

	/**
	 * @implements TC-GEO-001-STA01 — the block's {@code lit} blockstate tracks whether it is burning
	 *     lava (bucket or tank feed) and clears once the fuel runs out.
	 * @covers R-VIS-01, R-VIS-03
	 */
	@GameTest
	public void tcGeo001Sta01_litStateTracksBurning(GameTestHelper helper) {
		FluidMachineScenarios.geothermalLitStateTracksBurning(helper);
	}

	/**
	 * @implements TC-GEO-001-CON01 — pairwise: each of the 5 non-FACING faces forms an EU link
	 *     (OUT-only); the FACING face (default NORTH) is energy-inert (D-FACING, R-NRG-03). An
	 *     adjacent AIR face yields no block entity / no crash.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcGeo001Con01_pairwiseFaces(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		BlockPos abs = geo.getBlockPos();
		for (Direction d : Direction.values()) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), abs, d);
			if (d == Direction.NORTH) {
				if (p != null) {
					helper.fail("geothermal generator FACING face (north) must be inert (no energy port)");
				}
				continue;
			}
			if (p == null || !p.supportsExtraction() || p.supportsInsertion()) {
				helper.fail("geothermal generator face " + d + " must be OUT-only");
			}
		}
		BlockPos airPos = POS.relative(Direction.UP);
		if (helper.getLevel().getBlockEntity(helper.absolutePos(airPos)) != null) {
			helper.fail("AIR face unexpectedly has a block entity at " + airPos);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-PRF01 — generation rate equals Config.geothermalEuPerTick (16 EU/t) while
	 *     lava burns.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGeo001Prf01_ratePerTickMatchesConfig(GameTestHelper helper) {
		FluidMachineScenarios.geothermalRatePerTickMatchesConfig(helper);
	}

	/**
	 * @implements TC-GEO-001-PRF02 — a single-tick transfer into an adjacent consumer is capped at the
	 *     LV per-tick transfer limit (EnergyTier.LV.maxVoltage() = 32 EU).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGeo001Prf02_packetCappedAtLv(GameTestHelper helper) {
		FluidMachineScenarios.geothermalPacketCappedAtLv(helper);
	}

	/**
	 * @implements TC-GEO-001-PRF03 — burning exactly 1 bucket of lava (from the slot) yields
	 * geothermalBurnTicks * geothermalEuPerTick total EU (16000), measured via cumulative buffer
	 * growth (draining the buffer between ticks so the cap never masks the sum).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGeo001Prf03_oneBucketYieldsTotalEu(GameTestHelper helper) {
		GeneratorEnergyScenarios.geothermalTankBucketBoundary(helper);
	}

	// ============================================================================================
	// Pump — additional L2 cases (NRG faces, FUN, NEG, PRF), PER skipped per scope.
	// ============================================================================================

	// NOTE: pump per-face energy roles are covered by EnergyFaceGameTest#rNrg03_pumpWorkingFacesInOnly
	// (FACING is inert, the other five faces are IN-only). An earlier rNrg03_pumpEveryFaceInOnly test
	// here asserted "all six faces IN" against the pre-MOD-061 code; the pump now follows the standard
	// horizontal-machine rule (FACING inert), so that duplicate was removed in favour of the more
	// precise EnergyFaceGameTest case, which also checks the null port on FACING.

	/**
	 * @implements TC-PUMP-001-FUN02 — with energy.getAmount() exactly pumpEuPerBucket (1000) and a lava
	 *     source in front of the pump (FACING face) and an empty tank, one tick acquires 1 bucket and
	 *     drains the EU to 0. This is also the suite's PRF evidence for pumpEuPerBucket=1000
	 *     (Config.pumpEuPerBucket, BVA row in pump.md).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcPump001Fun02_exactEuAcquiresOneBucket(GameTestHelper helper) {
		FluidMachineScenarios.pumpExactEuAcquiresOneBucket(helper);
	}

	/**
	 * @implements TC-PUMP-001-FUN03 — the pump pulls lava from an adjacent extractable fluid port (not a
	 *     world source) via {@code FluidMover.move}. A donor pump's tank is used as the extractable
	 *     neighbour: {@code PumpBlockEntity#fluidTank.canExtract} is always {@code true} (unlike the
	 *     geothermal generator's tank, whose {@code canExtract} is always false — R-CON-08 — so it
	 *     cannot serve as a donor here).
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Fun03_pullsFromAdjacentFluidStorage(GameTestHelper helper) {
		FluidMachineScenarios.pumpPullsFromAdjacentFluidStorage(helper);
	}

	/**
	 * @implements TC-PUMP-001-FUN04 — the pump pushes its ENTIRE tank (2 buckets) into an adjacent
	 *     insertable fluid storage in a single tick (not one bucket at a time).
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Fun04_pushesEntireTankInOneTick(GameTestHelper helper) {
		FluidMachineScenarios.pumpPushesEntireTankInOneTick(helper);
	}

	/**
	 * @implements TC-PUMP-001-FUN05 — the pump's tank progress (fluid already held) survives a
	 *     power-loss/power-restore cycle: no reset, no dupe, acquisition just resumes.
	 * @covers R-NRG-10
	 */
	@GameTest
	public void tcPump001Fun05_progressPersistsAcrossPowerLoss(GameTestHelper helper) {
		FluidMachineScenarios.pumpProgressPersistsAcrossPowerLoss(helper);
	}

	/**
	 * @implements TC-PUMP-001-FUN01 — end-to-end source -> tank -> sink, distinct from the existing
	 *     tcFluidPump_lavaSourceToGeothermal (kept under its original ID): asserts on the pump's own
	 *     fields (EU spent, tank amount) rather than only the geothermal generator's output.
	 * @covers R-CON-01, R-NRG-15
	 */
	@GameTest
	public void tcPump001Fun01_sourceToTankToSink(GameTestHelper helper) {
		FluidMachineScenarios.pumpSourceToTankToSink(helper);
	}

	/**
	 * @implements TC-PUMP-001-NEG01 — no lava source and no adjacent fluid storage: the pump never
	 *     acquires, never spends EU, and its tank stays empty.
	 * @covers R-NRG-06
	 */
	@GameTest
	public void tcPump001Neg01_noSourceNoAcquisition(GameTestHelper helper) {
		FluidMachineScenarios.pumpNoSourceNoAcquisition(helper);
	}

	/** @implements TC-PUMP-001-NEG02 — with no power, the pump never acquires lava. @covers R-NRG-06 */
	@GameTest
	public void tcPump001Neg02_noPowerNoAcquisition(GameTestHelper helper) {
		FluidMachineScenarios.pumpNoPowerNoAcquisition(helper);
	}

	/**
	 * @implements TC-PUMP-001-NEG03 — a full tank (4 buckets) pauses acquisition; EU is not spent and
	 *     the world source is untouched, even with power and a source present.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcPump001Neg03_fullTankPausesAcquisition(GameTestHelper helper) {
		FluidMachineScenarios.pumpFullTankPausesAcquisition(helper);
	}

	/**
	 * @implements TC-PUMP-001-NEG04 — a non-insertable neighbour (plain stone, no FluidStorage.SIDED)
	 *     does not receive lava; it stays in the pump's tank, no crash.
	 * @covers R-CON-10
	 */
	@GameTest
	public void tcPump001Neg04_noInsertableNeighbourNoPush(GameTestHelper helper) {
		FluidMachineScenarios.pumpNoInsertableNeighbourNoPush(helper);
	}

	/**
	 * @implements TC-PUMP-001-POS05 — flowing (non-source) lava allows acquiring connected source blocks.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Pos05_flowingLavaAcquiresSource(GameTestHelper helper) {
		FluidMachineScenarios.pumpFlowingLavaAcquiresSource(helper);
	}

	/**
	 * MOD-028 review finding #2 — guards the x81 mB<->droplet conversion AT THE DROPLET BOUNDARY (the
	 * surface a real foreign Fabric mod's fluid storage actually reads/writes through), not just via a
	 * round trip that happens entirely inside our own adapters. {@code tcPump001Fun04} inserts 2 buckets
	 * and later reads back {@code pump.fluidTank.amount} in mB — that path converts mB -> droplets ->
	 * mB and the two conversions cancel out, so a symmetric-but-wrong factor (e.g. both directions using
	 * 100 instead of 81) would still pass it. Here we read/write the published
	 * {@code Storage<FluidVariant>} (via the real {@code FluidStorage.SIDED} capability lookup registered
	 * for the pump in {@code ModBlockEntities}) directly, in droplets, and cross-check against the tank's
	 * own mB amount — pinning the factor and its direction independently.
	 *
	 * <p>Fabric-only by construction: the published {@code Storage<FluidVariant>} surface exists only on
	 * the Fabric Transfer API.
	 *
	 * @implements MOD-028 fluid-adapter droplet-boundary coverage (Fabric parity with
	 *     {@code NeoForgeFluidRuntimeTest#foreignHandlerExtractsExactlyOneBucketFromPump}).
	 * @covers R-CON-01
	 */
	@GameTest
	public void mod024_dropletBoundaryPinsExact81xConversion(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos pumpAbs = pump.getBlockPos();

		// Seed the pump's tank directly with exactly 1 bucket (bypassing acquireLava — this test targets
		// the capability adapter's unit conversion, not the acquisition tick logic).
		pump.fluidTank.fluid = FluidHolder.of(Fluids.LAVA);
		pump.fluidTank.amount = FluidAmounts.BUCKET;

		// Look the published capability up exactly as a foreign mod would: FluidStorage.SIDED.find.
		net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> storage =
				net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pumpAbs, Direction.NORTH);
		if (storage == null) {
			helper.fail("pump's fluid storage was not published via FluidStorage.SIDED");
			return;
		}

		long droplets = -1;
		long capacityDroplets = -1;
		for (net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> view
				: storage) {
			if (!view.isResourceBlank()) {
				droplets = view.getAmount();
				capacityDroplets = view.getCapacity();
			}
		}

		long expectedDroplets = FluidAmounts.BUCKET * FluidAmounts.FABRIC_DROPLETS_PER_MB; // 1000 * 81 = 81000
		long expectedCapacityDroplets = PumpBlockEntity.TANK_CAPACITY * FluidAmounts.FABRIC_DROPLETS_PER_MB;
		if (droplets != expectedDroplets) {
			helper.fail("published droplet-facing amount must be exactly " + expectedDroplets
					+ " (1 bucket x 81 droplets/mB) but was " + droplets);
		}
		if (capacityDroplets != expectedCapacityDroplets) {
			helper.fail("published droplet-facing capacity must be exactly " + expectedCapacityDroplets
					+ " but was " + capacityDroplets);
		}

		// Extract exactly FluidConstants.BUCKET (81000) droplets and assert the tank drops by exactly
		// 1000 mB — pins BOTH the read-side and the write-side conversion independently.
		long extractedDroplets;
		try (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction tx =
				net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
			extractedDroplets = storage.extract(
					net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(Fluids.LAVA),
					net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET, tx);
			tx.commit();
		}

		if (extractedDroplets != net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET) {
			helper.fail("expected to extract exactly FluidConstants.BUCKET (81000) droplets, got " + extractedDroplets);
		}
		if (pump.fluidTank.amount != 0) {
			helper.fail("pump tank (read directly, in mB) must drop by exactly 1 bucket (1000 mB) after "
					+ "extracting 81000 droplets, but has " + pump.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * A foreign fluid storage whose amounts are NOT whole millibuckets — the case the mod's own
	 * transfers never produce and therefore never tested. It hands over everything it holds (and accepts
	 * up to its remaining room) regardless of whether that figure divides by 81.
	 *
	 * <p>Deliberately not snapshot-participating: these scenarios commit, and a rollback-capable fake
	 * would only add noise to what is being pinned here (the adapter's unit arithmetic).
	 */
	private static final class OddDropletStorage
			implements net.fabricmc.fabric.api.transfer.v1.storage.Storage<
					net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> {
		private final net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant;
		private long droplets;
		private final long capacityDroplets;

		OddDropletStorage(net.minecraft.world.level.material.Fluid fluid, long droplets, long capacityDroplets) {
			this.variant = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid);
			this.droplets = droplets;
			this.capacityDroplets = capacityDroplets;
		}

		long droplets() {
			return droplets;
		}

		@Override
		public long insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant resource, long maxAmount,
				net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
			if (!resource.equals(variant) || maxAmount <= 0) {
				return 0;
			}
			long accepted = Math.min(maxAmount, capacityDroplets - droplets);
			droplets += accepted;
			return accepted;
		}

		@Override
		public long extract(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant resource, long maxAmount,
				net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
			if (!resource.equals(variant) || maxAmount <= 0) {
				return 0;
			}
			long given = Math.min(maxAmount, droplets);
			droplets -= given;
			return given;
		}

		@Override
		public java.util.Iterator<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<
				net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>> iterator() {
			net.fabricmc.fabric.api.transfer.v1.storage.StorageView<
					net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> view = new net.fabricmc.fabric.api.transfer.v1.storage.StorageView<>() {
				@Override
				public long extract(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant resource, long maxAmount,
						net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
					return OddDropletStorage.this.extract(resource, maxAmount, transaction);
				}

				@Override
				public boolean isResourceBlank() {
					return droplets <= 0;
				}

				@Override
				public net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant getResource() {
					return variant;
				}

				@Override
				public long getAmount() {
					return droplets;
				}

				@Override
				public long getCapacity() {
					return capacityDroplets;
				}
			};
			return java.util.List.of(view).iterator();
		}
	}

	/**
	 * MOD-284: a foreign storage yields 121 droplets — one whole millibucket plus a 40-droplet tail that
	 * the neutral mB contract cannot express. Flooring the report to 1 mB while letting the donor keep
	 * the loss destroys those 40 droplets outright. The adapter must hand the tail back, so the donor
	 * ends up down by exactly the reported amount and nothing evaporates.
	 *
	 * <p>This path is Fabric-only by construction: {@code NeoForgeFluidLookup} resolves our own blocks
	 * through {@code FluidPortHost} and never crosses a droplet conversion at all, so the NeoForge lane
	 * cannot catch a regression here.
	 *
	 * @implements MOD-284 droplet-tail conservation on extract
	 * @covers R-CON-01
	 */
	@GameTest
	public void mod284_extractFromOddForeignStorageDestroysNothing(GameTestHelper helper) {
		long odd = 121L; // 1 mB (81) + 40 droplets
		OddDropletStorage foreign = new OddDropletStorage(Fluids.LAVA, odd, 81_000L);
		dev.alaindustrial.core.fluid.FluidPort port =
				dev.alaindustrial.core.fabric.FabricFluidPort.of(foreign);
		long[] reportedMb = {0};
		EnergyTransactions.get().runCommitting(
				txn -> reportedMb[0] = port.extract(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));

		if (reportedMb[0] != 1L) {
			helper.fail("expected the adapter to report exactly 1 mB from 121 droplets, got " + reportedMb[0]);
			return;
		}
		long expectedLeft = odd - FluidAmounts.toDroplets(reportedMb[0]); // 121 - 81 = 40
		if (foreign.droplets() != expectedLeft) {
			helper.fail("donor must be down by exactly the reported 1 mB (81 droplets), leaving "
					+ expectedLeft + " droplets, but holds " + foreign.droplets()
					+ " — the sub-mB tail was destroyed instead of returned");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-284, the mirror case: a foreign storage has only 121 droplets of room. Reporting the floored
	 * 1 mB while letting it keep all 121 credits the receiver with more than the source is debited —
	 * fluid out of nowhere. The adapter must reclaim the 40-droplet tail.
	 *
	 * @implements MOD-284 droplet-tail conservation on insert
	 * @covers R-CON-01
	 */
	@GameTest
	public void mod284_insertIntoOddForeignStorageCreatesNothing(GameTestHelper helper) {
		long room = 121L;
		OddDropletStorage foreign = new OddDropletStorage(Fluids.LAVA, 0L, room);
		dev.alaindustrial.core.fluid.FluidPort port =
				dev.alaindustrial.core.fabric.FabricFluidPort.of(foreign);
		long[] reportedMb = {0};
		EnergyTransactions.get().runCommitting(
				txn -> reportedMb[0] = port.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));

		if (reportedMb[0] != 1L) {
			helper.fail("expected the adapter to report exactly 1 mB into 121 droplets of room, got "
					+ reportedMb[0]);
			return;
		}
		long expectedHeld = FluidAmounts.toDroplets(reportedMb[0]); // 81
		if (foreign.droplets() != expectedHeld) {
			helper.fail("receiver must hold exactly the reported 1 mB (81 droplets) but holds "
					+ foreign.droplets() + " — the extra tail was conjured out of nowhere");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN06 — the pump acquires WATER (not just lava) from a water source block in
	 *     front of it (FACING face). Generalised fluid intake, post-restoration: the tank whitelist accepts
	 *     both lava and water, and the source block is consumed like a lava source would be.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Fun06_acquiresWaterFromSource(GameTestHelper helper) {
		FluidMachineScenarios.pumpAcquiresWaterFromSource(helper);
	}

	/**
	 * @implements TC-PUMP-001-NEG06 — single-variant tank: once the tank holds lava, a water source in
	 *     front of the pump is NOT acquired (no mixing). The tank's single-variant guard rejects the water
	 *     and the EU is not spent. This is the core guarantee behind the pump's "one fluid at a time" rule.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Neg06_lavaTankRejectsWater(GameTestHelper helper) {
		FluidMachineScenarios.pumpLavaTankRejectsWater(helper);
	}

	/**
	 * @implements TC-PUMP-001-FUN07 — bucket feed via the GUI slots: a lava bucket in the input slot is
	 *     emptied into the tank (1 bucket), and the empty bucket drops into the output slot. No EU cost
	 *     (manual refill, not pumping). Mirrors the geothermal generator's bucket-emptying behaviour.
	 * @covers R-GUI-07
	 */
	@GameTest
	public void tcPump001Fun07_bucketEmptiesIntoTank(GameTestHelper helper) {
		FluidMachineScenarios.pumpBucketEmptiesIntoTank(helper);
	}

	/**
	 * @implements TC-PUMP-001-FUN08 — bucket drain via the GUI slots: an empty bucket in the drain-input
	 *     slot is filled from the tank (1 bucket), and the full lava bucket drops into the drain-output
	 *     slot. No EU cost (manual drain). The filled bucket always matches the tank's single-variant fluid.
	 * @covers R-GUI-07
	 */
	@GameTest
	public void tcPump001Fun08_fillsBucketFromTank(GameTestHelper helper) {
		FluidMachineScenarios.pumpFillsBucketFromTank(helper);
	}

	// ============================================================================================
	// FluidTank (MOD-028) — transaction semantics + legacy NBT-migration round trip.
	//
	// This coverage was designed as an L1 JUnit suite mirroring EnergyBufferTest, but FluidTank's
	// FluidHolder/FluidPort surface carries a net.minecraft.world.level.material.Fluid dependency, and
	// common's `test` sourceSet compiles/runs with NO Minecraft/NeoForge jar on its classpath at all
	// (verified: `./gradlew :common:dependencies --configuration testCompileClasspath` — only JUnit;
	// EnergyBufferTest/EnergyTierTest/EnergyShareTest all avoid Minecraft types entirely for exactly this
	// reason). Per the migration plan's documented fallback ("if L1 can't raise ValueInput without a
	// server, move to L2 with a comment"), this coverage lives here instead, using the real
	// FluidHolder/Fluids/ValueInput/ValueOutput available in a gametest's live ServerLevel.
	// ============================================================================================

	/** @implements FluidTank transaction rollback restores a positive amount without losing fluid identity. */
	@GameTest
	public void fluidTank_rollbackToPositiveAmountKeepsFluidIdentity(GameTestHelper helper) {
		FluidMachineScenarios.fluidTankRollbackToPositiveAmountKeepsFluidIdentity(helper);
	}

	/**
	 * @implements FluidTank full-drain-then-rollback keeps fluid identity — the cross-mod capability
	 *     contract regression. A full drain drives amount to exactly 0; on rollback to the pre-drain
	 *     amount the tank MUST still report which fluid it holds, or it becomes invisible to capability
	 *     readers (TankAsFluidStorage/TankAsResourceHandler report fluid()). extract() therefore does NOT
	 *     pre-clear fluid on a full drain — clearing happens at the transaction terminal only.
	 * @covers R-CON-01
	 */
	@GameTest
	public void fluidTank_fullDrainThenRollbackKeepsFluidIdentity(GameTestHelper helper) {
		FluidMachineScenarios.fluidTankFullDrainThenRollbackKeepsFluidIdentity(helper);
	}

	/**
	 * @implements MOD-028 NBT save-compat — legacy Fabric v0.1.0 "FluidTank" (droplets) loads correctly
	 *     when the new "FluidTankMb" key is absent, converting ÷81 and clamping to the new mB capacity.
	 * @covers R-PER-01
	 */
	@GameTest
	public void fluidTank_legacyDropletKeyMigratesToMbOnLoad(GameTestHelper helper) {
		FluidMachineScenarios.fluidTankLegacyDropletKeyMigratesToMbOnLoad(helper);
	}

	/**
	 * @implements MOD-028 NBT save-compat — a new "FluidTankMb" key takes priority over a stale legacy
	 *     "FluidTank" key when both are present.
	 * @covers R-PER-01
	 */
	@GameTest
	public void fluidTank_newMbKeyTakesPriorityOverLegacyKey(GameTestHelper helper) {
		FluidMachineScenarios.fluidTankNewMbKeyTakesPriorityOverLegacyKey(helper);
	}

	/**
	 * @implements MOD-126 — a bucket-fed geothermal generator publishes its lava through the fluid
	 *     capability so HUD mods (Jade / WTHIT / TOP) see it instead of "Empty". Bucket lava lands in the
	 *     burn reserve ({@code lavaTicks}), not the raw {@code fluidTank}, so before the fix the published
	 *     {@code Storage<FluidVariant>} reported a blank resource (capacity but no fluid) — exactly what a
	 *     HUD renders as "Empty". This reads the capability the way Jade does on the server (
	 *     {@code FluidStorage.SIDED.find} with side {@code null}, iterating {@code StorageView}s) and
	 *     asserts a non-blank LAVA view with amount > 0. Fails without the {@code LavaFuelView} fix.
	 *
	 *     <p>Fabric-only by construction: the read goes through the Fabric Transfer API
	 *     {@code FluidStorage.SIDED} surface, which does not exist on the common/NeoForge lane.</p>
	 * @covers R-CON-01
	 */
	@GameTest
	public void mod126_bucketFedGeothermalPublishesLavaToHud(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		// One tick loads the bucket's worth of lava into the burn reserve (lavaTicks); the raw fluidTank
		// stays empty on this path — that is the whole point of the bug.
		drive(geo, helper, 2);
		if (geo.fluidTank.amount != 0) {
			helper.fail("precondition: bucket-fed lava must sit in the burn reserve, not the raw fluidTank; "
					+ "fluidTank held " + geo.fluidTank.amount + " mB");
			return;
		}

		// Read the published capability exactly as Jade's server-side FluidStorageProvider does: side = null.
		net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> storage =
				net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, geo.getBlockPos(), null);
		if (storage == null) {
			helper.fail("geothermal generator did not publish a fluid capability (side=null)");
			return;
		}

		boolean sawLava = false;
		long amountDroplets = 0;
		long capacityDroplets = 0;
		for (net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> view
				: storage) {
			if (!view.isResourceBlank()) {
				sawLava = view.getResource().getFluid() == Fluids.LAVA;
				amountDroplets = view.getAmount();
				capacityDroplets = view.getCapacity();
			}
		}

		if (!sawLava || amountDroplets <= 0) {
			helper.fail("HUD read: bucket-fed generator must publish a non-blank LAVA view with amount > 0, "
					+ "got sawLava=" + sawLava + " amountDroplets=" + amountDroplets + " (this is the MOD-126 bug)");
			return;
		}
		long expectedCapacityDroplets =
				GeothermalGeneratorBlockEntity.TANK_CAPACITY * FluidAmounts.FABRIC_DROPLETS_PER_MB;
		if (capacityDroplets != expectedCapacityDroplets) {
			helper.fail("published capacity must stay the single 10-bucket gauge (" + expectedCapacityDroplets
					+ " droplets), got " + capacityDroplets);
		}
		helper.succeed();
	}

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

	/**
	 * Cross-loader numeric parity oracle for the fluid feature: lava source → pump → geothermal tank → EU,
	 * pinning the SAME exact numbers {@link #tcFluidPump_lavaSourceToGeothermal} asserts, so a conversion-factor
	 * slip on one loader cannot pass both suites. Body: {@link CoreFluidScenarios#sourceToPumpToGeoToEu}.
	 */
	@GameTest(maxTicks = 100)
	public void coreFluid_sourceToPumpToGeoToEu(GameTestHelper helper) {
		CoreFluidScenarios.sourceToPumpToGeoToEu(helper);
	}

	/**
	 * Compressed end-to-end fluid transport check (pump source → geothermal tank → EU) — the loader-neutral
	 * twin of {@link #tcFluidPump_lavaSourceToGeothermal}. Body: {@link WorldContentScenarios#pumpSourceToTankToSinkToEu}.
	 */
	@GameTest(maxTicks = 100)
	public void worldContent_pumpSourceToTankToSinkToEu(GameTestHelper helper) {
		WorldContentScenarios.pumpSourceToTankToSinkToEu(helper);
	}
}
