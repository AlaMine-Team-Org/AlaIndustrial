package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Loader-neutral bodies for the machine side of the fluid feature (MOD-323 batch C): the geothermal
 * generator's tank/burn/buffer behaviour and the pump's acquire/push/bucket paths, plus the
 * {@code FluidTank} transaction/NBT-migration semantics. Migrated verbatim from the Fabric
 * {@code FluidGameTest} suite; each body uses only the vanilla {@code GameTestHelper} + loader-neutral
 * content ({@link ModContent}, the common {@code BlockEntity} classes) so both gametest lanes run the
 * same set. Bodies that read a loader-specific capability surface (Fabric Transfer API droplet
 * boundary, {@code EnergyStorage.SIDED} face probing) stay in the Fabric file by construction.
 */
public final class FluidMachineScenarios {

	private FluidMachineScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static GeothermalGeneratorBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModContent.GEOTHERMAL_GENERATOR.get(),
				GeothermalGeneratorBlockEntity.class);
	}

	private static void drive(GeothermalGeneratorBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	private static PumpBlockEntity placePump(GameTestHelper helper, BlockPos pos) {
		return placePump(helper, pos, Direction.EAST);
	}

	/** Place a pump facing {@code facing} — the pump acquires fluid only from that face. */
	private static PumpBlockEntity placePump(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModContent.PUMP.get().defaultBlockState()
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

	// ============================================================================================
	// Geothermal generator
	// ============================================================================================

	/**
	 * Lava source -> pump -> geothermal tank -> EU with no bucket item involved: the pump holds the
	 * acquired bucket in its tank, pushes it into the geo tank, and the generator burns it into an
	 * exact multiple of {@code geothermalEuPerTick}. Two-phase rig pinning exact numbers.
	 * Mirrors: FluidGameTest.tcFluidPump_lavaSourceToGeothermal
	 */
	public static void lavaSourceToGeothermal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Compact rig inside the force-loaded region (x 1..7, y 2, z 1): geo - pump - lava in a row.
		BlockPos geoRel = new BlockPos(4, 2, 1);
		BlockPos pumpRel = new BlockPos(5, 2, 1);
		BlockPos lavaRel = new BlockPos(6, 2, 1);
		BlockPos geoAbs = helper.absolutePos(geoRel);
		BlockPos pumpAbs = helper.absolutePos(pumpRel);
		BlockPos lavaAbs = helper.absolutePos(lavaRel);

		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		// Face the pump EAST (toward the lava source) so it acquires from the source and pushes into the
		// geo sink on its WEST face.
		level.setBlockAndUpdate(pumpAbs, ModContent.PUMP.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.EAST));
		// The geothermal sink is placed later (Phase 1), so the pump first holds the lava it acquires.

		boolean lavaIsSource = level.getFluidState(lavaAbs)
				.isSourceOfType(Fluids.LAVA);

		// --- Phase 1: transport. The pump acquires a bucket from the source and pushes it onward.
		// The pump fills and empties its tank within a single tick (acquire -> push), so its tank is
		// never caught non-empty by an after-tick sample. To observe the pump actually HOLDING lava we
		// tick it once with no adjacent fluid sink (the geo isn't placed yet), so the acquired bucket
		// stays in the pump tank; then we place the geo and tick again to push it across.
		long pumpTankPeak = 0;
		boolean lavaConsumed = false;
		if (level.getBlockEntity(pumpAbs) instanceof PumpBlockEntity pump) {
			pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket); // stand-in for network supply (≥1 bucket)
			pump.serverTick(level, pumpAbs, level.getBlockState(pumpAbs));
			pumpTankPeak = Math.max(pumpTankPeak, pump.fluidTank.amount); // acquired, not yet pushed
		}
		if (!level.getFluidState(lavaAbs)
				.isSourceOfType(Fluids.LAVA)) {
			lavaConsumed = true;
		}

		// Now place the geothermal sink and let the pump push its tank lava into it.
		level.setBlockAndUpdate(geoAbs, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		long geoTankBefore = 0;
		if (level.getBlockEntity(geoAbs) instanceof GeothermalGeneratorBlockEntity geo) {
			geoTankBefore = geo.fluidTank.amount;
		}
		long geoTankPeak = geoTankBefore;
		for (int i = 0; i < 4; i++) {
			if (level.getBlockEntity(pumpAbs) instanceof PumpBlockEntity pump) {
				pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);
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
		level.setBlockAndUpdate(pumpAbs, Blocks.AIR.defaultBlockState());
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
		long expectedBucket = FluidAmounts.BUCKET;
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

	/**
	 * The generator's collision shape is a full cube (16^3).
	 * Mirrors: FluidGameTest.tcGeo001Phy02_hitboxIsFullCube
	 */
	public static void geothermalHitboxIsFullCube(GameTestHelper helper) {
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
	 * A pump-fed tank fill makes the generator burn from the tank while its item slots stay empty
	 * (no bucket item involved).
	 * Mirrors: FluidGameTest.tcGeo001Fun02_pumpFillsTankAndBurnsWithoutBucket
	 */
	public static void geothermalPumpFillsTankAndBurnsWithoutBucket(GameTestHelper helper) {
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
	 * The tank holds exactly 10 buckets' worth of burn ticks (10 * geothermalBurnTicks), matching
	 * maxProgress/tankCapacity(); no overflow.
	 * Mirrors: FluidGameTest.tcGeo001Fun03_tankHoldsTenBucketsOfBurnTicks
	 */
	public static void geothermalTankHoldsTenBucketsOfBurnTicks(GameTestHelper helper) {
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
	 * The buffer grows to geothermalBuffer (4000 EU) and pauses there, regardless of the lava-tick
	 * source (bucket or tank).
	 * Mirrors: FluidGameTest.tcGeo001Fun04_bufferCapsAtGeothermalMax
	 */
	public static void geothermalBufferCapsAtGeothermalMax(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		long cap = geo.getEnergyStorage().getCapacity();
		if (cap != Config.geothermalBuffer) {
			helper.fail("expected buffer cap " + Config.geothermalBuffer + " but was " + cap);
		}
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 64));
		geo.getEnergyStorage().setAmountUntracked(cap - 1); // BVA: max-1
		drive(geo, helper, 5);
		long amount = geo.getEnergyStorage().getAmount();
		if (amount != cap) {
			helper.fail("expected buffer to settle at cap " + cap + " but was " + amount);
		}
		helper.succeed();
	}

	/**
	 * A directly-adjacent LV consumer receives EU, draining the buffer.
	 * Mirrors: FluidGameTest.tcGeo001Fun05_pushesToAdjacentConsumer
	 */
	public static void geothermalPushesToAdjacentConsumer(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		BlockPos sink = POS.east();
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity batteryBox = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 64));
		drive(geo, helper, 20);
		if (batteryBox == null || batteryBox.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery_box received no EU from the geothermal generator");
		}
		helper.succeed();
	}

	/**
	 * The input slot rejects a non-lava-bucket item (empty bucket, water bucket, cobblestone).
	 * Mirrors: FluidGameTest.tcGeo001Neg02_slotRejectsNonLavaBucket
	 */
	public static void geothermalSlotRejectsNonLavaBucket(GameTestHelper helper) {
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

	/**
	 * The fluid tank rejects a non-lava fluid via canInsert.
	 * Mirrors: FluidGameTest.tcGeo001Neg03_tankRejectsNonLava
	 */
	public static void geothermalTankRejectsNonLava(GameTestHelper helper) {
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
	 * A full energy buffer pauses the lavaTicks->EU conversion so lava-ticks are not wasted; lava
	 * intake (bucket->lavaTicks) is intentionally NOT blocked.
	 * Mirrors: FluidGameTest.tcGeo001Neg04_fullBufferPausesBurn
	 */
	public static void geothermalFullBufferPausesBurn(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		// Load one bucket into the burn buffer so lavaTicks > 0.
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		drive(geo, helper, 1); // bucket consumed → lavaTicks = geothermalBurnTicks
		// Clear the input slot so further intake cannot confound the burn-pause check.
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
		geo.getEnergyStorage().setAmountUntracked(geo.getEnergyStorage().getCapacity()); // force buffer full
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
	 * A lava bucket is consumed into lavaTicks even when the energy buffer is full; the
	 * lavaTicks->EU step stays paused — intake and burn are decoupled.
	 * Mirrors: FluidGameTest.tcGeo001Fun06_lavaBucketLoadedWhenEnergyFull
	 */
	public static void geothermalLavaBucketLoadedWhenEnergyFull(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.getEnergyStorage().setAmountUntracked(geo.getEnergyStorage().getCapacity()); // force buffer full
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

	/**
	 * The generator never accepts external EU (producer only).
	 * Mirrors: FluidGameTest.tcGeo001Neg05_rejectsExternalEu
	 */
	public static void geothermalRejectsExternalEu(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		if (geo.getEnergyStorage().supportsInsertion()) {
			helper.fail("geothermal generator storage must not support insertion (maxInsert=0)");
		}
		helper.succeed();
	}

	/**
	 * The tank never lets a neighbour extract lava back out ({@code canExtract} is always false),
	 * probed via an actual {@code extract()} call.
	 * Mirrors: FluidGameTest.tcGeo001Neg06_tankNeverExtractable
	 */
	public static void geothermalTankNeverExtractable(GameTestHelper helper) {
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
	 * The block's {@code lit} blockstate tracks whether it is burning lava (bucket or tank feed) and
	 * clears once the fuel runs out.
	 * Mirrors: FluidGameTest.tcGeo001Sta01_litStateTracksBurning
	 */
	public static void geothermalLitStateTracksBurning(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		BlockPos abs = geo.getBlockPos();

		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		drive(geo, helper, 3);
		if (!helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("geothermal generator must be LIT while burning lava");
		}

		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
		geo.getEnergyStorage().setAmountUntracked(geo.getEnergyStorage().getCapacity()); // stop new burns from starting
		drive(geo, helper, Config.geothermalBurnTicks + 10); // exhaust any remaining lavaTicks
		if (helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("geothermal generator must not be LIT once the burn ends");
		}
		helper.succeed();
	}

	/**
	 * Generation rate equals Config.geothermalEuPerTick (16 EU/t) while lava burns.
	 * Mirrors: FluidGameTest.tcGeo001Prf01_ratePerTickMatchesConfig
	 */
	public static void geothermalRatePerTickMatchesConfig(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET, 1));
		drive(geo, helper, 1); // tick 1 starts the burn
		geo.getEnergyStorage().setAmountUntracked(0); // measure one clean tick from empty
		drive(geo, helper, 1);
		long made = geo.getEnergyStorage().getAmount();
		if (made != Config.geothermalEuPerTick) {
			helper.fail("EU/t expected " + Config.geothermalEuPerTick + " but measured " + made);
		}
		helper.succeed();
	}

	/**
	 * A single-tick transfer into an adjacent consumer is capped at the LV per-tick transfer limit
	 * (EnergyTier.LV.maxVoltage() = 32 EU).
	 * Mirrors: FluidGameTest.tcGeo001Prf02_packetCappedAtLv
	 */
	public static void geothermalPacketCappedAtLv(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		geo.getEnergyStorage().setAmountUntracked(geo.getEnergyStorage().getCapacity()); // buffer full

		BlockPos sink = POS.east();
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity batteryBox = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (batteryBox == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		batteryBox.getEnergyStorage().setAmountUntracked(0);

		drive(geo, helper, 1);

		long lvCap = EnergyTier.LV.maxVoltage();
		long gained = batteryBox.getEnergyStorage().getAmount();
		if (!(gained > 0 && gained <= lvCap)) {
			helper.fail("LV transfer must be in (0," + lvCap + "] EU per tick but moved " + gained);
		}
		helper.succeed();
	}

	// ============================================================================================
	// Pump
	// ============================================================================================

	/**
	 * With energy exactly pumpEuPerBucket (1000) and a lava source in front (FACING face), one tick
	 * acquires 1 bucket and drains the EU to 0 — the PRF evidence for pumpEuPerBucket=1000.
	 * Mirrors: FluidGameTest.tcPump001Fun02_exactEuAcquiresOneBucket
	 */
	public static void pumpExactEuAcquiresOneBucket(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);
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
	 * The pump pulls lava from an adjacent extractable fluid port (a donor pump's tank), not a world
	 * source, via {@code FluidMover.move}.
	 * Mirrors: FluidGameTest.tcPump001Fun03_pullsFromAdjacentFluidStorage
	 */
	public static void pumpPullsFromAdjacentFluidStorage(GameTestHelper helper) {
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
		donor.getEnergyStorage().setAmountUntracked(0);
		pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);
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
	 * The pump pushes its ENTIRE tank (2 buckets) into an adjacent insertable fluid storage in a
	 * single tick (not one bucket at a time).
	 * Mirrors: FluidGameTest.tcPump001Fun04_pushesEntireTankInOneTick
	 */
	public static void pumpPushesEntireTankInOneTick(GameTestHelper helper) {
		BlockPos pumpRel = new BlockPos(1, 2, 1);
		BlockPos geoRel = new BlockPos(2, 2, 1);
		// Pump faces WEST so it pushes through its EAST face into the geo (push skips the FACING face).
		PumpBlockEntity pump = placePump(helper, pumpRel, Direction.WEST);
		helper.setBlock(geoRel, ModContent.GEOTHERMAL_GENERATOR.get());
		GeothermalGeneratorBlockEntity geo = helper.getBlockEntity(geoRel, GeothermalGeneratorBlockEntity.class);
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET * 2, txn));
		pump.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
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
	 * The pump's tank progress (fluid already held) survives a power-loss/power-restore cycle: no
	 * reset, no dupe, acquisition just resumes.
	 * Mirrors: FluidGameTest.tcPump001Fun05_progressPersistsAcrossPowerLoss
	 */
	public static void pumpProgressPersistsAcrossPowerLoss(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());

		pump.getEnergyStorage().setAmountUntracked(0);
		drivePump(pump, helper, 10);
		if (pump.fluidTank.amount != 0) {
			helper.fail("without power the tank must stay empty, got " + pump.fluidTank.amount);
		}
		if (!level.getFluidState(lavaAbs).isSourceOfType(Fluids.LAVA)) {
			helper.fail("without power the lava source must remain untouched");
		}

		pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);
		drivePump(pump, helper, 5);
		if (pump.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("after power restore expected exactly 1 bucket acquired, got " + pump.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * End-to-end source -> tank -> sink, asserting on the pump's own fields (EU spent, tank amount)
	 * rather than only the geothermal generator's output.
	 * Mirrors: FluidGameTest.tcPump001Fun01_sourceToTankToSink
	 */
	public static void pumpSourceToTankToSink(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pumpRel = new BlockPos(2, 2, 1);
		BlockPos lavaRel = new BlockPos(1, 2, 1);
		BlockPos geoRel = new BlockPos(3, 2, 1);
		BlockPos lavaAbs = helper.absolutePos(lavaRel);

		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		// The pump faces WEST (toward the lava source) and pushes its EAST face into the geo sink.
		PumpBlockEntity pump = placePump(helper, pumpRel, Direction.WEST);
		helper.setBlock(geoRel, ModContent.GEOTHERMAL_GENERATOR.get());
		GeothermalGeneratorBlockEntity geo = helper.getBlockEntity(geoRel, GeothermalGeneratorBlockEntity.class);

		pump.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
		long euBefore = pump.getEnergyStorage().getAmount();
		for (int i = 0; i < 5; i++) {
			pump.getEnergyStorage().setAmountUntracked(Math.max(pump.getEnergyStorage().getAmount(), Config.pumpEuPerBucket));
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
	 * No lava source and no adjacent fluid storage: the pump never acquires, never spends EU, and
	 * its tank stays empty.
	 * Mirrors: FluidGameTest.tcPump001Neg01_noSourceNoAcquisition
	 */
	public static void pumpNoSourceNoAcquisition(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		pump.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
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

	/**
	 * With no power, the pump never acquires lava.
	 * Mirrors: FluidGameTest.tcPump001Neg02_noPowerNoAcquisition
	 */
	public static void pumpNoPowerNoAcquisition(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		pump.getEnergyStorage().setAmountUntracked(0);
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
	 * A full tank (4 buckets) pauses acquisition; EU is not spent and the world source is untouched,
	 * even with power and a source present.
	 * Mirrors: FluidGameTest.tcPump001Neg03_fullTankPausesAcquisition
	 */
	public static void pumpFullTankPausesAcquisition(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos lavaAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), PumpBlockEntity.TANK_CAPACITY, txn));
		pump.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
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
	 * A non-insertable neighbour (plain stone, no fluid capability) does not receive lava; it stays
	 * in the pump's tank, no crash.
	 * Mirrors: FluidGameTest.tcPump001Neg04_noInsertableNeighbourNoPush
	 */
	public static void pumpNoInsertableNeighbourNoPush(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		helper.setBlock(POS.relative(Direction.EAST), Blocks.STONE);
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET * 2, txn));
		pump.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
		drivePump(pump, helper, 10);
		if (pump.fluidTank.amount != FluidAmounts.BUCKET * 2) {
			helper.fail("lava must remain in the pump's tank with no insertable neighbour, got "
					+ pump.fluidTank.amount);
		}
		helper.succeed();
	}

	/**
	 * Flowing (non-source) lava allows acquiring connected source blocks.
	 * Mirrors: FluidGameTest.tcPump001Pos05_flowingLavaAcquiresSource
	 */
	public static void pumpFlowingLavaAcquiresSource(GameTestHelper helper) {
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
		pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);
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
	 * The pump acquires WATER (not just lava) from a water source block in front of it (FACING
	 * face); the source block is consumed like a lava source would be.
	 * Mirrors: FluidGameTest.tcPump001Fun06_acquiresWaterFromSource
	 */
	public static void pumpAcquiresWaterFromSource(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos waterAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(waterAbs, Blocks.WATER.defaultBlockState());
		pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);
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
	 * Single-variant tank: once the tank holds lava, a water source in front of the pump is NOT
	 * acquired (no mixing); the EU is not spent.
	 * Mirrors: FluidGameTest.tcPump001Neg06_lavaTankRejectsWater
	 */
	public static void pumpLavaTankRejectsWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PumpBlockEntity pump = placePump(helper, POS);
		BlockPos waterAbs = helper.absolutePos(POS.relative(Direction.EAST));
		level.setBlockAndUpdate(waterAbs, Blocks.WATER.defaultBlockState());
		// Prime the tank with lava.
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		long euBefore = Config.pumpEuPerBucket;
		pump.getEnergyStorage().setAmountUntracked(euBefore);
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
	 * Bucket feed via the GUI slots: a lava bucket in the input slot is emptied into the tank
	 * (1 bucket), and the empty bucket drops into the output slot. No EU cost.
	 * Mirrors: FluidGameTest.tcPump001Fun07_bucketEmptiesIntoTank
	 */
	public static void pumpBucketEmptiesIntoTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		pump.setItem(PumpBlockEntity.FILL_INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		pump.getEnergyStorage().setAmountUntracked(0); // bucket feed needs no EU
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
	 * Bucket drain via the GUI slots: an empty bucket in the drain-input slot is filled from the
	 * tank (1 bucket), and the full lava bucket drops into the drain-output slot. No EU cost.
	 * Mirrors: FluidGameTest.tcPump001Fun08_fillsBucketFromTank
	 */
	public static void pumpFillsBucketFromTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper, POS);
		// Prime the tank with 1 bucket of lava.
		EnergyTransactions.get().runCommitting(txn ->
				pump.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		pump.setItem(PumpBlockEntity.DRAIN_INPUT_SLOT, new ItemStack(Items.BUCKET));
		pump.getEnergyStorage().setAmountUntracked(0); // bucket drain needs no EU
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
	// FluidTank — transaction semantics + legacy NBT-migration round trip.
	// ============================================================================================

	/**
	 * Transaction rollback restores a positive amount without losing fluid identity.
	 * Mirrors: FluidGameTest.fluidTank_rollbackToPositiveAmountKeepsFluidIdentity
	 */
	public static void fluidTankRollbackToPositiveAmountKeepsFluidIdentity(GameTestHelper helper) {
		dev.alaindustrial.core.fluid.FluidTank tank = new dev.alaindustrial.core.fluid.FluidTank(
				FluidAmounts.BUCKET * 4,
				f -> f.is(Fluids.LAVA), f -> true, () -> {
				});
		EnergyTransactions.get().runCommitting(txn ->
				tank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		long before = tank.amount;
		try {
			EnergyTransactions.get().runCommitting(txn -> {
				tank.extract(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET / 2, txn);
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
	 * Full-drain-then-rollback keeps fluid identity — the cross-mod capability contract regression:
	 * on rollback to the pre-drain amount the tank MUST still report which fluid it holds, or it
	 * becomes invisible to capability readers.
	 * Mirrors: FluidGameTest.fluidTank_fullDrainThenRollbackKeepsFluidIdentity
	 */
	public static void fluidTankFullDrainThenRollbackKeepsFluidIdentity(GameTestHelper helper) {
		dev.alaindustrial.core.fluid.FluidTank tank = new dev.alaindustrial.core.fluid.FluidTank(
				FluidAmounts.BUCKET * 4,
				f -> f.is(Fluids.LAVA), f -> true, () -> {
				});
		EnergyTransactions.get().runCommitting(txn ->
				tank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		long before = tank.amount; // 1 bucket
		try {
			// FULL drain (the exact amount stored) drives amount to 0 inside the transaction, then the
			// throw aborts it — forcing a rollback to the pre-drain state (amount AND fluid).
			EnergyTransactions.get().runCommitting(txn -> {
				tank.extract(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn);
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
	 * NBT save-compat — legacy Fabric v0.1.0 "FluidTank" (droplets) loads correctly when the new
	 * "FluidTankMb" key is absent, converting /81 and clamping to the new mB capacity.
	 * Mirrors: FluidGameTest.fluidTank_legacyDropletKeyMigratesToMbOnLoad
	 */
	public static void fluidTankLegacyDropletKeyMigratesToMbOnLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.core.RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.GEOTHERMAL_GENERATOR.get());
		GeothermalGeneratorBlockEntity src = helper.getBlockEntity(POS, GeothermalGeneratorBlockEntity.class);

		// Simulate a legacy v0.1.0 save: write ONLY the droplet-valued "FluidTank" key (no "FluidTankMb"),
		// as the pre-MOD-028 saveAdditional did.
		net.minecraft.nbt.CompoundTag tag = src.saveCustomOnly(registries);
		tag.remove("FluidTankMb");
		tag.putLong("FluidTank", 81_000L * 3); // 3 buckets, in legacy droplets

		GeothermalGeneratorBlockEntity restored = new GeothermalGeneratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

		long expectedMb = FluidAmounts.BUCKET * 3;
		if (restored.fluidTank.amount != expectedMb || !restored.fluidTank.fluid().is(Fluids.LAVA)) {
			helper.fail("legacy droplet migration mismatch: expected " + expectedMb + " mB lava, got "
					+ restored.fluidTank.amount + " fluid=" + restored.fluidTank.fluid());
		}
		helper.succeed();
	}

	/**
	 * NBT save-compat — a new "FluidTankMb" key takes priority over a stale legacy "FluidTank" key
	 * when both are present.
	 * Mirrors: FluidGameTest.fluidTank_newMbKeyTakesPriorityOverLegacyKey
	 */
	public static void fluidTankNewMbKeyTakesPriorityOverLegacyKey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.core.RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.GEOTHERMAL_GENERATOR.get());
		GeothermalGeneratorBlockEntity src = helper.getBlockEntity(POS, GeothermalGeneratorBlockEntity.class);

		net.minecraft.nbt.CompoundTag tag = src.saveCustomOnly(registries);
		tag.putLong("FluidTankMb", FluidAmounts.BUCKET * 5); // authoritative
		tag.putLong("FluidTank", 81_000L * 9); // stale legacy value that must be ignored

		GeothermalGeneratorBlockEntity restored = new GeothermalGeneratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

		long expectedMb = FluidAmounts.BUCKET * 5;
		if (restored.fluidTank.amount != expectedMb) {
			helper.fail("new FluidTankMb key must win over legacy FluidTank: expected " + expectedMb
					+ " mB, got " + restored.fluidTank.amount);
		}
		helper.succeed();
	}
}
