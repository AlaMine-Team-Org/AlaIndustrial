package dev.alaindustrial.command.demo.energy;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.SignTexts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The energy test stand behind {@code /ala demo energy} (MOD-799): an arena of small, labelled networks — the
 * bays — on which the owner checks the energy rule of ADR-049 by hand, and {@code check} measures it.
 *
 * <p>The arena is its own place, north of the showcase stand ({@link #ARENA_Z}), {@link #WIDTH}×{@link #DEPTH}
 * blocks; it never writes into the showcase. Thirty bays sit in a grid of slots in its west part (five columns
 * of eleven blocks, six rows of eight), the mega network and three long lines in the strip {@code x 56..87}.
 * Every block is written through a {@link BayWriter} that keeps the demo stand's rule — one cell, one owner
 * (ADR-028) — so a bay that runs into its neighbour fails the build check instead of quietly sharing a cable.
 */
public final class EnergyStand {
	/** Arena footprint (x). */
	public static final int WIDTH = 88;
	/** Arena footprint (z). */
	public static final int DEPTH = 50;
	/** How high above the floor the arena is cleared of terrain. */
	public static final int CLEAR_HEIGHT = 16;
	/** Z of the arena's north edge in the world: well clear of the showcase stand, which starts at z = 0. */
	public static final int ARENA_Z = -70;

	private static final int SLOT_W = 11;
	private static final int SLOT_D = 8;
	private static final int COLUMNS = 5;
	private static final Block FLOOR = Blocks.SMOOTH_STONE;
	private static final int SIGN_LINE = 15;

	private static volatile List<String> lastProblems = List.of();

	/**
	 * Bays that FAIL for a reason found by the stand itself, not by a fault of the bay: each is a finding for the
	 * energy core, open until the core changes. The gametest holds these bays to FAIL and every other bay to PASS,
	 * so a core fix turns it red and the entry here is removed in the same change. Empty: the four first findings
	 * (E26, E27, E31, E34) were fixed by the MOD-789 core and are held by {@code FlowStandFindingsTest}.
	 */
	private static final Map<String, String> KNOWN_FINDINGS = Map.of();

	private EnergyStand() {
	}

	/** Every bay, in id order. */
	static List<Bay> bays() {
		List<Bay> all = new ArrayList<>(PriorityBays.all());
		all.addAll(NetworkBays.all());
		all.addAll(LongLineBays.all());
		return all;
	}

	/** The known finding of bay {@code id} (see {@link #KNOWN_FINDINGS}), or {@code null}. */
	public static String knownFinding(String id) {
		return KNOWN_FINDINGS.get(id);
	}

	/** The ids of every bay, for the command's suggestions. */
	public static List<String> bayIds() {
		return bays().stream().map(Bay::id).toList();
	}

	/** Bay {@code index} of the west grid (0..29, row-major): a 10×7 rectangle with its sign at (0, 6). */
	static Bay slot(int index, String id, String title, Consumer<BayWriter> build, Expectation... expect) {
		int col = index % COLUMNS;
		int row = index / COLUMNS;
		return new Bay(id, title, col * SLOT_W, row * SLOT_D, SLOT_W - 1, SLOT_D - 1, build, List.of(expect));
	}

	/**
	 * The arena origin: the floor of the column at {@code (0, ARENA_Z)}. The datum column carries nothing above
	 * the floor, so a rebuild lands on the same level.
	 */
	public static BlockPos findOrigin(ServerLevel level) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(0, 0, ARENA_Z);
		for (int y = level.getMaxY(); y > level.getMinY(); y--) {
			p.setY(y);
			if (!level.getBlockState(p).isAir()) {
				return new BlockPos(0, y, ARENA_Z);
			}
		}
		return new BlockPos(0, level.getMinY(), ARENA_Z);
	}

	/** Clear the arena, lay its floor and build every bay with its sign. Returns the number of bays. */
	public static int build(ServerLevel level, BlockPos origin) {
		clear(level, origin);
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				level.setBlock(origin.offset(x, 0, z), FLOOR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		return write(level, origin, false).size();
	}

	/** The plans of a stand built at {@code origin}, re-derived without touching the world. */
	static List<BayPlan> plans(ServerLevel level, BlockPos origin) {
		return write(level, origin, true);
	}

	private static List<BayPlan> write(ServerLevel level, BlockPos origin, boolean dry) {
		Map<BlockPos, String> ledger = new HashMap<>();
		List<String> problems = new ArrayList<>();
		BayWriter writer = new BayWriter(level, origin, dry, ledger, problems);
		List<BayPlan> plans = new ArrayList<>();
		for (Bay bay : bays()) {
			writer.begin(bay);
			bay.build().accept(writer);
			sign(writer, bay);
			plans.add(writer.end());
		}
		if (!dry) {
			lastProblems = List.copyOf(problems);
			for (String problem : problems) {
				Industrialization.LOGGER.warn("energy stand: {}", problem);
			}
		}
		return plans;
	}

	/** What the last real build reported: cells written twice or outside their bay. Empty when healthy. */
	public static List<String> lastBuildProblems() {
		return lastProblems;
	}

	/** Air out everything above the floor up to {@link #CLEAR_HEIGHT}. */
	public static void clear(ServerLevel level, BlockPos origin) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				for (int y = 1; y <= CLEAR_HEIGHT; y++) {
					BlockPos pos = origin.offset(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, air, Block.UPDATE_ALL);
					}
				}
			}
		}
	}

	/** A standing sign in the bay's south-west corner with the id and the title, wrapped to the sign's width. */
	private static void sign(BayWriter w, Bay bay) {
		BlockPos pos = w.at(0, 1, bay.depth() - 1);
		w.place(pos, Blocks.OAK_SIGN.defaultBlockState());
		if (w.dry() || !(w.level().getBlockEntity(pos) instanceof SignBlockEntity sign)) {
			return;
		}
		List<Component> text = new ArrayList<>();
		text.add(Component.literal(bay.id()));
		for (String line : wrap(bay.title())) {
			text.add(Component.literal(line));
		}
		SignTexts.setFront(sign, text);
		sign.setWaxed(true);
		sign.setChanged();
		w.level().sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
	}

	/** Split {@code title} into lines of at most {@link #SIGN_LINE} characters, at spaces. */
	static List<String> wrap(String title) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : title.split(" ")) {
			if (!line.isEmpty() && line.length() + 1 + word.length() > SIGN_LINE) {
				lines.add(line.toString());
				line.setLength(0);
			}
			line.append(line.isEmpty() ? "" : " ").append(word);
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** The world position of bay-local {@code (x, y, z)} of bay {@code bayId} in the arena at {@code origin}. */
	public static BlockPos cell(BlockPos origin, String bayId, int x, int y, int z) {
		for (Bay bay : bays()) {
			if (bay.id().equals(bayId)) {
				return origin.offset(bay.x0() + x, y, bay.z0() + z);
			}
		}
		throw new IllegalArgumentException("no bay " + bayId);
	}

	/** Where {@code /ala demo energy tp <bay>} puts the player: above the bay's sign, looking north over it. */
	public static double[] camera(BlockPos origin, String bayId) {
		for (Bay bay : bays()) {
			if (bay.id().equalsIgnoreCase(bayId)) {
				return new double[] {origin.getX() + bay.x0() + bay.width() / 2.0, origin.getY() + 6.0,
						origin.getZ() + bay.z0() + bay.depth() + 3.0};
			}
		}
		return null;
	}
}
