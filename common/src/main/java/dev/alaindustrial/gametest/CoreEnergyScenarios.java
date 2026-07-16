package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.EnergyNetwork;
import dev.alaindustrial.core.EnergyRole;
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

	/** Tick any of the energy BEs (null-safe), driving its serverTick at its absolute pos. */
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
		} else if (be instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			// Covers macerator/electric_furnace/compressor/extractor — all extend MachineBlockEntity
			// and share its final serverTick (slot layout 0=input, 1=output).
			mac.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof BatteryBoxBlockEntity bb) {
			bb.serverTick(helper.getLevel(), p, st);
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
				return;
			}
			// Assert the SPECIFIC output item, not just non-empty — otherwise a regression returning the
			// input emerald (or any fallback item) would pass trivially. Sibling tests (iron_ore→dust)
			// already check identity; this first recipe must too.
			if (!out.is(dev.alaindustrial.registry.ModContent.EMERALD_DUST.get())) {
				helper.fail("macerator output was " + out.getItem() + " x" + out.getCount()
						+ ", expected emerald_dust — wrong recipe resolved on this loader");
				return;
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

	// ── scenario 6: wind mill pushes buffered EU to an adjacent battery box ───────────────────────

	/**
	 * Wind mill pushes its buffered EU into a directly-adjacent battery box (passive LV generator, no cable).
	 * The buffer is pre-filled so the test is independent of the low test-region altitude (the height→rate
	 * arithmetic is covered numerically at L1 in {@code WindMillOutputTest}); this verifies the world wiring —
	 * face roles and the cable-less push path — on the NeoForge lane.
	 * Mirrors: WindMillGameTest.tcWindmill001Con01_pushesToAdjacentBattery
	 */
	public static void windMillChargesAdjacentBox(GameTestHelper helper) {
		BlockPos millPos = new BlockPos(1, 2, 1);
		helper.setBlock(millPos, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(millPos, WindMillBlockEntity.class);
		if (mill == null) {
			helper.fail("wind mill block entity missing after placement");
		}
		mill.getEnergyStorage().amount = mill.getEnergyStorage().getCapacity(); // ample supply to push
		mill.setChanged();
		BlockPos sink = millPos.east(); // mill sits on the box's WEST side
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

	// ── scenario 7: machine negatives — no power, full output jam, input swap ──────────────────────

	/**
	 * A machine with a valid input but NO energy produces no output and does not advance progress
	 * (R-NRG-10). Exercises the shared {@code MachineBlockEntity} processing loop on the NeoForge lane —
	 * the Fabric lane covers this per-machine in {@code MachineGameTest#assertNoPowerNoOutput}.
	 * Mirrors: MachineGameTest.tcMach001Neg01_noPowerNoOutput
	 */
	public static void machineNoPowerNoOutput(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 0;
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (!mac.getItem(1).isEmpty()) {
				helper.fail("macerator produced output without energy: " + mac.getItem(1));
				return;
			}
			if (mac.getDataAccess().get(2) != 0) {
				helper.fail("macerator advanced progress without energy: " + mac.getDataAccess().get(2));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	/**
	 * A machine whose output slot is already at the max stack (64) of the recipe's product JAMS: it must
	 * not overflow past 64, must not advance progress, must not consume the input. Catches a
	 * dupe/overflow regression on the NeoForge processing path.
	 * Mirrors: MachineGameTest.tcMach001Neg03_fullOutputJamsMachine
	 */
	public static void machineFullOutputJams(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 4));
			mac.setItem(1, new ItemStack(ModContent.IRON_DUST.get(), 64));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int outCount = mac.getItem(1).getCount();
			int progress = mac.getDataAccess().get(2);
			if (outCount != 64) {
				helper.fail("output slot overflowed: " + outCount + " items (expected 64)");
				return;
			}
			if (progress != 0) {
				helper.fail("machine advanced progress to " + progress + " despite full output slot");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	/**
	 * Swapping the input item mid-operation resets progress to 0 (R-NRG-10): partial progress on item A
	 * must not carry over to item B. Drives half a maceration cycle, swaps raw_iron → raw_copper,
	 * asserts progress reset.
	 * Mirrors: MachineGameTest.tcMach001Fun04_maceratorInputSwapResetsProgress
	 */
	public static void machineInputSwapResetsProgress(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			int halfway = Config.maceratorDuration / 2;
			for (int i = 0; i < halfway; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int progressBefore = mac.getDataAccess().get(2);
			if (progressBefore <= 0) {
				helper.fail("expected partial progress before the swap but got " + progressBefore);
				return;
			}
			mac.setItem(0, new ItemStack(Items.RAW_COPPER, 1));
			if (mac.getDataAccess().get(2) != 0) {
				helper.fail("progress did not reset to 0 after swapping the input: "
						+ mac.getDataAccess().get(2));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 8: battery box rate BVA — buffer publishes EXACTLY LV.maxVoltage() ───────────────

	/**
	 * The battery box's energy buffer publishes per-tick rate caps of EXACTLY
	 * {@code EnergyTier.LV.maxVoltage()} for both insert and extract. Asserting the canonical
	 * {@link dev.alaindustrial.core.EnergyBuffer#maxInsert}/{@code maxExtract} fields directly (loader-neutral —
	 * the buffer is shared common code; each loader only wraps it) catches both a dead port
	 * ({@code maxInsert == 0}) and a missing cap (unlimited insert) — either regression would pass a
	 * weaker "{@code <= lvCap}" upper-bound check. Complements the Fabric lane's
	 * {@code BatteryBoxGameTest.tcBatteryBox001Prf03/Prf04} which exercise the same invariant via the
	 * loader-specific capability view.
	 * Mirrors: BatteryBoxGameTest.tcBatteryBox001Prf03_inputRateCappedAtLv
	 */
	public static void batteryBoxRateExactLv(GameTestHelper helper) {
		helper.setBlock(DROP, ModContent.BATTERY_BOX.get());
		if (be(helper, DROP) instanceof BatteryBoxBlockEntity bb) {
			long lvCap = EnergyTier.LV.maxVoltage();
			dev.alaindustrial.core.EnergyBuffer buf = bb.getEnergyStorage();
			if (buf.maxInsert != lvCap) {
				helper.fail("battery box maxInsert=" + buf.maxInsert + " EU, expected exactly LV.maxVoltage()="
						+ lvCap + " (maxInsert==0 = dead port; > lvCap = uncapped — both are bugs)");
				return;
			}
			if (buf.maxExtract != lvCap) {
				helper.fail("battery box maxExtract=" + buf.maxExtract + " EU, expected exactly LV.maxVoltage()="
						+ lvCap + " (maxExtract==0 = dead port; > lvCap = uncapped — both are bugs)");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("battery box block entity missing");
	}

	// ── scenario 9: solar panel day/night generation (R-NRG-15) ────────────────────────────────────

	/** Set the level to clear daytime and recompute skyDarken synchronously (no tick wait). */
	private static void setClearDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set day");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();
	}

	/** Set the level to clear midnight and recompute skyDarken synchronously. */
	private static void setNight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();
	}

	private static final BlockPos SOLAR = new BlockPos(1, 2, 1);

	/**
	 * Solar panel generates EU by day under open sky, accumulating at exactly {@code solarEuPerTick} ×
	 * globalEuRateMultiplier × ticks. Exercises the full day-brightness wiring on the NeoForge lane
	 * (level.isBrightOutside → produce → buffer). The buffer (8000) is far from full at 20 × 1 EU = 20.
	 * Mirrors: SolarPanelGameTest.tcSolar001Fun01_generatesByDay
	 */
	public static void solarPanelGeneratesByDay(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			int ticks = 20;
			for (int i = 0; i < ticks; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long perTick = Math.max(1, Math.round(Config.solarEuPerTick * Config.globalEuRateMultiplier));
			long expected = perTick * ticks;
			long got = panel.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("solar day generation over " + ticks + " ticks: got " + got + " EU, expected exactly "
						+ expected + " (perTick=" + perTick + ", bright=" + helper.getLevel().isBrightOutside() + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	/**
	 * Solar panel generates 0 EU at midnight (night mode). A panel that leaks day generation into the
	 * night (broken brightness read or cached skyDarken) would fail here.
	 * Mirrors: SolarPanelGameTest.tcSolar001Neg01_noEuAtNight
	 */
	public static void solarPanelNoEuAtNight(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.SOLAR_PANEL.get());
		setNight(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			for (int i = 0; i < 20; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long got = panel.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("solar panel generated " + got + " EU at midnight; expected 0 (bright="
						+ helper.getLevel().isBrightOutside() + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	// ── scenario 10: geothermal generator EU rate + no-lava negative ───────────────────────────────

	private static final BlockPos GEO = new BlockPos(1, 2, 1);

	/**
	 * Geothermal generator burns a lava bucket at exactly {@code geothermalEuPerTick} EU/t: over 5 ticks
	 * the buffer grows by {@code 5 × geothermalEuPerTick = 80} EU (buffer 4000 is far from full). Catches a
	 * regression that halves/doubles the conversion factor. The empty bucket is returned to the output slot.
	 * Mirrors: FluidGameTest.tcGeo001Fun01_lavaBucketProducesEu
	 */
	public static void geothermalLavaBucketRate(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, GEO) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
			geo.setItem(dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity.INPUT_SLOT,
					new ItemStack(Items.LAVA_BUCKET));
			int ticks = 5;
			for (int i = 0; i < ticks; i++) {
				geo.serverTick(helper.getLevel(), geo.getBlockPos(),
						helper.getLevel().getBlockState(geo.getBlockPos()));
			}
			long expected = (long) ticks * Config.geothermalEuPerTick;
			long got = geo.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("geothermal produced " + got + " EU over " + ticks + " ticks, expected exactly "
						+ expected + " (" + ticks + " × geothermalEuPerTick=" + Config.geothermalEuPerTick + ")");
				return;
			}
			if (!geo.getItem(dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity.OUTPUT_SLOT)
					.is(Items.BUCKET)) {
				helper.fail("lava bucket consumed but the empty bucket was not returned");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("geothermal block entity missing");
	}

	/**
	 * Geothermal generator with no lava produces 0 EU. Catches a regression where the generator produces
	 * EU unconditionally (ignores the lava/bucket gate).
	 * Mirrors: FluidGameTest.tcGeo001Neg01_noLavaNoEu
	 */
	public static void geothermalNoLavaNoEu(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, GEO) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
			for (int i = 0; i < 10; i++) {
				geo.serverTick(helper.getLevel(), geo.getBlockPos(),
						helper.getLevel().getBlockState(geo.getBlockPos()));
			}
			long got = geo.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("geothermal produced " + got + " EU without lava; expected 0");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("geothermal block entity missing");
	}

	// ── scenario 11: cable network negatives — diagonal no-connect, vanilla neighbor no-NPE ────────

	/**
	 * Two cables touching only at a corner/edge (no shared face) must NOT merge into one network:
	 * connections are strictly the 6 orthogonal faces, never diagonal.
	 * Mirrors: NetworkGameTest.tcCable001Phy09_diagonalCablesDoNotConnect
	 */
	public static void diagonalCablesDoNotConnect(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 3, 1); // +x AND +y from a: corner/edge contact only
		helper.setBlock(a, ModContent.COPPER_CABLE.get());
		helper.setBlock(b, ModContent.COPPER_CABLE.get());
		if (be(helper, a) instanceof CableBlockEntity ca) {
			ca.ensureRegistered();
		}
		if (be(helper, b) instanceof CableBlockEntity cb) {
			cb.ensureRegistered();
		}
		NetworkManager.tickAll(helper.getLevel());
		EnergyNetwork netA = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(a));
		EnergyNetwork netB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(b));
		if (netA == null || netB == null) {
			helper.fail("both diagonally-placed cables must register their own (separate) networks");
			return;
		}
		if (netA == netB) {
			helper.fail("diagonal (corner/edge-only) cables must NOT merge into one network (R-CON-06)");
			return;
		}
		helper.succeed();
	}

	/**
	 * A cable adjacent to a vanilla furnace (no Team Reborn / NeoForge energy capability) does not NPE
	 * during endpoint discovery and does not leak EU into it.
	 * Mirrors: NetworkGameTest.tcCable001Neg01_vanillaNeighborNoNpe
	 */
	public static void cableVanillaNeighborNoNpe(GameTestHelper helper) {
		BlockPos gen = new BlockPos(1, 2, 1);
		BlockPos cable = new BlockPos(2, 2, 1);
		BlockPos furnace = new BlockPos(3, 2, 1);
		helper.setBlock(gen, ModContent.GENERATOR.get());
		helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		helper.setBlock(furnace, Blocks.FURNACE);
		long genEuBefore = 0;
		if (be(helper, gen) instanceof GeneratorBlockEntity g) {
			g.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			genEuBefore = g.getEnergyStorage().getAmount();
		} else {
			helper.fail("generator block entity missing");
			return;
		}
		// 60 ticks: must not NPE while probing the vanilla furnace neighbour each tick.
		for (int i = 0; i < 60; i++) {
			tick(helper, be(helper, gen));
			tick(helper, be(helper, cable));
			NetworkManager.tickAll(helper.getLevel());
		}
		// Cross-loader no-leak oracle: the generator burns coal and produces EU, but the only neighbour
		// on the cable's far side is a vanilla furnace (no energy capability on either loader). EU must
		// therefore accumulate in the generator's own buffer (strictly more than before) — if it had
		// leaked into / been voided by the furnace, the buffer would not grow. This mirrors the Fabric
		// lane's EnergyStorage.SIDED.find(...)==null check without depending on a loader-specific API.
		long genEuAfter = be(helper, gen) instanceof GeneratorBlockEntity g2 ? g2.getEnergyStorage().getAmount() : -1;
		if (genEuAfter <= genEuBefore) {
			helper.fail("generator buffer did not grow over 60 ticks (before=" + genEuBefore
					+ ", after=" + genEuAfter + ") — EU likely leaked into the vanilla furnace or was voided");
			return;
		}
		helper.succeed();
	}

	// ── scenario 12: NBT persistence round-trip for all 4 machines ─────────────────────────────────

	/**
	 * NBT save/load round-trip preserves an electric furnace's energy + progress + input count.
	 * Exercises the shared {@code MachineBlockEntity} save/load path on the NeoForge lane for a machine
	 * OTHER than the macerator (already covered by {@link #nbtRoundTripPreservesState}).
	 * Mirrors: PersistenceGameTest (furnace round-trip)
	 */
	public static void furnaceNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(PER_POS);
		helper.setBlock(PER_POS, ModContent.ELECTRIC_FURNACE.get());
		dev.alaindustrial.block.entity.MachineBlockEntity src =
				helper.getBlockEntity(PER_POS, dev.alaindustrial.block.entity.MachineBlockEntity.class);
		if (src == null) {
			helper.fail("electric furnace block entity missing");
			return;
		}
		long energy0 = 2345L;
		int progress0 = 9;
		src.getEnergyStorage().amount = energy0;
		src.setItem(0, new ItemStack(Items.RAW_IRON, 3));
		src.getDataAccess().set(2, progress0);
		int input0 = src.getItem(0).getCount();

		CompoundTag tag = src.saveCustomOnly(registries);
		dev.alaindustrial.block.entity.MachineBlockEntity restored =
				new dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != energy0
				|| restored.getDataAccess().get(2) != progress0
				|| restored.getItem(0).getCount() != input0) {
			helper.fail("furnace round-trip mismatch: energy " + energy0 + "->"
					+ restored.getEnergyStorage().getAmount() + " progress " + progress0 + "->"
					+ restored.getDataAccess().get(2) + " input " + input0 + "->"
					+ restored.getItem(0).getCount());
			return;
		}
		helper.succeed();
	}

	// ── scenario 13: wind mill weather multiplier on a raised rig (R-NRG-04) ──────────────────────

	private static final BlockPos WIND_RAISED = new BlockPos(1, 20, 1); // above sea level (see WindMillGameTest)

	/** Place a wind mill on a glass pillar at WIND_RAISED with a rotor. */
	private static WindMillBlockEntity placeWindRaised(GameTestHelper helper) {
		for (int y = 2; y < WIND_RAISED.getY(); y++) {
			helper.setBlock(new BlockPos(WIND_RAISED.getX(), y, WIND_RAISED.getZ()), Blocks.GLASS);
		}
		helper.setBlock(WIND_RAISED, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(WIND_RAISED, WindMillBlockEntity.class);
		if (mill != null) {
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(dev.alaindustrial.registry.ModContent.WINDMILL_ROTOR.get()));
		}
		return mill;
	}

	/**
	 * A thunderstorm multiplies the wind mill's base rate (×windMillThunderFactor). Rather than calling
	 * {@code WindMillOutput.euFor} for the oracle (which would make the test tautological — the same code
	 * under test computes the expected value), this drives the real mill in-world for a fixed tick window
	 * at clear weather and at thunder, measuring the buffer growth each time. The storm rate must be
	 * STRICTLY greater than the clear rate (thunder factor > 1), proving the weather wiring end-to-end
	 * through the block entity, not just the static helper.
	 * Mirrors: WindMillGameTest.tcWindmill001Sta01_thunderMultipliesRate
	 */
	public static void windMillThunderMultipliesRate(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWindRaised(helper);
		if (mill == null) {
			helper.fail("raised wind mill block entity missing");
			return;
		}
		ServerLevel level = helper.getLevel();
		int ticks = Config.windMillSampleTicks > 0 ? Config.windMillSampleTicks : 40;

		// Clear-weather sample: empty buffer, drive, measure growth.
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		mill.getEnergyStorage().amount = 0;
		for (int i = 0; i < ticks; i++) {
			mill.serverTick(level, mill.getBlockPos(), level.getBlockState(mill.getBlockPos()));
		}
		long clearRate = mill.getEnergyStorage().getAmount();

		// Storm sample: empty buffer again, drive, measure growth.
		level.getWeatherData().setRaining(true);
		level.getWeatherData().setThundering(true);
		level.setRainLevel(1.0f);
		mill.getEnergyStorage().amount = 0;
		for (int i = 0; i < ticks; i++) {
			mill.serverTick(level, mill.getBlockPos(), level.getBlockState(mill.getBlockPos()));
		}
		long stormRate = mill.getEnergyStorage().getAmount();

		if (clearRate <= 0) {
			helper.fail("raised wind mill generated 0 EU over " + ticks + " clear-weather ticks"
					+ " — raise the rig further so height base > 0");
			return;
		}
		if (stormRate <= clearRate) {
			helper.fail("thunder did not raise wind output: clear=" + clearRate + " storm=" + stormRate
					+ " over " + ticks + " ticks (expected storm > clear, thunderFactor="
					+ Config.windMillThunderFactor + ")");
			return;
		}
		helper.succeed();
	}

	// ── scenario 15: generator full buffer pauses burn (R-NRG-11) ──────────────────────────────────

	/**
	 * A generator with coal but a FULL buffer must pause the burn — burnTime must not decrement while
	 * there is no room for the produced EU. Catches a regression that wastes fuel when full.
	 * Mirrors: GeneratorGameTest.tcGen001Neg03_fullBufferPausesBurn
	 */
	public static void generatorFullBufferPausesBurn(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			// Start a burn.
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			// Force buffer full.
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			int burn1 = gen.getDataAccess().get(2); // progress == burnTime
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			int burn2 = gen.getDataAccess().get(2);
			if (!(burn1 > 0 && burn1 == burn2)) {
				helper.fail("full buffer must freeze burn: burn1=" + burn1 + " burn2=" + burn2);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	/**
	 * Generator EU rate equals {@code Config.fuelEuPerTick} (canon 8 EU/t): one clean tick from empty
	 * produces exactly that much.
	 * Mirrors: GeneratorGameTest.tcGen001Prf01_ratePerTickMatchesConfig
	 */
	public static void generatorRatePerTickMatchesConfig(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos())); // tick 1 starts the burn
			gen.getEnergyStorage().amount = 0; // measure one clean tick from empty
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			long made = gen.getEnergyStorage().getAmount();
			if (made != Config.fuelEuPerTick) {
				helper.fail("EU/t expected " + Config.fuelEuPerTick + " but measured " + made);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	// ── scenario 16: battery box conservation — genDrain == boxGain (no leak) ──────────────────────

	/**
	 * A cabled generator whose only consumer is a battery box with partial room loses EXACTLY what the
	 * box gains — no surplus destroyed against the maxInsert==0 generator on the cable path. Pre-charge
	 * the generator (no fuel: buffer only goes down), pre-charge the box to leave 5 EU of room, drive,
	 * assert genDrain == boxGain (cable loss floors to 0 on a 1-cable line).
	 * Mirrors: NetworkGameTest.tcCable001Nrg03_generatorNotDrainedByPartialConsumer
	 */
	public static void batteryBoxConservationPartialConsumer(GameTestHelper helper) {
		helper.setBlock(LINE_GEN, ModContent.GENERATOR.get());
		helper.setBlock(LINE_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(LINE_MAC, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		long genStart = Config.generatorBuffer;
		if (be(helper, LINE_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = genStart; // no fuel: buffer only goes down
			gen.setChanged();
		}
		final long room = 5;
		long boxStart = Config.batteryBoxBuffer - room;
		if (be(helper, LINE_MAC) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = boxStart; // leave exactly `room` EU
			bb.setChanged();
		}
		driveLine(helper, 10);
		long genEnd = be(helper, LINE_GEN) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1;
		long boxEnd = be(helper, LINE_MAC) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		long genDrain = genStart - genEnd;
		long boxGain = boxEnd - boxStart;
		if (boxEnd != Config.batteryBoxBuffer) {
			helper.fail("battery box did not top off its 5 EU of room: " + boxEnd + "/" + Config.batteryBoxBuffer);
			return;
		}
		if (genDrain != boxGain) {
			helper.fail("generator lost " + genDrain + " EU but box gained " + boxGain
					+ " — surplus destroyed (cable-path EnergyMover-class leak)");
			return;
		}
		helper.succeed();
	}

	// ── scenario 17: moonlit solar panel night generation ──────────────────────────────────────────

	private static final BlockPos MOONLIT = new BlockPos(1, 2, 1);

	/**
	 * Moonlit solar panel generates EU at midnight, accumulating at exactly {@code moonlitEuPerTick} ×
	 * globalEuRateMultiplier × ticks. Exercises the night-brightness branch (inverse of the day panel).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Fun01_generatesAtNight
	 */
	public static void moonlitPanelGeneratesAtNight(GameTestHelper helper) {
		helper.setBlock(MOONLIT, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		if (be(helper, MOONLIT) instanceof dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity panel) {
			int ticks = 20;
			for (int i = 0; i < ticks; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long perTick = Math.max(1, Math.round(Config.moonlitEuPerTick * Config.globalEuRateMultiplier));
			long expected = perTick * ticks;
			long got = panel.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("moonlit night generation over " + ticks + " ticks: got " + got + " EU, expected exactly "
						+ expected + " (perTick=" + perTick + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("moonlit panel block entity missing");
	}

	// ── scenario 18: extractor + compressor positive recipes ──────────────────────────────────────

	/**
	 * Compressor compresses a copper dust into a copper ingot. Proves the compressor's recipe lookup
	 * resolves on the NeoForge lane (recipe-type registration seam) — distinct from the macerator case.
	 * Mirrors: MachineGameTest.tcComp001Fun02_compressorMakesCopperIngot
	 */
	public static void compressorMakesCopperIngot(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.COMPRESSOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(dev.alaindustrial.registry.ModContent.COPPER_DUST.get(), 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.COPPER_INGOT)) {
				helper.fail("compressor did not produce a copper ingot from copper dust: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("compressor block entity missing");
	}

	/**
	 * Extractor extracts flint from gravel. Proves the extractor's recipe lookup resolves on the NeoForge
	 * lane — distinct from the macerator/compressor cases.
	 * Mirrors: MachineGameTest.tcExtr001Fun02a_extractorMakesFlint
	 */
	public static void extractorMakesFlint(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.GRAVEL, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.FLINT)) {
				helper.fail("extractor did not produce flint from gravel: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	/**
	 * Electric furnace smelts raw iron into an iron ingot. Proves the furnace's recipe lookup (mod +
	 * vanilla fallback) resolves on the NeoForge lane.
	 * Mirrors: MachineGameTest.tcMach002Fun01_furnaceSmeltsRawIron
	 */
	public static void furnaceSmeltsRawIron(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.ELECTRIC_FURNACE.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.IRON_INGOT)) {
				helper.fail("electric furnace did not smelt raw iron into an iron ingot: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("electric furnace block entity missing");
	}

	// ── scenario 19: pump source → tank → sink (fluid transport) ───────────────────────────────────

	private static final BlockPos PUMP = new BlockPos(2, 2, 1);
	private static final BlockPos PUMP_SOURCE = new BlockPos(3, 2, 1);
	private static final BlockPos PUMP_SINK = new BlockPos(1, 2, 1);

	/**
	 * Pump moves a lava source into an adjacent geothermal generator's tank, which then burns it for EU.
	 * Proves the FluidPort/FluidTank abstraction works end-to-end on the NeoForge lane with a real fluid
	 * source (Capabilities.Fluid.BLOCK resolves, acquire+push commit, geo burns).
	 * Mirrors: FluidGameTest.tcFluidPump_lavaSourceToGeothermal (compressed)
	 */
	public static void pumpSourceToTankToSinkToEu(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(PUMP, ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST)); // face the source
		helper.setBlock(PUMP_SOURCE, Blocks.LAVA);
		// Geo sink on the pump's west face
		helper.setBlock(PUMP_SINK, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, PUMP) instanceof dev.alaindustrial.block.entity.PumpBlockEntity pump) {
			pump.getEnergyStorage().amount = Config.pumpEuPerBucket * 4; // ample supply
			for (int i = 0; i < 40; i++) {
				pump.serverTick(level, helper.absolutePos(PUMP),
						level.getBlockState(helper.absolutePos(PUMP)));
				if (be(helper, PUMP_SINK) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
					geo.serverTick(level, helper.absolutePos(PUMP_SINK),
							level.getBlockState(helper.absolutePos(PUMP_SINK)));
				}
			}
			// The geo either burned the acquired lava to EU or has lava in its tank; either way the
			// pump moved fluid across. Assert the source was consumed OR the geo buffer grew.
			boolean sourceGone = !level.getFluidState(helper.absolutePos(PUMP_SOURCE))
					.isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA);
			long geoEu = be(helper, PUMP_SINK) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity g
					? g.getEnergyStorage().getAmount() : -1;
			if (!sourceGone && geoEu <= 0) {
				helper.fail("pump moved no lava: source still present=" + !sourceGone + " geoEu=" + geoEu);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("pump block entity missing");
	}

	// ── scenario 20: machine no-recipe negative (parametric, all 4 machines) ───────────────────────

	/**
	 * Electric furnace with a non-recipe input (dirt) spends no EU even when fully powered. Catches a
	 * regression that consumes EU for an invalid input.
	 * Mirrors: MachineGameTest.tcMach002Neg05_furnaceNonRecipeNoEuSpent
	 */
	public static void furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.ELECTRIC_FURNACE.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			long start = 800;
			mac.getEnergyStorage().amount = start;
			mac.setItem(0, new ItemStack(Items.DIRT, 1));
			for (int i = 0; i < 200; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (!mac.getItem(1).isEmpty()) {
				helper.fail("furnace produced output from dirt: " + mac.getItem(1));
				return;
			}
			if (mac.getEnergyStorage().amount != start) {
				helper.fail("furnace spent EU on a non-recipe: " + start + " -> " + mac.getEnergyStorage().amount);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("electric furnace block entity missing");
	}

	/**
	 * Compressor full output (64 copper ingots) jams: no overflow, no progress, input not consumed.
	 * Mirrors: MachineGameTest.tcMach003Neg03_compressorFullOutputJamsMachine
	 */
	public static void compressorFullOutputJams(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.COMPRESSOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(dev.alaindustrial.registry.ModContent.COPPER_DUST.get(), 4));
			mac.setItem(1, new ItemStack(Items.COPPER_INGOT, 64));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int outCount = mac.getItem(1).getCount();
			int progress = mac.getDataAccess().get(2);
			if (outCount != 64) {
				helper.fail("compressor output overflowed: " + outCount + " (expected 64)");
				return;
			}
			if (progress != 0) {
				helper.fail("compressor advanced progress to " + progress + " despite full output");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("compressor block entity missing");
	}

	/**
	 * Extractor input swap mid-operation resets progress (R-NRG-10): blaze_rod swapped for gravel
	 * clears accumulated progress.
	 * Mirrors: MachineGameTest.tcMach004Fun04_extractorInputSwapResetsProgress
	 */
	public static void extractorInputSwapResetsProgress(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.BLAZE_ROD, 1));
			int halfway = Config.extractorDuration / 2;
			for (int i = 0; i < halfway; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int progressBefore = mac.getDataAccess().get(2);
			if (progressBefore <= 0) {
				helper.fail("expected partial progress before swap but got " + progressBefore);
				return;
			}
			mac.setItem(0, new ItemStack(Items.GRAVEL, 1));
			if (mac.getDataAccess().get(2) != 0) {
				helper.fail("progress did not reset after swapping blaze_rod -> gravel: "
						+ mac.getDataAccess().get(2));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	// ── scenario 21: daylight solar panel day generation ───────────────────────────────────────────

	/**
	 * Daylight solar panel generates EU by day, accumulating at exactly {@code daylightEuPerTick} ×
	 * globalEuRateMultiplier × ticks. The daylight panel is the day-evolved branch (4 EU/t).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Fun01_generatesByDay
	 */
	public static void daylightPanelGeneratesByDay(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity panel) {
			int ticks = 20;
			for (int i = 0; i < ticks; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long perTick = Math.max(1, Math.round(Config.daylightEuPerTick * Config.globalEuRateMultiplier));
			long expected = perTick * ticks;
			long got = panel.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("daylight day generation over " + ticks + " ticks: got " + got + " EU, expected exactly "
						+ expected + " (perTick=" + perTick + ", bright=" + helper.getLevel().isBrightOutside() + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("daylight panel block entity missing");
	}

	/**
	 * Daylight solar panel is day-only: at midnight it produces 0 EU (it does NOT inherit the moonlit
	 * night branch). Catches a regression that lets the evolved panel generate at night.
	 */
	public static void daylightPanelNoEuAtNight(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setNight(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity panel) {
			for (int i = 0; i < 20; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long got = panel.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("daylight panel generated " + got + " EU at midnight; expected 0 (day-only)");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("daylight panel block entity missing");
	}

	// ── scenario 22: extra machine recipes (multi-output / vanilla fallback) ───────────────────────

	/**
	 * Extractor produces 3× blaze powder from a blaze rod (multi-output recipe). Catches a regression
	 * that drops the ×3 multiplier.
	 * Mirrors: MachineGameTest.tcMach004Fun02_extractorConsumesExactlyOnePerOperation
	 */
	public static void extractorBlazeRodToPowder(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.BLAZE_ROD, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.BLAZE_POWDER) || out.getCount() < 3) {
				helper.fail("extractor blaze_rod expected >=3 blaze_powder, got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	/**
	 * Electric furnace smelts sand to glass (vanilla smelting fallback). Proves the furnace inherits the
	 * vanilla {@code minecraft:smelting} recipe type, not just the mod's own recipes.
	 * Mirrors: MachineGameTest.tcEfurn001Fun03a_furnaceSmeltsSand
	 */
	public static void furnaceSmeltsSandToGlass(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.ELECTRIC_FURNACE.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.SAND, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.GLASS)) {
				helper.fail("furnace did not smelt sand to glass: " + (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("electric furnace block entity missing");
	}

	/**
	 * Compressor compresses an iron dust into an iron ingot. Mirrors the vanilla ingot-forming path.
	 * Mirrors: MachineGameTest.tcComp001Fun04_compressorMakesIronIngot
	 */
	public static void compressorIronDustToIngot(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.COMPRESSOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(dev.alaindustrial.registry.ModContent.IRON_DUST.get(), 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.IRON_INGOT)) {
				helper.fail("compressor did not produce iron ingot from iron dust: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("compressor block entity missing");
	}

	// ── scenario 23: machine E_op exact / E_op−1 BVA (parametric) ──────────────────────────────────

	/**
	 * Macerator E_op exact BVA: exactly {@code maceratorDuration × machineEuPerTick} EU completes one
	 * operation and leaves the buffer at 0. Catches an off-by-one in the EU-per-tick accounting.
	 * Mirrors: MachineGameTest.tcMach001Prf04_maceratorEopExactCompletes
	 */
	public static void maceratorEopExactCompletes(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			long eOp = (long) Config.maceratorDuration * Config.machineEuPerTick;
			mac.getEnergyStorage().amount = eOp;
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			for (int i = 0; i < Config.maceratorDuration; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (mac.getItem(1).isEmpty() || !mac.getItem(1).is(dev.alaindustrial.registry.ModContent.IRON_DUST.get())) {
				helper.fail("E_op exact (" + eOp + ") did not complete the maceration");
				return;
			}
			if (mac.getEnergyStorage().amount != 0) {
				helper.fail("E_op exact should leave amount==0 but got " + mac.getEnergyStorage().amount);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	/**
	 * Macerator E_op−1 BVA: one EU short of a full operation never produces output and progress freezes
	 * at {@code duration − 1}. Catches a regression that completes on a short budget.
	 * Mirrors: MachineGameTest.tcMach001Prf03_maceratorEopMinusOneStalls
	 */
	public static void maceratorEopMinusOneStalls(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = (long) Config.maceratorDuration * Config.machineEuPerTick - 1;
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (!mac.getItem(1).isEmpty()) {
				helper.fail("E_op−1 must not produce any output");
				return;
			}
			int progress = mac.getDataAccess().get(2);
			if (progress != Config.maceratorDuration - 1) {
				helper.fail("E_op−1 progress expected " + (Config.maceratorDuration - 1) + " but got " + progress);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 24: generator buffer cap-1 BVA (R-NRG-01) ─────────────────────────────────────────

	/**
	 * Generator buffer caps at {@code Config.generatorBuffer} (BVA): pre-charged to cap−1, after a
	 * burning tick it tops off to exactly cap, never above. Starting at cap only proves "stays full";
	 * the cap−1 leg proves the boundary is actually reached and enforced.
	 * Mirrors: GeneratorGameTest.tcGen001Fun02_bufferCapsAtMax
	 */
	public static void generatorBufferCapsAtMaxBva(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			long cap = gen.getEnergyStorage().getCapacity();
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = cap - 1; // BVA: one EU short
			// A few ticks: the burn must start and produce EU, topping the buffer off to exactly cap.
			for (int i = 0; i < 5; i++) {
				gen.serverTick(helper.getLevel(), gen.getBlockPos(),
						helper.getLevel().getBlockState(gen.getBlockPos()));
			}
			long got = gen.getEnergyStorage().getAmount();
			if (got != cap) {
				helper.fail("generator buffer did not settle at cap: expected " + cap + " (from cap-1) got " + got);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	/**
	 * Solar panel buffer caps at {@code Config.solarBuffer} (BVA): pre-charged to cap−1, after a clear-day
	 * tick it tops off to exactly cap.
	 * Mirrors: SolarPanelGameTest.tcSolar001Prf02_bufferCapsAtMax
	 */
	public static void solarPanelBufferCapsAtMaxBva(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			long cap = Config.solarBuffer;
			panel.getEnergyStorage().amount = cap - 1;
			for (int i = 0; i < 5; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long got = panel.getEnergyStorage().getAmount();
			if (got != cap) {
				helper.fail("solar buffer did not settle at cap: expected " + cap + " (from cap-1) got " + got);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	// ── scenario 25: solar evolution chip (day → daylight) ─────────────────────────────────────────

	private static final BlockPos EVO = new BlockPos(1, 2, 1);

	/**
	 * A base solar panel with a day alignment chip, under clear daylight, evolves into the daylight
	 * panel after {@code solarEvolveTicks} of accumulated sky-time. The block in the world changes from
	 * SOLAR_PANEL to DAYLIGHT_SOLAR_PANEL. Catches a regression that breaks the evolution wiring.
	 * Mirrors: SolarPanelGameTest.tcSolar001Fun02_dayChipEvolvesToDaylight
	 */
	public static void solarDayChipEvolvesToDaylight(GameTestHelper helper) {
		helper.setBlock(EVO, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, EVO) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			panel.setItem(dev.alaindustrial.block.entity.SolarPanelBlockEntity.CHIP_SLOT,
					new ItemStack(dev.alaindustrial.registry.ModContent.ALIGNMENT_CHIP_DAY.get()));
			for (int i = 0; i < Config.solarEvolveTicks + 100; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
				// Re-grab the BE: evolution replaces it with a DaylightSolarPanelBlockEntity.
				if (!(be(helper, EVO) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity)) {
					break;
				}
			}
			net.minecraft.world.level.block.state.BlockState evolved =
					helper.getLevel().getBlockState(helper.absolutePos(EVO));
			if (evolved.getBlock() != ModContent.DAYLIGHT_SOLAR_PANEL.get()) {
				helper.fail("solar panel did not evolve into daylight after " + Config.solarEvolveTicks
						+ " ticks with a day chip; block=" + evolved.getBlock());
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	// ── scenario 26: ore drop tier-gate (R-BRK-09, all 4 metals) ───────────────────────────────────

	private static final BlockPos ORE = new BlockPos(1, 2, 1);

	/**
	 * Ore blocks require a minimum pickaxe tier: tin/silver/nickel need stone+, uranium needs iron+.
	 * A wooden pickaxe (below every ore's tier) must NOT be the correct tool for drops on any of them.
	 * Catches a regression that drops the {@code needs_stone_tool}/{@code needs_iron_tool} tags.
	 * Mirrors: OreGameTest.tcOre001Brk02_pickaxeTierGate (wooden-too-low leg)
	 */
	public static void oreWoodenPickaxeNoDrop(GameTestHelper helper) {
		net.minecraft.world.item.ItemStack woodenPick = new net.minecraft.world.item.ItemStack(Items.WOODEN_PICKAXE);
		java.util.function.Supplier<net.minecraft.world.level.block.Block>[] ores = new java.util.function.Supplier[]{
				ModContent.TIN_ORE, ModContent.DEEPSLATE_TIN_ORE,
				ModContent.SILVER_ORE, ModContent.DEEPSLATE_SILVER_ORE,
				ModContent.NICKEL_ORE, ModContent.DEEPSLATE_NICKEL_ORE,
				ModContent.URANIUM_ORE, ModContent.DEEPSLATE_URANIUM_ORE,
		};
		int rel = 1;
		for (java.util.function.Supplier<net.minecraft.world.level.block.Block> ore : ores) {
			BlockPos orePos = new BlockPos(rel, 2, 1);
			helper.setBlock(orePos, ore.get());
			net.minecraft.world.level.block.state.BlockState state =
					helper.getLevel().getBlockState(helper.absolutePos(orePos));
			if (woodenPick.isCorrectToolForDrops(state)) {
				helper.fail("wooden pickaxe is the correct tool for " + ore + " — tier gate (needs_stone/iron_tool) missing");
				return;
			}
			rel++;
		}
		helper.succeed();
	}

	/**
	 * A stone pickaxe IS the correct tool for tin/silver/nickel (needs_stone_tool), but NOT for uranium
	 * (needs_iron_tool). Catches a regression that mis-tags uranium as stone-tier or the others as iron-tier.
	 * Mirrors: OreGameTest.tcOre001Brk02_pickaxeTierGate (stone/iron legs)
	 */
	public static void oreStonePickaxeTierGate(GameTestHelper helper) {
		net.minecraft.world.item.ItemStack stonePick = new net.minecraft.world.item.ItemStack(Items.STONE_PICKAXE);
		net.minecraft.world.item.ItemStack ironPick = new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE);
		// Stone-tier ores: stone pick OK.
		java.util.function.Supplier<net.minecraft.world.level.block.Block>[] stoneOres = new java.util.function.Supplier[]{
				ModContent.TIN_ORE, ModContent.SILVER_ORE, ModContent.NICKEL_ORE,
		};
		int rel = 1;
		for (java.util.function.Supplier<net.minecraft.world.level.block.Block> ore : stoneOres) {
			BlockPos orePos = new BlockPos(rel, 2, 2);
			helper.setBlock(orePos, ore.get());
			net.minecraft.world.level.block.state.BlockState state =
					helper.getLevel().getBlockState(helper.absolutePos(orePos));
			if (!stonePick.isCorrectToolForDrops(state)) {
				helper.fail("stone pickaxe is NOT the correct tool for " + ore + " (needs_stone_tool)");
				return;
			}
			rel++;
		}
		// Uranium: stone pick too low, iron pick OK.
		BlockPos uraniumPos = new BlockPos(1, 2, 3);
		helper.setBlock(uraniumPos, ModContent.URANIUM_ORE.get());
		net.minecraft.world.level.block.state.BlockState uraniumState =
				helper.getLevel().getBlockState(helper.absolutePos(uraniumPos));
		if (stonePick.isCorrectToolForDrops(uraniumState)) {
			helper.fail("stone pickaxe is the correct tool for uranium — must need iron+ (needs_iron_tool)");
			return;
		}
		if (!ironPick.isCorrectToolForDrops(uraniumState)) {
			helper.fail("iron pickaxe is NOT the correct tool for uranium (needs_iron_tool)");
			return;
		}
		helper.succeed();
	}

	// ── scenario 27: network split + rejoin (R-CON-04, R-CON-09) ───────────────────────────────────

	/**
	 * Removing the only cable between a generator and a macerator stops delivery; replacing it resumes
	 * flow. Catches a regression where the network fails to split/rejoin on topology change.
	 * Mirrors: NetworkGameTest.it001Neg01_breakStopsDelivery + it001Fun02_rejoinResumesFlow
	 */
	public static void networkSplitRejoinResumesFlow(GameTestHelper helper) {
		buildLine(helper);
		driveLine(helper, 60);
		// Break the cable: the macerator must stop receiving.
		helper.setBlock(LINE_CABLE, Blocks.AIR);
		if (be(helper, LINE_MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 0;
		}
		driveLine(helper, 40);
		long afterBreak = be(helper, LINE_MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity m
				? m.getEnergyStorage().getAmount() : -1;
		if (afterBreak != 0) {
			helper.fail("macerator kept receiving EU after the cable was removed: " + afterBreak);
			return;
		}
		// Rejoin: replacing the cable must resume flow.
		helper.setBlock(LINE_CABLE, ModContent.COPPER_CABLE.get());
		driveLine(helper, 80);
		long afterRejoin = be(helper, LINE_MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity m
				? m.getEnergyStorage().getAmount() : -1;
		if (afterRejoin <= 0) {
			helper.fail("flow did not resume after the cable was replaced: " + afterRejoin);
			return;
		}
		helper.succeed();
	}

	// ── scenario 28: geothermal fluid tank droplet↔MB boundary ─────────────────────────────────────

	/**
	 * One lava bucket yields exactly {@code geothermalBurnTicks × geothermalEuPerTick} EU total — the
	 * canonical fuel-value invariant (one bucket = 1000 burn ticks × 16 EU/t = 16000 EU). The geothermal
	 * converts a bucket directly into its internal {@code lavaTicks} burn buffer (not the fluid tank, which
	 * is pump-fed), so this asserts the cumulative EU after the whole bucket burns. Catches a regression
	 * that drops the conversion factor or mis-counts burn ticks. Drives enough ticks to exhaust the burn
	 * and drains the EU buffer between ticks so the cap does not mask the total.
	 * Mirrors: FluidGameTest.tcGeo001Prf03_oneBucketYieldsTotalEu
	 */
	public static void geothermalTankBucketBoundary(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, GEO) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
			geo.setItem(dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity.INPUT_SLOT,
					new ItemStack(Items.LAVA_BUCKET));
			long expected = (long) Config.geothermalBurnTicks * Config.geothermalEuPerTick;
			long total = 0;
			// Drive well past the burn duration, draining the buffer each tick so the cap never pauses
			// the burn (which would mask the cumulative total).
			for (int i = 0; i < Config.geothermalBurnTicks + 100; i++) {
				geo.serverTick(helper.getLevel(), geo.getBlockPos(),
						helper.getLevel().getBlockState(geo.getBlockPos()));
				total += geo.getEnergyStorage().getAmount();
				geo.getEnergyStorage().amount = 0; // drain, so production never pauses
			}
			if (total != expected) {
				helper.fail("one lava bucket yielded " + total + " EU total, expected exactly " + expected
						+ " (geothermalBurnTicks=" + Config.geothermalBurnTicks + " × geothermalEuPerTick="
						+ Config.geothermalEuPerTick + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("geothermal block entity missing");
	}

	// ── scenario 29: generator rejects external EU (producer-only, R-NRG-03) ────────────────────────

	/**
	 * The generator publishes {@code maxInsert == 0}: it is a producer only and never accepts external EU.
	 * A regression that lets the generator soak up EU from its neighbours (acting as a sink) would fail
	 * here. Catches the same invariant as the Fabric lane's GeneratorGameTest NEG01.
	 * Mirrors: GeneratorGameTest.tcGen001Neg01_rejectsExternalEu
	 */
	public static void generatorRejectsExternalEu(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			if (gen.getEnergyStorage().supportsInsertion()) {
				helper.fail("generator storage supports insertion — it must be a producer only (maxInsert=0)");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	// ── scenario 30: battery box drop carries EU (R-BRK-07) ────────────────────────────────────────

	/**
	 * A charged battery box emits its buffered EU as the {@code STORED_ENERGY} data component on
	 * {@code collectComponents()} — what the loot table's {@code copy_components} reads onto the drop.
	 * This is the NeoForge lane's second check of the data-component seam (the first is
	 * {@link #batteryBoxDropCarriesEnergy}); here a different charge value exercises the same path.
	 * Mirrors: BatteryBoxGameTest.tcBatteryBox001Brk07_energyCarriedByComponent
	 */
	public static void batteryBoxDropCarriesEnergyHalfCharge(GameTestHelper helper) {
		helper.setBlock(DROP, ModContent.BATTERY_BOX.get());
		if (be(helper, DROP) instanceof BatteryBoxBlockEntity bb) {
			long charge = bb.getEnergyStorage().getCapacity() / 2; // half charge (≠ the 12345 in the other case)
			bb.getEnergyStorage().amount = charge;
			DataComponentMap map = bb.collectComponents();
			Long carried = map.get(ModDataComponents.STORED_ENERGY.get());
			if (carried == null || carried.longValue() != charge) {
				helper.fail("battery box did not carry half-charge STORED_ENERGY: " + carried + "/" + charge);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("battery box block entity missing");
	}

	// ── scenario 31: cable throughput cap ≤ LV.maxVoltage (R-NRG-04) ───────────────────────────────

	/**
	 * A cabled generator with an ample pre-charged buffer delivers at most {@code EnergyTier.LV.maxVoltage()}
	 * EU to the consumer in one network tick; the cable is not destroyed and the surplus is simply not
	 * transferred (no overvoltage penalty). Catches a regression that removes the per-tick packet cap.
	 * Mirrors: NetworkGameTest.tcCable001Nrg01_throughputCappedAtLvVoltage
	 */
	public static void cableThroughputCappedAtLv(GameTestHelper helper) {
		BlockPos genPos = new BlockPos(1, 2, 1);
		BlockPos cablePos = new BlockPos(2, 2, 1);
		BlockPos boxPos = new BlockPos(3, 2, 1);
		helper.setBlock(genPos, ModContent.GENERATOR.get());
		helper.setBlock(cablePos, ModContent.COPPER_CABLE.get());
		helper.setBlock(boxPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		long cap = EnergyTier.LV.maxVoltage();
		if (be(helper, genPos) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = cap * 10; // ample, no fuel: buffer fixed for this tick
		}
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0; // empty: room is never the limit
		}
		// Register + run exactly one network tick so the packet-cap limit is observed cleanly.
		tick(helper, be(helper, genPos));
		tick(helper, be(helper, cablePos));
		NetworkManager.tickAll(helper.getLevel());
		long got = be(helper, boxPos) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		if (got > cap) {
			helper.fail("cable exceeded the LV throughput cap in one tick: delivered=" + got + " cap=" + cap);
			return;
		}
		if (got <= 0) {
			helper.fail("cable delivered no EU even though supply and room were ample: " + got);
			return;
		}
		helper.succeed();
	}

	// ── scenario 32: machine lit blockstate tracks active/idle (R-VIS-01) ──────────────────────────

	/**
	 * The macerator's {@code lit} blockstate is on while an operation is progressing (powered + valid
	 * input) and off once the machine has no work left (input exhausted). Catches a regression that
	 * leaves the block permanently lit (or never lights it).
	 * Mirrors: MachineGameTest.tcMach001Sta01_maceratorLitTracksActive
	 */
	public static void maceratorLitTracksActive(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			BlockPos abs = mac.getBlockPos();
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			for (int i = 0; i < 3; i++) {
				mac.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
			}
			if (!helper.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
				helper.fail("macerator must be LIT while actively processing");
				return;
			}
			// Drain the input so the machine has nothing left; give it a tick to clear LIT.
			mac.setItem(0, ItemStack.EMPTY);
			for (int i = 0; i < 3; i++) {
				mac.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
			}
			if (helper.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
				helper.fail("macerator must not stay LIT once there is no input left to process");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 33: extra machine recipes — cactus (×2 dye), pumpkin (×5 seeds) ──────────────────

	/**
	 * Extractor produces 2× green dye from a cactus (plant-derived ×2 recipe). Catches a regression that
	 * drops the ×2 multiplier on the plant-processing niche.
	 * Mirrors: MachineGameTest.tcExtr001Fun06_extractorMakesGreenDye
	 */
	public static void extractorCactusToGreenDye(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.CACTUS, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.DYE.green()) || out.getCount() < 2) {
				helper.fail("extractor cactus expected >=2 green_dye, got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	/**
	 * Extractor produces 5× pumpkin seeds from a pumpkin (the largest multiplier in the recipe set, ×5).
	 * Exercises a distinct stack-fit boundary from the ×2 / ×3 paths.
	 * Mirrors: MachineGameTest.tcExtr001Fun07_extractorMakesPumpkinSeeds
	 */
	public static void extractorPumpkinToSeeds(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.PUMPKIN, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.PUMPKIN_SEEDS) || out.getCount() < 5) {
				helper.fail("extractor pumpkin expected >=5 pumpkin_seeds, got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	// ── scenario 34: macerator tag-ingredient recipe (iron_ore ×2 doubling) ───────────────────────

	/**
	 * Macerator grinds an iron ORE BLOCK into 2× iron dust via the {@code #alaindustrial:macerable_iron}
	 * tag — the ×2 doubling path for the ore-block input (resolved through the item tag). Under MOD-095
	 * raw_iron also macerates to ×2 via its own direct recipe, so both inputs double. Proves tag
	 * ingredients resolve on the NeoForge lane.
	 * Mirrors: MachineGameTest.tcMach001FunIronOre_maceratorGrindsIronOre
	 */
	public static void maceratorIronOreDoublesDust(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 8000;
			mac.setItem(0, new ItemStack(Items.IRON_ORE, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(dev.alaindustrial.registry.ModContent.IRON_DUST.get())
					|| out.getCount() < 2) {
				helper.fail("macerator iron_ore expected >=2 iron_dust (×2 doubling), got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 35: wind mill roofed → 0 EU (mode wiring) ─────────────────────────────────────────

	/**
	 * A roofed wind mill (no open-sky column above) produces 0 EU regardless of height/weather. Catches
	 * a regression that drops the open-sky gate. The mill is at the default low POS, so height is already
	 * 0; a stone block above makes the mode ROOFED, distinguishing "dead from roof" from "dead from height".
	 * Mirrors: WindMillGameTest.tcWindmill001Neg01_roofedYieldsZero (mode leg)
	 */
	public static void windMillRoofedYieldsZero(GameTestHelper helper) {
		BlockPos millPos = new BlockPos(1, 2, 1);
		helper.setBlock(millPos, ModContent.WIND_MILL.get());
		helper.setBlock(millPos.above(), Blocks.STONE); // roof
		if (be(helper, millPos) instanceof WindMillBlockEntity mill) {
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT,
					new ItemStack(dev.alaindustrial.registry.ModContent.WINDMILL_ROTOR.get()));
			mill.getEnergyStorage().amount = 0;
			for (int i = 0; i < Config.windMillSampleTicks + 5; i++) {
				mill.serverTick(helper.getLevel(), mill.getBlockPos(),
						helper.getLevel().getBlockState(mill.getBlockPos()));
			}
			long got = mill.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("roofed wind mill generated " + got + " EU; expected 0 (no open sky)");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("wind mill block entity missing");
	}

	// ── scenario 36: two-generator supply sums into one consumer (R-CON-16, no dupe) ──────────────

	/**
	 * Two LV generators feeding one battery box through joined cables deliver summed supply (bounded by
	 * the LV packet cap) — the delivered EU exceeds what a single generator alone could deliver, proving
	 * the two sources sum rather than one shadowing the other.
	 * Mirrors: NetworkGameTest.tcCable001Con05_twoGeneratorsSumIntoOneConsumer
	 */
	public static void twoGeneratorsSumIntoOneConsumer(GameTestHelper helper) {
		BlockPos genA = new BlockPos(1, 2, 1);
		BlockPos cableA = new BlockPos(2, 2, 1);
		BlockPos cableB = new BlockPos(3, 2, 1);
		BlockPos genB = new BlockPos(4, 2, 1);
		BlockPos boxPos = new BlockPos(3, 2, 2);
		helper.setBlock(genA, ModContent.GENERATOR.get());
		helper.setBlock(cableA, ModContent.COPPER_CABLE.get());
		helper.setBlock(cableB, ModContent.COPPER_CABLE.get());
		helper.setBlock(genB, ModContent.GENERATOR.get());
		helper.setBlock(boxPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		for (BlockPos g : new BlockPos[]{genA, genB}) {
			if (be(helper, g) instanceof GeneratorBlockEntity gen) {
				gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
				gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
			}
		}
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}
		int ticks = 20;
		for (int i = 0; i < ticks; i++) {
			for (BlockPos g : new BlockPos[]{genA, genB}) {
				tick(helper, be(helper, g));
			}
			tick(helper, be(helper, cableA));
			tick(helper, be(helper, cableB));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, boxPos));
		}
		long got = be(helper, boxPos) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		long singleCeiling = Config.fuelEuPerTick * (long) ticks;
		if (got <= singleCeiling) {
			helper.fail("battery_box should receive summed supply from two generators, got only " + got
					+ " (a single generator alone could not exceed " + singleCeiling + " over " + ticks + ")");
			return;
		}
		helper.succeed();
	}

	// ── teleporter station (MOD-091): loader-neutral seams the NeoForge world lane must guard ──────

	private static final BlockPos STATION = new BlockPos(1, 2, 1);

	/**
	 * The station accepts EU on its five working faces and stays inert on its FACING front, on this
	 * loader too. This is the exact defect class the NeoForge energy adapter has produced before
	 * (every face reporting both insert and extract regardless of its real role), so the Fabric-side
	 * {@code TC-TELE-001-NRG03} is not enough on its own — the adapter is per-loader code.
	 */
	public static void teleporterFaceRoles(GameTestHelper helper) {
		helper.setBlock(STATION, ModContent.TELEPORTER.get());
		if (!(be(helper, STATION) instanceof TeleporterBlockEntity station)) {
			helper.fail("teleporter block entity missing");
			return;
		}
		Direction facing = station.getBlockState().getValue(HorizontalMachineBlock.FACING);
		if (station.energyPort(facing) != null) {
			helper.fail("teleporter FACING front must expose no energy port on this loader");
			return;
		}
		for (Direction dir : Direction.values()) {
			if (dir == facing) {
				continue;
			}
			if (station.energyRoleForFace(dir) != EnergyRole.IN) {
				helper.fail("teleporter face " + dir + " must accept EU (IN), got "
						+ station.energyRoleForFace(dir));
				return;
			}
		}
		if (station.getEnergyStorage().supportsExtraction()) {
			helper.fail("teleporter must never emit EU — the network could drain the jump fund");
			return;
		}
		helper.succeed();
	}

	/**
	 * The station's privacy flag rides its dropped item through the {@code teleporter_private} data
	 * component — the MOD-022 frozen-registry seam, which is exactly what breaks per-loader when a
	 * component is not registered on one of them (see the battery-box STORED_ENERGY case above).
	 */
	public static void teleporterDropCarriesPrivacy(GameTestHelper helper) {
		helper.setBlock(STATION, ModContent.TELEPORTER.get());
		if (!(be(helper, STATION) instanceof TeleporterBlockEntity station)) {
			helper.fail("teleporter block entity missing");
			return;
		}
		station.setPrivate(false);
		station.getEnergyStorage().amount = 4242L;
		DataComponentMap map = station.collectComponents();
		if (!Boolean.FALSE.equals(map.get(ModDataComponents.TELEPORTER_PRIVATE.get()))) {
			helper.fail("teleporter did not carry TELEPORTER_PRIVATE on drop: "
					+ map.get(ModDataComponents.TELEPORTER_PRIVATE.get())
					+ " (data component unregistered on this loader?)");
			return;
		}
		Long eu = map.get(ModDataComponents.STORED_ENERGY.get());
		if (eu == null || eu.longValue() != 4242L) {
			helper.fail("teleporter did not carry STORED_ENERGY on drop: " + eu);
			return;
		}
		helper.succeed();
	}
}
