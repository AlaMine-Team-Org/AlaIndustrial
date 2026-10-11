package dev.alaindustrial.command.demo.energy;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The energy stand's one way to write a cell, and the record of what each bay measures (MOD-799).
 *
 * <p>Same rule as the demo stand's writer (ADR-028, one cell one owner): every block goes through
 * {@link #place}, which records the cell in arena coordinates first, so a second write on it — by this bay or
 * a neighbour — is reported instead of swallowed, and so is a write outside the bay's own rectangle. A writer
 * in {@linkplain #dry dry} mode records the same plan without touching the world: {@code check} re-derives the
 * probes of a stand built in an earlier session that way, from the same code that built it.
 */
final class BayWriter {
	private final ServerLevel level;
	private final BlockPos arenaOrigin;
	private final boolean dry;
	private final Map<BlockPos, String> written;
	private final List<String> problems;

	private Bay bay;
	private BayPlan.Builder plan;

	BayWriter(ServerLevel level, BlockPos arenaOrigin, boolean dry, Map<BlockPos, String> written,
			List<String> problems) {
		this.level = level;
		this.arenaOrigin = arenaOrigin;
		this.dry = dry;
		this.written = written;
		this.problems = problems;
	}

	/** Start writing {@code next}: local coordinates are now relative to its corner. */
	void begin(Bay next) {
		bay = next;
		plan = new BayPlan.Builder(next);
	}

	/** Finish the current bay and hand back what it measures. */
	BayPlan end() {
		BayPlan done = plan.build();
		bay = null;
		plan = null;
		return done;
	}

	ServerLevel level() {
		return level;
	}

	boolean dry() {
		return dry;
	}

	/** The world position of bay-local {@code (x, y, z)}. */
	BlockPos at(int x, int y, int z) {
		return arenaOrigin.offset(bay.x0() + x, y, bay.z0() + z);
	}

	/** Write one cell, recording it in the ledger first. */
	void place(BlockPos pos, BlockState state) {
		BlockPos local = pos.subtract(arenaOrigin);
		String owner = bay.id() + ":" + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		String previous = written.put(local, owner);
		String cell = "cell (" + local.getX() + ", " + local.getY() + ", " + local.getZ() + ")";
		if (previous != null) {
			problems.add(cell + " written twice — " + previous + " overwritten by " + owner);
		}
		if (local.getX() < bay.x0() || local.getX() >= bay.x0() + bay.width()
				|| local.getZ() < bay.z0() || local.getZ() >= bay.z0() + bay.depth()) {
			problems.add(cell + " of bay " + bay.id() + " lies outside its rectangle");
		}
		if (!dry) {
			level.setBlockAndUpdate(pos, state);
		}
	}

	/** Claim a cell that a structure assembler fills by itself, so no other bay writes there. */
	void claim(BlockPos pos, String what) {
		BlockPos local = pos.subtract(arenaOrigin);
		String previous = written.put(local, bay.id() + ":" + what);
		if (previous != null) {
			problems.add("cell " + local.toShortString() + " written twice — " + previous + " and " + what);
		}
	}

	void set(int x, int y, int z, Block block) {
		place(at(x, y, z), block.defaultBlockState());
	}

	/** A copper cable at bay-local {@code (x, 1, z)}. */
	void cable(int x, int z) {
		cable(x, z, ModContent.COPPER_CABLE.get());
	}

	void cable(int x, int z, Block grade) {
		cableAt(at(x, 1, z), grade);
	}

	void cableAt(BlockPos pos, Block grade) {
		place(pos, grade.defaultBlockState());
		plan.cable(pos);
	}

	/** A straight run of copper from {@code (x1, z1)} to {@code (x2, z2)}, both ends included. */
	void run(int x1, int z1, int x2, int z2) {
		run(x1, z1, x2, z2, ModContent.COPPER_CABLE.get());
	}

	void run(int x1, int z1, int x2, int z2, Block grade) {
		int dx = Integer.signum(x2 - x1);
		int dz = Integer.signum(z2 - z1);
		for (int x = x1, z = z1; ; x += dx, z += dz) {
			cable(x, z, grade);
			if (x == x2 && z == z2) {
				return;
			}
		}
	}

	/**
	 * A snake of {@code length} cables starting at {@code (x, z)}, rows of {@code row} cables along +X, one free
	 * row between rows so neighbouring rows do not touch, joined by one cable at alternating ends. Returns the
	 * bay-local cell just past the last cable, along the direction the last row ran.
	 */
	int[] snake(int x, int z, int length, int row, Block grade) {
		int cx = x;
		int cz = z;
		int dir = 1;
		int col = 0;
		boolean connector = false;
		for (int laid = 0; laid < length; laid++) {
			cable(cx, cz, grade);
			if (laid == length - 1) {
				break;
			}
			if (connector) {
				cz++;
				dir = -dir;
				col = 0;
				connector = false;
			} else if (col < row - 1) {
				cx += dir;
				col++;
			} else {
				cz++;
				connector = true;
			}
		}
		return connector ? new int[] {cx, cz + 1} : new int[] {cx + dir, cz};
	}

	/** A creative energy source at {@code (x, 1, z)} whose rate is decided at check time from the bay's machines. */
	void source(String name, int x, int z, ToIntFunction<Map<String, Long>> rate) {
		BlockPos pos = at(x, 1, z);
		set(x, 1, z, ModContent.CREATIVE_ENERGY_SOURCE.get());
		plan.probe(BayPlan.Probe.source(name, pos, rate));
	}

	void source(String name, int x, int z, int rate) {
		source(name, x, z, needs -> rate);
	}

	void sourceAt(String name, BlockPos pos, int rate) {
		place(pos, ModContent.CREATIVE_ENERGY_SOURCE.get().defaultBlockState());
		plan.probe(BayPlan.Probe.source(name, pos, needs -> rate));
	}

	/** A Macerator with ore, its front (inert face) turned to {@code facing}. */
	void machine(String name, int x, int z, Direction facing) {
		machine(name, x, z, facing, 0);
	}

	/** A Macerator with {@code chips} Overclocker Chips I — a heavier draw (two chips double it twice). */
	void machine(String name, int x, int z, Direction facing, int chips) {
		machineAt(name, at(x, 1, z), facing, chips);
	}

	void machineAt(String name, BlockPos pos, Direction facing, int chips) {
		place(pos, facing(ModContent.MACERATOR.get(), facing));
		plan.probe(BayPlan.Probe.machine(name, pos, chips));
	}

	/** A Battery Box: its IN face is {@code facing} (toward the cable that charges it), its OUT face opposite. */
	void store(String name, int x, int z, Direction facing, long charge) {
		BlockPos pos = at(x, 1, z);
		place(pos, facing(ModContent.BATTERY_BOX.get(), facing));
		plan.probe(BayPlan.Probe.store(name, pos, charge));
	}

	/** A consumer with an unknown need (pump, charge pad, teleporter). */
	void unknown(String name, int x, int z, Block block, Direction facing) {
		BlockPos pos = at(x, 1, z);
		place(pos, facing(block, facing));
		plan.probe(BayPlan.Probe.of(name, ProbeKind.UNKNOWN, pos));
	}

	/** A real generator already placed (by {@link #set} or an assembler) at {@code pos}. */
	void generator(String name, BlockPos pos) {
		plan.probe(BayPlan.Probe.of(name, ProbeKind.GENERATOR, pos));
	}

	/** Leave {@code (x, 1, z)} empty and name it: the flag {@code flag} reads "a cable now fills it". */
	void gap(String flag, int x, int z) {
		BlockPos pos = at(x, 1, z);
		claim(pos, "gap");
		plan.gap(flag, pos);
	}

	private static BlockState facing(Block block, Direction facing) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(HorizontalMachineBlock.FACING)
				? state.setValue(HorizontalMachineBlock.FACING, facing) : state;
	}
}
