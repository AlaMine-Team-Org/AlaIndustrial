package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The measurement MOD-275 and MOD-287 both ask for: what a <b>full</b> 108-slot warehouse with
 * several assemblers running off it actually costs the server tick (MOD-287 acceptance criterion
 * "warehouse lookups must not scan every slot linearly per item — measurement attached to the task",
 * MOD-275 "no TPS drop (measurement attached)").
 *
 * <h2>What is measured, and against what</h2>
 *
 * Three phases in the same world, on the same rig, back to back:
 *
 * <ol>
 *   <li><b>none</b> — the tick loop with nothing in it. Not a formality: it is the noise floor, and
 *       without it a number like "40 µs" cannot be told from measurement overhead.</li>
 *   <li><b>idle</b> — four assemblers beside a full four-module warehouse, with no blueprints. This
 *       is the state a real base spends most of its time in, and the one where a naive
 *       implementation would still be re-scanning 108 slots every tick.</li>
 *   <li><b>running</b> — the same four, each with a blueprint, crafting continuously off the same
 *       warehouse.</li>
 * </ol>
 *
 * <h2>Why the rig is laid out the way it is</h2>
 *
 * The warehouse is <b>full and hostile</b>: 100 slots of a filler the recipes never want, and the
 * planks only in slots 100..107. Every ingredient lookup therefore walks about a hundred non-matching
 * stacks before it finds anything — the worst case the "does it scan linearly" criterion is about.
 * Filling slot 0 with planks would have measured the best case and reported a flattering number.
 *
 * <p>The window is sized so nothing changes state mid-measurement: the planks outlast the run (eight
 * full stacks are 256 operations against the ~190 the four machines can finish) and the output areas
 * do not fill (six slots hold 384 sticks against the ~200 produced), so no machine drops into
 * OUTPUT_FULL and quietly stops being measured.
 *
 * <h2>What the assertion is, and what it is not</h2>
 *
 * The gate is a wall-clock ceiling with roughly two orders of magnitude of headroom, not a pinned
 * figure: wall-clock on a developer machine is not reproducible to the microsecond, and a tight
 * threshold would flake. It exists to catch a structural regression — a plan on every tick, a walk
 * that went quadratic — rather than to police a number. The number itself is <b>printed</b>, and
 * {@code tools/bench_assembler.py} re-takes it and formats the table for
 * {@code docs/PERFORMANCE.md}.
 */
public final class AssemblerPerfScenarios {

	private AssemblerPerfScenarios() {
	}

	/** Four storage modules in a row — {@code 4 × 27 = 108} slots, the cluster cap. */
	private static final BlockPos[] MODULES = {
		new BlockPos(2, 2, 1), new BlockPos(2, 2, 2), new BlockPos(2, 2, 3), new BlockPos(2, 2, 4),
	};

	/** Four assemblers, two per side, each face-adjacent to a module of the one cluster. */
	private static final BlockPos[] ASSEMBLERS = {
		new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(1, 2, 3), new BlockPos(3, 2, 3),
	};

	/** Warehouse slots below this hold filler the recipes never match. */
	private static final int FILLER_SLOTS = 100;
	/** Total warehouse slots: four modules of 27 — the cluster cap of 108. */
	private static final int WAREHOUSE_SLOTS = 4 * StorageModuleBlockEntity.CONTAINER_SIZE;

	/** Ticks run before the clock starts, so JIT and the first plan are not in the measurement. */
	private static final int WARMUP_TICKS = 200;
	/** Ticks actually measured per phase. */
	private static final int MEASURED_TICKS = 2000;

	/**
	 * Ceiling for one server tick of the whole rig, in nanoseconds.
	 *
	 * <p>0.25 ms is 0.5 % of the 50 ms tick budget and about ten times what the rig actually costs
	 * (13.6 us on the NeoForge lane, 19.3 us on Fabric) — loose enough not to flake on a busy machine,
	 * tight enough to catch the regression it exists for. Sized against that regression rather than
	 * against the healthy number: re-planning on every tick instead of twice per operation multiplies
	 * the cost by roughly forty, and a 1 ms ceiling would have let it through.
	 */
	private static final long TICK_BUDGET_NS = 250_000L;

