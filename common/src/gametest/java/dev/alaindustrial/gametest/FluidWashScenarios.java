package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.compat.LineBlockFacts;
import dev.alaindustrial.registry.ModBlockProperties;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * MOD-661: flowing fluid washes away the mod's transport lines — every cable, pipe and the monitoring
 * wire — on both lines of Minecraft, whatever shape the block has taken.
 *
 * <p>The two lines decide this differently. 26.3 asks the {@code #minecraft:washed_away_by_fluids} tag,
 * so the blocks are listed there. 26.2 washes away whatever does not block motion, and "blocks motion"
 * there is read from the collision box: a straight pipe is thin and washed, but a pipe with arms up and
 * down is a full block tall and counted as solid, so the same pipe survived or not depending on its
 * neighbours. That line forces the blocks non-solid instead. The column below is that case: its middle
 * pipe is a full block tall, and it must go like the rest.
 */
public final class FluidWashScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidWashScenarios::waterWashesAwayEveryTransportLine,
								"mod661_water_washes_away_every_transport_line")
						.fabricId("FluidWashGameTest", "mod661WaterWashesAwayEveryTransportLine").ticks(200),
				RosterEntry.of(FluidWashScenarios::washedAwayListMatchesTheLineMechanism,
						"mod703_washed_away_list_matches_the_line_mechanism"));

		private Roster() {}
	}

	/**
	 * The transport lines flowing fluid washes away, kept HERE by hand — the test's own oracle, independent
	 * of {@link ModBlockProperties#WASHED_AWAY_BY_FLUIDS}, the production list that marks the blocks. Every id
	 * below is washed in the world; a block dropped from the production list (or an emptied list) is then
	 * caught twice — it stays standing in the water, and {@link #productionListMatches} names it. A new
	 * washed-away block has to be added here on purpose (MOD-726: {@code fluid_pipe_advanced} was missing
	 * from the 26.3 tag).
	 */
	static final List<String> EXPECTED_LINE_IDS = List.of(
			"copper_cable", "tin_cable", "gold_cable", "electrum_cable",
			"insulated_copper_cable", "insulated_tin_cable", "insulated_gold_cable", "insulated_electrum_cable",
			"item_pipe", "item_pipe_advanced", "fluid_pipe", "fluid_pipe_advanced", "reinforced_fluid_pipe",
			"steam_pipe", "reinforced_steam_pipe", "smart_wire");

	/** The blocks of {@link #EXPECTED_LINE_IDS}, resolved from the registry. */
	private static List<Block> lines() {
		List<Block> lines = new ArrayList<>();
		for (String id : EXPECTED_LINE_IDS) {
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(id));
			if (block == Blocks.AIR) {
				throw new IllegalStateException("FluidWashScenarios expects '" + id + "', not a registered block");
			}
			lines.add(block);
		}
		return lines;
	}

	/**
	 * Fails the test unless the production list names exactly the test's own ids, as sets: a block missing
	 * from production would not be marked washable, a surplus one is not covered here (MOD-726).
	 */
	private static boolean productionListMatches(GameTestHelper helper) {
		Set<String> expected = new TreeSet<>(EXPECTED_LINE_IDS);
		Set<String> production = new TreeSet<>(ModBlockProperties.WASHED_AWAY_BY_FLUIDS);
		if (expected.equals(production) && ModBlockProperties.WASHED_AWAY_BY_FLUIDS.size() == production.size()) {
			return true;
		}
		Set<String> missing = new TreeSet<>(expected);
		missing.removeAll(production);
		Set<String> surplus = new TreeSet<>(production);
		surplus.removeAll(expected);
		helper.fail("MOD-726: ModBlockProperties.WASHED_AWAY_BY_FLUIDS differs from the test's own list — missing "
				+ missing + ", not expected " + surplus + " (" + ModBlockProperties.WASHED_AWAY_BY_FLUIDS.size()
				+ " entries, " + EXPECTED_LINE_IDS.size() + " expected)");
		return false;
	}

	/**
	 * The oracle between {@link ModBlockProperties#WASHED_AWAY_BY_FLUIDS} and the mechanism the line really washes
	 * by (MOD-703, batch 5): every mod block is asked through {@link LineBlockFacts#washedAwayByLine} — 26.3 the
	 * {@code #minecraft:washed_away_by_fluids} tag, 26.2 the {@code forceSolidOff} flag
	 * {@code compat.LineBlockProps.washedAwayByFluids} sets — and the set it answers must be exactly the list.
	 *
	 * <p>The world scenario above proves the listed blocks are washed away; it cannot see the reverse, a mod
	 * block the line washes away although nothing lists it (a stray id in the 26.3 tag), nor a listed id the
	 * tag lost while the list kept it — that one only shows as a block left standing, on one line. Here both
	 * are named, on both lines, without water. The one mod block a VANILLA rule washes away,
	 * {@link #WASHED_BY_A_VANILLA_RULE}, is tolerated and never required.
	 *
	 * @implements MOD-703-WO01 — the mod blocks this line's mechanism washes away are exactly
	 *     {@code ModBlockProperties.WASHED_AWAY_BY_FLUIDS}, each of which is a registered block
	 */
	public static void washedAwayListMatchesTheLineMechanism(GameTestHelper helper) {
		Set<String> listed = new TreeSet<>(ModBlockProperties.WASHED_AWAY_BY_FLUIDS);
		Set<String> tolerated = new TreeSet<>(WASHED_BY_A_VANILLA_RULE);
		Set<String> byLine = new TreeSet<>();
		int seen = 0;
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!id.getNamespace().equals(Industrialization.MOD_ID)) {
				continue;
			}
			seen++;
			if (LineBlockFacts.washedAwayByLine(BuiltInRegistries.BLOCK.getValue(id))) {
				byLine.add(id.getPath());
			}
		}
		Set<String> unregistered = new TreeSet<>();
		for (String id : listed) {
			if (BuiltInRegistries.BLOCK.getValue(Industrialization.id(id)) == Blocks.AIR) {
				unregistered.add(id);
			}
		}
		if (seen == 0 || byLine.isEmpty()) {
			helper.fail("MOD-703 precondition: the line's mechanism marks none of the " + seen
					+ " mod blocks — the probe reads nothing");
			return;
		}
		Set<String> marked = new TreeSet<>(byLine);
		marked.removeAll(tolerated);
		if (!unregistered.isEmpty() || !marked.equals(listed)) {
			Set<String> notMarked = new TreeSet<>(listed);
			notMarked.removeAll(byLine);
			Set<String> notListed = new TreeSet<>(byLine);
			notListed.removeAll(listed);
			notListed.removeAll(tolerated);
			helper.fail("MOD-703: WASHED_AWAY_BY_FLUIDS and the line's mechanism differ — listed but not marked by "
					+ "the line " + notMarked + ", marked by the line but not listed " + notListed
					+ ", listed but not a registered block " + unregistered);
			return;
		}
		helper.succeed();
	}

	/**
	 * Mod blocks a line washes away through a VANILLA rule rather than through
	 * {@link ModBlockProperties#WASHED_AWAY_BY_FLUIDS}: the oil fire is in {@code #minecraft:fire}
	 * ({@code data/minecraft/tags/block/fire.json}), which 26.3's {@code #minecraft:washed_away_by_fluids}
	 * includes; 26.2 washes it for having no collision, which its {@code forceSolidOff} flag does not record.
	 * Tolerated by the oracle, never required — fire is not a transport line.
	 */
	static final List<String> WASHED_BY_A_VANILLA_RULE = List.of("oil_fire");

	/** Middle of the pipe column: arms up and down make it a full block tall. */
	private static final BlockPos COLUMN_MIDDLE = new BlockPos(6, 3, 7);

	private FluidWashScenarios() {
	}

	/** Whether this block is one of the transport lines flowing fluid washes away (MOD-661). */
	static boolean isTransportLine(Block block) {
		for (Block line : lines()) {
			if (line == block) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Three rows on a stone floor, seven blocks at most per row (flowing water reaches seven cells), each
	 * row fed by a water source at its start; and a column of three item pipes on a stone post, fed at
	 * its middle. Every block must be washed away.
	 *
	 * @implements MOD-661 — every cable, pipe and the monitoring wire is washed away, a full-height pipe too.
	 */
	public static void waterWashesAwayEveryTransportLine(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		if (!productionListMatches(helper)) {
			return;
		}
		List<BlockPos> placed = new ArrayList<>();
		List<Block> blocks = new ArrayList<>();
		int[] rows = {1, 3, 5};
		List<Block> lines = lines();
		if (lines.size() > rows.length * 7) {
			helper.fail("MOD-726 precondition: " + lines.size() + " transport lines do not fit " + rows.length
					+ " rows of seven — add a row");
			return;
		}
		int next = 0;
		for (int row : rows) {
			for (int x = 1; x <= 7 && next < lines.size(); x++, next++) {
				BlockPos pos = new BlockPos(x, 1, row);
				Block block = lines.get(next);
				helper.setBlock(pos, block);
				placed.add(pos);
				blocks.add(block);
			}
		}

		// The column: a stone post keeps the floor water off its bottom pipe, so the middle pipe keeps
		// its arm down until the water that reaches it is its own.
		helper.setBlock(new BlockPos(6, 1, 7), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 1, 7), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 2, 7), Blocks.STONE);
		Block pipe = ModContent.ITEM_PIPE.get();
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(6, y, 7), pipe);
		}
		double height = helper.getBlockState(COLUMN_MIDDLE)
				.getCollisionShape(helper.getLevel(), helper.absolutePos(COLUMN_MIDDLE), CollisionContext.empty())
				.bounds().getYsize();
		if (height < 1.0) {
			helper.fail("MOD-661 precondition: the middle pipe of the column must be a full block tall (arms up and"
					+ " down), is " + height + " — the case this test exists for is not being built");
			return;
		}
		placed.add(COLUMN_MIDDLE);
		blocks.add(pipe);

		for (int row : rows) {
			helper.setBlock(new BlockPos(0, 1, row), Blocks.WATER);
		}
		helper.setBlock(new BlockPos(5, 3, 7), Blocks.WATER);

		helper.succeedWhen(() -> {
			for (int i = 0; i < placed.size(); i++) {
				if (helper.getBlockState(placed.get(i)).is(blocks.get(i))) {
					helper.fail("MOD-661: flowing water left " + blocks.get(i) + " standing at " + placed.get(i));
				}
			}
		});
	}
}
