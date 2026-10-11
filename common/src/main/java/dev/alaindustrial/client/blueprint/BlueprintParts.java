package dev.alaindustrial.client.blueprint;

import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.List;
import java.util.Map;

/**
 * Block ids and placement helpers shared by the blueprint groups (MOD-797): one helper per recurring piece of
 * a scene (a machine facing the viewer, a cable with given arms, a battery box at a given face), so a step
 * reads as the arrangement it shows.
 */
final class BlueprintParts {

	static final String GENERATOR = "alaindustrial:generator";
	static final String COPPER_CABLE = "alaindustrial:copper_cable";
	static final String ELECTRIC_FURNACE = "alaindustrial:electric_furnace";
	static final String SOLAR_PANEL = "alaindustrial:solar_panel";
	static final String BATTERY_BOX = "alaindustrial:battery_box";
	static final String MACERATOR = "alaindustrial:macerator";
	static final String COMPRESSOR = "alaindustrial:compressor";
	static final String EXTRACTOR = "alaindustrial:extractor";
	static final String TIN_CABLE = "alaindustrial:tin_cable";
	static final String INSULATED_COPPER_CABLE = "alaindustrial:insulated_copper_cable";
	static final String GOLD_CABLE = "alaindustrial:gold_cable";
	static final String ELECTRUM_CABLE = "alaindustrial:electrum_cable";
	static final String CESU = "alaindustrial:cesu";
	static final String CHARGE_PAD = "alaindustrial:charge_pad";
	static final String IRON_FURNACE = "alaindustrial:iron_furnace";
	static final String ALLOY_SMELTER = "alaindustrial:alloy_smelter";
	static final String GEOTHERMAL_GENERATOR = "alaindustrial:geothermal_generator";
	static final String CANNING_MACHINE = "alaindustrial:canning_machine";
	static final String INSULATED_TIN_CABLE = "alaindustrial:insulated_tin_cable";
	static final String INSULATED_GOLD_CABLE = "alaindustrial:insulated_gold_cable";
	static final String INSULATED_ELECTRUM_CABLE = "alaindustrial:insulated_electrum_cable";
	static final String DAYLIGHT_SOLAR_PANEL = "alaindustrial:daylight_solar_panel";
	static final String MOONLIT_SOLAR_PANEL = "alaindustrial:moonlit_solar_panel";
	static final String RADIANT_SOLAR_PANEL = "alaindustrial:radiant_solar_panel";
	static final String TIN_BLOCK = "alaindustrial:tin_block";
	static final String ITEM_PIPE = "alaindustrial:item_pipe";
	static final String ITEM_PIPE_ADVANCED = "alaindustrial:item_pipe_advanced";
	static final String PUMP = "alaindustrial:pump";
	static final String FLUID_PIPE = "alaindustrial:fluid_pipe";
	static final String WIND_MILL = "alaindustrial:wind_mill";
	static final String WATER_MILL = "alaindustrial:water_mill";
	static final String LIGHTNING_ROD = "alaindustrial:lightning_rod_generator";
	static final String PIEZO_PLATE = "alaindustrial:piezo_plate";
	static final String FLUID_TANK = "alaindustrial:fluid_tank";
	static final String FLUID_TANK_ADVANCED = "alaindustrial:fluid_tank_advanced";
	static final String GALVANIC_BATH = "alaindustrial:galvanic_bath";
	static final String ELECTRIC_HEATER = "alaindustrial:electric_heater";
	static final String VULCANIZER = "alaindustrial:vulcanizer";
	static final String BLOCK_BREAKER = "alaindustrial:block_breaker";
	static final String ASSEMBLER = "alaindustrial:assembler";
	static final String STORAGE_MODULE = "alaindustrial:storage_module";

