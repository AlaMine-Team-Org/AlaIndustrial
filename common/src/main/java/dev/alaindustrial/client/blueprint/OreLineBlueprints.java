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

/** Blueprints about ore to metal lines (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class OreLineBlueprints {

	private static final String MOD = "alaindustrial:";

	private static final String RAW_TIN = MOD + "raw_tin";
	private static final String TIN_DUST = MOD + "tin_dust";
	private static final String TIN_INGOT = MOD + "tin_ingot";
	private static final String TIN_PLATE = MOD + "tin_plate";
	private static final String RAW_SILVER = MOD + "raw_silver";
	private static final String RAW_NICKEL = MOD + "raw_nickel";
	private static final String RAW_PALLADIUM = MOD + "raw_palladium";
	private static final String RAW_URANIUM = MOD + "raw_uranium";
	private static final String SILVER_DUST = MOD + "silver_dust";
	private static final String NICKEL_DUST = MOD + "nickel_dust";
	private static final String PALLADIUM_DUST = MOD + "palladium_dust";
	private static final String SILVER_INGOT = MOD + "silver_ingot";
	private static final String NICKEL_INGOT = MOD + "nickel_ingot";
	private static final String PALLADIUM_INGOT = MOD + "palladium_ingot";
	private static final String URANIUM_INGOT = MOD + "uranium_ingot";
	private static final String URANIUM_PLATE = MOD + "uranium_plate";
	private static final String FORGE_HAMMER = MOD + "forge_hammer";
	private static final String CUPRONICKEL_PLATE = MOD + "cupronickel_plate";
	private static final String CUPRONICKEL_REINFORCED_PLATE = MOD + "cupronickel_reinforced_plate";
	private static final String INVAR_PLATE = MOD + "invar_plate";
	private static final String INVAR_REINFORCED_PLATE = MOD + "invar_reinforced_plate";
	private static final String CHOKE = MOD + "choke";
	private static final String CORE_BARREL = MOD + "core_barrel";
	private static final String EMERALD_DUST = MOD + "emerald_dust";

	private static final String STONE_PICKAXE = "minecraft:stone_pickaxe";
	private static final String IRON_PICKAXE = "minecraft:iron_pickaxe";
	private static final String COAL = "minecraft:coal";
	private static final String EMERALD = "minecraft:emerald";
	private static final String EMERALD_ORE = "minecraft:emerald_ore";
	private static final String CRAFTING_TABLE = "minecraft:crafting_table";

	private static final String DEEPSLATE_TIN_ORE = MOD + "deepslate_tin_ore";
	private static final String DEEPSLATE_SILVER_ORE = MOD + "deepslate_silver_ore";
	private static final String DEEPSLATE_NICKEL_ORE = MOD + "deepslate_nickel_ore";
	private static final String DEEPSLATE_SULFUR_ORE = MOD + "deepslate_sulfur_ore";
	private static final String DEEPSLATE_URANIUM_ORE = MOD + "deepslate_uranium_ore";

	/** The deepslate twins of the five stone ores, in the row order the sheet shows them. */
	private static final String[] DEEPSLATE_ORE_IDS = {DEEPSLATE_TIN_ORE, DEEPSLATE_SILVER_ORE,
			DEEPSLATE_NICKEL_ORE, DEEPSLATE_SULFUR_ORE, DEEPSLATE_URANIUM_ORE};

	/**
	 * The macerator alone (position 1); the generator (2, position 2) powering it while it turns raw tin
	 * into two dusts; the iron furnace (0, west of the line, position 3) smelting the dust into ingots on
	 * coal; raw tin into the compressor (5, position 4) that stays dark because a press does not grind
	 * ore; the same compressor pressing a tin ingot into a plate.
	 */
	public static final BlueprintDef TIN_LINE = new BlueprintDef("tin_line",
			List.of(RAW_TIN, TIN_DUST, TIN_INGOT),
			List.of(
					new Step(List.of(machine(MACERATOR, 0, 0, false, false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(grindLine(false, true), 2, 2, 2, 0, Verdict.GOOD,
							List.of(in(RAW_TIN, 0), out(TIN_DUST, 0), out(TIN_DUST, 0))),
					new Step(tinLine(true, false), 0, 3, List.of(Flow.energy(3, 1), Flow.item(1, 0, TIN_DUST)),
							Verdict.NONE, List.of(in(COAL, 0), in(TIN_DUST, 0), out(TIN_INGOT, 0))),
					new Step(tinLine(false, false), 5, 4, 3, 1, Verdict.BAD, List.of(in(RAW_TIN, 5))),
					new Step(tinLine(false, true), 5, 4, List.of(Flow.energy(3, 1), Flow.energy(3, 5)),
							Verdict.GOOD, List.of(in(TIN_INGOT, 5), out(TIN_PLATE, 5)))));

	/**
	 * Raw silver into two silver dusts on the macerator line (0..2); the electric furnace (4) behind a
	 * second cable (3) smelting silver and nickel dust into ingots; palladium dust in a lone iron
	 * furnace that never lights up for it; the same dust in the powered electric furnace (0) becoming
	 * an ingot.
	 */
	public static final BlueprintDef NOBLE_METALS = new BlueprintDef("noble_metals",
			List.of(RAW_SILVER, RAW_NICKEL, RAW_PALLADIUM, SILVER_INGOT, NICKEL_INGOT, PALLADIUM_INGOT,
					NICKEL_DUST),
			List.of(
					new Step(grindLine(false, true), 0, 1, 2, 0, Verdict.GOOD,
							List.of(in(RAW_SILVER, 0), out(SILVER_DUST, 0), out(SILVER_DUST, 0))),
					new Step(join(grindLine(true, false), List.of(cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							machine(ELECTRIC_FURNACE, 4, 0, false, true))), 4, 2,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(0, 4, SILVER_DUST)),
							Verdict.GOOD,
							List.of(in(SILVER_DUST, 4), out(SILVER_INGOT, 4), in(NICKEL_DUST, 4),
									out(NICKEL_INGOT, 4))),
					new Step(List.of(machine(IRON_FURNACE, 0, 0, false, false)), 0, 3, -1, -1, Verdict.BAD,
							List.of(in(COAL, 0), in(PALLADIUM_DUST, 0))),
					new Step(List.of(machine(ELECTRIC_FURNACE, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 4, 2, 0,
							Verdict.GOOD, List.of(in(PALLADIUM_DUST, 0), out(PALLADIUM_INGOT, 0)))));

	/**
	 * The five deepslate ores in a row (0..4, tin first, uranium last); a stone pickaxe mining tin (0)
	 * and silver (1); the same pickaxe on uranium (4) dropping nothing, an iron pickaxe getting raw
	 * uranium; then the compressor (2) on a generator (0) and a cable (1) pressing a uranium ingot into
	 * a plate.
	 */
	public static final BlueprintDef DEEPSLATE_ORES = new BlueprintDef("deepslate_ores",
			List.of(DEEPSLATE_TIN_ORE, DEEPSLATE_SILVER_ORE, DEEPSLATE_NICKEL_ORE, DEEPSLATE_SULFUR_ORE,
					DEEPSLATE_URANIUM_ORE, URANIUM_PLATE),
			List.of(
					new Step(deepslateRow(), 0, 1, -1, -1, Verdict.NONE),
					new Step(deepslateRow(), 0, 1, -1, -1, Verdict.GOOD,
							List.of(in(STONE_PICKAXE, 0), out(RAW_TIN, 0), in(STONE_PICKAXE, 1),
									out(RAW_SILVER, 1))),
					new Step(deepslateRow(), 4, 2, -1, -1, Verdict.BAD, List.of(in(STONE_PICKAXE, 4))),
					new Step(deepslateRow(), 4, 2, -1, -1, Verdict.GOOD,
							List.of(in(IRON_PICKAXE, 4), out(RAW_URANIUM, 4))),
					new Step(poweredLine(COMPRESSOR, false, true), 2, 3, 0, 2, Verdict.GOOD,
							List.of(in(URANIUM_INGOT, 2), out(URANIUM_PLATE, 2)))));

	/**
	 * The crafting table (0) welding the hammer and two cupronickel plates into one reinforced plate,
	 * then two invar plates the same way; one plate alone welding nothing; six reinforced invar plates
	 * and two chokes building a core barrel.
	 */
	public static final BlueprintDef WELDED_PLATES = new BlueprintDef("welded_plates",
			List.of(CUPRONICKEL_REINFORCED_PLATE, INVAR_REINFORCED_PLATE),
			List.of(
					new Step(List.of(table()), 0, 1, -1, -1, Verdict.GOOD,
							welded(CUPRONICKEL_PLATE, CUPRONICKEL_REINFORCED_PLATE)),
					new Step(List.of(table()), 0, 1, -1, -1, Verdict.GOOD,
							welded(INVAR_PLATE, INVAR_REINFORCED_PLATE)),
					new Step(List.of(table()), 0, 1, -1, -1, Verdict.BAD,
							List.of(in(FORGE_HAMMER, 0), in(CUPRONICKEL_PLATE, 0))),
					new Step(List.of(table()), 0, 1, -1, -1, Verdict.GOOD, coreBarrelParts())));

	/**
	 * Emerald ore into two emerald dusts on the macerator line (0..2); the dust into an electric
	 * furnace (0) that stays dark because gem dust does not melt; the compressor (2) pressing the dust
	 * back into an emerald; a whole emerald ground into a single dust.
	 */
	public static final BlueprintDef GEM_DUSTS = new BlueprintDef("gem_dusts",
			List.of(EMERALD_DUST),
			List.of(
					new Step(grindLine(false, true), 0, 1, 2, 0, Verdict.GOOD,
							List.of(in(EMERALD_ORE, 0), out(EMERALD_DUST, 0), out(EMERALD_DUST, 0))),
					new Step(List.of(machine(ELECTRIC_FURNACE, 0, 0, false, false),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 2, -1, -1,
							Verdict.BAD, List.of(in(EMERALD_DUST, 0))),
					new Step(poweredLine(COMPRESSOR, false, true), 2, 3, 0, 2, Verdict.GOOD,
							List.of(in(EMERALD_DUST, 2), out(EMERALD, 2))),
					new Step(grindLine(false, true), 0, 1, 2, 0, Verdict.NONE,
							List.of(in(EMERALD, 0), out(EMERALD_DUST, 0)))));

	private OreLineBlueprints() {
	}

	/** Macerator (0), cable (1), generator (2); the macerator lights up late in the step when asked. */
	private static List<Placement> grindLine(boolean lit, boolean litLater) {
		return List.of(machine(MACERATOR, 0, 0, lit, litLater), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				generatorAt(2));
	}

	/** Generator (0), cable (1), machine (2) lighting up late in the step when asked. */
	private static List<Placement> poweredLine(String machineId, boolean lit, boolean litLater) {
		return List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				machine(machineId, 2, 0, lit, litLater));
	}

	/** The tin line: an iron furnace west of the macerator, a compressor east of the generator. */
	private static List<Placement> tinLine(boolean furnaceLitLater, boolean pressLitLater) {
		return List.of(machine(IRON_FURNACE, -1, 0, false, furnaceLitLater),
				machine(MACERATOR, 0, 0, true, false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				generatorAt(2), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
				machine(COMPRESSOR, 4, 0, false, pressLitLater));
	}

	/** The five deepslate ores in a row at x 0..4, plain blocks with no state of their own. */
	private static List<Placement> deepslateRow() {
		List<Placement> row = new ArrayList<>();
		for (String ore : DEEPSLATE_ORE_IDS) {
			row.add(new Placement(ore, row.size(), 0, 0, Map.of()));
		}
		return row;
	}

	/** A crafting table at the origin. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	private static ItemMotion in(String itemId, int block) {
		return new ItemMotion(itemId, block, true);
	}

	private static ItemMotion out(String itemId, int block) {
		return new ItemMotion(itemId, block, false);
	}

	/** The hammer and two {@code plate}s into the table (0), one {@code reinforced} plate out. */
	private static List<ItemMotion> welded(String plate, String reinforced) {
		return List.of(in(FORGE_HAMMER, 0), in(plate, 0), in(plate, 0), out(reinforced, 0),
				out(FORGE_HAMMER, 0));
	}

	/** Six reinforced invar plates and two chokes into the table (0), the core barrel out. */
	private static List<ItemMotion> coreBarrelParts() {
		List<ItemMotion> all = new ArrayList<>();
		for (int plate = 0; plate < 6; plate++) {
			all.add(in(INVAR_REINFORCED_PLATE, 0));
		}
		for (int choke = 0; choke < 2; choke++) {
			all.add(in(CHOKE, 0));
		}
		all.add(out(CORE_BARREL, 0));
		return all;
	}
}
