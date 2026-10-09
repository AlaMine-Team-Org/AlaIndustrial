package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.compat.LineBlockFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block properties the version seam of {@code compat.LineBlockProps} decides, held by ONE reference on both
 * Minecraft lines (MOD-703, batch 0).
 *
 * <p><b>Why a second reference next to the registry snapshot.</b> {@code RegistrySnapshot} records the push
 * reaction of every block too, but as the line's own enum value ({@code POPPED} here, {@code DESTROY} on 26.2),
 * so each line captures its own snapshot and nothing compares the two. This scenario asks the line through
 * {@link LineBlockFacts#pushIntent}, which turns the value into a word both lines share; a twin of
 * {@code LineBlockProps} that pops where the other line pins is then red on one of the lines. It adds what the
 * snapshot does not record at all: whether the block's states tick randomly.
 *
 * <p><b>One line per block that is not plain</b> — a block a piston moves normally and none of whose states
 * ticks randomly is left out, so the reference names only the blocks that carry a decision. Which blocks exist
 * is the registry snapshot's job. {@code ticks} is {@code all} when every state of the block ticks randomly,
 * {@code some} when only some do (a crop that stops at its last stage).
 *
 * <p>Washing away by flowing fluid, the third property of the seam, is held by
 * {@code FluidWashScenarios} (both the world behaviour and the production list) and by its oracle against the
 * line's own mechanism.
 *
 * <p>Changed only on purpose, by editing {@link #EXPECTED} together with the change that alters a block's
 * push reaction or random ticking; the failure prints the full actual list.
 */
public final class BlockPropsCharacterizationScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BlockPropsCharacterizationScenarios::blockPropsMatchTheReference,
						"mod703_block_props_match_the_line_neutral_reference"));

		private Roster() {}
	}

	/** Every mod block that is not plain: {@code <id> push=<intent> ticks=<none|some|all>}, sorted by id. */
	static final List<String> EXPECTED = List.of(
			"biofuel push=pops ticks=none",
			"crystal_farm_door push=pops ticks=none",
			"crystal_farm_floor push=pinned ticks=none",
			"crystal_farm_glass push=pinned ticks=none",
			"crystal_seedbed push=pinned ticks=none",
			"diesel push=pops ticks=none",
			"enriched_uranium_torch push=pops ticks=none",
			"enriched_uranium_wall_torch push=pops ticks=none",
			"fuel_oil push=pops ticks=none",
			"incubator_dome push=pinned ticks=none",
			"irradiated_soil push=normal ticks=all",
			"kok_sagyz push=pops ticks=all",
			"mob_wheel_cell push=pinned ticks=none",
			"mob_wheel_controller push=pinned ticks=none",
			"mob_wheel_frame push=pinned ticks=none",
			"mob_wheel_gate push=pinned ticks=none",
			"mob_wheel_rotor push=pinned ticks=none",
			"nutrient_solution push=pops ticks=none",
			"oil push=pops ticks=none",
			"oil_fire push=pops ticks=none",
			"piezo_plate push=pops ticks=none",
			"reactor_button push=pops ticks=none",
			"reactor_door push=pops ticks=none",
			"reactor_lever push=pops ticks=none",
			"silent_piezo_plate push=pops ticks=none",
			"soot_layer push=pops ticks=none",
			"teleporter_capsule push=pinned ticks=none",
			"tempered_iron_ladder push=pops ticks=none",
			"trellis push=pops ticks=some",
			"workstation push=pinned ticks=none");

	private BlockPropsCharacterizationScenarios() {}

	/**
	 * @implements MOD-703-BP01 — the push reaction (as a line-neutral word) and the random ticking of every
	 *     mod block that is not plain match one reference on both lines and both loaders.
	 */
	public static void blockPropsMatchTheReference(GameTestHelper helper) {
		List<String> actual = actualLines();
		if (actual.equals(EXPECTED)) {
			helper.succeed();
			return;
		}
		TreeSet<String> missing = new TreeSet<>(EXPECTED);
		missing.removeAll(actual);
		TreeSet<String> unexpected = new TreeSet<>(actual);
		unexpected.removeAll(EXPECTED);
		helper.fail("MOD-703: block properties differ from the reference — expected but absent " + missing
				+ ", present but not expected " + unexpected + "; full actual list: " + actual);
	}

	/** The non-plain blocks' lines, sorted by id (the registry's mod namespace, not the manifest order). */
	static List<String> actualLines() {
		TreeSet<String> ids = new TreeSet<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (id.getNamespace().equals(Industrialization.MOD_ID)) {
				ids.add(id.getPath());
			}
		}
		List<String> lines = new ArrayList<>();
		for (String path : ids) {
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(path));
			String push = LineBlockFacts.pushIntent(block.defaultBlockState());
			String ticks = randomTicking(block);
			if (!push.equals("normal") || !ticks.equals("none")) {
				lines.add(path + " push=" + push + " ticks=" + ticks);
			}
		}
		return lines;
	}

	/** {@code none}, {@code some} or {@code all} of the block's possible states tick randomly. */
	private static String randomTicking(Block block) {
		int ticking = 0;
		List<BlockState> states = block.getStateDefinition().getPossibleStates();
		for (BlockState state : states) {
			if (state.isRandomlyTicking()) {
				ticking++;
			}
		}
		return ticking == 0 ? "none" : ticking == states.size() ? "all" : "some";
	}
}
