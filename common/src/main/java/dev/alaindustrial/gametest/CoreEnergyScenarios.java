package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.EnergyNetwork;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral world-based energy gametest bodies (MOD-022). Each scenario is a plain
 * {@code Consumer<GameTestHelper>} using only the vanilla {@code GameTestHelper} + loader-neutral content
 * ({@link ModContent}, {@code NetworkManager}, {@code Config}, the common {@code BlockEntity} classes) —
 * no loader-specific gametest infrastructure. Both the Fabric {@code @GameTest} suite (via its own copies)
 * and the NeoForge {@code gameTestServer} lane (via {@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests})
 * exercise the SAME energy core. These are the scenarios the JUnit {@code EphemeralTestServerProvider}
 * could not run — they need a live ticking {@code ServerLevel}.
 *
 * <p>The bodies mirror the Fabric {@code GeneratorGameTest}/{@code NetworkGameTest}/{@code PersistenceGameTest}
 * cases they are traced from (see the "Mirrors:" note on each method); numbers come from {@link Config}.
 */
public final class CoreEnergyScenarios {

	private CoreEnergyScenarios() {}

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────

	/** Null-safe world BE lookup by RELATIVE pos (helper.getBlockEntity asserts presence and throws). */
	private static BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	/** Tick any of the four energy BEs (null-safe), driving its serverTick at its absolute pos. */
	private static void tick(GameTestHelper helper, BlockEntity be) {
		if (be == null) {
			return;
		}
		BlockPos p = be.getBlockPos();
		BlockState st = helper.getLevel().getBlockState(p);
		if (be instanceof GeneratorBlockEntity gen) {
			gen.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof CableBlockEntity c) {
			c.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof MaceratorBlockEntity mac) {
			mac.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof BatteryBoxBlockEntity bb) {
			bb.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof WaterMillBlockEntity wm) {
			wm.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof WindMillBlockEntity wd) {
			wd.serverTick(helper.getLevel(), p, st);
		}
	}

	// ── scenario 0: macerator processes a recipe (recipe-registry seam, MOD-022) ──────────────────

	private static final BlockPos MAC = new BlockPos(1, 2, 1);

