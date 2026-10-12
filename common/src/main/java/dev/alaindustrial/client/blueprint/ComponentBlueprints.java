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
 * Blueprints about crafting components and the by-products of machines (MOD-797): circuits and casings,
 * coils and chokes, the fluxweave thread, the carbon ceramic chain and what the recycler leaves behind.
 * Listed in {@link BlueprintCatalog#ALL}.
 */
public final class ComponentBlueprints {

	private static final String MOD = "alaindustrial:";
	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String MACHINE_CASING = MOD + "machine_casing";
	private static final String ADVANCED_MACHINE_CASING = MOD + "advanced_machine_casing";
	private static final String ELECTRONIC_CIRCUIT = MOD + "electronic_circuit";
	private static final String ADVANCED_CIRCUIT = MOD + "advanced_circuit";
	private static final String REACTOR_CIRCUIT = MOD + "reactor_circuit";
	private static final String REACTOR_CONTROLLER = MOD + "reactor_controller";

	private static final String COPPER_COIL = MOD + "copper_coil";
	private static final String CHOKE = MOD + "choke";
	private static final String CHOKE_REINFORCED = MOD + "choke_reinforced";
	private static final String CHOKE_ADVANCED = MOD + "choke_advanced";
	private static final String RESONANCE_COIL = MOD + "resonance_coil";
	private static final String RESONANCE_CHIP = MOD + "resonance_chip";

	private static final String COTTON_SEEDS = MOD + "cotton_seeds";
	private static final String COTTON_FIBER = MOD + "cotton_fiber";
	private static final String FLUX_THREAD = MOD + "flux_thread";
	private static final String FLUXWEAVE_CLOTH = MOD + "fluxweave_cloth";

	private static final String CARBON_ROD = MOD + "carbon_rod";
	private static final String CARBON_ROD_DOUBLE = MOD + "carbon_rod_double";
	private static final String CARBON_BRIQUETTE = MOD + "carbon_briquette";
	private static final String CERAMIC_PLATE = MOD + "ceramic_plate";
	private static final String CARBON_CERAMIC = MOD + "carbon_ceramic";
	private static final String SOOT = MOD + "soot";

	private static final String SAWDUST = MOD + "sawdust";
	private static final String ASH = MOD + "ash";
	private static final String SLAG = MOD + "slag";
	private static final String SLAG_POOR = MOD + "slag_poor";
	private static final String SLAG_RICH = MOD + "slag_rich";
	private static final String SLAG_BLOCK = MOD + "slag_block";
	private static final String BLADES_IRON = MOD + "recycler_blades_iron";
	private static final String BLADES_TEMPERED = MOD + "recycler_blades_tempered";
	private static final String BLADES_DIAMOND = MOD + "recycler_blades_diamond";

	/**
	 * The crafting table (0, position 1) making an electronic circuit; the machine casing (1, position 2)
	 * made from iron plates; the casing wired to a generator, where the cable does not even join it (BAD);
	 * a macerator crafted from the casing in its place (1, position 3), powered and grinding raw iron
	 * (GOOD); the advanced circuit and the advanced casing (1, position 4); the reactor circuit going into
	 * the reactor controller (1, position 5).
	 */
	public static final BlueprintDef CIRCUITS = new BlueprintDef("circuits",
			List.of(ELECTRONIC_CIRCUIT, ADVANCED_CIRCUIT, REACTOR_CIRCUIT, MACHINE_CASING, ADVANCED_MACHINE_CASING),
			List.of(
					new Step(List.of(table(0, 0)), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+" + MOD + "silver_ingot", "+" + MOD + "nickel_ingot", "+minecraft:quartz",
									"+minecraft:emerald", "-" + ELECTRONIC_CIRCUIT)),
					new Step(List.of(table(0, 0), block(MACHINE_CASING, 2, 0, 0)), 1, 2, List.of(), Verdict.NONE,
							motions(0, "+" + MOD + "iron_plate", "+" + MOD + "iron_plate", "-" + MACHINE_CASING)),
					new Step(List.of(table(0, 0), block(MACHINE_CASING, 2, 0, 0), cableOf(COPPER_CABLE, 3, 0, 0,
							"east"), generatorAt(4)), 1, 2, List.of(), Verdict.BAD,
							motions(1, "+minecraft:raw_iron", "-minecraft:raw_iron")),
					new Step(List.of(table(0, 0), machine(MACERATOR, 2, 0, false, true),
							cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"), generatorAt(4)), 1, 3,
							List.of(Flow.energy(3, 1)), Verdict.GOOD,
							then(motions(0, "+" + MACHINE_CASING, "+minecraft:flint", "+" + ELECTRONIC_CIRCUIT,
									"-" + MACERATOR), motions(1, "+minecraft:raw_iron", "-" + MOD + "iron_dust"))),
					new Step(List.of(table(0, 0), block(ADVANCED_MACHINE_CASING, 2, 0, 0)), 1, 4, List.of(),
							Verdict.NONE,
							motions(0, "+" + ELECTRONIC_CIRCUIT, "+" + MOD + "rubber", "+" + MOD + "gold_plate",
									"-" + ADVANCED_CIRCUIT, "+" + MACHINE_CASING, "+" + MOD + "tempered_iron_plate",
									"-" + ADVANCED_MACHINE_CASING)),
					new Step(List.of(table(0, 0), new Placement(REACTOR_CONTROLLER, 2, 0, 0,
							Map.of("facing", "south", "formed", "false"))), 1, 5, List.of(), Verdict.GOOD,
							motions(0, "+" + ADVANCED_CIRCUIT, "+" + RESONANCE_COIL, "+" + MOD + "palladium_plate",
									"-" + REACTOR_CIRCUIT, "+" + REACTOR_CIRCUIT, "+" + ADVANCED_MACHINE_CASING,
									"-" + REACTOR_CONTROLLER))));

	/**
	 * All on the crafting table (0, position 1): the copper coil from cable and tin, then the resonance coil
	 * around it; the choke from invar plates, coils and a circuit; the reinforced choke around the choke and
	 * the advanced one around that. Then the resonance chip bouncing off a plain solar panel (1, position 2,
	 * BAD), going into a daylight panel in the same cell, which grows into the mirror concentrator (GOOD).
	 */
	public static final BlueprintDef COILS_AND_CHOKES = new BlueprintDef("coils_and_chokes",
			List.of(COPPER_COIL, CHOKE, CHOKE_REINFORCED, CHOKE_ADVANCED, RESONANCE_COIL, RESONANCE_CHIP),
			List.of(
					new Step(List.of(table(0, 0)), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+" + COPPER_CABLE, "+" + MOD + "tin_ingot", "-" + COPPER_COIL,
									"+" + COPPER_COIL, "+" + MOD + "spatial_crystal", "+" + MOD + "resonant_shard",
									"-" + RESONANCE_COIL)),
					new Step(List.of(table(0, 0)), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+" + MOD + "invar_plate", "+" + COPPER_COIL, "+" + ELECTRONIC_CIRCUIT,
									"-" + CHOKE)),
					new Step(List.of(table(0, 0)), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+" + CHOKE, "+" + MOD + "diamond_dust", "-" + CHOKE_REINFORCED,
									"+" + CHOKE_REINFORCED, "+" + RESONANCE_COIL, "+" + MOD + "palladium_plate",
									"-" + CHOKE_ADVANCED)),
					new Step(List.of(table(0, 0), panelAt(SOLAR_PANEL, 2)), 1, 2, List.of(), Verdict.BAD,
							motions(1, "+" + RESONANCE_CHIP, "-" + RESONANCE_CHIP)),
					new Step(List.of(table(0, 0), panelAt(DAYLIGHT_SOLAR_PANEL, 2)), 1, 2, List.of(), Verdict.NONE,
							motions(1, "+" + RESONANCE_CHIP)),
					new Step(List.of(table(0, 0), new Placement(RADIANT_SOLAR_PANEL, 2, 0, 0,
							Map.of("assembled", "false", "facing", "south"))), 1, 2, List.of(), Verdict.GOOD,
							List.of())));

	/**
	 * Grass (0, position 1) giving cotton seeds; the seeds planted on a trellis over wet farmland (lower half
	 * 2, position 2); the ripe bush giving cotton fibre; the galvanic bath fed water from a tank but with no
	 * cable to the generator, dark (bath 2, position 3, BAD); the cable in, the bath plating fibre and
	 * silver dust into flux thread (GOOD); four threads on the crafting table (0, position 4) making cloth,
	 * and the cloth making the chestplate.
	 */
	public static final BlueprintDef FLUXWEAVE_LINE = new BlueprintDef("fluxweave_line",
			List.of(COTTON_SEEDS, COTTON_FIBER, FLUX_THREAD, FLUXWEAVE_CLOTH),
			List.of(
					new Step(List.of(new Placement("minecraft:short_grass", 0, 1, 0, Map.of()),
							block("minecraft:grass_block", 0, 0, 0)), 0, 1, List.of(), Verdict.NONE,
							motions(0, "-" + COTTON_SEEDS)),
					new Step(BlueprintRigs.trellisOn(true, "0", "3"), 2, 2, List.of(), Verdict.NONE,
							motions(2, "+" + COTTON_SEEDS)),
					new Step(BlueprintRigs.trellisOn(true, "6", null), 2, 2, List.of(), Verdict.GOOD,
							motions(2, "-" + COTTON_FIBER, "-" + COTTON_FIBER, "-" + COTTON_SEEDS)),
					new Step(List.of(tankAt(FLUID_TANK), fluidPipeAt(1, "extract", "insert"), bath(false),
							generatorAt(4)), 2, 3, List.of(Flow.water(0, 2)), Verdict.BAD,
							motions(2, "+" + COTTON_FIBER, "+" + MOD + "silver_dust")),
					new Step(bathLine(FLUID_TANK, true), 2, 3, List.of(Flow.water(0, 2), Flow.energy(4, 2)),
							Verdict.GOOD,
							motions(2, "+" + COTTON_FIBER, "+" + MOD + "silver_dust", "-" + FLUX_THREAD)),
					new Step(List.of(table(0, 0)), 0, 4, List.of(), Verdict.NONE,
							motions(0, "+" + FLUX_THREAD, "+" + FLUX_THREAD, "-" + FLUXWEAVE_CLOTH,
									"+" + FLUXWEAVE_CLOTH, "-" + MOD + "fluxweave_chestplate"))));

	/**
	 * The compressor (0, position 1) pressing coal dust into a carbon rod; two rods and the forge hammer on
	 * the crafting table (0, position 2) making a double rod; the alloy smelter (0, position 3) firing double
	 * rods with slag into a briquette, while the compressor (4) presses soot into one; the quench press with
	 * its piston (1, position 4) turned away from the water (0), so nothing happens (BAD); the piston aimed
	 * into the water, firing and splitting the briquette into ceramic plates (GOOD); four plates on the
	 * table (0) making a carbon ceramic block (1, position 5).
	 */
	public static final BlueprintDef CARBON_LINE = new BlueprintDef("carbon_line",
			List.of(CARBON_ROD, CARBON_ROD_DOUBLE, CARBON_BRIQUETTE, SOOT, CERAMIC_PLATE, CARBON_CERAMIC),
			List.of(
					new Step(List.of(machine(COMPRESSOR, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 1,
							List.of(Flow.energy(2, 0)), Verdict.NONE,
							motions(0, "+" + MOD + "coal_dust", "-" + CARBON_ROD)),
					new Step(List.of(table(0, 0)), 0, 2, List.of(), Verdict.NONE,
							motions(0, "+" + MOD + "forge_hammer", "+" + CARBON_ROD, "+" + CARBON_ROD,
									"-" + CARBON_ROD_DOUBLE)),
					new Step(List.of(machine(ALLOY_SMELTER, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2),
							cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"), machine(COMPRESSOR, 4, 0, false, true)),
							0, 3, List.of(Flow.energy(2, 0), Flow.energy(2, 4)), Verdict.NONE,
							then(motions(0, "+" + CARBON_ROD_DOUBLE, "+" + SLAG, "-" + CARBON_BRIQUETTE),
									motions(4, "+" + SOOT, "-" + CARBON_BRIQUETTE))),
					new Step(press("west"), 1, 4, List.of(), Verdict.BAD,
							motions(0, "+" + CARBON_BRIQUETTE, "+minecraft:redstone")),
					new Step(press("east"), 1, 4, List.of(), Verdict.GOOD,
							motions(0, "+" + CARBON_BRIQUETTE, "+minecraft:redstone", "-" + CERAMIC_PLATE,
									"-" + CERAMIC_PLATE)),
					new Step(List.of(table(0, 0), block(CARBON_CERAMIC, 2, 0, 0), block(CARBON_CERAMIC, 3, 0, 0),
							block(CARBON_CERAMIC, 2, 1, 0)), 1, 5, List.of(), Verdict.GOOD,
							motions(0, "+" + CERAMIC_PLATE, "+" + CERAMIC_PLATE, "-" + CARBON_CERAMIC))));

	/**
	 * The recycler (0, position 1) powered through a cable from a generator but with no blades, standing
	 * still (BAD); iron blades going in, cobblestone grinding into poor slag and ash, one lamp lit (GOOD);
	 * the sawmill (4, position 2) on the same generator sending sawdust over, two lamps and plain slag; a
	 * sword added, three lamps and rich slag; the blades fired in a furnace (0, position 3) and finished on
	 * the crafting table (1); poor slag and ash on the table (0) making slag blocks (1, position 4) and grey
	 * dye.
	 */
	public static final BlueprintDef BYPRODUCTS = new BlueprintDef("byproducts",
			List.of(SAWDUST, ASH, SLAG, SLAG_POOR, SLAG_RICH, SLAG_BLOCK, BLADES_IRON, BLADES_TEMPERED,
					BLADES_DIAMOND),
			List.of(
					new Step(recyclerLine(null, false), 0, 1, List.of(Flow.energy(2, 0)), Verdict.BAD,
							motions(0, "+minecraft:cobblestone")),
					new Step(recyclerLine("1", false), 0, 1, List.of(Flow.energy(2, 0)), Verdict.GOOD,
							motions(0, "+" + BLADES_IRON, "+minecraft:cobblestone", "-" + SLAG_POOR, "-" + ASH)),
					new Step(recyclerLine("2", true), 4, 2,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(4, 0, SAWDUST)), Verdict.NONE,
							then(motions(4, "+minecraft:oak_log", "-" + SAWDUST),
									motions(0, "+minecraft:cobblestone", "-" + SLAG))),
					new Step(recyclerLine("3", true), 0, 1,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(4, 0, SAWDUST)), Verdict.GOOD,
							motions(0, "+minecraft:cobblestone", "+minecraft:iron_sword", "-" + SLAG_RICH)),
					new Step(List.of(new Placement("minecraft:furnace", 0, 0, 0,
							Map.of("facing", "south", "lit", "false"), Map.of("lit", "true")), table(2, 0)), 0, 3,
							List.of(), Verdict.NONE,
							then(motions(0, "+" + BLADES_IRON, "-" + BLADES_TEMPERED), motions(1, "+" + BLADES_TEMPERED,
									"+minecraft:diamond", "+" + MOD + "reinforced_bearing", "-" + BLADES_DIAMOND))),
					new Step(List.of(table(0, 0), block(SLAG_BLOCK, 2, 0, 0), block(SLAG_BLOCK, 3, 0, 0),
							block(SLAG_BLOCK, 2, 1, 0)), 1, 4, List.of(), Verdict.NONE,
							motions(0, "+" + SLAG_POOR, "-" + SLAG_BLOCK, "+" + ASH, "+" + ASH,
									"-minecraft:gray_dye"))));

	private ComponentBlueprints() {
	}

	private static Placement block(String id, int x, int y, int z) {
		return new Placement(id, x, y, z, Map.of());
	}

	private static Placement table(int x, int z) {
		return block(CRAFTING_TABLE, x, 0, z);
	}

	/** Item motions on block {@code index}: each entry is "+id" for an input or "-id" for an output. */
	private static List<ItemMotion> motions(int index, String... entries) {
		List<ItemMotion> all = new ArrayList<>();
		for (String entry : entries) {
			all.add(new ItemMotion(entry.substring(1), index, entry.charAt(0) == '+'));
		}
		return all;
	}

	private static List<ItemMotion> then(List<ItemMotion> head, List<ItemMotion> tail) {
		List<ItemMotion> all = new ArrayList<>(head);
		all.addAll(tail);
		return all;
	}

	/**
	 * The quench press: 0 a water source, 1 a piston at its west facing {@code facing} that extends late,
	 * 2 a lever on the piston switched on late.
	 */
	private static List<Placement> press(String facing) {
		return List.of(new Placement("minecraft:water", 1, 0, 0, Map.of()),
				new Placement("minecraft:piston", 0, 0, 0, Map.of("facing", facing, "extended", "false"),
						Map.of("extended", "true")),
				new Placement("minecraft:lever", 0, 1, 0, Map.of("face", "floor", "facing", "south",
						"powered", "false"), Map.of("powered", "true")));
	}

	/**
	 * 0 the recycler, 1 cable, 2 generator, and with {@code sawmill} 3 cable and 4 the sawmill. The recycler
	 * runs late in the step with {@code lamps} lamps lit; null leaves it dark (no blades).
	 */
	private static List<Placement> recyclerLine(String lamps, boolean sawmill) {
		Map<String, String> late = lamps == null ? Map.of() : Map.of("lit", "true", "lamps", lamps);
		List<Placement> all = new ArrayList<>(List.of(
				new Placement(RECYCLER, 0, 0, 0, Map.of("facing", "south", "lit", "false", "lamps", "0"), late),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)));
		if (sawmill) {
			all.add(cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"));
			all.add(machine(SAWMILL, 4, 0, false, true));
		}
		return all;
	}
}