	/**
	 * Sticks the four machines must have made during the measured window for the number to mean
	 * anything. {@value #MEASURED_TICKS} ticks hold about 47 operations per machine (40 working ticks
	 * plus the start and finish ticks), so four machines owe roughly 750 sticks; 600 leaves room for
	 * scheduling without letting a stalled rig through.
	 */
	private static final int MIN_STICKS = 600;

	/** The stick recipe's yield — how the stick count is turned back into an operation count. */
	private static final int STICKS_PER_OPERATION = 4;

	/**
	 * Plans per completed operation the machine is allowed. The design is exactly two — one to decide
	 * whether to start, one to finish — and the allowance covers a plan that ran on a tick where the
	 * machine could not start after all. Nothing like the ~42 a per-tick walk would produce.
	 */
	private static final int MAX_PLANS_PER_OPERATION = 3;

	/**
	 * Measure and report the server-tick cost of a full warehouse with four assemblers.
	 *
	 * @implements TC-ASM-024 — a full warehouse with four assemblers costs a fraction of a tick.
	 */
	public static void perf01FullWarehouseTickCost(GameTestHelper helper) {
		// Phase 1: the empty loop, so the other two numbers can be read against something.
		long none = measure(helper, List.of());

		for (BlockPos pos : MODULES) {
			helper.setBlock(pos, ModContent.STORAGE_MODULE.get());
		}
		StorageModuleBlockEntity first = helper.getBlockEntity(MODULES[0], StorageModuleBlockEntity.class);
		fillWarehouse(helper);

		List<AssemblerBlockEntity> machines = new ArrayList<>(ASSEMBLERS.length);
		for (BlockPos pos : ASSEMBLERS) {
			helper.setBlock(pos, ModContent.ASSEMBLER.get());
			AssemblerBlockEntity be = helper.getBlockEntity(pos, AssemblerBlockEntity.class);
			be.getEnergyStorage().amount = Config.assemblerBuffer;
			machines.add(be);
		}
		if (first.isEmpty()) {
			helper.fail("the warehouse did not fill — the rig is not measuring what it claims");
			return;
		}

		// Phase 2: idle. No blueprints, so every machine has nothing to plan and sleeps.
		long idle = measure(helper, machines);

		// Phase 3: running. Same rig, one blueprint each, crafting off the shared warehouse.
		for (AssemblerBlockEntity be : machines) {
			be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
			be.getEnergyStorage().amount = Config.assemblerBuffer;
		}
		int plansBefore = plans(machines);
		long running = measure(helper, machines);
		int plans = plans(machines) - plansBefore;

		int made = 0;
		for (AssemblerBlockEntity be : machines) {
			for (int i = 0; i < AssemblerBlockEntity.OUTPUT_SLOT_COUNT; i++) {
				ItemStack out = be.getItem(AssemblerBlockEntity.OUTPUT_SLOT_START + i);
				if (out.is(Items.STICK)) {
					made += out.getCount();
				}
			}
		}
		if (made < MIN_STICKS) {
			// The guard that matters most, and the one this rig needed: the first version of it charged
			// the buffers once, so the machines ran 25 operations each and then slept for the rest of the
			// window — and the benchmark cheerfully reported a third of the real cost as a pass.
			helper.fail("the running phase produced only " + made + " sticks, expected at least "
					+ MIN_STICKS + " — the machines were stalled for most of the measured window, so this "
					+ "number is not the cost of a working warehouse");
			return;
		}

		int operations = made / STICKS_PER_OPERATION;
		report(none, idle, running, made, plans, operations);

		// The gate that matters: the warehouse is read twice per operation, not on every one of the
		// forty ticks in between. Exact rather than statistical — the plan count is an integer, and a
		// regression to per-tick planning turns 2 into ~42.
		if (plans > MAX_PLANS_PER_OPERATION * operations) {
			helper.fail(String.format(Locale.ROOT,
					"%d operations planned the warehouse %d times (%.1f per operation) — the design is two, "
							+ "once to decide whether to start and once to finish; anything near the %d-tick "
							+ "duration means the warehouse is being walked every tick",
					operations, plans, plans / (double) operations, Config.assemblerDuration));
			return;
		}

		long perTick = running / MEASURED_TICKS;
		if (perTick > TICK_BUDGET_NS) {
			helper.fail(String.format(Locale.ROOT,
					"four assemblers on a full %d-slot warehouse cost %.3f ms per server tick, over the "
							+ "%.3f ms ceiling",
					WAREHOUSE_SLOTS, perTick / 1_000_000.0, TICK_BUDGET_NS / 1_000_000.0));
			return;
		}
		helper.succeed();
	}