	static final String POLYMERIZER = "alaindustrial:polymerizer";
	static final String RECYCLER = "alaindustrial:recycler";
	static final String SAWMILL = "alaindustrial:sawmill";
	static final String THERMAL_CENTRIFUGE = "alaindustrial:thermal_centrifuge";
	static final String COMPONENT_REPAIR_BENCH = "alaindustrial:component_repair_bench";
	static final String ENERGY_CONDENSER = "alaindustrial:energy_condenser";
	static final String FERMENTER = "alaindustrial:fermenter";
	static final String FLUID_PIPE_ADVANCED = "alaindustrial:fluid_pipe_advanced";
	static final String REINFORCED_FLUID_PIPE = "alaindustrial:reinforced_fluid_pipe";
	static final String HIGH_ALTITUDE_WIND_MILL = "alaindustrial:high_altitude_wind_mill";
	static final String STORM_WIND_MILL = "alaindustrial:storm_wind_mill";
	static final String SPRINKLER = "alaindustrial:sprinkler";
	static final String MOB_REPELLER = "alaindustrial:mob_repeller";
	static final String REACTOR_PORT = "alaindustrial:reactor_port";
	static final String REACTOR_CASING = "alaindustrial:reactor_casing";
	static final String FUEL_ROD_ASSEMBLY = "alaindustrial:fuel_rod_assembly";

	static final String IRON_CHEST = "alaindustrial:iron_chest";
	static final String SILVER_CHEST = "alaindustrial:silver_chest";
	static final String GOLD_CHEST = "alaindustrial:gold_chest";
	static final String ELECTRUM_CHEST = "alaindustrial:electrum_chest";
	static final String DIAMOND_CHEST = "alaindustrial:diamond_chest";
	static final String SHIELDING_CHEST = "alaindustrial:shielding_chest";
	static final String IRRADIATED_SOIL = "alaindustrial:irradiated_soil";
	static final String GEIGER_COUNTER = "alaindustrial:geiger_counter";
	static final String MOB_REPELLER_MV = "alaindustrial:mob_repeller_mv";
	static final String MOB_REPELLER_HV = "alaindustrial:mob_repeller_hv";
	static final String TRELLIS = "alaindustrial:trellis";
	static final String GARDEN_DRONE_STATION = "alaindustrial:garden_drone_station";
	static final String INCUBATOR = "alaindustrial:incubator";
	static final String INCUBATOR_DOME = "alaindustrial:incubator_dome";
	static final String WRENCH = "alaindustrial:wrench";
	static final String NETWORK_ANALYZER = "alaindustrial:network_analyzer";

	private BlueprintParts() {
	}

	static Placement generator(boolean lit) {
		return new Placement(GENERATOR, 0, 0, 0, Map.of("facing", "south", "lit", Boolean.toString(lit)));
	}

	static Placement generatorLightingUp() {
		return new Placement(GENERATOR, 0, 0, 0, Map.of("facing", "south", "lit", "false"), Map.of("lit", "true"));
	}

	static Placement furnaceLightingUp() {
		return new Placement(ELECTRIC_FURNACE, 2, 0, 0, Map.of("facing", "south", "lit", "false"),
				Map.of("lit", "true"));
	}

	static Placement solarPanel() {
		return new Placement(SOLAR_PANEL, 0, 0, 0, Map.of());
	}

	/** A battery box whose input (front) faces {@code facing}; the opposite face is its output. */
	static Placement batteryBox(String facing) {
		return new Placement(BATTERY_BOX, 2, 0, 0, Map.of("facing", facing));
	}

	/** A machine facing the viewer; {@code litLater} lights it up once its input went in. */
	static Placement machine(String id, int x, int z, boolean lit, boolean litLater) {
		Map<String, String> late = litLater ? Map.of("lit", "true") : Map.of();
		return new Placement(id, x, 0, z, Map.of("facing", "south", "lit", Boolean.toString(lit)), late);
	}

	/** A copper cable at ({@code x}, 0, {@code z}) with arms west and east; {@code westLow} meets a slab. */
	static Placement cableAt(int x, int z, boolean west, boolean westLow, boolean east, boolean eastLow) {
		return new Placement(COPPER_CABLE, x, 0, z, Map.of("west", Boolean.toString(west),
				"west_low", Boolean.toString(westLow), "east", Boolean.toString(east),
				"east_low", Boolean.toString(eastLow)));
	}

	/** A copper cable at ({@code x}, {@code y}, {@code z}) with arms only toward {@code arms}. */
	static Placement cableArms(int x, int y, int z, String... arms) {
		return cableOf(COPPER_CABLE, x, y, z, arms);
	}

	/** A cable of grade {@code id} at ({@code x}, {@code y}, {@code z}); each of {@code arms} is set true. */
	static Placement cableOf(String id, int x, int y, int z, String... arms) {
		Map<String, String> props = new java.util.HashMap<>();
		for (String arm : arms) {
			props.put(arm, "true");
		}
		return new Placement(id, x, y, z, props);
	}

