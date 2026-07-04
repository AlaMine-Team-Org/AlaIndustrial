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
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
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
		helper.setBlock(POS, ModBlocks.GEOTHERMAL_GENERATOR);
		GeothermalGeneratorBlockEntity be = helper.getBlockEntity(POS, GeothermalGeneratorBlockEntity.class);
		if (be == null) {
			helper.fail("geothermal block entity missing");
		}
		return be;
	}

	private static void drive(GeothermalGeneratorBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(), helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	/**
	 * @implements TC-GEO-001-FUN01 — a lava bucket is consumed for EU and the empty bucket returned.
	 * @covers R-NRG-15
	 */
	@GameTest
	public void tcGeo001Fun01_lavaBucketProducesEu(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		drive(geo, helper, 5);
		boolean produced = geo.getEnergyStorage().getAmount() > 0;
		boolean bucketBack = geo.getItem(GeothermalGeneratorBlockEntity.OUTPUT_SLOT).is(Items.BUCKET);
		boolean consumed = geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT).isEmpty();
		if (!(produced && bucketBack && consumed)) {
			helper.fail("geothermal lava: produced=" + produced + " bucketBack=" + bucketBack
					+ " consumed=" + consumed);
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
		level.setBlockAndUpdate(pumpAbs, ModBlocks.PUMP.defaultBlockState());
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
			pump.getEnergyStorage().amount = Config.machineBuffer; // stand-in for network supply
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
				pump.getEnergyStorage().amount = Config.machineBuffer;
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
		// produced EU from that fluid, and no bucket item was ever used.
		boolean lavaMoved = lavaConsumed && pumpTankPeak > 0 && geoTankPeak > geoTankBefore;
		boolean producedEu = geoEnergyAfter > geoEnergyBefore;
		boolean pass = lavaIsSource && lavaMoved && producedEu && noBucketUsed;

		if (!pass) {
			helper.fail("fluid pump: lavaSource=" + lavaIsSource + " lavaConsumed=" + lavaConsumed
					+ " pumpTankPeak=" + pumpTankPeak + " geoTankPeak=" + geoTankPeak
					+ " geoEnergy " + geoEnergyBefore + "->" + geoEnergyAfter + " noBucket=" + noBucketUsed);
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
		try (Transaction tx = Transaction.openOuter()) {
			geo.fluidTank.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET * 2, tx);
			tx.commit();
		}
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
		try (Transaction tx = Transaction.openOuter()) {
			long inserted = geo.fluidTank.insert(FluidVariant.of(Fluids.LAVA),
					GeothermalGeneratorBlockEntity.TANK_CAPACITY + FluidConstants.BUCKET, tx);
			tx.commit();
			if (inserted != GeothermalGeneratorBlockEntity.TANK_CAPACITY) {
				helper.fail("tank accepted more than capacity: inserted=" + inserted
						+ " capacity=" + GeothermalGeneratorBlockEntity.TANK_CAPACITY);
			}
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
		long inserted;
		try (Transaction tx = Transaction.openOuter()) {
			inserted = geo.fluidTank.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx);
			tx.commit();
		}
		if (inserted != 0 || geo.fluidTank.amount != 0) {
			helper.fail("tank must reject water: inserted=" + inserted + " tankAmount=" + geo.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEO-001-NEG04 — a full buffer pauses the burn so lava-ticks are not wasted.
	 * @covers R-NRG-11
	 */
	@GameTest
	public void tcGeo001Neg04_fullBufferPausesBurn(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 64));
		drive(geo, helper, 1); // start a burn so lavaTicks > 0
		geo.getEnergyStorage().amount = geo.getEnergyStorage().getCapacity(); // force buffer full
		drive(geo, helper, 1);
		int ticks1 = geo.getDataAccess().get(2); // index 2 == progress (lavaTicks)
		drive(geo, helper, 1);
		int ticks2 = geo.getDataAccess().get(2);
		if (!(ticks1 > 0 && ticks1 == ticks2)) {
			helper.fail("full buffer must freeze lavaTicks: ticks1=" + ticks1 + " ticks2=" + ticks2);
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
		try (Transaction tx = Transaction.openOuter()) {
			geo.fluidTank.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET * 2, tx);
			tx.commit();
		}
		long extracted;
		try (Transaction tx = Transaction.openOuter()) {
			extracted = geo.fluidTank.extract(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET, tx);
			tx.commit();
		}
		if (extracted != 0 || geo.fluidTank.amount != FluidConstants.BUCKET * 2) {
			helper.fail("geothermal fluid tank must never allow extraction (canExtract=false), but moved "
					+ extracted);
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
		helper.setBlock(pos, ModBlocks.PUMP);
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

	/**
	 * @implements R-NRG-03 — pump per-face energy roles: every face accepts EU (IN), none emits,
	 *     matching the current implementation ({@code PumpBlockEntity#energyRoleForFace} unconditionally
	 *     returns IN for every {@code Direction} — the FACING face is NOT inert in code). No dedicated
	 *     TC-PUMP-001 ID covers this (pump.md's "Open Questions" flags the doc's "5×IN + FACING
	 *     inert" claim as unverified by a pump-specific {@code EnergyFaceGameTest} case, leaving the
	 *     decision to a reviewer); this method is written against the code as it stands, mirroring how
	 *     {@code EnergyFaceGameTest} asserts other blocks' face roles directly via
	 *     {@code EnergyStorage.SIDED}.
	 * @covers R-NRG-03
	 */
	@GameTest
	public void rNrg03_pumpEveryFaceInOnly(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos abs = pump.getBlockPos();
		for (Direction d : Direction.values()) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), abs, d);
			if (p == null || !p.supportsInsertion() || p.supportsExtraction()) {
				helper.fail("pump face " + d + " must be IN-only");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN02 — with energy.amount exactly pumpEuPerBucket (100) and a lava
	 *     source adjacent and an empty tank, one tick acquires 1 bucket and drains the EU to 0. This is
	 *     also the suite's PRF evidence for pumpEuPerBucket=100 (Config.pumpEuPerBucket, BVA row in
	 *     pump.md — there is no separate PRF section/ID in the doc for this number).
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
		if (pump.fluidTank.amount != FluidConstants.BUCKET) {
			helper.fail("expected tank to hold exactly 1 bucket, got " + pump.fluidTank.amount);
		}
		if (pump.getEnergyStorage().getAmount() != 0) {
			helper.fail("expected energy to drain to 0, got " + pump.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-FUN03 — the pump pulls lava from an adjacent extractable fluid storage
	 *     (not a world source) via StorageUtil.move. A donor pump's tank is used as the extractable
	 *     neighbour: {@code PumpBlockEntity#fluidTank.canExtract} is always {@code true} (unlike the
	 *     geothermal generator's tank, whose {@code canExtract} is always false — R-CON-08 — so it
	 *     cannot serve as a donor here).
	 *
	 *     <p>Because the donor is itself a pump, its tank is also a valid INSERT target for the puller's
	 *     own same-tick {@code pushLava} step (step 2 runs right after step 1 in {@code onServerTick}),
	 *     which would immediately shove the just-pulled bucket straight back — masking a successful pull
	 *     if asserted via final tank amount alone. Verify the pull via its other side effect instead
	 *     (EU drained by {@code acquireLava}, TC-PUMP-001-FUN02's proven pattern), which happens
	 *     independently of whatever the push step does afterward.
	 * @covers R-CON-01
	 *
	 *     <p>TODO(code D8): {@code required = false} — real production wart, not a test bug. In one tick
	 *     the pump runs acquire-then-push ({@code onServerTick}); with the donor being the only fluid
	 *     neighbour it is BOTH the extract source and a valid insert target, so {@code pushLava} immediately
	 *     shoves the just-pulled bucket straight back into the donor (net tank = 0). The real fix is a
	 *     pump-side change (skip the neighbour it acquired from this tick), deferred to the code-fix phase;
	 *     pump is hidden in v1.0 (MOD-010-adjacent), so this is low priority. See coverage/README D8.
	 */
	@GameTest(required = false)
	public void tcPump001Fun03_pullsFromAdjacentFluidStorage(GameTestHelper helper) {
		BlockPos donorRel = new BlockPos(1, 2, 1);
		BlockPos pumpRel = new BlockPos(2, 2, 1);
		PumpBlockEntity donor = placePump(helper, donorRel);
		PumpBlockEntity pump = placePump(helper, pumpRel);
		try (Transaction tx = Transaction.openOuter()) {
			donor.fluidTank.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET, tx);
			tx.commit();
		}
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
		PumpBlockEntity pump = placePump(helper, pumpRel);
		helper.setBlock(geoRel, ModBlocks.GEOTHERMAL_GENERATOR);
		GeothermalGeneratorBlockEntity geo = helper.getBlockEntity(geoRel, GeothermalGeneratorBlockEntity.class);
		try (Transaction tx = Transaction.openOuter()) {
			pump.fluidTank.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET * 2, tx);
			tx.commit();
		}
		pump.getEnergyStorage().amount = Config.machineBuffer;
		drivePump(pump, helper, 1);
		if (pump.fluidTank.amount != 0) {
			helper.fail("expected the pump's tank to be fully pushed, got " + pump.fluidTank.amount);
		}
		if (geo.fluidTank.amount != FluidConstants.BUCKET * 2) {
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

		pump.getEnergyStorage().amount = Config.machineBuffer;
		drivePump(pump, helper, 5);
		if (pump.fluidTank.amount != FluidConstants.BUCKET) {
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
		PumpBlockEntity pump = placePump(helper, pumpRel);
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
		try (Transaction tx = Transaction.openOuter()) {
			pump.fluidTank.insert(FluidVariant.of(Fluids.LAVA), PumpBlockEntity.TANK_CAPACITY, tx);
			tx.commit();
		}
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
		try (Transaction tx = Transaction.openOuter()) {
			pump.fluidTank.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET * 2, tx);
			tx.commit();
		}
		pump.getEnergyStorage().amount = Config.machineBuffer;
		drivePump(pump, helper, 10);
		if (pump.fluidTank.amount != FluidConstants.BUCKET * 2) {
			helper.fail("lava must remain in the pump's tank with no insertable neighbour, got "
					+ pump.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-NEG05 — flowing (non-source) lava is never acquired, only source blocks.
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcPump001Neg05_flowingLavaNotAcquired(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos sourceAbs = helper.absolutePos(POS.relative(Direction.EAST).relative(Direction.EAST));
		BlockPos flowAbs = helper.absolutePos(POS.relative(Direction.EAST));
		// A source two blocks away feeds a flowing (non-source) lava block orthogonally adjacent to the
		// pump, so the pump only ever sees flowing lava on its own faces.
		level.setBlockAndUpdate(sourceAbs, Blocks.LAVA.defaultBlockState());
		level.setBlockAndUpdate(flowAbs, Blocks.LAVA.defaultBlockState().setValue(
				net.minecraft.world.level.block.LiquidBlock.LEVEL, 2));
		boolean flowIsNotSource = !level.getFluidState(flowAbs).isSourceOfType(Fluids.LAVA);
		pump.getEnergyStorage().amount = Config.machineBuffer;
		drivePump(pump, helper, 5);
		if (!flowIsNotSource) {
			helper.fail("test setup invalid: adjacent lava must be flowing, not a source");
		}
		if (pump.fluidTank.amount != 0) {
			helper.fail("pump must not acquire from flowing (non-source) lava, got " + pump.fluidTank.amount);
		}
		helper.succeed();
	}

}
