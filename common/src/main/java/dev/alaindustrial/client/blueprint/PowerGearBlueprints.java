package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;
import static dev.alaindustrial.client.blueprint.BlueprintRigs.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about the items that carry energy and the gear that spends it (MOD-797): the charged
 * crystals, batteries and the energy pack, the jetpack, the electric bow and saber, and the diamond and
 * netherite tool upgrades. Listed in {@link BlueprintCatalog#ALL}.
 */
public final class PowerGearBlueprints {

	private static final String TABLE = "minecraft:crafting_table";
	private static final String SMITHING_TABLE = "minecraft:smithing_table";

	private static final String ENERGY_CRYSTAL = "alaindustrial:energy_crystal";
	private static final String ENERGY_CRYSTAL_BLANK = "alaindustrial:energy_crystal_blank";
	private static final String LAPOTRON_CRYSTAL = "alaindustrial:lapotron_crystal";
	private static final String LAPOTRON_CRYSTAL_BLANK = "alaindustrial:lapotron_crystal_blank";
	private static final String RESONANT_CRYSTAL = "alaindustrial:resonant_crystal";
	private static final String RESONANT_CRYSTAL_BLANK = "alaindustrial:resonant_crystal_blank";
	private static final String RESONANT_SHARD = "alaindustrial:resonant_shard";

	private static final String BATTERY = "alaindustrial:battery";
	private static final String BATTERY_POUCH = "alaindustrial:battery_pouch";
	private static final String ENERGY_PACK = "alaindustrial:energy_pack";
	private static final String JETPACK = "alaindustrial:jetpack";
	private static final String ELECTRIC_BOW = "alaindustrial:electric_bow";
	private static final String ELECTRIC_SABER = "alaindustrial:electric_saber";

	private static final String DRILL_DIAMOND = "alaindustrial:electric_drill_diamond_tip";
	private static final String DRILL_NETHERITE = "alaindustrial:electric_drill_netherite_tip";
	private static final String DRILL_HEAD = "alaindustrial:netherite_drill_head";
	private static final String DRILL_TEMPLATE = "alaindustrial:netherite_drill_upgrade_smithing_template";
	private static final String CHAINSAW_DIAMOND = "alaindustrial:electric_chainsaw_diamond_tip";
	private static final String SHOVEL_DIAMOND = "alaindustrial:electric_shovel_diamond_tip";
	private static final String HOE_DIAMOND = "alaindustrial:electric_hoe_diamond_tip";

	private static final String CIRCUIT = "alaindustrial:electronic_circuit";
	private static final String COIL = "alaindustrial:copper_coil";

	/**
	 * The energy crystal blank crafted on a table (0); the blank in a reinforced store with no feed, which
	 * stays a blank (bad); the store fed by a geothermal generator turning it into the crystal; the lapotron
	 * blank built around that crystal and charged the same way; the incubator making a resonant shard from
	 * amethyst; the resonant blank built around the lapotron crystal and charged. Indices of the line:
	 * 0 store, 1 generator, 2 gold cable, 3 table; of the incubator rig: 0 base, 3 generator.
	 */
	public static final BlueprintDef ENERGY_CRYSTALS = new BlueprintDef("energy_crystals",
			List.of(ENERGY_CRYSTAL_BLANK, ENERGY_CRYSTAL, LAPOTRON_CRYSTAL_BLANK, LAPOTRON_CRYSTAL,
					RESONANT_CRYSTAL_BLANK, RESONANT_CRYSTAL, RESONANT_SHARD),
			List.of(
					new Step(List.of(table(4, 1)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:diamond_dust", 0, true),
									new ItemMotion("minecraft:redstone", 0, true),
									new ItemMotion("alaindustrial:tempered_iron", 0, true),
									new ItemMotion(ENERGY_CRYSTAL_BLANK, 0, false))),
					new Step(List.of(cesu("west"), table(4, 1)), 0, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion(ENERGY_CRYSTAL_BLANK, 0, true))),
					new Step(crystalLine(), 0, 2, List.of(Flow.energy(1, 0)), Verdict.GOOD,
							List.of(new ItemMotion(ENERGY_CRYSTAL_BLANK, 0, true),
									new ItemMotion(ENERGY_CRYSTAL, 0, false))),
					new Step(crystalLine(), 3, 1,
							List.of(Flow.energy(1, 0), Flow.item(3, 0, LAPOTRON_CRYSTAL_BLANK)), Verdict.NONE,
							List.of(new ItemMotion(ENERGY_CRYSTAL, 3, true),
									new ItemMotion("alaindustrial:energy_clot_ii", 3, true),
									new ItemMotion("alaindustrial:advanced_circuit", 3, true),
									new ItemMotion("alaindustrial:resonance_coil", 3, true),
									new ItemMotion(LAPOTRON_CRYSTAL, 0, false))),
					new Step(incubatorRig(true), 0, 3, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:mutation_chip_create", 0, true),
									new ItemMotion("minecraft:amethyst_shard", 0, true),
									new ItemMotion(RESONANT_SHARD, 0, false))),
					new Step(crystalLine(), 0, 2,
							List.of(Flow.energy(1, 0), Flow.item(3, 0, RESONANT_CRYSTAL_BLANK)), Verdict.GOOD,
							List.of(new ItemMotion(LAPOTRON_CRYSTAL, 3, true),
									new ItemMotion(RESONANT_SHARD, 3, true),
									new ItemMotion("alaindustrial:spatial_crystal", 3, true),
									new ItemMotion("alaindustrial:energy_clot_iii", 3, true),
									new ItemMotion(RESONANT_CRYSTAL, 0, false)))));

	/**
	 * A battery crafted on a table (3, number 1); a generator cable meeting the battery box's output face,
	 * so the battery stays empty (bad); the box turned so the cable meets its input, charging the battery;
	 * the energy pack crafted from three batteries; the box charging the pack and the battery pouch; then,
	 * with no generator, a charged battery in the box's discharge slot running an electric furnace.
	 * Indices of the line: 0 generator, 1 cable, 2 box, 3 table; of the last step: 0 box, 1 cable, 2 furnace.
	 */
	public static final BlueprintDef BATTERIES = new BlueprintDef("batteries",
			List.of(BATTERY, BATTERY_POUCH, ENERGY_PACK),
			List.of(
					new Step(List.of(table(4, 1)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:tin_ingot", 0, true),
									new ItemMotion("minecraft:redstone", 0, true),
									new ItemMotion(COPPER_CABLE, 0, true),
									new ItemMotion(BATTERY, 0, false))),
					new Step(boxLine("east"), 2, 2, List.of(Flow.energy(0, 1)), Verdict.BAD,
							List.of(new ItemMotion(BATTERY, 2, true))),
					new Step(boxLine("west"), 2, 2, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(new ItemMotion(BATTERY, 2, true), new ItemMotion(BATTERY, 2, false))),
					new Step(boxLine("west"), 3, 1, List.of(Flow.energy(0, 2)), Verdict.NONE,
							List.of(new ItemMotion(BATTERY, 3, true),
									new ItemMotion("minecraft:leather", 3, true),
									new ItemMotion(CIRCUIT, 3, true),
									new ItemMotion(ENERGY_PACK, 3, false))),
					new Step(boxLine("west"), 2, 2, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(new ItemMotion(ENERGY_PACK, 2, true), new ItemMotion(ENERGY_PACK, 2, false),
									new ItemMotion(BATTERY_POUCH, 2, true),
									new ItemMotion(BATTERY_POUCH, 2, false))),
					new Step(List.of(batteryBox("west"), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							machine(ELECTRIC_FURNACE, 4, 0, false, true)), 0, 2, List.of(Flow.energy(0, 2)),
							Verdict.GOOD,
							List.of(new ItemMotion(BATTERY, 0, true), new ItemMotion("minecraft:raw_iron", 2, true),
									new ItemMotion("minecraft:iron_ingot", 2, false)))));

	/**
	 * The jetpack crafted around an energy pack (0); a charging station with no cable, red and empty (bad);
	 * the station fed by a generator charging the jetpack; the jetpack flying from the station up a stone
	 * pillar; then gliding down from the pillar to the grass. Indices of the line: 0 station, 1 generator,
	 * 2 cable, 3..5 the pillar bottom to top, 6 the grass it lands on.
	 */
	public static final BlueprintDef JETPACK_FLIGHT = new BlueprintDef("jetpack",
			List.of(JETPACK),
			List.of(
					new Step(List.of(table(0, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(ENERGY_PACK, 0, true), new ItemMotion(BATTERY, 0, true),
									new ItemMotion(COIL, 0, true), new ItemMotion(CIRCUIT, 0, true),
									new ItemMotion(JETPACK, 0, false))),
					new Step(List.of(pad("empty", null)), 0, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion(JETPACK, 0, true))),
					new Step(padLine("charging", "ready"), 0, 2, List.of(Flow.energy(1, 0)), Verdict.GOOD,
							List.of(new ItemMotion(JETPACK, 0, true))),
					new Step(flightField(false), 5, 3, List.of(Flow.energy(1, 0), Flow.item(0, 5, JETPACK)),
							Verdict.GOOD, List.of()),
					new Step(flightField(true), 6, 4, List.of(Flow.item(5, 6, JETPACK)), Verdict.NONE,
							List.of())));

	/**
	 * The electric bow and then the electric saber crafted on a table (3, number 1); the battery box fed by
	 * a generator charging both; on a shooting range, an empty bow whose arrow drops one block ahead (bad);
	 * the charged bow hitting the target, which lights up. Indices of the line: 0 generator, 1 cable,
	 * 2 box, 3 table; of the range: 0..4 grass, 0 the archer's spot, 5 the target on the last grass.
	 */
	public static final BlueprintDef ELECTRIC_WEAPONS = new BlueprintDef("electric_weapons",
			List.of(ELECTRIC_BOW, ELECTRIC_SABER),
			List.of(
					new Step(List.of(table(4, 1)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:bow", 0, true), new ItemMotion(BATTERY, 0, true),
									new ItemMotion(COIL, 0, true), new ItemMotion(CIRCUIT, 0, true),
									new ItemMotion(ELECTRIC_BOW, 0, false))),
					new Step(List.of(table(4, 1)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:diamond_sword", 0, true),
									new ItemMotion(BATTERY, 0, true), new ItemMotion(COIL, 0, true),
									new ItemMotion(CIRCUIT, 0, true), new ItemMotion(ELECTRIC_SABER, 0, false))),
					new Step(boxLine("west"), 2, 2, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(new ItemMotion(ELECTRIC_BOW, 2, true), new ItemMotion(ELECTRIC_BOW, 2, false),
									new ItemMotion(ELECTRIC_SABER, 2, true),
									new ItemMotion(ELECTRIC_SABER, 2, false))),
					new Step(range(false), 0, 3, List.of(Flow.item(0, 1, "minecraft:arrow")), Verdict.BAD,
							List.of(new ItemMotion(ELECTRIC_BOW, 0, true))),
					new Step(range(true), 5, 4, List.of(Flow.item(0, 5, "minecraft:arrow")), Verdict.GOOD,
							List.of(new ItemMotion(ELECTRIC_BOW, 0, true)))));

	/**
	 * The diamond-tipped drill crafted around the electric drill (0); that drill with Silk Touch on taking
	 * coal ore whole (0); the tipped chainsaw and shovel taking leaves (2) and a grass block (3) whole; a
	 * plain electric hoe leaving dry farmland (1, bad); the tipped hoe leaving it wet; then the netherite
	 * drill head made on a table (0) and fitted on the smithing table (1) with the template.
	 */
	public static final BlueprintDef DRILL_TIPS = new BlueprintDef("drill_tips",
			List.of(DRILL_DIAMOND, DRILL_NETHERITE, DRILL_HEAD, DRILL_TEMPLATE, CHAINSAW_DIAMOND, SHOVEL_DIAMOND,
					HOE_DIAMOND),
			List.of(
					new Step(List.of(table(0, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:electric_drill", 0, true),
									new ItemMotion("alaindustrial:diamond_dust", 0, true),
									new ItemMotion("alaindustrial:invar_ingot", 0, true),
									new ItemMotion("alaindustrial:silver_gear", 0, true),
									new ItemMotion(DRILL_DIAMOND, 0, false))),
					new Step(List.of(at("minecraft:coal_ore", 0, 0, 0)), 0, 2, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(DRILL_DIAMOND, 0, true),
									new ItemMotion("minecraft:coal_ore", 0, false))),
					new Step(List.of(at("minecraft:coal_ore", 0, 0, 0), at("minecraft:oak_log", 2, 0, 0),
							at("minecraft:oak_leaves", 2, 1, 0), at("minecraft:grass_block", 4, 0, 0)), 2, 3,
							List.of(), Verdict.NONE,
							List.of(new ItemMotion(CHAINSAW_DIAMOND, 2, true),
									new ItemMotion("minecraft:oak_leaves", 2, false),
									new ItemMotion(SHOVEL_DIAMOND, 3, true),
									new ItemMotion("minecraft:grass_block", 3, false))),
					new Step(field("0"), 1, 4, List.of(), Verdict.BAD,
							List.of(new ItemMotion("alaindustrial:electric_hoe", 1, true))),
					new Step(field("7"), 1, 4, List.of(), Verdict.GOOD, List.of(new ItemMotion(HOE_DIAMOND, 1, true))),
					new Step(List.of(table(0, 0), at(SMITHING_TABLE, 2, 0, 0)), 1, 5,
							List.of(Flow.item(0, 1, DRILL_HEAD)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:netherite_scrap", 0, true),
									new ItemMotion("alaindustrial:netherite_alloy_ingot", 0, true),
									new ItemMotion("alaindustrial:electrum_gear", 0, true),
									new ItemMotion(DRILL_TEMPLATE, 1, true), new ItemMotion(DRILL_DIAMOND, 1, true),
									new ItemMotion(DRILL_NETHERITE, 1, false)))));

	private PowerGearBlueprints() {
	}

	private static Placement at(String id, int x, int y, int z) {
		return new Placement(id, x, y, z, Map.of());
	}

	private static Placement table(int x, int z) {
		return at(TABLE, x, 0, z);
	}

	/** 0 reinforced store taking energy on its west face, 1 geothermal generator, 2 gold cable, 3 table. */
	private static List<Placement> crystalLine() {
		return List.of(cesu("west"), geothermal(0, true, false), cableOf(GOLD_CABLE, 1, 0, 0, "west", "east"),
				table(4, 1));
	}

	/** 0 generator, 1 cable, 2 battery box whose input faces {@code facing}, 3 table. */
	private static List<Placement> boxLine(String facing) {
		return List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), batteryBox(facing),
				table(4, 1));
	}

	/** A charging station at (4, 0, 0) in {@code state}, switching to {@code lateState} (null: no change). */
	private static Placement pad(String state, String lateState) {
		return new Placement(CHARGE_PAD, 4, 0, 0, Map.of("state", state),
				lateState == null ? Map.of() : Map.of("state", lateState));
	}

	/** 0 station, 1 generator, 2 cable meeting the low station. */
	private static List<Placement> padLine(String state, String lateState) {
		return List.of(pad(state, lateState), generatorAt(2), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east",
				"east_low"));
	}

	/** {@link #padLine} with the station full, 3..5 a stone pillar behind it, 6 grass when {@code landing}. */
	private static List<Placement> flightField(boolean landing) {
		List<Placement> all = new ArrayList<>(padLine("ready", null));
		for (int y = 0; y <= 2; y++) {
			all.add(stone(0, y, -2));
		}
		if (landing) {
			all.add(at("minecraft:grass_block", 2, 0, -2));
		}
		return all;
	}

	/** 0..4 a row of grass, 5 a target on the last one; {@code hit} lights the target late in the step. */
	private static List<Placement> range(boolean hit) {
		List<Placement> all = new ArrayList<>();
		for (int x = 0; x <= 4; x++) {
			all.add(at("minecraft:grass_block", x, 0, 0));
		}
		all.add(new Placement("minecraft:target", 4, 1, 0, Map.of("power", "0"),
				hit ? Map.of("power", "15") : Map.of()));
		return all;
	}

	/** 0 a water-free dirt block beside, 1 farmland of {@code moisture} (0 dry, 7 wet). */
	private static List<Placement> field(String moisture) {
		return List.of(at("minecraft:dirt", 0, 0, 0),
				new Placement("minecraft:farmland", 1, 0, 0, Map.of("moisture", moisture)));
	}
}
