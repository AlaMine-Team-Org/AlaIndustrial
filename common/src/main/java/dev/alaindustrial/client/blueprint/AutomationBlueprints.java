package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about automating crafting and machine bookkeeping (MOD-797). Listed in
 * {@link BlueprintCatalog#ALL}.
 */
public final class AutomationBlueprints {

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String TABLE_BLOCK = "alaindustrial:upgrade_table";

	private static final String ASSEMBLY_BLUEPRINT = "alaindustrial:assembly_blueprint";
	private static final String CHIP = "alaindustrial:stats_chip";
	private static final String CORE_BARREL = "alaindustrial:core_barrel";

	private static final String PAPER = "minecraft:paper";
	private static final String LAPIS_DUST = "alaindustrial:lapis_dust";
	private static final String OAK_PLANKS = "minecraft:oak_planks";
	private static final String STICK = "minecraft:stick";

	private static final String COPPER_COIL = "alaindustrial:copper_coil";
	private static final String GLASS_PANE = "minecraft:glass_pane";
	private static final String EMPTY_CHIP = "alaindustrial:empty_chip";
	private static final String REDSTONE = "minecraft:redstone";
	private static final String RAW_IRON = "minecraft:raw_iron";
	private static final String IRON_INGOT = "minecraft:iron_ingot";

	private static final String DRILL_COLUMN_MODULE = "alaindustrial:drill_column_module";
	private static final String ELECTRIC_DRILL = "alaindustrial:electric_drill";
	private static final String INVAR_PLATE = "alaindustrial:invar_reinforced_plate";
	private static final String CHOKE = "alaindustrial:choke";
	private static final String CHOKE_REINFORCED = "alaindustrial:choke_reinforced";
	private static final String IRRADIATED_DIAMOND = "alaindustrial:irradiated_diamond";
	private static final String MODULE_BLANK = "alaindustrial:module_blank";
	private static final String ADVANCED_CIRCUIT = "alaindustrial:advanced_circuit";
	private static final String COBBLESTONE = "minecraft:cobblestone";

	/**
	 * Eight paper and lapis dust giving four blank sheets at a crafting table; the assembler recording a
	 * stick recipe onto a blank and stamping it from its storage module; the same machine with the power
	 * line gone, which stands still; the line back with the module behind the machine; the recorded sheet
	 * taken out whole.
	 * Indices: 0 assembler, 1 storage module, 2 cable, 3 generator.
	 */
	public static final BlueprintDef ASSEMBLY_SHEETS = new BlueprintDef("assembly_sheets",
			List.of(ASSEMBLY_BLUEPRINT),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE, motions(0,
							"+" + PAPER, "+" + PAPER, "+" + PAPER, "+" + PAPER,
							"+" + PAPER, "+" + PAPER, "+" + PAPER, "+" + PAPER, "+" + LAPIS_DUST,
							"-" + ASSEMBLY_BLUEPRINT, "-" + ASSEMBLY_BLUEPRINT, "-" + ASSEMBLY_BLUEPRINT,
							"-" + ASSEMBLY_BLUEPRINT)),
					new Step(assembler(0), 0, 2, List.of(Flow.energy(3, 0)), Verdict.NONE, motions(0,
							"+" + ASSEMBLY_BLUEPRINT, "+" + OAK_PLANKS, "+" + OAK_PLANKS,
							"-" + STICK, "-" + STICK, "-" + STICK, "-" + STICK)),
					new Step(List.of(new Placement(ASSEMBLER, 0, 0, 0, Map.of("facing", "south")),
							new Placement(STORAGE_MODULE, -1, 0, 0, Map.of())), 0, 2, List.of(), Verdict.BAD,
							motions(0, "+" + OAK_PLANKS)),
					new Step(assemblerBehind(), 0, 2,
							List.of(Flow.energy(3, 0), Flow.item(1, 0, OAK_PLANKS)), Verdict.GOOD,
							motions(0, "-" + STICK)),
					new Step(assemblerBehind(), 0, 2, List.of(), Verdict.GOOD,
							motions(0, "-" + ASSEMBLY_BLUEPRINT))));

	/**
	 * The statistics chip at a crafting table; installed in an electric furnace with no cable, which stays
	 * dark with no work to count (1, position 2, BAD); the same furnace on a live line, counting at once
	 * (3, position 2); the panel's counters growing one state later. Line indices: 0 table, 1 generator,
	 * 2 cable, 3 furnace.
	 */
	public static final BlueprintDef STATS_CHIP = new BlueprintDef("stats_chip",
			List.of(CHIP),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE, motions(0,
							"+" + COPPER_COIL, "+" + GLASS_PANE, "+" + GLASS_PANE,
							"+" + EMPTY_CHIP, "+" + REDSTONE, "-" + CHIP)),
					new Step(List.of(table(), machine(ELECTRIC_FURNACE, 4, 0, false, false)), 1, 2, List.of(),
							Verdict.BAD, motions(1, "+" + CHIP, "+" + RAW_IRON)),
					new Step(join(List.of(table()), furnaceLine(false, true)), 3, 2,
							List.of(Flow.energy(1, 3)), Verdict.GOOD,
							motions(3, "+" + CHIP, "+" + RAW_IRON, "-" + IRON_INGOT)),
					new Step(join(List.of(table()), furnaceLine(true, false)), 3, 2,
							List.of(Flow.energy(1, 3)), Verdict.GOOD,
							motions(3, "+" + RAW_IRON, "-" + IRON_INGOT))));

	/**
	 * The core barrel and the column bore module at a crafting table (position 1); the upgrade table with
	 * no cable, dark, the module and the drill waiting inside (1, position 2, BAD); the powered table
	 * taking the module and handing the drill back upgraded; the drill taking a three-high stone column
	 * in one pass (position 3).
	 * Indices: 0 lower half, 1 upper half, 2 cable, 3 generator, 4..6 the stone column.
	 */
	public static final BlueprintDef COLUMN_DRILL = new BlueprintDef("column_drill",
			List.of(CORE_BARREL),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE, motions(0,
							"+" + INVAR_PLATE, "+" + INVAR_PLATE, "+" + INVAR_PLATE,
							"+" + INVAR_PLATE, "+" + INVAR_PLATE, "+" + INVAR_PLATE,
							"+" + CHOKE, "+" + CHOKE, "-" + CORE_BARREL)),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE, motions(0,
							"+" + CORE_BARREL, "+" + CORE_BARREL, "+" + IRRADIATED_DIAMOND,
							"+" + CHOKE_REINFORCED, "+" + CHOKE_REINFORCED, "+" + MODULE_BLANK,
							"+" + ADVANCED_CIRCUIT, "+" + INVAR_PLATE, "+" + INVAR_PLATE,
							"-" + DRILL_COLUMN_MODULE)),
					new Step(upgradeRig(false).subList(0, 2), 1, 2, List.of(), Verdict.BAD,
							motions(1, "+" + DRILL_COLUMN_MODULE, "+" + ELECTRIC_DRILL)),
					new Step(upgradeRig(true), 1, 2, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							motions(1, "+" + DRILL_COLUMN_MODULE, "-" + ELECTRIC_DRILL)),
					new Step(join(upgradeRig(false), stoneColumn()), 5, 3, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(ELECTRIC_DRILL, 5, true),
									new ItemMotion(COBBLESTONE, 4, false),
									new ItemMotion(COBBLESTONE, 5, false),
									new ItemMotion(COBBLESTONE, 6, false)))));

	private AutomationBlueprints() {
	}

	/** The crafting table at the origin, where this group's recipes are laid out. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	/** Item motions on block {@code index}: each entry is "+id" for an input or "-id" for an output. */
	private static List<ItemMotion> motions(int index, String... entries) {
		List<ItemMotion> all = new ArrayList<>();
		for (String entry : entries) {
			all.add(new ItemMotion(entry.substring(1), index, entry.charAt(0) == '+'));
		}
		return all;
	}

	/** The assembler with its storage module behind it, fed from the east: 0 machine, 1 module, 2..3 line. */
	private static List<Placement> assemblerBehind() {
		return List.of(new Placement(ASSEMBLER, 0, 0, 0, Map.of("facing", "south")),
				new Placement(STORAGE_MODULE, 0, 0, -1, Map.of()),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	/** A generator at 2, a copper cable at 3 and the electric furnace at 4; it lights up when fed. */
	private static List<Placement> furnaceLine(boolean lit, boolean litLater) {
		return List.of(generatorAt(2), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
				machine(ELECTRIC_FURNACE, 4, 0, lit, litLater));
	}

	/** The upgrade table fed from its east side: 0 lower half, 1 upper half, 2 cable, 3 generator. */
	private static List<Placement> upgradeRig(boolean litLater) {
		Map<String, String> late = litLater ? Map.of("lit", "true") : Map.of();
		return List.of(new Placement(TABLE_BLOCK, 0, 0, 0,
				Map.of("part", "lower", "facing", "south", "lit", "false"), late),
				new Placement(TABLE_BLOCK, 0, 1, 0,
						Map.of("part", "upper", "facing", "south", "lit", "false"), late),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	/** A three-high stone column west of the rig: index 0 of the join is its bottom block. */
	private static List<Placement> stoneColumn() {
		return List.of(stone(-2, 0, 0), stone(-2, 1, 0), stone(-2, 2, 0));
	}
}