	private static int plans(List<AssemblerBlockEntity> machines) {
		int total = 0;
		for (AssemblerBlockEntity be : machines) {
			total += be.plansRun();
		}
		return total;
	}

	/** Warm up, then time {@link #MEASURED_TICKS} ticks of the given machines. Returns nanoseconds. */
	private static long measure(GameTestHelper helper, List<AssemblerBlockEntity> machines) {
		tick(helper, machines, WARMUP_TICKS);
		long start = System.nanoTime();
		tick(helper, machines, MEASURED_TICKS);
		return System.nanoTime() - start;
	}

	/**
	 * Tick the machines, topping the buffer up first.
	 *
	 * <p>The top-up is not decoration. A full buffer is 25 operations, and a benchmark that merely
	 * charges the machines once measures 25 working operations followed by 1900 ticks of a machine
	 * asleep on NO_ENERGY — which is exactly what the first run of this rig did, reporting a third of
	 * the real cost. Standing in for the cable that would be feeding them costs one long write per
	 * machine per tick, several orders below what is being measured.
	 */
	private static void tick(GameTestHelper helper, List<AssemblerBlockEntity> machines, int ticks) {
		for (int i = 0; i < ticks; i++) {
			for (AssemblerBlockEntity be : machines) {
				be.getEnergyStorage().amount = Config.assemblerBuffer;
				BlockPos pos = be.getBlockPos();
				BlockState state = helper.getLevel().getBlockState(pos);
				be.serverTick(helper.getLevel(), pos, state);
			}
		}
	}

	/**
	 * Fill all 108 slots: filler the recipes never match in the first {@value #FILLER_SLOTS}, planks
	 * only at the far end. The point is that every ingredient lookup has to walk past the filler.
	 */
	private static void fillWarehouse(GameTestHelper helper) {
		for (int slot = 0; slot < WAREHOUSE_SLOTS; slot++) {
			StorageModuleBlockEntity module = helper.getBlockEntity(
					MODULES[slot / StorageModuleBlockEntity.CONTAINER_SIZE], StorageModuleBlockEntity.class);
			int local = slot % StorageModuleBlockEntity.CONTAINER_SIZE;
			module.setItem(local, slot < FILLER_SLOTS
					? new ItemStack(Items.COBBLESTONE, 64)
					: new ItemStack(Items.OAK_PLANKS, 64));
		}
	}

	/** Two planks in the left column — four sticks, the cheapest possible recipe to plan. */
	private static ItemStack stickBlueprint() {
		List<ItemStack> cells = new ArrayList<>(Collections.nCopies(BlueprintPattern.GRID_SIZE, ItemStack.EMPTY));
		cells.set(0, new ItemStack(Items.OAK_PLANKS));
		cells.set(3, new ItemStack(Items.OAK_PLANKS));
		return AssemblyBlueprintItem.record(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
				BlueprintPattern.of(cells));
	}

	/**
	 * Print the table. Deliberately on {@code System.out} with a fixed prefix rather than through a
	 * logger: {@code tools/bench_assembler.py} greps for that prefix, and a logger's formatting is not
	 * part of any contract.
	 */
	private static void report(long none, long idle, long running, int made, int plans, int operations) {
		System.out.println("[ala-bench] assembler warehouse benchmark: "
				+ ASSEMBLERS.length + " assemblers, " + WAREHOUSE_SLOTS + " warehouse slots ("
				+ FILLER_SLOTS + " of filler), " + MEASURED_TICKS + " measured ticks per phase");
		line("none", none);
		line("idle", idle);
		line("running", running);
		System.out.println(String.format(Locale.ROOT,
				"[ala-bench] running phase: %d sticks = %d operations, %d warehouse plans (%.2f per operation)",
				made, operations, plans, plans / (double) operations));
	}

	private static void line(String phase, long totalNs) {
		System.out.println(String.format(Locale.ROOT,
				"[ala-bench] %-8s total %8.2f ms | per server tick %8.1f us | %6.3f %% of the 50 ms budget",
				phase, totalNs / 1_000_000.0, totalNs / (double) MEASURED_TICKS / 1000.0,
				totalNs / (double) MEASURED_TICKS / 500_000.0));
	}
}
