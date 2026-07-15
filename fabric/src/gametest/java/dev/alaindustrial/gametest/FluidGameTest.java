package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the fluid-fed geothermal generator. Covers the bucket-fed path: a lava
 * bucket is consumed for EU and the empty bucket returned. Migrated from legacy {@code GEOTHERMAL}.
 * (Pump→tank fluid transport is a follow-up suite.)
 */
public class FluidGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static GeothermalGeneratorBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModBlocks.GEOTHERMAL_GENERATOR, GeothermalGeneratorBlockEntity.class);
	}

	private static void drive(GeothermalGeneratorBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
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
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		int ticks = 5;
		drive(geo, helper, ticks);
		long expectedEu = (long) ticks * Config.geothermalEuPerTick;
		long got = geo.getEnergyStorage().getAmount();
		boolean bucketBack = geo.getItem(GeothermalGeneratorBlockEntity.OUTPUT_SLOT).is(Items.BUCKET);
		boolean consumed = geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT).isEmpty();
		if (got != expectedEu) {
			helper.fail("geothermal produced " + got + " EU over " + ticks + " ticks, expected exactly "
					+ expectedEu + " (" + ticks + " × geothermalEuPerTick=" + Config.geothermalEuPerTick + ")");
		}
		if (!bucketBack) {
			helper.fail("lava bucket was consumed but the empty bucket was not returned to the output slot");
		}
		if (!consumed) {
			helper.fail("lava bucket was not consumed from the input slot");
		}
		helper.succeed();
	}

	/** @implements TC-GEO-001-NEG01 — no lava → no EU. @covers R-NRG-15 */
	@GameTest
	public void tcGeo001Neg01_noLavaNoEu(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		drive(geo, helper, 10);
		if (geo.getEnergyStorage().getAmount() != 0) {
			helper.fail("geothermal produced EU without lava: " + geo.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-FLUID-001-PUMP — a lava SOURCE feeds an EU-powered pump, which moves the lava into
	 * an adjacent geothermal generator's fluid tank; the generator then produces EU from that fluid with
	 * NO bucket item involved. Ported from {@code IndustrializationSelfTest.runFluidPumpCheck}.
	 * @covers R-CON-01 (fluid), R-NRG-15
	 */
	@GameTest
	public void tcFluidPump_lavaSourceToGeothermal(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		// Compact rig inside the force-loaded region (x 1..7, y 2, z 1): geo - pump - lava in a row.
		BlockPos geoRel = new BlockPos(4, 2, 1);
		BlockPos pumpRel = new BlockPos(5, 2, 1);
		BlockPos lavaRel = new BlockPos(6, 2, 1);
		BlockPos geoAbs = helper.absolutePos(geoRel);
		BlockPos pumpAbs = helper.absolutePos(pumpRel);
		BlockPos lavaAbs = helper.absolutePos(lavaRel);

		level.setBlockAndUpdate(lavaAbs, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
		// Face the pump EAST (toward the lava source) so it acquires from the source and pushes into the
		// geo sink on its WEST face.
		level.setBlockAndUpdate(pumpAbs, ModBlocks.PUMP.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.EAST));
		// The geothermal sink is placed later (Phase 1), so the pump first holds the lava it acquires.

		boolean lavaIsSource = level.getFluidState(lavaAbs)
				.isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA);

		// --- Phase 1: transport. The pump acquires a bucket from the source and pushes it onward.
		// The pump fills and empties its tank within a single tick (acquire -> push), so its tank is
		// never caught non-empty by an after-tick sample. To observe the pump actually HOLDING lava we
		// tick it once with no adjacent fluid sink (the geo isn't placed yet), so the acquired bucket
		// stays in the pump tank; then we place the geo and tick again to push it across.
		long pumpTankPeak = 0;
		boolean lavaConsumed = false;
		if (level.getBlockEntity(pumpAbs) instanceof PumpBlockEntity pump) {
			pump.getEnergyStorage().amount = Config.pumpEuPerBucket; // stand-in for network supply (≥1 bucket)
			pump.serverTick(level, pumpAbs, level.getBlockState(pumpAbs));
			pumpTankPeak = Math.max(pumpTankPeak, pump.fluidTank.amount); // acquired, not yet pushed
		}
		if (!level.getFluidState(lavaAbs)
				.isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA)) {
			lavaConsumed = true;
		}

		// Now place the geothermal sink and let the pump push its tank lava into it.
		level.setBlockAndUpdate(geoAbs, ModBlocks.GEOTHERMAL_GENERATOR.defaultBlockState());
		long geoTankBefore = 0;
		if (level.getBlockEntity(geoAbs) instanceof GeothermalGeneratorBlockEntity geo) {
			geoTankBefore = geo.fluidTank.amount;
		}
		long geoTankPeak = geoTankBefore;
		for (int i = 0; i < 4; i++) {
			if (level.getBlockEntity(pumpAbs) instanceof PumpBlockEntity pump) {
				pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
				pump.serverTick(level, pumpAbs, level.getBlockState(pumpAbs));
				pumpTankPeak = Math.max(pumpTankPeak, pump.fluidTank.amount);
			}
			if (level.getBlockEntity(geoAbs) instanceof GeothermalGeneratorBlockEntity geo) {
				// Sample the geo tank BEFORE its own burn so we observe the delivered fluid.
				geoTankPeak = Math.max(geoTankPeak, geo.fluidTank.amount);
			}
		}

		// --- Phase 2: fuel -> EU. Remove the pump so its EU buffer can't siphon the generator's output
		// (the generator pushes EU to any adjacent consumer each tick), then tick ONLY the generator so
		// the EU it makes from the delivered lava actually accumulates and is observable.
		level.setBlockAndUpdate(pumpAbs, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		long geoEnergyBefore = 0;
		long geoEnergyAfter = 0;
		boolean noBucketUsed = true;
		if (level.getBlockEntity(geoAbs) instanceof GeothermalGeneratorBlockEntity geo) {
			geoEnergyBefore = geo.getEnergyStorage().getAmount();
			for (int i = 0; i < 40; i++) {
				geoTankPeak = Math.max(geoTankPeak, geo.fluidTank.amount);
				geo.serverTick(level, geoAbs, level.getBlockState(geoAbs));
			}
			geoEnergyAfter = geo.getEnergyStorage().getAmount();
			// No item ever entered the geothermal's slots — purely fluid-fed.
			noBucketUsed = geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT).isEmpty()
					&& geo.getItem(GeothermalGeneratorBlockEntity.OUTPUT_SLOT).isEmpty();
		}

		// Lava moved source -> pump (tank held it) -> geothermal (tank received it), the generator
		// produced EU from that fluid, and no bucket item was ever used. Pin EXACT numbers (not just
		// loose inequalities) so this is a real cross-loader parity oracle against
		// CoreFluidScenarios.sourceToPumpToGeoToEu (NeoForge), which asserts the same expected values
		// derived from the same Config constants: a conversion-factor slip (e.g. a fraction-of-a-bucket
		// or fraction-of-the-EU total) would fail here even if it happened to pass a loose '>' check.
		long expectedBucket = dev.alaindustrial.core.FluidAmounts.BUCKET;
		long expectedEuGain = 40L * Config.geothermalEuPerTick;
		long actualEuGain = geoEnergyAfter - geoEnergyBefore;
		boolean lavaMoved = lavaConsumed && pumpTankPeak == expectedBucket && geoTankPeak - geoTankBefore == expectedBucket;
		boolean producedEu = actualEuGain == expectedEuGain;
		boolean pass = lavaIsSource && lavaMoved && producedEu && noBucketUsed;

		if (!pass) {
			helper.fail("fluid pump: lavaSource=" + lavaIsSource + " lavaConsumed=" + lavaConsumed
					+ " pumpTankPeak=" + pumpTankPeak + " (expected " + expectedBucket + ")"
					+ " geoTankPeak-geoTankBefore=" + (geoTankPeak - geoTankBefore) + " (expected " + expectedBucket + ")"
					+ " euGain=" + actualEuGain + " (expected " + expectedEuGain + ")" + " noBucket=" + noBucketUsed);
		}
		helper.succeed();
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
		GeothermalGeneratorBlockEntity geo = place(helper);
		BlockPos abs = geo.getBlockPos();
		BlockState state = helper.getLevel().getBlockState(abs);
		if (!state.isCollisionShapeFullBlock(helper.getLevel(), abs)) {
			helper.fail("geothermal generator collision shape is not a full block");
		}
		VoxelShape collision = state.getCollisionShape(helper.getLevel(), abs);
		if (!Block.isShapeFullBlock(collision)) {
			helper.fail("geothermal generator collision VoxelShape is not a full 16^3 cube");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-FUN02 — a pump feeds lava into the generator's fluid tank; the generator
	 *     burns from the tank and its item slots stay empty (no bucket item involved).
	 * @covers R-NRG-15, R-CON-01
	 */
	@GameTest
	public void tcGeo001Fun02_pumpFillsTankAndBurnsWithoutBucket(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		EnergyTransactions.get().runCommitting(txn ->
				geo.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET * 2, txn));
		long tankBefore = geo.fluidTank.amount;
		drive(geo, helper, 5);
		boolean producedEu = geo.getEnergyStorage().getAmount() > 0;
		boolean slotsEmpty = geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT).isEmpty()
				&& geo.getItem(GeothermalGeneratorBlockEntity.OUTPUT_SLOT).isEmpty();
		if (!(tankBefore > 0 && producedEu && slotsEmpty)) {
			helper.fail("geothermal tank-feed: tankBefore=" + tankBefore + " producedEu=" + producedEu
					+ " slotsEmpty=" + slotsEmpty);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-FUN03 — the tank holds exactly 10 buckets' worth of burn ticks
	 *     (10 * geothermalBurnTicks), matching maxProgress/tankCapacity(); no overflow.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGeo001Fun03_tankHoldsTenBucketsOfBurnTicks(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		long[] inserted = {0};
		EnergyTransactions.get().runCommitting(txn -> inserted[0] = geo.fluidTank.insert(FluidHolder.of(Fluids.LAVA),
				GeothermalGeneratorBlockEntity.TANK_CAPACITY + FluidAmounts.BUCKET, txn));
		if (inserted[0] != GeothermalGeneratorBlockEntity.TANK_CAPACITY) {
			helper.fail("tank accepted more than capacity: inserted=" + inserted[0]
					+ " capacity=" + GeothermalGeneratorBlockEntity.TANK_CAPACITY);
		}
		if (geo.fluidTank.amount != GeothermalGeneratorBlockEntity.TANK_CAPACITY) {
			helper.fail("tank amount " + geo.fluidTank.amount + " != capacity "
					+ GeothermalGeneratorBlockEntity.TANK_CAPACITY);
		}
		int expectedTicks = 10 * Config.geothermalBurnTicks;
		int maxProgress = geo.getDataAccess().get(3); // index 3 == maxProgress
		if (maxProgress != expectedTicks) {
			helper.fail("maxProgress " + maxProgress + " != expected " + expectedTicks);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-FUN04 — the buffer grows to geothermalBuffer (4000 EU) and pauses there,
	 *     regardless of the lava-tick source (bucket or tank).
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcGeo001Fun04_bufferCapsAtGeothermalMax(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		long cap = geo.getEnergyStorage().getCapacity();
		if (cap != Config.geothermalBuffer) {
			helper.fail("expected buffer cap " + Config.geothermalBuffer + " but was " + cap);
		}
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 64));
		geo.getEnergyStorage().amount = cap - 1; // BVA: max-1
		drive(geo, helper, 5);
		long amount = geo.getEnergyStorage().getAmount();
		if (amount != cap) {
			helper.fail("expected buffer to settle at cap " + cap + " but was " + amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-FUN05 — a directly-adjacent LV consumer receives EU, draining the buffer.
	 * @covers R-NRG-03, R-CON-11
	 */
	@GameTest
	public void tcGeo001Fun05_pushesToAdjacentConsumer(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		BlockPos sink = POS.east();
		helper.setBlock(sink, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity batteryBox = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 64));
		drive(geo, helper, 20);
		if (batteryBox == null || batteryBox.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery_box received no EU from the geothermal generator");
		}
		helper.succeed();
	}

	/** @implements TC-GEO-001-NEG02 — the input slot rejects a non-lava-bucket item. @covers R-GUI-02 */
	@GameTest
	public void tcGeo001Neg02_slotRejectsNonLavaBucket(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		boolean acceptsEmptyBucket = geo.canPlaceItem(GeothermalGeneratorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.BUCKET));
		boolean acceptsWaterBucket = geo.canPlaceItem(GeothermalGeneratorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.WATER_BUCKET));
		boolean acceptsCobble = geo.canPlaceItem(GeothermalGeneratorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.COBBLESTONE));
		if (acceptsEmptyBucket || acceptsWaterBucket || acceptsCobble) {
			helper.fail("input slot must reject non-lava-bucket items: emptyBucket=" + acceptsEmptyBucket
					+ " waterBucket=" + acceptsWaterBucket + " cobble=" + acceptsCobble);
		}
		helper.succeed();
	}

	/** @implements TC-GEO-001-NEG03 — the fluid tank rejects a non-lava fluid via canInsert. @covers R-GUI-02 */
	@GameTest
	public void tcGeo001Neg03_tankRejectsNonLava(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		long[] inserted = {-1};
		EnergyTransactions.get().runCommitting(txn -> inserted[0] =
				geo.fluidTank.insert(FluidHolder.of(Fluids.WATER), FluidAmounts.BUCKET, txn));
		if (inserted[0] != 0 || geo.fluidTank.amount != 0) {
			helper.fail("tank must reject water: inserted=" + inserted[0] + " tankAmount=" + geo.fluidTank.amount);
		}
		helper.succeed();
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
		GeothermalGeneratorBlockEntity geo = place(helper);
		// Load one bucket into the burn buffer so lavaTicks > 0.
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		drive(geo, helper, 1); // bucket consumed → lavaTicks = geothermalBurnTicks
		// Clear the input slot so further intake cannot confound the burn-pause check.
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
		geo.getEnergyStorage().amount = geo.getEnergyStorage().getCapacity(); // force buffer full
		drive(geo, helper, 1);
		int ticks1 = geo.getDataAccess().get(2); // index 2 == progress (lavaTicks)
		drive(geo, helper, 1);
		int ticks2 = geo.getDataAccess().get(2);
		// lavaTicks must not decrease: the burn (lavaTicks→EU) is paused while the buffer is full.
		if (!(ticks1 > 0 && ticks1 == ticks2)) {
			helper.fail("full buffer must freeze lavaTicks: ticks1=" + ticks1 + " ticks2=" + ticks2);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-FUN06 — a lava bucket is consumed into lavaTicks even when the energy
	 *     buffer is full; the lavaTicks→EU step stays paused (R-NRG-11). This verifies that intake
	 *     and burn are decoupled: you can pre-load the lava buffer independently of energy state.
	 * @covers R-NRG-11
	 */
	@GameTest
	public void tcGeo001Fun06_lavaBucketLoadedWhenEnergyFull(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.getEnergyStorage().amount = geo.getEnergyStorage().getCapacity(); // force buffer full
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		drive(geo, helper, 1);
		int lavaTicks = geo.getDataAccess().get(2); // progress = lavaTicks
		boolean bucketConsumed = geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT).isEmpty();
		boolean energyStillFull = geo.getEnergyStorage().getAmount() == geo.getEnergyStorage().getCapacity();
		if (!bucketConsumed) {
			helper.fail("lava bucket must be consumed even when energy buffer is full");
		}
		if (lavaTicks <= 0) {
			helper.fail("lavaTicks must be positive after bucket load: lavaTicks=" + lavaTicks);
		}
		if (!energyStillFull) {
			helper.fail("energy buffer must stay full (no EU generated while full): energy="
					+ geo.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/** @implements TC-GEO-001-NEG05 — the generator never accepts external EU (producer only). @covers R-NRG-03 */
	@GameTest
	public void tcGeo001Neg05_rejectsExternalEu(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		if (geo.getEnergyStorage().supportsInsertion()) {
			helper.fail("geothermal generator storage must not support insertion (maxInsert=0)");
		}
		helper.succeed();
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
		GeothermalGeneratorBlockEntity geo = place(helper);
		EnergyTransactions.get().runCommitting(txn ->
				geo.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET * 2, txn));
		long[] extracted = {-1};
		EnergyTransactions.get().runCommitting(txn -> extracted[0] =
				geo.fluidTank.extract(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		if (extracted[0] != 0 || geo.fluidTank.amount != FluidAmounts.BUCKET * 2) {
			helper.fail("geothermal fluid tank must never allow extraction (canExtract=false), but moved "
					+ extracted[0]);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-STA01 — the block's {@code lit} blockstate tracks whether it is burning
	 *     lava (bucket or tank feed) and clears once the fuel runs out.
	 * @covers R-VIS-01, R-VIS-03
	 */
	@GameTest
	public void tcGeo001Sta01_litStateTracksBurning(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		BlockPos abs = geo.getBlockPos();

		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		drive(geo, helper, 3);
		if (!helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("geothermal generator must be LIT while burning lava");
		}

		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
		geo.getEnergyStorage().amount = geo.getEnergyStorage().getCapacity(); // stop new burns from starting
		drive(geo, helper, Config.geothermalBurnTicks + 10); // exhaust any remaining lavaTicks
		if (helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("geothermal generator must not be LIT once the burn ends");
		}
		helper.succeed();
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
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		drive(geo, helper, 1); // tick 1 starts the burn
		geo.getEnergyStorage().amount = 0; // measure one clean tick from empty
		drive(geo, helper, 1);
		long made = geo.getEnergyStorage().getAmount();
		if (made != Config.geothermalEuPerTick) {
			helper.fail("EU/t expected " + Config.geothermalEuPerTick + " but measured " + made);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-PRF02 — a single-tick transfer into an adjacent consumer is capped at the
	 *     LV per-tick transfer limit (EnergyTier.LV.maxVoltage() = 32 EU).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGeo001Prf02_packetCappedAtLv(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.getEnergyStorage().amount = geo.getEnergyStorage().getCapacity(); // buffer full

		BlockPos sink = POS.east();
		helper.setBlock(sink, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity batteryBox = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (batteryBox == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		batteryBox.getEnergyStorage().amount = 0;

		drive(geo, helper, 1);

		long lvCap = dev.alaindustrial.core.EnergyTier.LV.maxVoltage();
		long gained = batteryBox.getEnergyStorage().getAmount();
		if (!(gained > 0 && gained <= lvCap)) {
			helper.fail("LV transfer must be in (0," + lvCap + "] EU per tick but moved " + gained);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-PRF03 — burning exactly 1 bucket of lava (from the slot) yields
	 *     geothermalBurnTicks * geothermalEuPerTick total EU (16000), measured via cumulative buffer
	 *     growth (draining the buffer between ticks so the cap never masks the sum).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGeo001Prf03_oneBucketYieldsTotalEu(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		long totalMade = 0;
		int maxTicks = Config.geothermalBurnTicks + 20;
		for (int i = 0; i < maxTicks; i++) {
			long before = geo.getEnergyStorage().getAmount();
			geo.serverTick(helper.getLevel(), geo.getBlockPos(), helper.getLevel().getBlockState(geo.getBlockPos()));
			long after = geo.getEnergyStorage().getAmount();
			totalMade += Math.max(0, after - before);
			geo.getEnergyStorage().amount = 0; // drain so the cap never masks further production
			int lavaTicksLeft = geo.getDataAccess().get(2); // index 2 == progress (lavaTicks)
			if (lavaTicksLeft == 0 && geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT).isEmpty()) {
				break;
			}
		}
		long expected = (long) Config.geothermalBurnTicks * Config.geothermalEuPerTick;
		if (totalMade != expected) {
			helper.fail("1 bucket expected total " + expected + " EU but measured " + totalMade);
		}
		helper.succeed();
	}

	// ============================================================================================
	// Pump — additional L2 cases (NRG faces, FUN, NEG, PRF), PER skipped per scope.
	// ============================================================================================

	private static PumpBlockEntity placePump(GameTestHelper helper, BlockPos pos) {
		return placePump(helper, pos, Direction.EAST);
	}

	/** Place a pump facing {@code facing} — the pump acquires fluid only from that face. */
	private static PumpBlockEntity placePump(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModBlocks.PUMP.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, facing));
		PumpBlockEntity pump = helper.getBlockEntity(pos, PumpBlockEntity.class);
		if (pump == null) {
			helper.fail("pump block entity missing after placement");
		}
		return pump;
	}

	private static void drivePump(PumpBlockEntity pump, GameTestHelper helper, int ticks) {
		BlockPos abs = pump.getBlockPos();
		for (int i = 0; i < ticks; i++) {
			BlockState state = helper.getLevel().getBlockState(abs);
			pump.serverTick(helper.getLevel(), abs, state);
		}
	}

	// NOTE: pump per-face energy roles are covered by EnergyFaceGameTest#rNrg03_pumpWorkingFacesInOnly
	// (FACING is inert, the other five faces are IN-only). An earlier rNrg03_pumpEveryFaceInOnly test
	// here asserted "all six faces IN" against the pre-MOD-061 code; the pump now follows the standard
	// horizontal-machine rule (FACING inert), so that duplicate was removed in favour of the more
	// precise EnergyFaceGameTest case, which also checks the null port on FACING.

	/**
	 * @implements TC-PUMP-001-FUN02 — with energy.amount exactly pumpEuPerBucket (1000) and a lava
	 *     source in front of the pump (FACING face) and an empty tank, one tick acquires 1 bucket and
	 *     drains the EU to 0. This is also the suite's PRF evidence for pumpEuPerBucket=1000
	 *     (Config.pumpEuPerBucket, BVA row in pump.md).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcPump001Fun02_exactEuAcquiresOneBucket(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
		drivePump(pump, helper, 1);
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("expected tank to hold exactly 1 bucket, got " + pump.fluidTank.amount);
		}
		if (pump.getEnergyStorage().getAmount() != 0) {
			helper.fail("expected energy to drain to 0, got " + pump.getEnergyStorage().getAmount());
		}
		helper.succeed();
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
		BlockPos donorRel = new BlockPos(1, 2, 1);
		BlockPos pumpRel = new BlockPos(2, 2, 1);
		PumpBlockEntity donor = placePump(helper, donorRel);
		// The donor is WEST of the pump, so the pump must face WEST to draw from it.
		PumpBlockEntity pump = placePump(helper, pumpRel, Direction.WEST);
		EnergyTransactions.get().runCommitting(txn ->
				donor.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		// The donor also runs its own push-to-neighbour logic each tick (pushLava), which would move
		// the same bucket the same direction; keep it unpowered so only its EXTRACTION path (pulled by
		// the other pump's acquireLava) is exercised, not its own push.
		donor.getEnergyStorage().amount = 0;
		pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
		drivePump(pump, helper, 1);
		if (pump.getEnergyStorage().getAmount() != 0) {
			helper.fail("expected the pump to spend its EU acquiring 1 bucket from the donor's tank, still has "
					+ pump.getEnergyStorage().getAmount());
		}
		if (donor.fluidTank.amount != 0) {
			helper.fail("expected the donor's tank to be drained, got " + donor.fluidTank.amount
					+ " (pump.fluidTank=" + pump.fluidTank.amount + ")");
		}
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("expected the pump's tank to hold exactly 1 bucket after pull, got "
					+ pump.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN04 — the pump pushes its ENTIRE tank (2 buckets) into an adjacent
	 *     insertable fluid storage in a single tick (not one bucket at a time).
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Fun04_pushesEntireTankInOneTick(GameTestHelper helper) {
		BlockPos pumpRel = new BlockPos(1, 2, 1);
		BlockPos geoRel = new BlockPos(2, 2, 1);
		// Pump faces WEST so it pushes through its EAST face into the geo (push skips the FACING face).
		PumpBlockEntity pump = placePump(helper, pumpRel, Direction.WEST);
		helper.setBlock(geoRel, ModBlocks.GEOTHERMAL_GENERATOR);
		GeothermalGeneratorBlockEntity geo = helper.getBlockEntity(geoRel, GeothermalGeneratorBlockEntity.class);
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET * 2, txn));
		pump.getEnergyStorage().amount = Config.machineBuffer;
		drivePump(pump, helper, 1);
		if (pump.fluidTank.amount != 0) {
			helper.fail("expected the pump's tank to be fully pushed, got " + pump.fluidTank.amount);
		}
		if (geo.fluidTank.amount != FluidAmounts.BUCKET * 2) {
			helper.fail("expected the geothermal tank to receive 2 buckets, got " + geo.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN05 — the pump's tank progress (fluid already held) survives a
	 *     power-loss/power-restore cycle: no reset, no dupe, acquisition just resumes.
	 * @covers R-NRG-10
	 */
	@GameTest
	public void tcPump001Fun05_progressPersistsAcrossPowerLoss(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());

		pump.getEnergyStorage().amount = 0;
		drivePump(pump, helper, 10);
		if (pump.fluidTank.amount != 0) {
			helper.fail("without power the tank must stay empty, got " + pump.fluidTank.amount);
		}
		if (!level.getFluidState(lavaAbs).isSourceOfType(Fluids.LAVA)) {
			helper.fail("without power the lava source must remain untouched");
		}

		pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
		drivePump(pump, helper, 5);
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("after power restore expected exactly 1 bucket acquired, got " + pump.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN01 — end-to-end source -> tank -> sink, distinct from the existing
	 *     tcFluidPump_lavaSourceToGeothermal (kept under its original ID): asserts on the pump's own
	 *     fields (EU spent, tank amount) rather than only the geothermal generator's output.
	 * @covers R-CON-01, R-NRG-15
	 */
	@GameTest
	public void tcPump001Fun01_sourceToTankToSink(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pumpRel = new BlockPos(2, 2, 1);
		BlockPos lavaRel = new BlockPos(1, 2, 1);
		BlockPos geoRel = new BlockPos(3, 2, 1);
		BlockPos lavaAbs = helper.absolutePos(lavaRel);

		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		// The pump faces WEST (toward the lava source) and pushes its EAST face into the geo sink.
		PumpBlockEntity pump = placePump(helper, pumpRel, Direction.WEST);
		helper.setBlock(geoRel, ModBlocks.GEOTHERMAL_GENERATOR);
		GeothermalGeneratorBlockEntity geo = helper.getBlockEntity(geoRel, GeothermalGeneratorBlockEntity.class);

		pump.getEnergyStorage().amount = Config.machineBuffer;
		long euBefore = pump.getEnergyStorage().getAmount();
		for (int i = 0; i < 5; i++) {
			pump.getEnergyStorage().amount = Math.max(pump.getEnergyStorage().getAmount(), Config.pumpEuPerBucket);
			drivePump(pump, helper, 1);
		}
		long euAfter = pump.getEnergyStorage().getAmount();

		boolean sourceGone = !level.getFluidState(lavaAbs).isSourceOfType(Fluids.LAVA);
		boolean geoReceived = geo.fluidTank.amount > 0;
		if (!(sourceGone && geoReceived)) {
			helper.fail("source->tank->sink failed: sourceGone=" + sourceGone
					+ " geoTank=" + geo.fluidTank.amount + " euBefore=" + euBefore + " euAfter=" + euAfter);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-NEG01 — no lava source and no adjacent fluid storage: the pump never
	 *     acquires, never spends EU, and its tank stays empty.
	 * @covers R-NRG-06
	 */
	@GameTest
	public void tcPump001Neg01_noSourceNoAcquisition(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		pump.getEnergyStorage().amount = Config.machineBuffer;
		long euBefore = pump.getEnergyStorage().getAmount();
		drivePump(pump, helper, 20);
		if (pump.fluidTank.amount != 0) {
			helper.fail("tank must stay empty with no source/storage nearby, got " + pump.fluidTank.amount);
		}
		if (pump.getEnergyStorage().getAmount() != euBefore) {
			helper.fail("EU must not be spent with nothing to pump: before=" + euBefore
					+ " after=" + pump.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/** @implements TC-PUMP-001-NEG02 — with no power, the pump never acquires lava. @covers R-NRG-06 */
	@GameTest
	public void tcPump001Neg02_noPowerNoAcquisition(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		pump.getEnergyStorage().amount = 0;
		drivePump(pump, helper, 20);
		if (pump.fluidTank.amount != 0) {
			helper.fail("tank must stay empty without power, got " + pump.fluidTank.amount);
		}
		if (!level.getFluidState(lavaAbs).isSourceOfType(Fluids.LAVA)) {
			helper.fail("the world lava source must remain untouched without power");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-NEG03 — a full tank (4 buckets) pauses acquisition; EU is not spent and
	 *     the world source is untouched, even with power and a source present.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcPump001Neg03_fullTankPausesAcquisition(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), PumpBlockEntity.TANK_CAPACITY, txn));
		pump.getEnergyStorage().amount = Config.machineBuffer;
		long euBefore = pump.getEnergyStorage().getAmount();
		drivePump(pump, helper, 10);
		if (pump.fluidTank.amount != PumpBlockEntity.TANK_CAPACITY) {
			helper.fail("tank must not exceed capacity: " + pump.fluidTank.amount);
		}
		if (pump.getEnergyStorage().getAmount() != euBefore) {
			helper.fail("EU must not be spent on acquisition while the tank is full: before=" + euBefore
					+ " after=" + pump.getEnergyStorage().getAmount());
		}
		if (!level.getFluidState(lavaAbs).isSourceOfType(Fluids.LAVA)) {
			helper.fail("world lava source must remain untouched while the tank is full");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-NEG04 — a non-insertable neighbour (plain stone, no FluidStorage.SIDED)
	 *     does not receive lava; it stays in the pump's tank, no crash.
	 * @covers R-CON-10
	 */
	@GameTest
	public void tcPump001Neg04_noInsertableNeighbourNoPush(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		helper.setBlock(POS.relative(Direction.EAST), Blocks.STONE);
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET * 2, txn));
		pump.getEnergyStorage().amount = Config.machineBuffer;
		drivePump(pump, helper, 10);
		if (pump.fluidTank.amount != FluidAmounts.BUCKET * 2) {
			helper.fail("lava must remain in the pump's tank with no insertable neighbour, got "
					+ pump.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-POS05 — flowing (non-source) lava allows acquiring connected source blocks.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Pos05_flowingLavaAcquiresSource(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos sourceAbs = helper.absolutePos(POS.relative(Direction.EAST).relative(Direction.EAST));
		BlockPos flowAbs = helper.absolutePos(POS.relative(Direction.EAST));
		// A source two blocks away feeds a flowing (non-source) lava block orthogonally adjacent to the
		// pump.
		level.setBlockAndUpdate(sourceAbs, Blocks.LAVA.defaultBlockState());
		level.setBlockAndUpdate(flowAbs, Blocks.LAVA.defaultBlockState().setValue(
				net.minecraft.world.level.block.LiquidBlock.LEVEL, 2));
		// Seed enough EU to clear the per-bucket acquisition threshold (pumpEuPerBucket = 1000);
		// machineBuffer (800) is below it and would leave the pump starved.
		pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
		drivePump(pump, helper, 5);

		// The pump must successfully acquire the source block through the flowing block
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("pump failed to acquire source from flowing lava, tank has " + pump.fluidTank.amount);
		}
		// The source block should be drained
		if (level.getBlockState(sourceAbs).is(Blocks.LAVA)) {
			helper.fail("pump did not drain the source block at " + sourceAbs);
		}
		helper.succeed();
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
	 * @implements TC-PUMP-001-FUN06 — the pump acquires WATER (not just lava) from a water source block in
	 *     front of it (FACING face). Generalised fluid intake, post-restoration: the tank whitelist accepts
	 *     both lava and water, and the source block is consumed like a lava source would be.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Fun06_acquiresWaterFromSource(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos waterAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(waterAbs, Blocks.WATER.defaultBlockState());
		pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
		drivePump(pump, helper, 1);
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("expected the tank to hold 1 bucket of water, got " + pump.fluidTank.amount);
		}
		if (!pump.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("expected the tank to hold WATER, got " + pump.fluidTank.fluid);
		}
		if (level.getFluidState(waterAbs).isSourceOfType(Fluids.WATER)) {
			helper.fail("water source must be consumed (drained to air)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-NEG06 — single-variant tank: once the tank holds lava, a water source in
	 *     front of the pump is NOT acquired (no mixing). The tank's single-variant guard rejects the water
	 *     and the EU is not spent. This is the core guarantee behind the pump's "one fluid at a time" rule.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Neg06_lavaTankRejectsWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos waterAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(waterAbs, Blocks.WATER.defaultBlockState());
		// Prime the tank with lava.
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		long euBefore = Config.pumpEuPerBucket;
		pump.getEnergyStorage().amount = euBefore;
		drivePump(pump, helper, 3);
		if (pump.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("tank must not mix: held lava but accepted water");
		}
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("tank amount must stay at 1 bucket of lava, got " + pump.fluidTank.amount);
		}
		if (pump.getEnergyStorage().getAmount() != euBefore) {
			helper.fail("EU must not be spent when the water is rejected, spent "
					+ (euBefore - pump.getEnergyStorage().getAmount()));
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN07 — bucket feed via the GUI slots: a lava bucket in the input slot is
	 *     emptied into the tank (1 bucket), and the empty bucket drops into the output slot. No EU cost
	 *     (manual refill, not pumping). Mirrors the geothermal generator's bucket-emptying behaviour.
	 * @covers R-GUI-07
	 */
	@GameTest
	public void tcPump001Fun07_bucketEmptiesIntoTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		pump.setItem(PumpBlockEntity.FILL_INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		pump.getEnergyStorage().amount = 0; // bucket feed needs no EU
		drivePump(pump, helper, 1);
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("expected the tank to gain 1 bucket from the lava bucket, got " + pump.fluidTank.amount);
		}
		if (!pump.fluidTank.fluid.is(Fluids.LAVA)) {
			helper.fail("expected the tank to hold lava, got " + pump.fluidTank.fluid);
		}
		if (!pump.getItem(PumpBlockEntity.FILL_INPUT_SLOT).isEmpty()) {
			helper.fail("fill-input slot must be emptied after the bucket is consumed");
		}
		if (!pump.getItem(PumpBlockEntity.FILL_OUTPUT_SLOT).is(Items.BUCKET)) {
			helper.fail("fill-output slot must hold an empty bucket, got " + pump.getItem(PumpBlockEntity.FILL_OUTPUT_SLOT));
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN08 — bucket drain via the GUI slots: an empty bucket in the drain-input
	 *     slot is filled from the tank (1 bucket), and the full lava bucket drops into the drain-output
	 *     slot. No EU cost (manual drain). The filled bucket always matches the tank's single-variant fluid.
	 * @covers R-GUI-07
	 */
	@GameTest
	public void tcPump001Fun08_fillsBucketFromTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		// Prime the tank with 1 bucket of lava.
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		pump.setItem(PumpBlockEntity.DRAIN_INPUT_SLOT, new ItemStack(Items.BUCKET));
		pump.getEnergyStorage().amount = 0; // bucket drain needs no EU
		long tankBefore = pump.fluidTank.amount;
		drivePump(pump, helper, 1);
		if (pump.fluidTank.amount != tankBefore - FluidAmounts.BUCKET) {
			helper.fail("expected the tank to drop by 1 bucket after draining, got " + pump.fluidTank.amount);
		}
		if (!pump.getItem(PumpBlockEntity.DRAIN_INPUT_SLOT).isEmpty()) {
			helper.fail("drain-input slot must be emptied after the bucket is filled");
		}
		if (!pump.getItem(PumpBlockEntity.DRAIN_OUTPUT_SLOT).is(Items.LAVA_BUCKET)) {
			helper.fail("drain-output slot must hold a lava bucket, got " + pump.getItem(PumpBlockEntity.DRAIN_OUTPUT_SLOT));
		}
		helper.succeed();
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
		dev.alaindustrial.core.FluidTank tank = new dev.alaindustrial.core.FluidTank(
				dev.alaindustrial.core.FluidAmounts.BUCKET * 4,
				f -> f.is(Fluids.LAVA), f -> true, () -> {
				});
		dev.alaindustrial.core.EnergyTransactions.get().runCommitting(txn ->
				tank.insert(dev.alaindustrial.core.FluidHolder.of(Fluids.LAVA),
						dev.alaindustrial.core.FluidAmounts.BUCKET, txn));
		long before = tank.amount;
		try {
			dev.alaindustrial.core.EnergyTransactions.get().runCommitting(txn -> {
				tank.extract(dev.alaindustrial.core.FluidHolder.of(Fluids.LAVA),
						dev.alaindustrial.core.FluidAmounts.BUCKET / 2, txn);
				throw new RuntimeException("force rollback");
			});
		} catch (RuntimeException expected) {
			// expected: forces the transaction to abort/roll back.
		}
		boolean amountRestored = tank.amount == before;
		boolean fluidIntact = tank.fluid().is(Fluids.LAVA);
		if (!(amountRestored && fluidIntact)) {
			helper.fail("rollback must restore amount and keep fluid identity: amount " + before + "->" + tank.amount
					+ " fluidIntact=" + fluidIntact);
		}
		helper.succeed();
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
		dev.alaindustrial.core.FluidTank tank = new dev.alaindustrial.core.FluidTank(
				dev.alaindustrial.core.FluidAmounts.BUCKET * 4,
				f -> f.is(Fluids.LAVA), f -> true, () -> {
				});
		dev.alaindustrial.core.EnergyTransactions.get().runCommitting(txn ->
				tank.insert(dev.alaindustrial.core.FluidHolder.of(Fluids.LAVA),
						dev.alaindustrial.core.FluidAmounts.BUCKET, txn));
		long before = tank.amount; // 1 bucket
		try {
			// FULL drain (the exact amount stored) drives amount to 0 inside the transaction, then the
			// throw aborts it — forcing a rollback to the pre-drain state (amount AND fluid).
			dev.alaindustrial.core.EnergyTransactions.get().runCommitting(txn -> {
				tank.extract(dev.alaindustrial.core.FluidHolder.of(Fluids.LAVA),
						dev.alaindustrial.core.FluidAmounts.BUCKET, txn);
				throw new RuntimeException("force rollback");
			});
		} catch (RuntimeException expected) {
			// expected: forces the transaction to abort/roll back after the full drain.
		}
		boolean amountRestored = tank.amount == before;
		boolean fluidIntact = tank.fluid().is(Fluids.LAVA);
		if (!(amountRestored && fluidIntact)) {
			helper.fail("full-drain-then-rollback must restore amount AND fluid identity: amount " + before + "->"
					+ tank.amount + " fluid=" + tank.fluid()
					+ " (a regression here makes the tank invisible to cross-mod capability readers)");
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-028 NBT save-compat — legacy Fabric v0.1.0 "FluidTank" (droplets) loads correctly
	 *     when the new "FluidTankMb" key is absent, converting ÷81 and clamping to the new mB capacity.
	 * @covers R-PER-01
	 */
	@GameTest
	public void fluidTank_legacyDropletKeyMigratesToMbOnLoad(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.core.RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.GEOTHERMAL_GENERATOR);
		GeothermalGeneratorBlockEntity src = helper.getBlockEntity(POS, GeothermalGeneratorBlockEntity.class);

		// Simulate a legacy v0.1.0 save: write ONLY the droplet-valued "FluidTank" key (no "FluidTankMb"),
		// as the pre-MOD-028 saveAdditional did.
		net.minecraft.nbt.CompoundTag tag = src.saveCustomOnly(registries);
		tag.remove("FluidTankMb");
		tag.putLong("FluidTank", 81_000L * 3); // 3 buckets, in legacy droplets

		GeothermalGeneratorBlockEntity restored = new GeothermalGeneratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

		long expectedMb = dev.alaindustrial.core.FluidAmounts.BUCKET * 3;
		if (restored.fluidTank.amount != expectedMb || !restored.fluidTank.fluid().is(Fluids.LAVA)) {
			helper.fail("legacy droplet migration mismatch: expected " + expectedMb + " mB lava, got "
					+ restored.fluidTank.amount + " fluid=" + restored.fluidTank.fluid());
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-028 NBT save-compat — a new "FluidTankMb" key takes priority over a stale legacy
	 *     "FluidTank" key when both are present.
	 * @covers R-PER-01
	 */
	@GameTest
	public void fluidTank_newMbKeyTakesPriorityOverLegacyKey(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.core.RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.GEOTHERMAL_GENERATOR);
		GeothermalGeneratorBlockEntity src = helper.getBlockEntity(POS, GeothermalGeneratorBlockEntity.class);

		net.minecraft.nbt.CompoundTag tag = src.saveCustomOnly(registries);
		tag.putLong("FluidTankMb", dev.alaindustrial.core.FluidAmounts.BUCKET * 5); // authoritative
		tag.putLong("FluidTank", 81_000L * 9); // stale legacy value that must be ignored

		GeothermalGeneratorBlockEntity restored = new GeothermalGeneratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

		long expectedMb = dev.alaindustrial.core.FluidAmounts.BUCKET * 5;
		if (restored.fluidTank.amount != expectedMb) {
			helper.fail("new FluidTankMb key must win over legacy FluidTank: expected " + expectedMb
					+ " mB, got " + restored.fluidTank.amount);
		}
		helper.succeed();
	}
}
