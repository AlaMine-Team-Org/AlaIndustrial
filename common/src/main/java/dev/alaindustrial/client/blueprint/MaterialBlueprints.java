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
 * Blueprints about the mod's materials (MOD-797): alloy ingots, metal storage blocks and their shapes, dusts,
 * plates and reinforced plates, gears and bearings. Each sheet shows the machine or bench that makes the
 * material and where it goes next. Listed in {@link BlueprintCatalog#ALL}.
 */
public final class MaterialBlueprints {

	private static final String BRONZE_INGOT = "alaindustrial:bronze_ingot";
	private static final String ELECTRUM_INGOT = "alaindustrial:electrum_ingot";
	private static final String INVAR_INGOT = "alaindustrial:invar_ingot";
	private static final String CUPRONICKEL_INGOT = "alaindustrial:cupronickel_ingot";
	private static final String NETHERITE_ALLOY_INGOT = "alaindustrial:netherite_alloy_ingot";
	private static final String SHIELDING_ALLOY_INGOT = "alaindustrial:shielding_alloy_ingot";
	private static final String TEMPERED_IRON = "alaindustrial:tempered_iron";
	private static final String NICKEL_INGOT = "alaindustrial:nickel_ingot";
	private static final String SILVER_INGOT = "alaindustrial:silver_ingot";
	private static final String PALLADIUM_INGOT = "alaindustrial:palladium_ingot";

	private static final String BRONZE_BLOCK = "alaindustrial:bronze_block";
	private static final String BRONZE_STAIRS = "alaindustrial:bronze_stairs";
	private static final String BRONZE_SLAB = "alaindustrial:bronze_slab";
	private static final String BRONZE_WALL = "alaindustrial:bronze_wall";
	private static final String ELECTRUM_BLOCK = "alaindustrial:electrum_block";
	private static final String ELECTRUM_STAIRS = "alaindustrial:electrum_stairs";
	private static final String ELECTRUM_SLAB = "alaindustrial:electrum_slab";
	private static final String ELECTRUM_WALL = "alaindustrial:electrum_wall";
	private static final String INVAR_BLOCK = "alaindustrial:invar_block";
	private static final String NICKEL_BLOCK = "alaindustrial:nickel_block";

	private static final String IRON_DUST = "alaindustrial:iron_dust";
	private static final String COPPER_DUST = "alaindustrial:copper_dust";
	private static final String GOLD_DUST = "alaindustrial:gold_dust";
	private static final String SILVER_DUST = "alaindustrial:silver_dust";
	private static final String PALLADIUM_DUST = "alaindustrial:palladium_dust";
	private static final String COAL_DUST = "alaindustrial:coal_dust";
	private static final String DIAMOND_DUST = "alaindustrial:diamond_dust";
	private static final String LAPIS_DUST = "alaindustrial:lapis_dust";

	private static final String BRONZE_PLATE = "alaindustrial:bronze_plate";
	private static final String BRONZE_REINFORCED_PLATE = "alaindustrial:bronze_reinforced_plate";
	private static final String ELECTRUM_PLATE = "alaindustrial:electrum_plate";
	private static final String ELECTRUM_REINFORCED_PLATE = "alaindustrial:electrum_reinforced_plate";
	private static final String INVAR_PLATE = "alaindustrial:invar_plate";
	private static final String CUPRONICKEL_PLATE = "alaindustrial:cupronickel_plate";
	private static final String TEMPERED_IRON_PLATE = "alaindustrial:tempered_iron_plate";
	private static final String PALLADIUM_PLATE = "alaindustrial:palladium_plate";
	private static final String SHIELDING_PLATE = "alaindustrial:shielding_alloy_plate";
	private static final String SHIELDING_REINFORCED_PLATE = "alaindustrial:shielding_alloy_reinforced_plate";
	private static final String FORGE_HAMMER = "alaindustrial:forge_hammer";

	private static final String WOODEN_GEAR = "alaindustrial:wooden_gear";
	private static final String STONE_GEAR = "alaindustrial:stone_gear";
	private static final String IRON_GEAR = "alaindustrial:iron_gear";
	private static final String GOLD_GEAR = "alaindustrial:gold_gear";
	private static final String SILVER_GEAR = "alaindustrial:silver_gear";
	private static final String ELECTRUM_GEAR = "alaindustrial:electrum_gear";
	private static final String BASIC_BEARING = "alaindustrial:basic_bearing";
	private static final String REINFORCED_BEARING = "alaindustrial:reinforced_bearing";

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";

	/**
	 * A burning generator next to the alloy smelter with no cable between them, the smelter dark with iron
	 * and nickel in it; the cable laid, invar coming out; then bronze, cupronickel and electrum,
	 * netherite alloy, and shielding alloy from palladium and tempered iron. Indices of the powered line:
	 * 0 generator, 1 cable, 2 smelter; without the cable the smelter is 1.
	 */
	public static final BlueprintDef ALLOYS = new BlueprintDef("alloys",
			List.of(INVAR_INGOT, BRONZE_INGOT, CUPRONICKEL_INGOT, ELECTRUM_INGOT, NETHERITE_ALLOY_INGOT,
					SHIELDING_ALLOY_INGOT),
			List.of(
					new Step(List.of(generatorAt(0), machine(ALLOY_SMELTER, 2, 0, false, false)), 1, 1, List.of(),
							Verdict.BAD, List.of(in("minecraft:iron_ingot", 1), in("minecraft:iron_ingot", 1),
									in(NICKEL_INGOT, 1))),
					new Step(smelterLine(false, true), 2, 1, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(in("minecraft:iron_ingot", 2), in("minecraft:iron_ingot", 2), in(NICKEL_INGOT, 2),
									out(INVAR_INGOT, 2))),
					new Step(smelterLine(true, false), 2, 1, List.of(Flow.energy(0, 2)), Verdict.NONE,
							List.of(in("minecraft:copper_ingot", 2), in("minecraft:copper_ingot", 2),
									in("minecraft:copper_ingot", 2), in("alaindustrial:tin_ingot", 2),
									out(BRONZE_INGOT, 2), out(BRONZE_INGOT, 2))),
					new Step(smelterLine(true, false), 2, 1, List.of(Flow.energy(0, 2)), Verdict.NONE,
							List.of(in("minecraft:copper_ingot", 2), in(NICKEL_INGOT, 2), out(CUPRONICKEL_INGOT, 2),
									in("minecraft:gold_ingot", 2), in(SILVER_INGOT, 2), out(ELECTRUM_INGOT, 2))),
					new Step(smelterLine(true, false), 2, 1, List.of(Flow.energy(0, 2)), Verdict.NONE,
							List.of(in("minecraft:netherite_ingot", 2), in(ELECTRUM_INGOT, 2), in(ELECTRUM_INGOT, 2),
									out(NETHERITE_ALLOY_INGOT, 2), out(NETHERITE_ALLOY_INGOT, 2))),
					new Step(smelterLine(true, false), 2, 1, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(in(PALLADIUM_INGOT, 2), in(TEMPERED_IRON, 2), in(TEMPERED_IRON, 2),
									in(TEMPERED_IRON, 2), out(SHIELDING_ALLOY_INGOT, 2)))));

	/**
	 * Bronze ingots crafted into a block of bronze (1) beside a crafting table (0); the block taken back to
	 * ingots, with blocks of invar, nickel and electrum beside it; the stonecutter (0) cutting the bronze
	 * block (1) into stairs (2), a slab (3) and a wall (4); a stone pickaxe getting nothing from the
	 * electrum block (0), an iron pickaxe getting it; then electrum stairs, slab and two joined walls
	 * (3, 4) next to the electrum block.
	 */
	public static final BlueprintDef METAL_DECOR = new BlueprintDef("metal_decor",
			List.of(BRONZE_BLOCK, BRONZE_STAIRS, BRONZE_SLAB, BRONZE_WALL, ELECTRUM_BLOCK, ELECTRUM_STAIRS,
					ELECTRUM_SLAB, ELECTRUM_WALL, INVAR_BLOCK, NICKEL_BLOCK),
			List.of(
					new Step(List.of(at(CRAFTING_TABLE, 0), at(BRONZE_BLOCK, 1)), 1, 1, List.of(), Verdict.NONE,
							List.of(in(BRONZE_INGOT, 0), in(BRONZE_INGOT, 0), out(BRONZE_BLOCK, 0))),
					new Step(List.of(at(CRAFTING_TABLE, 0), at(BRONZE_BLOCK, 1), at(INVAR_BLOCK, 2),
							at(NICKEL_BLOCK, 3), at(ELECTRUM_BLOCK, 4)), 1, 1, List.of(), Verdict.NONE,
							List.of(in(BRONZE_BLOCK, 0), out(BRONZE_INGOT, 0), out(BRONZE_INGOT, 0))),
					new Step(bronzeShapes(), 0, 2, List.of(), Verdict.NONE,
							List.of(in(BRONZE_BLOCK, 0), out(BRONZE_STAIRS, 0), out(BRONZE_SLAB, 0),
									out(BRONZE_WALL, 0))),
					new Step(List.of(at(ELECTRUM_BLOCK, 0)), 0, 3, List.of(), Verdict.BAD,
							List.of(in("minecraft:stone_pickaxe", 0))),
					new Step(List.of(at(ELECTRUM_BLOCK, 0)), 0, 3, List.of(), Verdict.GOOD,
							List.of(in("minecraft:iron_pickaxe", 0), out(ELECTRUM_BLOCK, 0))),
					new Step(electrumShapes(), 3, 4, -1, -1, Verdict.GOOD)));

	/**
	 * The macerator (0) on a generator (2) grinding raw iron and raw copper into dust; a cable (3) and an
	 * electric furnace (4) added, gold dust going from one to the other; palladium dust in a plain furnace
	 * (0) that stays dark; the same dust and silver dust in an electric furnace (0); then the macerator
	 * feeding a compressor (4) with coal and diamond dust, and lapis ore giving a pile of lapis dust.
	 */
	public static final BlueprintDef DUSTS = new BlueprintDef("dusts",
			List.of(IRON_DUST, COPPER_DUST, GOLD_DUST, SILVER_DUST, PALLADIUM_DUST, COAL_DUST, DIAMOND_DUST,
					LAPIS_DUST),
			List.of(
					new Step(maceratorLine(false, true), 0, 1, List.of(Flow.energy(2, 0)), Verdict.NONE,
							List.of(in("minecraft:raw_iron", 0), out(IRON_DUST, 0), in("minecraft:raw_copper", 0),
									out(COPPER_DUST, 0))),
					new Step(join(maceratorLine(true, false), List.of(cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							machine(ELECTRIC_FURNACE, 4, 0, false, true))), 4, 2,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(0, 4, GOLD_DUST)), Verdict.NONE,
							List.of(in("minecraft:raw_gold", 0), out(GOLD_DUST, 0), in(GOLD_DUST, 4),
									out("minecraft:gold_ingot", 4))),
					new Step(List.of(new Placement("minecraft:furnace", 0, 0, 0, Map.of("facing", "south",
							"lit", "false"))), 0, 3, List.of(), Verdict.BAD,
							List.of(in("minecraft:coal", 0), in(PALLADIUM_DUST, 0))),
					new Step(List.of(machine(ELECTRIC_FURNACE, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(in(PALLADIUM_DUST, 0), out(PALLADIUM_INGOT, 0), in(SILVER_DUST, 0),
									out(SILVER_INGOT, 0))),
					new Step(compressorLine(false), 4, 4,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(0, 4, COAL_DUST)), Verdict.NONE,
							List.of(in("minecraft:coal_ore", 0), out(COAL_DUST, 0), in(COAL_DUST, 4),
									out("minecraft:coal", 4), in(DIAMOND_DUST, 4), out("minecraft:diamond", 4))),
					new Step(compressorLine(true), 0, 1,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(0, 4, LAPIS_DUST)), Verdict.GOOD,
							List.of(in("minecraft:lapis_ore", 0), out(LAPIS_DUST, 0), out(LAPIS_DUST, 0),
									out(LAPIS_DUST, 0), in(LAPIS_DUST, 4), out("minecraft:lapis_lazuli", 4)))));

	/**
	 * A crafting table (0) where the forge hammer beats a bronze ingot into a plate and two plates into a
	 * reinforced one; an electrum ingot in a macerator (2) on a generator (0) and cable (1), which stays
	 * dark; the compressor in its place pressing the plate and the reinforced plate; invar, cupronickel and
	 * tempered iron plates; palladium and shielding plates; then the reinforced shielding plate back at the
	 * crafting table with depleted uranium.
	 */
	public static final BlueprintDef REINFORCED_PLATES = new BlueprintDef("reinforced_plates",
			List.of(BRONZE_PLATE, BRONZE_REINFORCED_PLATE, ELECTRUM_PLATE, ELECTRUM_REINFORCED_PLATE, INVAR_PLATE,
					CUPRONICKEL_PLATE, TEMPERED_IRON_PLATE, PALLADIUM_PLATE, SHIELDING_PLATE,
					SHIELDING_REINFORCED_PLATE),
			List.of(
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(in(FORGE_HAMMER, 0), in(BRONZE_INGOT, 0), out(BRONZE_PLATE, 0),
									in(BRONZE_PLATE, 0), in(BRONZE_PLATE, 0), out(BRONZE_REINFORCED_PLATE, 0))),
					new Step(pressLine(MACERATOR, false, false), 2, 2, List.of(Flow.energy(0, 2)), Verdict.BAD,
							List.of(in(ELECTRUM_INGOT, 2))),
					new Step(pressLine(COMPRESSOR, false, true), 2, 2, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(in(ELECTRUM_INGOT, 2), out(ELECTRUM_PLATE, 2), in(ELECTRUM_PLATE, 2),
									in(ELECTRUM_PLATE, 2), out(ELECTRUM_REINFORCED_PLATE, 2))),
					new Step(pressLine(COMPRESSOR, true, false), 2, 2, List.of(Flow.energy(0, 2)), Verdict.NONE,
							List.of(in(INVAR_INGOT, 2), out(INVAR_PLATE, 2), in(CUPRONICKEL_INGOT, 2),
									out(CUPRONICKEL_PLATE, 2), in(TEMPERED_IRON, 2), out(TEMPERED_IRON_PLATE, 2))),
					new Step(pressLine(COMPRESSOR, true, false), 2, 2, List.of(Flow.energy(0, 2)), Verdict.NONE,
							List.of(in(PALLADIUM_INGOT, 2), out(PALLADIUM_PLATE, 2), in(SHIELDING_ALLOY_INGOT, 2),
									out(SHIELDING_PLATE, 2))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.GOOD,
							List.of(in(FORGE_HAMMER, 0), in(SHIELDING_PLATE, 0), in(SHIELDING_PLATE, 0),
									in("alaindustrial:depleted_uranium", 0), out(SHIELDING_REINFORCED_PLATE, 0)))));

	/**
	 * A crafting table (0) turning sticks and a button into a wooden gear; the wooden gear wrapped in
	 * cobblestone and iron, then in gold, silver and electrum; the two bearings made from the stone and
	 * electrum gears; a recycler (2) on a generator (0) and cable (1) with no blades, dark; then iron
	 * blades, built around a basic bearing, going in and the recycler running.
	 */
	public static final BlueprintDef GEARS_AND_BEARINGS = new BlueprintDef("gears_and_bearings",
			List.of(WOODEN_GEAR, STONE_GEAR, IRON_GEAR, GOLD_GEAR, SILVER_GEAR, ELECTRUM_GEAR, BASIC_BEARING,
					REINFORCED_BEARING),
			List.of(
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(in("minecraft:stick", 0), in("minecraft:oak_button", 0), out(WOODEN_GEAR, 0))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(in(WOODEN_GEAR, 0), in("minecraft:cobblestone", 0), out(STONE_GEAR, 0),
									in(WOODEN_GEAR, 0), in("minecraft:iron_ingot", 0), out(IRON_GEAR, 0))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(in("minecraft:gold_ingot", 0), out(GOLD_GEAR, 0), in(SILVER_INGOT, 0),
									out(SILVER_GEAR, 0), in(ELECTRUM_INGOT, 0), out(ELECTRUM_GEAR, 0))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(in(STONE_GEAR, 0), in("alaindustrial:nickel_plate", 0), out(BASIC_BEARING, 0),
									in(ELECTRUM_GEAR, 0), in(BRONZE_REINFORCED_PLATE, 0),
									out(REINFORCED_BEARING, 0))),
					new Step(recyclerLine(false), 2, 2, List.of(Flow.energy(0, 2)), Verdict.BAD,
							List.of(in("minecraft:cobblestone", 2))),
					new Step(recyclerLine(true), 2, 2, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(in("alaindustrial:recycler_blades_iron", 2), in("minecraft:cobblestone", 2),
									out("alaindustrial:slag_poor", 2)))));

	private MaterialBlueprints() {
	}

	private static ItemMotion in(String itemId, int block) {
		return new ItemMotion(itemId, block, true);
	}

	private static ItemMotion out(String itemId, int block) {
		return new ItemMotion(itemId, block, false);
	}

	/** A block of {@code id} with no state of its own at ({@code x}, 0, 0). */
	private static Placement at(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of());
	}

	/** Generator (0), cable (1), alloy smelter (2), lit now or late in the step. */
	private static List<Placement> smelterLine(boolean lit, boolean litLater) {
		return List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				machine(ALLOY_SMELTER, 2, 0, lit, litLater));
	}

	/** Generator (0), cable (1), {@code machineId} (2): the macerator that cannot press or the compressor. */
	private static List<Placement> pressLine(String machineId, boolean lit, boolean litLater) {
		return List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				machine(machineId, 2, 0, lit, litLater));
	}

	/** Macerator (0), cable (1), generator (2), the macerator lit now or late in the step. */
	private static List<Placement> maceratorLine(boolean lit, boolean litLater) {
		return List.of(machine(MACERATOR, 0, 0, lit, litLater), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				generatorAt(2));
	}

	/** {@link #maceratorLine} then a cable (3) and a compressor (4), both machines running when {@code lit}. */
	private static List<Placement> compressorLine(boolean lit) {
		return join(maceratorLine(lit, !lit), List.of(cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
				machine(COMPRESSOR, 4, 0, lit, !lit)));
	}

	/** Generator (0), cable (1), recycler (2); {@code runs} lights it with one kind of junk late in the step. */
	private static List<Placement> recyclerLine(boolean runs) {
		Placement recycler = new Placement(RECYCLER, 2, 0, 0, Map.of("facing", "south", "lit", "false", "lamps", "0"),
				runs ? Map.of("lit", "true", "lamps", "1") : Map.of());
		return List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), recycler);
	}

	/** Stonecutter (0), block of bronze (1), bronze stairs (2), slab (3) and a lone wall post (4). */
	private static List<Placement> bronzeShapes() {
		return List.of(new Placement("minecraft:stonecutter", 0, 0, 0, Map.of("facing", "south")),
				at(BRONZE_BLOCK, 1), stairs(BRONZE_STAIRS, 2), new Placement(BRONZE_SLAB, 3, 0, 0, Map.of("type",
						"bottom")), wall(BRONZE_WALL, 4, "none", "none"));
	}

	/** Block of electrum (0), electrum stairs (1), slab (2), two walls joined to each other (3, 4). */
	private static List<Placement> electrumShapes() {
		List<Placement> all = new ArrayList<>(List.of(at(ELECTRUM_BLOCK, 0), stairs(ELECTRUM_STAIRS, 1),
				new Placement(ELECTRUM_SLAB, 2, 0, 0, Map.of("type", "bottom"))));
		all.add(wall(ELECTRUM_WALL, 3, "none", "low"));
		all.add(wall(ELECTRUM_WALL, 4, "low", "none"));
		return all;
	}

	/** Bottom stairs at ({@code x}, 0, 0), the step toward the viewer. */
	private static Placement stairs(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of("facing", "north", "half", "bottom", "shape", "straight"));
	}

	/** A wall at ({@code x}, 0, 0) with a post and the given west and east sides. */
	private static Placement wall(String id, int x, String west, String east) {
		return new Placement(id, x, 0, 0, Map.of("up", "true", "west", west, "east", east, "north", "none",
				"south", "none"));
	}
}
