package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.SettlementStyle;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Where a town's three hall plan sheets lie (MOD-859, R92 of MOD-824): every town holds all three sheets of its own
 * style. Each sheet is a loot table with one guaranteed entry, given at generation to a container the town's own
 * templates placed — so the sheet appears when a player first opens it, like the lab's loot, and nobody reads a
 * container to learn about it (ADR-010):
 * <ul>
 *   <li>sheet 1 — the town hall's chest (the hall is the piece that holds the settlement core; its ruin template
 *       carries exactly one ordinary chest, the «hall safe» of the owner's decision);</li>
 *   <li>sheet 2 — the barrel or chest of one house: a house is a piece with a former resident's plaque, and the one
 *       is picked by {@link PlanSheetRules#houseIndex} from the world seed and the hall's place, so every chunk of the
 *       town picks the same house;</li>
 *   <li>the house plans (MOD-862, R99) — every house's barrel or chest holds the plan of its own type, the sheet-2
 *       house's both;</li>
 *   <li>sheet 3 — the lab's first barrel (every built-in lab has one, so its trapped chest keeps its own
 *       loot).</li>
 * </ul>
 * The tables are {@code <ns>:chests/ala_town/<style>/plan_sheet_<k>} ({@link PlanSheetRules#lootTable}); a style
 * from a datapack brings its own. A place a style's templates lack is logged once per style and sheet, and the town
 * is placed without that sheet.
 */
final class TownPlanSheets {

	/** Styles and sheets already reported as having no place, so each gap is logged once. */
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

	private TownPlanSheets() {
	}

	/** Gives the containers of {@code pieces} that fall into {@code chunkBox} their plan-sheet loot tables. */
	static void place(WorldGenLevel level, StructureTemplateManager templates, List<StructurePiece> pieces,
			BoundingBox chunkBox, ResourceKey<SettlementStyle> style, RandomSource random) {
		String styleId = style.identifier().toString();
		Optional<BlockPos> hallChest = Optional.empty();
		BlockPos anchor = null;
		List<BlockPos> houseContainers = new ArrayList<>();
		List<BlockPos> houseOrigins = new ArrayList<>();
		List<Integer> houseTypes = new ArrayList<>();
		AlaTownLabPiece lab = null;
		for (StructurePiece piece : pieces) {
			if (piece instanceof AlaTownLabPiece labPiece) {
				lab = labPiece;
			} else if (piece instanceof PoolElementStructurePiece pool) {
				StructureTemplate template = rigidTemplate(templates, pool);
				if (template == null) {
					continue;
				}
				if (anchor == null && !cells(template, pool, ModContent.SETTLEMENT_CORE.get()).isEmpty()) {
					anchor = pool.getPosition();
					hallChest = cells(template, pool, Blocks.CHEST).stream().findFirst();
				} else if (TownPlaques.hasPlaque(templates, pool)) {
					container(template, pool).ifPresent(cell -> {
						houseContainers.add(cell);
						houseOrigins.add(pool.getPosition());
						houseTypes.add(houseType(pool));
					});
				}
			}
		}
		if (anchor == null) {
			report(styleId, "hall", "no piece holds a settlement core");
			return;
		}
		if (hallChest.isPresent()) {
			give(level, chunkBox, hallChest.get(), PlanSheetRules.lootTable(styleId, 1), random);
		} else {
			report(styleId, "sheet 1", "the town hall has no chest");
		}
		if (!houseContainers.isEmpty()) {
			BlockPos sheet2 = pickHouse(level.getSeed(), anchor, houseContainers, houseOrigins);
			for (int i = 0; i < houseContainers.size(); i++) {
				BlockPos cell = houseContainers.get(i);
				int type = houseTypes.get(i);
				boolean withSheet2 = cell.equals(sheet2);
				// MOD-862, R99: each house holds the plan of its own type; the one picked holds sheet 2 as well.
				String table = type > 0 ? PlanSheetRules.housePlanTable(styleId, type, withSheet2)
						: withSheet2 ? PlanSheetRules.lootTable(styleId, 2) : null;
				if (table != null) {
					give(level, chunkBox, cell, table, random);
				}
			}
		} else {
			report(styleId, "sheet 2", "no house with a plaque holds a barrel or a chest");
		}
		placeInLab(level, chunkBox, lab, styleId, random);
	}

	/** The container of the house that holds sheet 2: {@link PlanSheetRules#houseIndex} over houses sorted by place. */
	private static BlockPos pickHouse(long worldSeed, BlockPos anchor, List<BlockPos> containers,
			List<BlockPos> origins) {
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < containers.size(); i++) {
			order.add(i);
		}
		order.sort(Comparator.comparing(origins::get));
		return containers.get(order.get(PlanSheetRules.houseIndex(worldSeed, anchor.getX(), anchor.getY(),
				anchor.getZ(), containers.size())));
	}

	/** Sheet 3: the lab's first barrel; every built-in lab has one, its trapped chest keeps its own loot. */
	private static void placeInLab(WorldGenLevel level, BoundingBox chunkBox, AlaTownLabPiece lab, String styleId,
			RandomSource random) {
		if (lab == null) {
			report(styleId, "sheet 3", "the town has no lab");
			return;
		}
		List<BlockPos> barrels = lab.cellsOf(Blocks.BARREL);
		if (barrels.isEmpty()) {
			report(styleId, "sheet 3", "the lab has no barrel");
			return;
		}
		give(level, chunkBox, barrels.getFirst(), PlanSheetRules.lootTable(styleId, 3), random);
	}

	/** The capsule house type {@code pool}'s template is (MOD-862), or 0 for another building. */
	private static int houseType(PoolElementStructurePiece pool) {
		if (!(pool.getElement() instanceof SinglePoolElement single)) {
			return 0;
		}
		String path = single.getTemplateLocation().getPath();
		return PlanSheetRules.houseType(path.substring(path.lastIndexOf('/') + 1));
	}

	/** The template of a rigid single-template pool piece, or {@code null}. */
	private static StructureTemplate rigidTemplate(StructureTemplateManager templates, PoolElementStructurePiece pool) {
		if (!(pool.getElement() instanceof SinglePoolElement single)
				|| single.getProjection() != StructureTemplatePool.Projection.RIGID) {
			return null;
		}
		return templates.getOrCreate(single.getTemplateLocation());
	}

	/** The first barrel, else the first chest, of a house's template as placed. */
	private static Optional<BlockPos> container(StructureTemplate template, PoolElementStructurePiece pool) {
		List<BlockPos> barrels = cells(template, pool, Blocks.BARREL);
		return barrels.isEmpty() ? cells(template, pool, Blocks.CHEST).stream().findFirst()
				: Optional.of(barrels.getFirst());
	}

	/** The cells where {@code template}, placed as {@code pool}, puts {@code block}, in the template's order. */
	static List<BlockPos> cells(StructureTemplate template, PoolElementStructurePiece pool, Block block) {
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
		List<BlockPos> out = new ArrayList<>();
		for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(pool.getPosition(), settings, block)) {
			out.add(info.pos());
		}
		return out;
	}

	private static void give(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, String table,
			RandomSource random) {
		if (chunkBox.isInside(pos)) {
			RandomizableContainer.setBlockEntityLootTable(level, random, pos,
					ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table)));
		}
	}

	private static void report(String style, String what, String why) {
		if (REPORTED.add(style + "/" + what)) {
			Industrialization.LOGGER.warn("[ala_town] style {}: no place for the hall plan's {}: {}", style, what,
					why);
		}
	}
}
