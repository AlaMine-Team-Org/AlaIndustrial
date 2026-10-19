package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.command.TownEditBench;
import dev.alaindustrial.compat.LineStructures;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * L2 suite for the town editing bench of {@code /ala town edit} (MOD-845): every piece of both built-in styles gets a
 * place on the bench, the pieces do not overlap, and a placed piece stands with a structure block in SAVE mode set to
 * its template's name, corner and size — and what that structure block would save is the template back, block for
 * block, including the jigsaw links and with the left-out cells left out again.
 *
 * <p>Laid out far from the test area (chunk 5200), the way the town suite places its towns.
 */
public final class TownEditScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownEditScenarios::benchHoldsEveryPieceApart, "town_edit_bench_holds_every_piece_apart")
						.ticks(20, 100),
				RosterEntry.of(TownEditScenarios::placedPieceSavesBackToItsTemplate,
						"town_edit_placed_piece_saves_back_to_its_template").ticks(40, 100));

		private Roster() {}
	}

	private TownEditScenarios() {}

	/** The pieces every built-in style has besides its ground pieces. */
	private static final List<String> BUILDINGS = List.of("town_hall", "capsule_house_1", "capsule_house_2",
			"capsule_house_3", "capsule_house_4", "workshop", "warehouse", "pump_station", "guard_post");

	/**
	 * @implements MOD-845-BENCH — {@code /ala town edit <style>} finds both built-in styles and every building of
	 *     each, and lays all pieces of a style out with at least the gap between any two boxes, the structure block's
	 *     cell included.
	 */
	public static void benchHoldsEveryPieceApart(GameTestHelper helper) {
		StructureTemplateManager templates = LineStructures.templates(helper.getLevel());
		List<String> styles = TownEditBench.styles(templates);
		if (!styles.containsAll(List.of("plains", "desert"))) {
			helper.fail("the bench sees the styles " + styles + ", not plains and desert");
			return;
		}
		for (String style : List.of("plains", "desert")) {
			List<String> pieces = TownEditBench.pieces(templates, style);
			if (!pieces.containsAll(BUILDINGS)) {
				helper.fail(style + ": the bench sees " + pieces + ", without every building " + BUILDINGS);
				return;
			}
			List<TownEditBench.Slot> slots = TownEditBench.plan(templates, style, pieces);
			if (slots.size() != pieces.size()) {
				helper.fail(style + ": " + slots.size() + " slots for " + pieces.size() + " pieces");
				return;
			}
			for (int i = 0; i < slots.size(); i++) {
				for (int j = i + 1; j < slots.size(); j++) {
					if (touch(slots.get(i), slots.get(j))) {
						helper.fail(style + ": " + slots.get(i).piece() + " and " + slots.get(j).piece()
								+ " stand closer than the structure block's cell");
						return;
					}
				}
			}
		}
		helper.succeed();
	}

	/** Two boxes, each grown by its structure block's cell on the north-west, overlap. */
	private static boolean touch(TownEditBench.Slot a, TownEditBench.Slot b) {
		int ax0 = a.offset().getX() - 1;
		int az0 = a.offset().getZ() - 1;
		int bx0 = b.offset().getX() - 1;
		int bz0 = b.offset().getZ() - 1;
		return ax0 <= b.offset().getX() + b.size().getX() - 1 && bx0 <= a.offset().getX() + a.size().getX() - 1
				&& az0 <= b.offset().getZ() + b.size().getZ() - 1 && bz0 <= a.offset().getZ() + a.size().getZ() - 1;
	}

	/**
	 * @implements MOD-845-SAVE — a piece placed by the bench stands with a structure block in SAVE mode named after
	 *     its template, pointing at its corner and sized as it; saving it as the structure block saves (structure
	 *     voids skipped) gives back the template's every block at the same cell, jigsaw links included.
	 */
	public static void placedPieceSavesBackToItsTemplate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplateManager templates = LineStructures.templates(level);
		TownEditBench.Slot slot = TownEditBench.plan(templates, "plains", List.of("guard_post")).getFirst();
		BlockPos origin = new BlockPos(5200 * 16, 100, 5200 * 16);
		BlockPos at = TownEditBench.place(level, origin, slot);

		BlockState state = level.getBlockState(at);
		if (!state.is(Blocks.STRUCTURE_BLOCK) || state.getValue(StructureBlock.MODE) != StructureMode.SAVE
				|| !(level.getBlockEntity(at) instanceof StructureBlockEntity block)) {
			helper.fail("no structure block in SAVE mode at " + at + ": " + state);
			return;
		}
		String expectedName = Industrialization.id("ala_town/plains/guard_post").toString();
		BlockPos corner = at.offset(block.getStructurePos());
		if (!block.getStructureName().equals(expectedName) || !corner.equals(origin.offset(slot.offset()))
				|| !block.getStructureSize().equals(slot.size()) || block.getMode() != StructureMode.SAVE) {
			helper.fail("the structure block is set to " + block.getStructureName() + " at " + corner + " size "
					+ block.getStructureSize() + ", expected " + expectedName + " at " + origin.offset(slot.offset())
					+ " size " + slot.size());
			return;
		}

		Identifier copy = Identifier.fromNamespaceAndPath("alaindustrial_gametest", "town_edit_round_trip");
		try {
			StructureBlockEntity.saveStructure(level, copy, corner, block.getStructureSize(), true, "gametest", false,
					List.of());
			StructureTemplate original = templates.getOrCreate(slot.template());
			StructureTemplate saved = templates.getOrCreate(copy);
			if (!saved.getSize().equals(original.getSize())) {
				helper.fail("saved size " + saved.getSize() + " differs from the template's " + original.getSize());
				return;
			}
			Set<Block> kinds = new HashSet<>();
			BlockPos.betweenClosed(corner, corner.offset(original.getSize()).offset(-1, -1, -1))
					.forEach(pos -> kinds.add(level.getBlockState(pos).getBlock()));
			kinds.remove(Blocks.STRUCTURE_VOID);
			if (!kinds.contains(Blocks.JIGSAW)) {
				helper.fail("the placed guard post has no jigsaw block: the town links were lost on the bench");
				return;
			}
			for (Block kind : kinds) {
				Set<String> want = cells(original, kind);
				Set<String> got = cells(saved, kind);
				if (!want.equals(got)) {
					helper.fail(kind + ": the template has " + want.size() + " cells, the saved piece " + got.size()
							+ "; first differing: " + firstDifference(want, got));
					return;
				}
			}
		} finally {
			templates.remove(copy);
		}
		helper.succeed();
	}

	private static Set<String> cells(StructureTemplate template, Block kind) {
		Set<String> out = new HashSet<>();
		for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO,
				new StructurePlaceSettings(), kind)) {
			out.add(info.pos().toShortString() + " " + info.state());
		}
		return out;
	}

	private static String firstDifference(Set<String> want, Set<String> got) {
		for (String cell : want) {
			if (!got.contains(cell)) {
				return "missing " + cell;
			}
		}
		for (String cell : got) {
			if (!want.contains(cell)) {
				return "extra " + cell;
			}
		}
		return "none";
	}
}