	/**
	 * A powered macerator with a valid input produces its recipe output. This proves the machine
	 * {@code RecipeType}+{@code RecipeSerializer} resolve and recipe lookup works on THIS loader — the
	 * NeoForge frozen-registry seam (MOD-022): NeoForge registers these via a {@code DeferredRegister}
	 * ({@code ModRecipesNeoForge}), so {@code Kind.type()} must resolve the deferred holder at tick time.
	 * Input {@code minecraft:emerald → alaindustrial:emerald_dust} (see data/.../recipe/maceration).
	 * Mirrors the Fabric-side {@code MachineGameTest} processing cases, which the NeoForge world lane lacked.
	 */
	public static void maceratorProcessesRecipe(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000; // > any single op's E_op; bypasses the per-tick cap
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.EMERALD));
			for (int i = 0; i < 400; i++) { // > longest machine duration + margin
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(MaceratorBlockEntity.OUTPUT_SLOT);
			if (out.isEmpty()) {
				helper.fail("macerator produced no output — maceration recipe did not resolve on this loader");
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 0b: battery box carries its EU on drop (data-component seam, MOD-022) ────────────

	private static final BlockPos DROP = new BlockPos(1, 2, 1);

	/**
	 * A charged battery box emits its buffered EU as the {@code STORED_ENERGY} data component on
	 * {@code collectComponents()} — what the loot table's {@code copy_components} reads onto the drop. This
	 * proves the custom data component registers + carries on THIS loader — the NeoForge frozen-registry
	 * seam (MOD-022): NeoForge registers it via a {@code DeferredRegister} ({@code ModDataComponentsNeoForge}),
	 * and {@code ModDataComponents.STORED_ENERGY.get()} must resolve. Before the fix the component was
	 * unregistered on NeoForge, the loot table failed to parse, and a charged box dropped empty.
	 * Mirrors the Fabric-side {@code BatteryBoxGameTest.tcBatteryBox001Brk07}.
	 */
	public static void batteryBoxDropCarriesEnergy(GameTestHelper helper) {
		helper.setBlock(DROP, ModContent.BATTERY_BOX.get());
		if (be(helper, DROP) instanceof BatteryBoxBlockEntity bb) {
			long charge = Math.min(12345L, bb.getEnergyStorage().getCapacity());
			bb.getEnergyStorage().amount = charge;
			DataComponentMap map = bb.collectComponents();
			Long carried = map.get(ModDataComponents.STORED_ENERGY.get());
			if (carried == null || carried.longValue() != charge) {
				helper.fail("battery box did not carry STORED_ENERGY on drop: " + carried + "/" + charge
						+ " (data component unregistered on this loader?)");
			}
			helper.succeed();
			return;
		}
		helper.fail("battery box block entity missing");
	}

	// ── scenario 1: generator → directly-adjacent battery box (no cable) ──────────────────────────

	private static final BlockPos GEN = new BlockPos(1, 2, 1);

	/**
	 * Generator pushes EU into a directly-adjacent battery box (cable-less push path).
	 * Mirrors: GeneratorGameTest.tcGen001Fun04_pushesToAdjacentConsumer
	 */
	public static void generatorChargesAdjacentBox(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		GeneratorBlockEntity gen = helper.getBlockEntity(GEN, GeneratorBlockEntity.class);
		if (gen == null) {
			helper.fail("generator block entity missing after placement");
		}
		BlockPos sink = GEN.east(); // generator sits on the box's WEST side
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		for (int i = 0; i < 20; i++) {
			tick(helper, gen);
		}
		if (box == null || box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery box received no EU from the generator");
		}
		helper.succeed();
	}

	/**
	 * A generator whose ONLY adjacent neighbour is FULL must not lose EU (EnergyMover full-neighbour
	 * no-leak guard). Pre-charge the generator to half with no fuel (produce()==0), fill the neighbour,
	 * and assert the generator buffer is byte-for-byte unchanged after driving.
	 * Mirrors: GeneratorGameTest.tcGen001Neg05_fullAdjacentConsumerDoesNotDrainGenerator
	 */
	public static void fullNeighbourNoLeak(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		GeneratorBlockEntity gen = helper.getBlockEntity(GEN, GeneratorBlockEntity.class);
		BlockPos sink = GEN.east();
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (gen == null || box == null) {
			helper.fail("generator or battery_box missing after placement");
		}
		long genStart = gen.getEnergyStorage().getCapacity() / 2;
		gen.getEnergyStorage().amount = genStart; // no fuel: buffer can only change via the push path
		gen.setChanged();
		box.getEnergyStorage().amount = box.getEnergyStorage().getCapacity(); // neighbour full
		box.setChanged();
		for (int i = 0; i < 20; i++) {
			tick(helper, gen);
		}
		long genEnd = gen.getEnergyStorage().getAmount();
		if (genEnd != genStart) {
			helper.fail("generator lost " + (genStart - genEnd) + " EU pushing into a FULL neighbour — "
					+ "EnergyMover refund leak (extracted EU not restored to the maxInsert==0 generator)");
		}
		helper.succeed();
	}

	// ── scenario 2: generator → cable → macerator (network transport) ─────────────────────────────

	private static final BlockPos LINE_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos LINE_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos LINE_MAC = new BlockPos(3, 2, 1);

	private static void buildLine(GameTestHelper helper) {
		helper.setBlock(LINE_GEN, ModContent.GENERATOR.get());
		helper.setBlock(LINE_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(LINE_MAC, ModContent.MACERATOR.get());
		if (be(helper, LINE_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, LINE_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
	}

	private static void driveLine(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			tick(helper, be(helper, LINE_GEN));
			tick(helper, be(helper, LINE_CABLE));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, LINE_MAC));
		}
	}

	/**
	 * Generator delivers EU down a copper cable to a macerator, and an energy network forms.
	 * Mirrors: NetworkGameTest.it001_delivery
	 */
	public static void generatorDeliversDownCable(GameTestHelper helper) {
		buildLine(helper);
		driveLine(helper, 120);
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(LINE_CABLE));
		if (net == null) {
			helper.fail("no energy network formed on the cable");
		}
		long mac = be(helper, LINE_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (mac <= 0) {
			helper.fail("macerator received no EU over the cable");
		}
		helper.succeed();
	}

	/**
	 * returnRoundRobin no-leak: a cabled generator whose only consumer accepts less than a full LV
	 * packet (5 EU room, well below the 32 EU cap) must lose EXACTLY what the consumer gains — no
	 * surplus destroyed against the maxInsert==0 generator on the cable path.
	 * Mirrors: NetworkGameTest.tcCable001Nrg03_generatorNotDrainedByPartialConsumer
	 */
	public static void returnRoundRobinNoLeak(GameTestHelper helper) {
		helper.setBlock(LINE_GEN, ModContent.GENERATOR.get());
		helper.setBlock(LINE_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(LINE_MAC, ModContent.MACERATOR.get());

		long genStart = Config.generatorBuffer;
		if (be(helper, LINE_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = genStart; // pre-charged, NO fuel: buffer only goes down
			gen.setChanged();
		}
		final long room = 5;
		long macStart = Config.maceratorBuffer - room;
		if (be(helper, LINE_MAC) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = macStart; // NO input: never processes, room only shrinks
			mac.setChanged();
		}

		driveLine(helper, 10);

		long genEnd = be(helper, LINE_GEN) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1;
		long macEnd = be(helper, LINE_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (macEnd != Config.maceratorBuffer) {
			helper.fail("macerator did not top off its 5 EU of room: " + macEnd + "/" + Config.maceratorBuffer);
		}
		long genDrain = genStart - genEnd;
		long macGain = macEnd - macStart;
		if (genDrain != macGain) {
			helper.fail("generator lost " + genDrain + " EU but macerator gained " + macGain
					+ " — surplus destroyed in returnRoundRobin (EnergyMover-class leak on the cable path)");
		}
		helper.succeed();
	}

	// ── scenario 3: MOD-009 battery box charges to 100% over a multi-cable network ────────────────

	private static final BlockPos BB_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos[] BB_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1),
	};
	private static final BlockPos BB_BOX = new BlockPos(7, 2, 1);

	/**
	 * MOD-009: a BatteryBox pre-charged 10 EU short of full charges all the way to exact capacity over a
	 * 5-cable network (no residual cable-loss term stranding the last packet).
	 * Mirrors: NetworkGameTest.mod009_batteryBoxChargesToFull
	 */
	public static void mod009BatteryBoxChargesToFull(GameTestHelper helper) {
		helper.setBlock(BB_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : BB_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(BB_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, BB_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		if (be(helper, BB_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = Config.batteryBoxBuffer - 10L;
		}
		for (int i = 0; i < 40; i++) {
			tick(helper, be(helper, BB_GEN));
			for (BlockPos c : BB_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, BB_BOX));
		}
		long got = be(helper, BB_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		if (got != Config.batteryBoxBuffer) {
			helper.fail("BatteryBox did not reach 100%: " + got + "/" + Config.batteryBoxBuffer
					+ " (stuck short = MOD-009 cable-loss regression)");
		}
		helper.succeed();
	}

	// ── scenario 4: MOD-021 proportional distance loss over a 10-cable line ───────────────────────

	// Zig-zag to fit 10 cables inside the default gametest region.
	private static final BlockPos LOSS_GEN = new BlockPos(0, 2, 1);
	private static final BlockPos[] LOSS_CABLES = {
		new BlockPos(1, 2, 1), new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1), new BlockPos(7, 2, 1),
		new BlockPos(7, 2, 2), new BlockPos(7, 2, 3), new BlockPos(7, 2, 4),
	};
	private static final BlockPos LOSS_BOX = new BlockPos(7, 2, 5);

	/**
	 * MOD-021: a 10-cable line loses EU proportional to distance. Flow pinned at the LV packet cap
	 * (generator far over-charged, box empty), the box gains {@code 32 − floor(32 × loss × 10)} EU/tick,
	 * strictly less than a lossless line's 32.
	 * Mirrors: NetworkGameTest.tcCable001Nrg02_lossOverTenCables
	 */
	public static void mod021LossOverTenCables(GameTestHelper helper) {
		helper.setBlock(LOSS_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : LOSS_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(LOSS_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		long cap = EnergyTier.LV.maxVoltage();
		if (be(helper, LOSS_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = cap * 200;
		}
		if (be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}
		int ticks = 50;
		for (int i = 0; i < ticks; i++) {
			tick(helper, be(helper, LOSS_GEN));
			for (BlockPos c : LOSS_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, LOSS_BOX));
		}
		long got = be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		long lossPerTick = (long) Math.floor(cap * Config.copperCableLossPerBlock * 10);
		long expected = (cap - lossPerTick) * ticks;
		if (lossPerTick <= 0) {
			helper.fail("test misconfigured: copperCableLossPerBlock too low to lose EU over 10 cables");
		}
		if (got >= cap * ticks) {
			helper.fail("expected proportional cable loss over 10 cables but got full lossless delivery: " + got);
		}
		if (got < expected - 2 * cap || got > expected) {
			helper.fail("delivered EU does not match the loss model: expected ~" + expected + " (±2 ticks), got " + got);
		}
		helper.succeed();
	}

	// ── scenario 4b: ring network — two arms joined by a closing cable merge on tick 0 (MOD-025) ────

	// Ring: GEN(1,2,2) touches two cable arms directly (north + south), each arm runs +x to
	// RING_A3/RING_B3 (x=4). MAC(4,2,2) sits between the two arms but — being a machine, not a
	// cable — is NOT itself part of the union-find graph (NetworkManager.register only unions on
	// cable-to-cable adjacency). The two arms are joined into a real ring by a 3-cable bypass column
	// one block further east (x=5), cable-to-cable adjacent to RING_A3/RING_B3, closing the cycle
	// around GEN+MAC without any cable occupying MAC's own cell. Mirrors:
	// NetworkGameTest.tcCable001Con04_ringNetworkNoDeadlock.
	private static final BlockPos RING_GEN = new BlockPos(1, 2, 2);
	private static final BlockPos RING_A0 = new BlockPos(1, 2, 1); // GEN's north neighbour
	private static final BlockPos RING_A1 = new BlockPos(2, 2, 1);
	private static final BlockPos RING_A2 = new BlockPos(3, 2, 1);
	private static final BlockPos RING_A3 = new BlockPos(4, 2, 1); // MAC's north neighbour
	private static final BlockPos RING_B0 = new BlockPos(1, 2, 3); // GEN's south neighbour
	private static final BlockPos RING_B1 = new BlockPos(2, 2, 3);
	private static final BlockPos RING_B2 = new BlockPos(3, 2, 3);
	private static final BlockPos RING_B3 = new BlockPos(4, 2, 3); // MAC's south neighbour
	private static final BlockPos RING_MAC = new BlockPos(4, 2, 2);
	private static final BlockPos RING_BYPASS_A = new BlockPos(5, 2, 1); // east of RING_A3
	private static final BlockPos RING_BYPASS_MID = new BlockPos(5, 2, 2); // closes the cycle
	private static final BlockPos RING_BYPASS_B = new BlockPos(5, 2, 3); // east of RING_B3

	private static void driveRing(GameTestHelper helper, int n) {
		BlockPos[] cables = {
			RING_A0, RING_A1, RING_A2, RING_A3, RING_B0, RING_B1, RING_B2, RING_B3,
			RING_BYPASS_A, RING_BYPASS_MID, RING_BYPASS_B,
		};
		for (int i = 0; i < n; i++) {
			tick(helper, be(helper, RING_GEN));
			for (BlockPos c : cables) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, RING_MAC));
		}
	}

	/**
	 * MOD-025: a ring network (two independent cable arms connecting the same generator to the same
	 * macerator, closed into a cycle by a bypass cable joining the two arms directly) resolves to ONE
	 * {@link EnergyNetwork} the instant the closing cable registers (tick 0) — union-find merge on
	 * cycle, no deadlock, no duplicate network.
	 * Mirrors: NetworkGameTest.tcCable001Con04_ringNetworkNoDeadlock
	 */
	public static void ringNetworkMergesOnClose(GameTestHelper helper) {
		helper.setBlock(RING_GEN, ModContent.GENERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		helper.setBlock(RING_A0, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_A1, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_A2, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_A3, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B0, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B1, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B2, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B3, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_MAC, ModContent.MACERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		if (be(helper, RING_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, RING_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}

		// helper.setBlock bypasses CableBlock.setPlacedBy (no LivingEntity placer), so a cable only joins
		// NetworkManager on its own first serverTick (CableBlockEntity.ensureRegistered). Register both
		// arms with one tick each — no NetworkManager.tickAll, so this is still "tick 0" for the network
		// topology, before any EU has moved.
		BlockPos[] armCables = {RING_A0, RING_A1, RING_A2, RING_A3, RING_B0, RING_B1, RING_B2, RING_B3};
		for (BlockPos c : armCables) {
			tick(helper, be(helper, c));
		}

		// Both arms are up but not yet joined: two disjoint cable networks.
		EnergyNetwork armABefore = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_A0));
		EnergyNetwork armBBefore = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_B0));
		if (armABefore == null || armBBefore == null || armABefore == armBBefore) {
			helper.fail("test setup: the two arms must start as disjoint networks before closing the ring");
		}

		// Close the cycle: place + register the bypass cable(s), cable-to-cable adjacent to both RING_A3
		// and RING_B3, so NetworkManager.register's adjacency scan must union both arms into one network
		// immediately (still tick 0 for the topology: registration, not NetworkManager.tickAll).
		helper.setBlock(RING_BYPASS_A, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_BYPASS_MID, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_BYPASS_B, ModContent.COPPER_CABLE.get());
		tick(helper, be(helper, RING_BYPASS_A));
		tick(helper, be(helper, RING_BYPASS_MID));
		tick(helper, be(helper, RING_BYPASS_B));

		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_A0));
		EnergyNetwork netB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_B0));
		if (net == null || netB == null || net != netB) {
			helper.fail("both ring branches must resolve to the same network (union-find merge on cycle)");
		}

		driveRing(helper, 120);

		long mac = be(helper, RING_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (mac <= 0) {
			helper.fail("ring network delivered no EU to the macerator: " + mac);
		}
		helper.succeed();
	}

	// ── scenario 5: NBT persistence round-trip ────────────────────────────────────────────────────

	private static final BlockPos PER_POS = new BlockPos(1, 2, 1);

	/**
	 * NBT save/load round-trip preserves a macerator's energy + progress + input count.
	 * Mirrors: PersistenceGameTest.rPer01_maceratorNbtRoundTrip
	 */
	public static void nbtRoundTripPreservesState(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(PER_POS);
		helper.setBlock(PER_POS, ModContent.MACERATOR.get());
		MaceratorBlockEntity src = helper.getBlockEntity(PER_POS, MaceratorBlockEntity.class);

		long energy0 = 1234L;
		int progress0 = 7;
		src.getEnergyStorage().amount = energy0;
		// setItem() resets progress on an input change, so place the input BEFORE setting progress.
		src.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 3));
		src.getDataAccess().set(2, progress0); // index 2 == progress
		int input0 = src.getItem(MaceratorBlockEntity.INPUT_SLOT).getCount();

		CompoundTag tag = src.saveCustomOnly(registries);
		MaceratorBlockEntity restored = new MaceratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		int progress1 = restored.getDataAccess().get(2);
		int input1 = restored.getItem(MaceratorBlockEntity.INPUT_SLOT).getCount();
		if (energy0 != energy1 || progress0 != progress1 || input0 != input1) {
			helper.fail("macerator round-trip mismatch: energy " + energy0 + "->" + energy1
					+ " progress " + progress0 + "->" + progress1 + " input " + input0 + "->" + input1);
		}
		helper.succeed();
	}

	// ── scenario 6: water mill generates from adjacent water and pushes to a battery box ──────────────

	private static final BlockPos MILL = new BlockPos(1, 2, 1);

	/**
	 * Water mill produces EU from vanilla water on a horizontal face and pushes it into a directly-adjacent
	 * battery box (passive LV generator, no fuel, no cable). Water detection reads the world fluid state, so
	 * this exercises the full world path on the NeoForge lane.
	 * Mirrors: WaterMillGameTest.tcWmill001Con01_pushesToAdjacentBattery
	 */
	public static void waterMillChargesAdjacentBox(GameTestHelper helper) {
		helper.setBlock(MILL, ModContent.WATER_MILL.get());
		WaterMillBlockEntity mill = helper.getBlockEntity(MILL, WaterMillBlockEntity.class);
		if (mill == null) {
			helper.fail("water mill block entity missing after placement");
		}
		helper.setBlock(MILL.north(), Blocks.WATER); // keep it generating
		BlockPos sink = MILL.east(); // mill sits on the box's WEST side
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery box missing after placement");
		}
		box.getEnergyStorage().amount = 0;
		for (int i = 0; i < 20; i++) {
			tick(helper, mill);
		}
		if (box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery box received no EU from the water mill");
		}
		helper.succeed();
	}

	/**
	 * Wind mill pushes its buffered EU into a directly-adjacent battery box (passive LV generator, no cable).
	 * The buffer is pre-filled so the test is independent of the low test-region altitude (the height→rate
	 * arithmetic is covered numerically at L1 in {@code WindMillOutputTest}); this verifies the world wiring —
	 * face roles and the cable-less push path — on the NeoForge lane.
	 * Mirrors: WindMillGameTest.tcWindmill001Con01_pushesToAdjacentBattery
	 */
	public static void windMillChargesAdjacentBox(GameTestHelper helper) {
		helper.setBlock(MILL, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(MILL, WindMillBlockEntity.class);
		if (mill == null) {
			helper.fail("wind mill block entity missing after placement");
		}
		mill.getEnergyStorage().amount = mill.getEnergyStorage().getCapacity(); // ample supply to push
		mill.setChanged();
		BlockPos sink = MILL.east(); // mill sits on the box's WEST side
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery box missing after placement");
		}
		box.getEnergyStorage().amount = 0;
		for (int i = 0; i < 20; i++) {
			tick(helper, mill);
		}
		if (box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery box received no EU from the wind mill");
		}
		helper.succeed();
	}
}
