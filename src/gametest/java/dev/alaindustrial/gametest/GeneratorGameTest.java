package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.NetworkManager;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * L2 functional suite for the fuel generator — automates the parts of {@code TC-GEN-001}
 * (docs/testing/blocks/generators/generator.md) that need a live {@link net.minecraft.server.level.ServerLevel}.
 * Migrated/expanded from the legacy {@code IndustrializationSelfTest} fuel-generator checks.
 *
 * <p>Drives {@code serverTick} directly (deterministic, no waiting) like the legacy self-test, then
 * {@code helper.succeed()}. Each method = exactly one case, traced via {@code @implements}.
 *
 * <p>Numbers come from {@link Config} (canon), never hard-coded.
 */
public class GeneratorGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Place a generator at {@link #POS} and return its block entity (absolute pos for serverTick). */
	private static GeneratorBlockEntity placeGenerator(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.GENERATOR);
		GeneratorBlockEntity gen = helper.getBlockEntity(POS, GeneratorBlockEntity.class);
		if (gen == null) {
			helper.fail("generator block entity missing after placement");
		}
		return gen;
	}

	private static void drive(GeneratorBlockEntity gen, GameTestHelper helper, int ticks) {
		BlockPos abs = gen.getBlockPos();
		for (int i = 0; i < ticks; i++) {
			BlockState state = helper.getLevel().getBlockState(abs);
			gen.serverTick(helper.getLevel(), abs, state);
		}
	}

	/**
	 * @implements TC-GEN-001-FUN01 — coal in the fuel slot burns and fills the EU buffer.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcGen001Fun01_coalRaisesBuffer(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 10);
		if (gen.getEnergyStorage().getAmount() <= 0) {
			helper.fail("buffer did not grow from burning coal");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-FUN02 — buffer caps at generatorBuffer and never exceeds it (BVA).
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcGen001Fun02_bufferCapsAtMax(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		long cap = gen.getEnergyStorage().getCapacity();
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		gen.getEnergyStorage().amount = cap - 1; // BVA: max-1
		drive(gen, helper, 5);
		long amount = gen.getEnergyStorage().getAmount();
		if (amount != cap) {
			helper.fail("expected buffer to settle at cap " + cap + " but was " + amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG01 — generator never accepts external EU (it is a producer only).
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcGen001Neg01_rejectsExternalEu(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		if (gen.getEnergyStorage().supportsInsertion()) {
			helper.fail("generator storage must not support insertion (maxInsert=0)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG03 — a full buffer pauses the burn so fuel is not wasted.
	 * @covers R-NRG-11
	 */
	@GameTest
	public void tcGen001Neg03_fullBufferPausesBurn(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 1); // start a burn so burnTime > 0
		gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity(); // force buffer full
		drive(gen, helper, 1);
		int burn1 = gen.getDataAccess().get(2); // index 2 = progress = burnTime
		drive(gen, helper, 1);
		int burn2 = gen.getDataAccess().get(2);
		if (!(burn1 > 0 && burn1 == burn2)) {
			helper.fail("full buffer must freeze burn: burn1=" + burn1 + " burn2=" + burn2);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-PER01 — buffer + remaining burn survive an NBT save/load round-trip.
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcGen001Per01_stateSurvivesNbtRoundTrip(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 5);
		long energy0 = gen.getEnergyStorage().getAmount();
		int burn0 = gen.getDataAccess().get(2);

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = gen.saveCustomOnly(registries);
		GeneratorBlockEntity restored = new GeneratorBlockEntity(gen.getBlockPos(),
				helper.getLevel().getBlockState(gen.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != energy0 || restored.getDataAccess().get(2) != burn0) {
			helper.fail("NBT round-trip lost state: energy " + energy0 + "->"
					+ restored.getEnergyStorage().getAmount() + " burn " + burn0 + "->"
					+ restored.getDataAccess().get(2));
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG04 — the fuel slot rejects a lava bucket; lava is the geothermal
	 *     generator's domain (burning it here would out-produce geothermal ~10×). Decision 2026-06-30.
	 */
	@GameTest
	public void tcGen001Neg04_lavaBucketRejected(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		if (gen.canPlaceItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.LAVA_BUCKET))) {
			helper.fail("fuel slot accepts a lava bucket — lava belongs to the geothermal generator");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-FUN03 — a coal block burns far longer than a single coal (the generator
	 *     inherits vanilla fuel burn values; ~10× in vanilla).
	 */
	@GameTest
	public void tcGen001Fun03_coalBlockBurnsLonger(GameTestHelper helper) {
		GeneratorBlockEntity g1 = placeGenerator(helper);
		g1.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));
		drive(g1, helper, 1);
		int coalDur = g1.getDataAccess().get(3); // index 3 = maxProgress = burnDuration

		BlockPos pos2 = new BlockPos(3, 2, 1);
		helper.setBlock(pos2, ModBlocks.GENERATOR);
		GeneratorBlockEntity g2 = helper.getBlockEntity(pos2, GeneratorBlockEntity.class);
		g2.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
		drive(g2, helper, 1);
		int blockDur = g2.getDataAccess().get(3);

		if (!(coalDur > 0 && blockDur > coalDur)) {
			helper.fail("coal block must burn longer than coal: coal=" + coalDur + " block=" + blockDur);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-FUN04 — generator pushes EU into a directly-adjacent consumer (no cable).
	 * @covers R-NRG-03, R-CON-11
	 */
	@GameTest
	public void tcGen001Fun04_pushesToAdjacentConsumer(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		BlockPos sink = new BlockPos(2, 2, 1); // east of POS, orthogonally adjacent
		// BatteryBox input face = FACING (MOD-006); the generator sits on its WEST side, so face WEST.
		helper.setBlock(sink, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity battery_box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 20);
		if (battery_box == null || battery_box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery_box received no EU from the generator");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG02 — a non-fuel item (cobblestone) yields no EU. Note: it IS placeable
	 *     in the fuel slot (canPlaceItem only checks the slot index), it simply never burns.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcGen001Neg02_nonFuelProducesNoEu(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COBBLESTONE, 64));
		drive(gen, helper, 20);
		if (gen.getEnergyStorage().getAmount() != 0) {
			helper.fail("cobblestone is not fuel; buffer must stay 0 but was " + gen.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-PRF01 — generation rate equals {@code Config.fuelEuPerTick} (canon 8 EU/t).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGen001Prf01_ratePerTickMatchesConfig(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 1);              // tick 1 starts the burn
		gen.getEnergyStorage().amount = 0;  // measure one clean tick from empty
		drive(gen, helper, 1);
		long made = gen.getEnergyStorage().getAmount();
		if (made != Config.fuelEuPerTick) {
			helper.fail("EU/t expected " + Config.fuelEuPerTick + " but measured " + made);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG02b — fuel slot rejects non-fuel (canPlaceItem validates fuel, R-GUI-02).
	 */
	@GameTest
	public void tcGen001Neg02b_fuelSlotRejectsNonFuel(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		if (gen.canPlaceItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COBBLESTONE))) {
			helper.fail("fuel slot accepts non-fuel cobblestone — canPlaceItem should validate fuel");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-PER02 — breaking a generator drops its fuel exactly once (no dupe/loss).
	 * @covers R-BRK-06, R-PER-04
	 */
	@GameTest
	public void tcGen001Per02_breakDropsFuelNoDupe(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		int placed = 5;
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, placed));
		BlockPos abs = gen.getBlockPos();
		Containers.dropContents(helper.getLevel(), abs, gen);
		AABB box = new AABB(abs.getX() - 2, abs.getY() - 2, abs.getZ() - 2,
				abs.getX() + 3, abs.getY() + 3, abs.getZ() + 3);
		int dropped = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (e.getItem().is(Items.COAL)) {
				dropped += e.getItem().getCount();
			}
		}
		if (dropped != placed) {
			helper.fail("expected " + placed + " coal dropped, got " + dropped);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-CON01 — pairwise neighbour connectivity: the generator delivers EU to
	 *     compatible neighbours (a directly-adjacent consumer and a directly-adjacent storage) and an
	 *     AIR face yields nothing (no block entity, no crash). A copper cable on a third face exercises
	 *     the network-transport path side-by-side without delivering to a bare cable (no consumer past it).
	 * @covers R-CON-01
	 */
	@GameTest
	public void tcGen001Con01_pairwiseNeighbours(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);

		// Macerator EAST (consumer: IN on every face). BatteryBox SOUTH with default FACING=NORTH — its input
		// face (MOD-006: input on FACING) is NORTH, which touches the generator on its −z side.
		// Copper cable WEST (a network segment, not itself a delivery target). UP face left as AIR.
		BlockPos maceratorPos = POS.relative(Direction.EAST);
		BlockPos batteryBoxPos = POS.relative(Direction.SOUTH);
		BlockPos cablePos = POS.relative(Direction.WEST);
		BlockPos airPos = POS.relative(Direction.UP);

		helper.setBlock(maceratorPos, ModBlocks.MACERATOR);
		helper.setBlock(batteryBoxPos, ModBlocks.BATTERY_BOX); // default FACING = NORTH → input toward the generator
		helper.setBlock(cablePos, ModBlocks.COPPER_CABLE);

		MaceratorBlockEntity macerator = helper.getBlockEntity(maceratorPos, MaceratorBlockEntity.class);
		BatteryBoxBlockEntity battery_box = helper.getBlockEntity(batteryBoxPos, BatteryBoxBlockEntity.class);
		CableBlockEntity cable = helper.getBlockEntity(cablePos, CableBlockEntity.class);
		if (macerator == null || battery_box == null || cable == null) {
			helper.fail("neighbour block entities missing after placement");
		}

		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		// Pre-charge the buffer: at 8 EU/t the first neighbour in Direction order would drain it before
		// the others get any (direct distribute has no fair split — that's the network's job, R-NRG-08).
		gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();

		// First serverTick on the cable registers it with the NetworkManager (level + neighbours loaded).
		cable.serverTick(helper.getLevel(), cablePos, helper.getLevel().getBlockState(cablePos));

		// Drive the generator (its serverTick directly pushes to the cable-less adjacent consumer/storage)
		// and run the network transport pass alongside it. The cabled path moves nothing here (the cable
		// has a producer but no consumer endpoint, so its network stays asleep); this just proves the two
		// delivery paths coexist without crashing.
		for (int i = 0; i < 20; i++) {
			drive(gen, helper, 1);
			NetworkManager.tickAll(helper.getLevel());
		}

		if (macerator.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent consumer (macerator) received no EU from the generator");
		}
		if (battery_box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent storage (battery_box) received no EU from the generator");
		}
		// The AIR face has no block entity — nothing was delivered there and nothing crashed.
		// Null-safe world lookup: helper.getBlockEntity asserts presence and would throw on an air face.
		if (helper.getLevel().getBlockEntity(helper.absolutePos(airPos)) != null) {
			helper.fail("AIR face unexpectedly has a block entity at " + airPos);
		}
		helper.succeed();
	}


	/**
	 * @implements TC-GEN-001-PHY02 — the generator's collision shape is a full cube (16³); it is a
	 *     solid occluder, not a partial-shape block.
	 * @covers R-PHY-02
	 */
	@GameTest
	public void tcGen001Phy02_hitboxIsFullCube(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		BlockPos abs = gen.getBlockPos();
		BlockState state = helper.getLevel().getBlockState(abs);

		// Direct convenience query: collision shape equals a full block.
		if (!state.isCollisionShapeFullBlock(helper.getLevel(), abs)) {
			helper.fail("generator collision shape is not a full block");
		}

		// Cross-check via the raw VoxelShape so a regression to a partial shape is caught either way.
		VoxelShape collision = state.getCollisionShape(helper.getLevel(), abs);
		if (!Block.isShapeFullBlock(collision)) {
			helper.fail("generator collision VoxelShape is not a full 16³ cube");
		}
		helper.succeed();
	}


	/**
	 * @implements TC-GEN-001-PRF02 — one transfer into a single consumer is capped at the LV per-tick
	 *     transfer limit ({@code EnergyTier.LV.maxVoltage()} = 32 EU). The generator buffer is filled to
	 *     capacity and a fresh BatteryBox (buffer 0) sits on a face it accepts input on; one serverTick must
	 *     move more than 0 but no more than 32 EU.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcGen001Prf02_packetCappedAtLv(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity(); // buffer near/at full

		BlockPos sink = POS.east(); // (2,2,1): generator is on its WEST side → face WEST so input meets it (MOD-006)
		helper.setBlock(sink, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity battery_box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (battery_box == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		battery_box.getEnergyStorage().amount = 0;

		drive(gen, helper, 1); // single tick -> EnergyNet.distribute, capped at srcTier.maxVoltage()=32

		long lvCap = dev.alaindustrial.core.EnergyTier.LV.maxVoltage();
		long gained = battery_box.getEnergyStorage().getAmount();
		if (!(gained > 0 && gained <= lvCap)) {
			helper.fail("LV transfer must be in (0," + lvCap + "] EU per tick but moved " + gained);
		}
		helper.succeed();
	}


	/**
	 * @implements TC-GEN-001-STA01 — the block's {@code lit} blockstate tracks whether it is burning.
	 *     {@code updateLit(made > 0)} runs every {@code serverTick}
	 *     ({@code AbstractGeneratorBlockEntity#serverTick}), writing the property back to the world via
	 *     {@code level.setBlock}, so LIT is read from the live level, not a cached state.
	 * @covers R-VIS-01
	 */
	@GameTest
	public void tcGen001Sta01_litStateTracksBurning(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		BlockPos abs = gen.getBlockPos();

		// Burning: coal in the fuel slot lights the block.
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 3);
		if (!helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("generator must be LIT while burning fuel");
		}

		// Not burning: empty the fuel slot and fill the buffer so a burn cannot start (produce() needs
		// room). updateLit(false) on the next tick must clear LIT.
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, ItemStack.EMPTY);
		gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		drive(gen, helper, 3);
		if (helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("generator must not be LIT once the burn ends");
		}
		helper.succeed();
	}

}