	/** Generator, cable of {@code grade}, furnace; the cable is pointed at as item {@code position}. */
	static Step gradeStep(String grade, int position) {
		return new Step(List.of(generator(true), cableOf(grade, 1, 0, 0, "west", "east"),
				machine(ELECTRIC_FURNACE, 2, 0, true, false)), 1, position, 0, 2, Verdict.NONE);
	}

	/** Generator, insulated gold, {@code middle}, insulated gold, furnace: one piece of the line swapped. */
	static List<Placement> goldLine(String middle) {
		return List.of(generator(true), cableOf(INSULATED_GOLD_CABLE, 1, 0, 0, "west", "east"),
				cableOf(middle, 2, 0, 0, "west", "east"), cableOf(INSULATED_GOLD_CABLE, 3, 0, 0, "west", "east"),
				machine(ELECTRIC_FURNACE, 4, 0, true, false));
	}

	static Placement panelAt(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of());
	}

	/** A single vanilla chest at ({@code x}, 0, 0), latch toward the viewer. */
	static Placement chest(int x) {
		return new Placement("minecraft:chest", x, 0, 0, Map.of("facing", "south", "type", "single"));
	}

	/** The pump at (2, 0, 0) with its front toward {@code facing}. */
	static Placement pump(String facing, boolean lit, boolean litLater) {
		Map<String, String> late = litLater ? Map.of("lit", "true") : Map.of();
		return new Placement(PUMP, 2, 0, 0, Map.of("facing", facing, "lit", Boolean.toString(lit)), late);
	}

	/**
	 * {@code pump} first, then a small lava lake north of it (the block at index 2 is the one right in front
	 * of a north-facing pump), then {@code rest}: indices 0 pump, 1..4 lava, 5.. the rest in order.
	 */
	static List<Placement> lake(Placement pump, Placement... rest) {
		List<Placement> all = new java.util.ArrayList<>();
		all.add(pump);
		all.add(lava(1, -1));
		all.add(lava(2, -1));
		all.add(lava(3, -1));
		all.add(lava(2, -2));
		all.addAll(List.of(rest));
		return all;
	}

	static Placement lava(int x, int z) {
		return new Placement("minecraft:lava", x, 0, z, Map.of());
	}

	/** An item pipe at ({@code x}, 0, 0) with its west and east faces in the given modes (null: no arm). */
	static Placement pipe(String id, int x, String west, String east) {
		Map<String, String> props = new java.util.HashMap<>();
		if (west != null) {
			props.put("west_mode", west);
		}
		if (east != null) {
			props.put("east_mode", east);
		}
		return new Placement(id, x, 0, 0, props);
	}

	/** A fluid pipe east of the pump, at (3, 0, 0); its core is drawn unfilled (the fill colour needs the world). */
	static Placement fluidPipe(String west, String east) {
		Map<String, String> props = new java.util.HashMap<>();
		props.put("filled", "false");
		props.put("west_mode", west);
		if (east != null) {
			props.put("east_mode", east);
		}
		return new Placement(FLUID_PIPE, 3, 0, 0, props);
	}

	/** A wind turbine or water mill at the origin, face west, with its rotor or wheel installed. */
	static Placement mill(String id) {
		String part = id.equals(WIND_MILL) ? "alaindustrial:windmill_rotor" : "alaindustrial:water_mill_wheel";
		return new Placement(id, 0, 0, 0, Map.of("facing", "west")).holding(part);
	}

	/** The water mill feeding a battery box, its wheel (one block west) with flowing water around it. */
	static List<Placement> waterMill() {
		return List.of(mill(WATER_MILL), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), batteryBox("west"),
				water(-1, -1, 0), water(-1, 0, -1), water(-1, 0, 1));
	}

	/** {@link #waterMill()} with a stone block in the wheel's plane. */
	static List<Placement> blockedWaterMill() {
		List<Placement> blocks = new java.util.ArrayList<>(waterMill());
		blocks.add(stone(-1, 1, 1));
		return blocks;
	}

	static Placement water(int x, int y, int z) {
		return new Placement("minecraft:water", x, y, z, Map.of());
	}

	static Placement stone(int x, int y, int z) {
		return new Placement("minecraft:stone", x, y, z, Map.of());
	}

	/** The lightning rod generator at the origin; {@code tipped} shows its mast. */
	static Placement rod(boolean tipped, boolean tippedLater) {
		Map<String, String> late = tippedLater ? Map.of("tipped", "true") : Map.of();
		return new Placement(LIGHTNING_ROD, 0, 0, 0,
				Map.of("tipped", Boolean.toString(tipped), "lit", Boolean.toString(tipped)), late);
	}

	/** A piezo plate at ({@code x}, {@code y}, 0), pressed later in the step. */
	static Placement plate(int x, int y) {
		return new Placement(PIEZO_PLATE, x, y, 0, Map.of("powered", "false"), Map.of("powered", "true"));
	}

	/** A battery box at ({@code x}, {@code y}, {@code z}) taking energy on its west face. */
	static Placement boxAt(int x, int y, int z) {
		return new Placement(BATTERY_BOX, x, y, z, Map.of("facing", "west"));
	}

	/** A tank at the origin, three quarters full of water. */
	static Placement tankAt(String id) {
		return new Placement(id, 0, 0, 0, Map.of()).holding("fluid:minecraft:water");
	}

	static Placement bath(boolean litLater) {
		Map<String, String> late = litLater ? Map.of("lit", "true") : Map.of();
		return new Placement(GALVANIC_BATH, 2, 0, 0, Map.of("facing", "south", "lit", "false"), late);
	}

	/** Tank, fluid pipe, galvanic bath, cable, generator. */
	static List<Placement> bathLine(String tank, boolean litLater) {
		return List.of(tankAt(tank), fluidPipeAt(1, "extract", "insert"), bath(litLater),
				cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"), generatorAt(4));
	}

	static List<ItemMotion> bathItems() {
		return List.of(new ItemMotion("minecraft:string", 2, true),
				new ItemMotion("alaindustrial:silver_dust", 2, true),
				new ItemMotion("alaindustrial:flux_thread", 2, false));
	}

	/** An electric heater at the origin; {@code warms} heats it up to glowing during the step. */
	static Placement heater(boolean warms) {
		Map<String, String> late = warms ? Map.of("glow", "glowing", "drawing", "true") : Map.of();
		return new Placement(ELECTRIC_HEATER, 0, 0, 0, Map.of("facing", "south", "glow", "cold", "drawing", "false"),
				late);
	}

	/** The block breaker facing west at cobblestone, with a stone pickaxe when {@code tooled}. */
	static List<Placement> breaker(boolean tooled) {
		Placement machine = new Placement(BLOCK_BREAKER, 0, 0, 0, Map.of("facing", "west", "lit", "false"),
				tooled ? Map.of("lit", "true") : Map.of());
		return List.of(tooled ? machine.holding("minecraft:stone_pickaxe") : machine,
				new Placement("minecraft:cobblestone", -1, 0, 0, Map.of()),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	/** The assembler with a storage module west of it, {@code moduleZ} blocks toward the back (-1: a corner). */
	static List<Placement> assembler(int moduleZ) {
		return List.of(new Placement(ASSEMBLER, 0, 0, 0, Map.of("facing", "south")),
				new Placement(STORAGE_MODULE, -1, 0, moduleZ, Map.of()),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	/** A fluid pipe at ({@code x}, 0, 0), core unfilled. */
	static Placement fluidPipeAt(int x, String west, String east) {
		return new Placement(FLUID_PIPE, x, 0, 0, Map.of("filled", "false", "west_mode", west, "east_mode", east));
	}

	static Placement generatorAt(int x) {
		return new Placement(GENERATOR, x, 0, 0, Map.of("facing", "south", "lit", "true"));
	}

	static Placement geothermal(int x, boolean lit, boolean litLater) {
		return machine(GEOTHERMAL_GENERATOR, x, 0, lit, litLater);
	}

	/** The reinforced store at (2, 0, 0) with its input (front) toward {@code facing}. */
	static Placement cesu(String facing) {
		return new Placement(CESU, 2, 0, 0, Map.of("facing", facing));
	}

	static Placement chargePad(String state) {
		return new Placement(CHARGE_PAD, 4, 0, 0, Map.of("state", state));
	}

	static Placement cable(boolean toGenerator, boolean toFurnace) {
		return new Placement(COPPER_CABLE, 1, 0, 0,
				Map.of("west", Boolean.toString(toGenerator), "east", Boolean.toString(toFurnace)));
	}

	static Placement furnace(boolean lit) {
		return new Placement(ELECTRIC_FURNACE, 2, 0, 0, Map.of("facing", "south", "lit", Boolean.toString(lit)));
	}

	/** A coal generator at ({@code x}, {@code y}, 0), burning. */
	static Placement generatorAt(int x, int y) {
		return new Placement(GENERATOR, x, y, 0, Map.of("facing", "south", "lit", "true"));
	}

	/** A sky or tempest mill at (0, 0, {@code z}) facing west, with a rotor when {@code rotor}. */
	static Placement t2Mill(String id, int z, boolean rotor) {
		Placement mill = new Placement(id, 0, 0, z, Map.of("facing", "west"));
		return rotor ? mill.holding("alaindustrial:windmill_rotor") : mill;
	}

	/** A storage module at ({@code x}, 0, 0) whose {@code joined} faces have merged with a neighbour. */
	static Placement storageModule(int x, String... joined) {
		Map<String, String> props = new java.util.HashMap<>();
		for (String face : joined) {
			props.put(face, "true");
		}
		return new Placement(STORAGE_MODULE, x, 0, 0, props);
	}

	/** A fluid pipe of grade {@code id} at ({@code x}, {@code y}, {@code z}); each pair is face mode, value. */
	static Placement fluidPipeOf(String id, int x, int y, int z, String... faceModes) {
		Map<String, String> props = new java.util.HashMap<>();
		props.put("filled", "false");
		for (int i = 0; i + 1 < faceModes.length; i += 2) {
			props.put(faceModes[i] + "_mode", faceModes[i + 1]);
		}
		return new Placement(id, x, y, z, props);
	}

	/** A portable tank at ({@code x}, {@code y}, {@code z}) holding {@code fluid} (null: empty). */
	static Placement tankWith(int x, int y, int z, String fluid) {
		Placement tank = new Placement(FLUID_TANK, x, y, z, Map.of());
		return fluid == null ? tank : tank.holding("fluid:" + fluid);
	}

	/** The fermenter at (0, 1, 0) facing the viewer. */
	static Placement fermenter(boolean lit, boolean litLater) {
		return new Placement(FERMENTER, 0, 1, 0, Map.of("facing", "south", "lit", Boolean.toString(lit)),
				litLater ? Map.of("lit", "true") : Map.of());
	}

	/** A recycler at (4, 0, 0) whose lamps show {@code kinds} kinds of junk once it runs. */
	static Placement recycler(String kinds) {
		return new Placement(RECYCLER, 4, 0, 0, Map.of("facing", "south", "lit", "false", "lamps", "0"),
				Map.of("lit", "true", "lamps", kinds));
	}

	/** The thermal centrifuge at (0, 1, 0); {@code runs} spins it up during the step. */
	static Placement centrifuge(boolean runs) {
		return new Placement(THERMAL_CENTRIFUGE, 0, 1, 0, Map.of("facing", "south", "lit", "false"),
				runs ? Map.of("lit", "true") : Map.of());
	}

	/** A sprinkler on a block at (0, 1, 0); {@code sprays} starts it during the step. */
	static Placement sprinkler(boolean sprays) {
		return new Placement(SPRINKLER, 0, 1, 0, Map.of("spraying", "false", "hanging", "false"),
				sprays ? Map.of("spraying", "true") : Map.of());
	}

	/** Three moist farmland blocks in front of the origin with wheat on them, ripening when {@code grows}. */
	static List<Placement> wheatField(boolean grows) {
		List<Placement> field = new java.util.ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			field.add(new Placement("minecraft:farmland", x, 0, 1, Map.of("moisture", "7")));
		}
		for (int x = -1; x <= 1; x++) {
			field.add(new Placement("minecraft:wheat", x, 1, 1, Map.of("age", "3"),
					grows ? Map.of("age", "7") : Map.of()));
		}
		return field;
	}

	/** {@code head} followed by {@code tail}. */
	static List<Placement> join(List<Placement> head, List<Placement> tail) {
		List<Placement> all = new java.util.ArrayList<>(head);
		all.addAll(tail);
		return all;
	}
}
