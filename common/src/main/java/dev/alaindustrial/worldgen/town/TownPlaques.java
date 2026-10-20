package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import dev.alaindustrial.worldgen.AbandonedLabPlacer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The plaques on a town's buildings (R40 of MOD-824, MOD-830): each is the record of a former resident — a broken
 * letter and three digits. The building templates carry the plates; the digits are engraved here, at placement,
 * as those of {@link RecordNumberLedger#formerResidentAt} for the plaque's letter plate, so every town shows its
 * own former residents, and the same plaque in the same world always the same one. The broken letter stays as the
 * template has it: the letter is lost on the plaque, not in the record. A plaque that fell with a collapsed corner
 * keeps the plates the template still has; its first remaining plate stands for the plaque, and its digit plates
 * show the number's last digits.
 *
 * <p><b>Each number becomes a tombstone</b> in the record service: the chunk that holds the plaque's letter plate
 * hands the number to {@link RecordNumberLedger#closeFormerResident} on the server thread — the ledger is server
 * data, and world generation runs on worker threads. A chunk is generated once, so each plaque is reported once;
 * a second report would change nothing anyway.
 *
 * <p><b>Found in the template, not in the world.</b> A plaque may cross a chunk border, and each chunk must engrave
 * its plates from the same letter plate; the template knows all four wherever they land.
 */
final class TownPlaques {

	/** Plates in a whole plaque: the lost letter and three digits. */
	private static final int PLATES = 4;

	private TownPlaques() {
	}

	/** Engraves the plaques of {@code piece} that fall into {@code chunkBox}, and reports their numbers. */
	static void engrave(WorldGenLevel level, StructureTemplateManager templates, PoolElementStructurePiece piece,
			BoundingBox chunkBox) {
		Map<BlockPos, BlockState> plates = plates(templates, piece);
		for (List<BlockPos> plaque : plaques(plates)) {
			BlockPos anchor = plaque.getFirst();
			Direction facing = plates.get(anchor).getValue(HorizontalDirectionalBlock.FACING);
			RecordNumber number = RecordNumberLedger.formerResidentAt(level.getSeed(), anchor.getX(), anchor.getY(),
					anchor.getZ());
			List<BlockPos> digitPlates = plaque.stream().filter(pos -> !isBrokenLetter(plates.get(pos))).toList();
			List<Block> digits = AbandonedLabPlacer.digits(number.digits());
			int first = digits.size() - digitPlates.size();
			for (int i = 0; i < digitPlates.size(); i++) {
				if (chunkBox.isInside(digitPlates.get(i))) {
					BlockState digit = digits.get(first + i).defaultBlockState()
							.setValue(HorizontalDirectionalBlock.FACING, facing);
					level.setBlock(digitPlates.get(i), digit, Block.UPDATE_CLIENTS);
				}
			}
			if (chunkBox.isInside(anchor)) {
				report(level, number);
			}
		}
	}

	/**
	 * The numbers the whole plaques of {@code piece} show — those that kept all three digit plates — in the world with
	 * {@code worldSeed}: what a shift worker's biography may quote (MOD-854). A plaque that fell with a collapsed
	 * corner shows only the last digits of its number and is left out.
	 */
	static List<RecordNumber> wholePlaqueNumbers(long worldSeed, StructureTemplateManager templates,
			PoolElementStructurePiece piece) {
		Map<BlockPos, BlockState> plates = plates(templates, piece);
		List<RecordNumber> numbers = new ArrayList<>();
		for (List<BlockPos> plaque : plaques(plates)) {
			long digitPlates = plaque.stream().filter(pos -> !isBrokenLetter(plates.get(pos))).count();
			if (digitPlates == PLATES - 1) {
				BlockPos anchor = plaque.getFirst();
				numbers.add(RecordNumberLedger.formerResidentAt(worldSeed, anchor.getX(), anchor.getY(),
						anchor.getZ()));
			}
		}
		return numbers;
	}

	/** The plaque plates of {@code piece}'s template as placed, or none for a piece that is not a rigid template. */
	private static Map<BlockPos, BlockState> plates(StructureTemplateManager templates,
			PoolElementStructurePiece piece) {
		Map<BlockPos, BlockState> plates = new HashMap<>();
		if (!(piece.getElement() instanceof SinglePoolElement single)
				|| single.getProjection() != StructureTemplatePool.Projection.RIGID) {
			return plates;
		}
		StructureTemplate template = templates.getOrCreate(single.getTemplateLocation());
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
		for (Supplier<Block> plate : plateBlocks()) {
			for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(piece.getPosition(), settings,
					plate.get())) {
				plates.put(info.pos(), info.state());
			}
		}
		return plates;
	}

	/**
	 * The plates grouped into plaques: plates of one facing that touch along the wall, in reading order. A whole
	 * plaque is four plates — the lost letter and three digits; a plaque that fell with a collapsed corner keeps
	 * fewer. A group with more digit plates than a number has digits is not a plaque and is left as it is.
	 */
	static List<List<BlockPos>> plaques(Map<BlockPos, BlockState> plates) {
		List<List<BlockPos>> plaques = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> ordered = new ArrayList<>(plates.keySet());
		ordered.sort(BlockPos::compareTo);
		for (BlockPos start : ordered) {
			if (!seen.add(start)) {
				continue;
			}
			Direction facing = plates.get(start).getValue(HorizontalDirectionalBlock.FACING);
			Direction along = facing.getClockWise();
			List<BlockPos> group = new ArrayList<>();
			Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				group.add(pos);
				for (BlockPos next : List.of(pos.relative(along), pos.relative(along.getOpposite()))) {
					BlockState neighbour = plates.get(next);
					if (neighbour != null && neighbour.getValue(HorizontalDirectionalBlock.FACING) == facing
							&& seen.add(next)) {
						queue.add(next);
					}
				}
			}
			long digitPlates = group.stream().filter(pos -> !isBrokenLetter(plates.get(pos))).count();
			if (group.size() <= PLATES && digitPlates < PLATES) {
				AbandonedLabPlacer.sortForReader(group, facing);
				plaques.add(group);
			}
		}
		return plaques;
	}

	private static boolean isBrokenLetter(BlockState state) {
		for (Supplier<Block> letter : AbandonedLabPlacer.BROKEN_LETTERS) {
			if (state.is(letter.get())) {
				return true;
			}
		}
		return false;
	}

	// WorldGenRegion#getServer is the server the chunk is generated for; only its task queue is used.
	private static void report(WorldGenLevel level, RecordNumber number) {
		MinecraftServer server = level.getServer();
		if (server != null) {
			server.execute(() -> RecordNumberLedger.get(server).closeFormerResident(number));
		}
	}

	private static List<Supplier<Block>> plateBlocks() {
		List<Supplier<Block>> plates = new ArrayList<>(AbandonedLabPlacer.DIGITS);
		plates.addAll(AbandonedLabPlacer.BROKEN_LETTERS);
		return plates;
	}
}
