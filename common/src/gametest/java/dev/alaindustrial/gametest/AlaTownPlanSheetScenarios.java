package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.item.misc.PlanSheet;
import dev.alaindustrial.item.misc.TownHallPlanSheetItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import dev.alaindustrial.worldgen.AbandonedLabPlacer;
import dev.alaindustrial.worldgen.town.AlaTownLabPiece;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The town hall plan sheets in a generated town (MOD-859, R92 of MOD-824): every town of a built-in style, laid out
 * and placed the way world generation does it, holds all three sheets of its own style — sheet 1 in the town hall,
 * sheet 2 in a house, sheet 3 in the lab under the hall — each in a container of the town's own templates, as a loot
 * table that, opened, gives exactly that sheet.
 */
public final class AlaTownPlanSheetScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AlaTownPlanSheetScenarios::plainsTownHoldsItsThreeSheets,
						"ala_town_plains_holds_its_three_plan_sheets").ticks(200, 200),
				RosterEntry.of(AlaTownPlanSheetScenarios::desertTownHoldsItsThreeSheets,
						"ala_town_desert_holds_its_three_plan_sheets").ticks(200, 200),
				RosterEntry.of(AlaTownPlanSheetScenarios::everyLabHasABarrelForSheetThree,
						"ala_town_every_lab_has_a_barrel_for_sheet_three").ticks(20, 20),
				RosterEntry.of(AlaTownPlanSheetScenarios::craftedSheetsCarryTheirNumberAndStyle,
						"ala_town_plan_sheet_recipes_give_numbered_styled_sheets").ticks(20, 20));

		private Roster() {}
	}

	private AlaTownPlanSheetScenarios() {}

	/** @implements MOD-859-PLACE — see the class comment; plains. */
	public static void plainsTownHoldsItsThreeSheets(GameTestHelper helper) {
		holdsItsThreeSheets(helper, "plains", Biomes.PLAINS, new ChunkPos(5200, 5200));
	}

	/** @implements MOD-859-PLACE — see the class comment; desert. */
	public static void desertTownHoldsItsThreeSheets(GameTestHelper helper) {
		holdsItsThreeSheets(helper, "desert", Biomes.DESERT, new ChunkPos(5200, 5212));
	}

	/**
	 * @implements MOD-859-LAB-BARREL — sheet 3 goes into the lab's first barrel, so every lab a town can draw has one
	 *     (the `reactor` lab got one for it); its trapped chest keeps its own loot.
	 */
	public static void everyLabHasABarrelForSheetThree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String lab : AbandonedLabPlacer.LABS) {
			StructureTemplate template = LineStructures.templates(level)
					.getOrCreate(Industrialization.id("abandoned_lab/" + lab));
			if (template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.BARREL).isEmpty()) {
				helper.fail("lab '" + lab + "' has no barrel for the town hall plan's sheet 3");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-859-CRAFT — each sheet has its own full 3x3 grid (paper, lapis and two ingredients of its own);
	 *     the centre block picks the style: grass for the plains, any yellow sandstone for the desert. Every one of
	 *     the six grids crafts exactly one sheet with that number and style, and a centre that names no style (dirt,
	 *     sand) crafts nothing — a sheet without a style would be useless.
	 */
	public static void craftedSheetsCarryTheirNumberAndStyle(GameTestHelper helper) {
		Map<String, ItemStack> centres = Map.of(
				"plains", new ItemStack(Items.GRASS_BLOCK),
				"desert", new ItemStack(Items.CUT_SANDSTONE));
		List<ItemStack[]> sides = List.of(
				new ItemStack[] {new ItemStack(Items.IRON_INGOT), new ItemStack(Items.BRICKS)},
				new ItemStack[] {new ItemStack(Items.OAK_DOOR), new ItemStack(Items.BED.white())},
				new ItemStack[] {new ItemStack(Items.REDSTONE), new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get())});
		ServerLevel level = helper.getLevel();
		for (Map.Entry<String, ItemStack> centre : centres.entrySet()) {
			Identifier style = Industrialization.id(centre.getKey());
			for (int sheet = 1; sheet <= PlanSheetRules.SHEETS; sheet++) {
				CraftingInput input = sheetGrid(sides.get(sheet - 1), centre.getValue());
				String label = "sheet " + sheet + " of " + centre.getKey();
				ItemStack out = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
						.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
				PlanSheet got = TownHallPlanSheetItem.sheetOf(out);
				if (!out.is(ModContent.TOWN_HALL_PLAN_SHEET.get()) || out.getCount() != 1
						|| !new PlanSheet(style, sheet).equals(got)) {
					helper.fail(label + ": the grid crafted " + out + " carrying " + got);
					return;
				}
			}
		}
		for (ItemStack wrong : List.of(new ItemStack(Items.DIRT), new ItemStack(Items.SAND))) {
			CraftingInput noStyle = sheetGrid(sides.get(0), wrong);
			level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, noStyle, level).ifPresent(r ->
					helper.fail("a grid with " + wrong + " in the centre crafted "
							+ r.value().assemble(noStyle)));
		}
		helper.succeed();
	}

	/** The sheet grid {@code PLP / XSX / LYL}: paper, lapis, the sheet's own pair X and Y, the style block S. */
	private static CraftingInput sheetGrid(ItemStack[] pair, ItemStack centre) {
		ItemStack paper = new ItemStack(Items.PAPER);
		ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);
		return CraftingInput.of(3, 3, List.of(
				paper, lapis, paper,
				pair[0], centre, pair[0],
				lapis, pair[1], lapis));
	}

	private static void holdsItsThreeSheets(GameTestHelper helper, String style, ResourceKey<Biome> biome,
			ChunkPos chunk) {
		ServerLevel level = helper.getLevel();
		Identifier id = Industrialization.id("ala_town_" + style);
		Identifier styleId = Industrialization.id(style);
		ChunkGenerator generator = AlaTownSite.generator(level, biome);
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, id), generator, chunk,
				AlaTownScenarios.SEED);
		if (!start.isValid()) {
			helper.fail(id + ": no town on flat ground of its own biome");
			return;
		}
		AlaTownSite.place(level, start, generator, AlaTownScenarios.SEED);
		Map<Integer, List<BlockPos>> bySheet = sheetContainers(level, start.getBoundingBox(), style);
		for (int sheet = 1; sheet <= PlanSheetRules.SHEETS; sheet++) {
			List<BlockPos> found = bySheet.getOrDefault(sheet, List.of());
			if (found.size() != 1) {
				helper.fail(id + ": " + found.size() + " containers hold sheet " + sheet + " " + found
						+ ", expected 1");
				return;
			}
		}
		String where = checkPlaces(start, bySheet.get(1).getFirst(), bySheet.get(2).getFirst(),
				bySheet.get(3).getFirst());
		if (where != null) {
			helper.fail(id + ": " + where);
			return;
		}
		for (int sheet = 1; sheet <= PlanSheetRules.SHEETS; sheet++) {
			BlockPos pos = bySheet.get(sheet).getFirst();
			RandomizableContainerBlockEntity container = (RandomizableContainerBlockEntity) level.getBlockEntity(pos);
			container.unpackLootTable(null);
			if (!holdsSheet(container, styleId, sheet)) {
				helper.fail(id + ": the container at " + pos + " opened without sheet " + sheet + " of " + styleId);
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Every container in the town's box whose loot table is one of this style's plan-sheet tables, by sheet number.
	 * The test reads the containers it means to check; the core never does (ADR-010).
	 */
	private static Map<Integer, List<BlockPos>> sheetContainers(ServerLevel level, BoundingBox box, String style) {
		String prefix = "chests/ala_town/" + style + "/plan_sheet_";
		Map<Integer, List<BlockPos>> out = new HashMap<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (!(be instanceof RandomizableContainerBlockEntity container)) {
						continue;
					}
					ResourceKey<LootTable> table = container.getLootTable();
					if (table == null || !table.identifier().getNamespace().equals(Industrialization.MOD_ID)
							|| !table.identifier().getPath().startsWith(prefix)) {
						continue;
					}
					int sheet = Character.getNumericValue(table.identifier().getPath().charAt(prefix.length()));
					out.computeIfAbsent(sheet, k -> new ArrayList<>()).add(be.getBlockPos());
				}
			}
		}
		return out;
	}

	/** What is wrong with where the three sheets lie, or {@code null}: the hall, a house, the lab. */
	private static String checkPlaces(StructureStart start, BlockPos hall, BlockPos house, BlockPos lab) {
		if (!inPiece(start, hall, "/town_hall")) {
			return "sheet 1 at " + hall + " is not in the town hall";
		}
		if (!inPiece(start, house, "/capsule_house_")) {
			return "sheet 2 at " + house + " is not in a house";
		}
		AlaTownLabPiece labPiece = AlaTownSite.labs(start).getFirst();
		if (!labPiece.labBox().isInside(lab)) {
			return "sheet 3 at " + lab + " is not in the lab " + labPiece.labBox();
		}
		return null;
	}

	private static boolean inPiece(StructureStart start, BlockPos pos, String templatePart) {
		for (PoolElementStructurePiece piece : AlaTownSite.poolPieces(start)) {
			Identifier template = AlaTownSite.template(piece);
			if (template != null && template.getPath().contains(templatePart) && piece.getBoundingBox().isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	private static boolean holdsSheet(RandomizableContainerBlockEntity container, Identifier style, int sheet) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.is(ModContent.TOWN_HALL_PLAN_SHEET.get())
					&& new PlanSheet(style, sheet).equals(TownHallPlanSheetItem.sheetOf(stack))) {
				return true;
			}
		}
		return false;
	}
}
