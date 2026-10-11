package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import dev.alaindustrial.entity.ChestBoatVariants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Blueprints about storing and moving stock (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class StorageBlueprints {

	private static final String MOD = "alaindustrial:";

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String OAK_BOAT = "minecraft:oak_boat";
	private static final String CHERRY_BOAT = "minecraft:cherry_boat";
	private static final String BAMBOO_RAFT = "minecraft:bamboo_raft";
	private static final String OAK_CHEST_BOAT = "minecraft:oak_chest_boat";
	private static final String BEACON = "minecraft:beacon";

	private static final String OAK_IRON_CHEST_BOAT = MOD + "oak_iron_chest_boat";
	private static final String CHERRY_DIAMOND_CHEST_BOAT = MOD + "cherry_diamond_chest_boat";
	private static final String BAMBOO_IRON_CHEST_RAFT = MOD + "bamboo_iron_chest_raft";
	private static final String OAK_SHIELDING_CHEST_BOAT = MOD + "oak_shielding_chest_boat";

	private static final String TIN_INGOT = MOD + "tin_ingot";
	private static final String URANIUM_BLOCK = MOD + "uranium_block";
	private static final String TIN_STAIRS = MOD + "tin_stairs";
	private static final String TIN_SLAB = MOD + "tin_slab";
	private static final String TIN_WALL = MOD + "tin_wall";
	private static final String SILVER_STAIRS = MOD + "silver_stairs";
	private static final String SILVER_SLAB = MOD + "silver_slab";
	private static final String SILVER_WALL = MOD + "silver_wall";
	private static final String TEMPERED_IRON_FENCE = MOD + "tempered_iron_fence";
	private static final String SILVER_PLATE = MOD + "silver_plate";
	private static final String SILVER_PLATE_BLOCK = MOD + "silver_plate_block";
	private static final String TEMPERED_IRON_PLATE = MOD + "tempered_iron_plate";
	private static final String TEMPERED_IRON_PLATE_BLOCK = MOD + "tempered_iron_plate_block";

	/** The chest tiers in id spelling, the smallest hold first. */
	private static final String[] CHEST_TIERS = {"iron", "silver", "gold", "electrum", "diamond", "shielding"};

	/** The metals that come as stairs, a slab and a wall, in subject order. */
	private static final String[] DECOR_METALS = {"tin", "silver", "nickel", "uranium", "palladium", "cupronickel",
			"invar", "netherite_alloy", "shielding_alloy", "tempered_iron"};

	/** The storage blocks of the third step's row; uranium is shown apart from them. */
	private static final String[] STORAGE_ROW = {TIN_BLOCK, MOD + "silver_block", MOD + "palladium_block",
			MOD + "cupronickel_block", MOD + "netherite_alloy_block", MOD + "shielding_alloy_block",
			MOD + "tempered_iron_block"};

	/**
	 * All steps at the crafting table (0, position 1): a plain oak boat and an iron chest give the oak boat
	 * with an iron chest; a cherry boat and a diamond chest give a much bigger hold; a bamboo raft takes
	 * an iron chest the same way (GOOD); a vanilla chest boat plus the chest gives nothing, a plain boat
	 * is needed (BAD); the shielding chest boat keeps its cargo from radiating (GOOD).
	 */
	public static final BlueprintDef CHEST_BOATS = new BlueprintDef("chest_boats",
			chestBoatSubjects(),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(OAK_BOAT, 0, true), new ItemMotion(IRON_CHEST, 0, true),
									new ItemMotion(OAK_IRON_CHEST_BOAT, 0, false))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(CHERRY_BOAT, 0, true), new ItemMotion(DIAMOND_CHEST, 0, true),
									new ItemMotion(CHERRY_DIAMOND_CHEST_BOAT, 0, false))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(BAMBOO_RAFT, 0, true), new ItemMotion(IRON_CHEST, 0, true),
									new ItemMotion(BAMBOO_IRON_CHEST_RAFT, 0, false))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.BAD,
							List.of(new ItemMotion(OAK_CHEST_BOAT, 0, true), new ItemMotion(IRON_CHEST, 0, true))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(OAK_BOAT, 0, true), new ItemMotion(SHIELDING_CHEST, 0, true),
									new ItemMotion(OAK_SHIELDING_CHEST_BOAT, 0, false)))));

	/**
	 * Nine tin ingots into a block of tin at the crafting table (0, position 1); the block back into nine
	 * ingots (GOOD); storage blocks of the mod's metals in a row at x 0..6 (position 2 on the middle block);
	 * the uranium block alone (0, position 3) with the Geiger counter going in and clicking, as it would
	 * in any plain chest; the shielding chest in its place (position 4) with the uranium block inside,
	 * silent (GOOD).
	 */
	public static final BlueprintDef STORAGE_BLOCKS = new BlueprintDef("storage_blocks",
			List.of(TIN_BLOCK, MOD + "silver_block", URANIUM_BLOCK, MOD + "palladium_block",
					MOD + "cupronickel_block", MOD + "netherite_alloy_block", MOD + "shielding_alloy_block",
					MOD + "tempered_iron_block"),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							then(batch(9, TIN_INGOT, 0, true), List.of(new ItemMotion(TIN_BLOCK, 0, false)))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(TIN_BLOCK, 0, true), new ItemMotion(TIN_INGOT, 0, false))),
					new Step(storageRow(), 3, 2, List.of(), Verdict.NONE, List.of()),
					new Step(List.of(new Placement(URANIUM_BLOCK, 0, 0, 0, Map.of())), 0, 3, List.of(),
							Verdict.NONE, List.of(new ItemMotion(GEIGER_COUNTER, 0, true))),
					new Step(List.of(new Placement(SHIELDING_CHEST, 0, 0, 0, Map.of("facing", "south"))), 0, 4,
							List.of(), Verdict.GOOD, List.of(new ItemMotion(URANIUM_BLOCK, 0, true)))));

	/**
	 * Six blocks of tin into four stairs, three into six slabs, six into six walls, all at the crafting
	 * table (0, position 1); the cut forms of a real build: silver stairs (0, position 2), a silver slab
	 * (1), two joined silver walls (2, 3) and two tempered iron fence pieces (4, 5); a beacon (1) on a 3x3
	 * base of tin blocks with tin stairs in its front middle cell (0, position 3) stays dark (BAD); the
	 * same base with a block of tin in that cell (0, position 4) is whole and the beacon works (GOOD).
	 */
	public static final BlueprintDef DECOR_FORMS = new BlueprintDef("decor_forms",
			decorSubjects(),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							then(batch(6, TIN_BLOCK, 0, true), batch(4, TIN_STAIRS, 0, false))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							then(batch(3, TIN_BLOCK, 0, true), batch(6, TIN_SLAB, 0, false))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							then(batch(6, TIN_BLOCK, 0, true), batch(6, TIN_WALL, 0, false))),
					new Step(decorBuild(), 0, 2, List.of(), Verdict.NONE, List.of()),
					new Step(beaconBase(true), 0, 3, List.of(), Verdict.BAD, List.of()),
					new Step(beaconBase(false), 0, 4, List.of(), Verdict.GOOD, List.of())));

	/**
	 * Four silver plates into a plating block at the crafting table (0, position 1); the block back into
	 * four plates (GOOD); two cladding faces three blocks wide and two high, silver at x 0..2 (position 2)
	 * and tempered iron at x 4..6; four tempered iron plates into their plating block (GOOD).
	 */
	public static final BlueprintDef PLATE_CLADDING = new BlueprintDef("plate_cladding",
			List.of(SILVER_PLATE_BLOCK, TEMPERED_IRON_PLATE_BLOCK),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							then(batch(4, SILVER_PLATE, 0, true),
									List.of(new ItemMotion(SILVER_PLATE_BLOCK, 0, false)))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.GOOD,
							then(List.of(new ItemMotion(SILVER_PLATE_BLOCK, 0, true)),
									batch(4, SILVER_PLATE, 0, false))),
					new Step(join(cladding(SILVER_PLATE_BLOCK, 0), cladding(TEMPERED_IRON_PLATE_BLOCK, 4)), 0, 2,
							List.of(), Verdict.NONE, List.of()),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.GOOD,
							then(batch(4, TEMPERED_IRON_PLATE, 0, true),
									List.of(new ItemMotion(TEMPERED_IRON_PLATE_BLOCK, 0, false))))));

	private StorageBlueprints() {
	}

	/** A crafting table at the origin, the workbench of every recipe step. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	/** {@code count} motions of {@code id} on block {@code index}, inputs when {@code into}, outputs else. */
	private static List<ItemMotion> batch(int count, String id, int index, boolean into) {
		List<ItemMotion> all = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			all.add(new ItemMotion(id, index, into));
		}
		return all;
	}

	/** {@code head} followed by {@code tail}: the motions of one step, in playing order. */
	private static List<ItemMotion> then(List<ItemMotion> head, List<ItemMotion> tail) {
		List<ItemMotion> all = new ArrayList<>(head);
		all.addAll(tail);
		return all;
	}

	/** The storage blocks of the mod in a row at x 0..6 (indices 0..6), tin first. */
	private static List<Placement> storageRow() {
		List<Placement> row = new ArrayList<>();
		for (int i = 0; i < STORAGE_ROW.length; i++) {
			row.add(new Placement(STORAGE_ROW[i], i, 0, 0, Map.of()));
		}
		return row;
	}

	/** Silver stairs (0), a silver slab (1), two joined silver walls (2, 3) and a run of fence (4, 5). */
	private static List<Placement> decorBuild() {
		return List.of(stairs(SILVER_STAIRS, 0), slab(SILVER_SLAB, 1), wall(SILVER_WALL, 2, "none", "low"),
				wall(SILVER_WALL, 3, "low", "none"), fence(TEMPERED_IRON_FENCE, 4, false, true),
				fence(TEMPERED_IRON_FENCE, 5, true, false));
	}

	/**
	 * A beacon (1) on a 3x3 base of tin blocks; the base's front middle cell (0) holds tin stairs when
	 * {@code flawed}, a block of tin otherwise; 2..9 the other base cells.
	 */
	private static List<Placement> beaconBase(boolean flawed) {
		List<Placement> all = new ArrayList<>(List.of(flawed
				? new Placement(TIN_STAIRS, 1, 0, 2, Map.of("facing", "north", "half", "bottom", "shape", "straight"))
				: new Placement(TIN_BLOCK, 1, 0, 2, Map.of()), new Placement(BEACON, 1, 1, 1, Map.of())));
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				if (x != 1 || z != 2) {
					all.add(new Placement(TIN_BLOCK, x, 0, z, Map.of()));
				}
			}
		}
		return all;
	}

	/** Bottom stairs at ({@code x}, 0, 0) with the step toward the viewer. */
	private static Placement stairs(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of("facing", "north", "half", "bottom", "shape", "straight"));
	}

	/** A bottom slab at ({@code x}, 0, 0). */
	private static Placement slab(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of("type", "bottom"));
	}

	/** A wall at ({@code x}, 0, 0) with its post and the given west and east sides. */
	private static Placement wall(String id, int x, String west, String east) {
		return new Placement(id, x, 0, 0,
				Map.of("up", "true", "north", "none", "south", "none", "west", west, "east", east));
	}

	/** A fence post at ({@code x}, 0, 0) joined toward the given sides. */
	private static Placement fence(String id, int x, boolean west, boolean east) {
		return new Placement(id, x, 0, 0, Map.of("west", Boolean.toString(west), "east", Boolean.toString(east)));
	}

	/** A cladding face of {@code id}, three blocks wide from {@code x} and two blocks high. */
	private static List<Placement> cladding(String id, int x) {
		List<Placement> face = new ArrayList<>();
		for (int dx = 0; dx < 3; dx++) {
			for (int y = 0; y <= 1; y++) {
				face.add(new Placement(id, x + dx, y, 0, Map.of()));
			}
		}
		return face;
	}

	/** All 66 chest boats: every boat wood crossed with every chest tier, the bamboo rafts last. */
	private static List<String> chestBoatSubjects() {
		List<String> all = new ArrayList<>();
		// The woods come from the boat table itself: each Minecraft line has its own set (poplar is 26.3 only).
		for (ChestBoatVariants.Wood wood : ChestBoatVariants.Wood.values()) {
			if (wood.raft()) {
				continue;
			}
			for (String tier : CHEST_TIERS) {
				all.add(MOD + wood.path() + "_" + tier + "_chest_boat");
			}
		}
		for (String tier : CHEST_TIERS) {
			all.add(MOD + "bamboo_" + tier + "_chest_raft");
		}
		return all;
	}

	/** Stairs, slab and wall of every decor metal, three per metal, the tempered iron fence last. */
	private static List<String> decorSubjects() {
		List<String> all = new ArrayList<>();
		for (String metal : DECOR_METALS) {
			all.add(MOD + metal + "_stairs");
			all.add(MOD + metal + "_slab");
			all.add(MOD + metal + "_wall");
		}
		all.add(TEMPERED_IRON_FENCE);
		return all;
	}
}
