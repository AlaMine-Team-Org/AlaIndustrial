package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.TownFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The facts of an ALA town a shift worker's biography may quote (MOD-854): the numbers on the town's whole house
 * plaques and the lab's number under the town hall. Both are derived, never stored — the plaques from the building
 * templates and the world seed ({@link TownPlaques}), the lab from its hatch ({@link AlaTownLabPiece#labNumber}) — so
 * the digits a biography names are the digits on the wall, whether or not the player has loaded those chunks yet.
 */
public final class TownBiographyFacts {

	private TownBiographyFacts() {
	}

	/**
	 * The facts of the ALA town with a piece at {@code pos} in {@code level} — pass the core's position — or
	 * {@link TownFacts#UNKNOWN} where no town stands.
	 *
	 * <p>Reads the town's structure start and each building's template, like {@link TownFootprint#at}: call it on the
	 * server thread from a tick or a player's action, and keep the result for the town — the answer never changes.
	 */
	public static TownFacts at(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos,
				holder -> holder.value() instanceof AlaTownStructure);
		return start.isValid() ? of(level.getSeed(), LineStructures.templates(level), start) : TownFacts.UNKNOWN;
	}

	/** The facts of the town {@code start} in the world with {@code worldSeed}. */
	public static TownFacts of(long worldSeed, StructureTemplateManager templates, StructureStart start) {
		List<Integer> plaques = new ArrayList<>();
		OptionalInt lab = OptionalInt.empty();
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof AlaTownLabPiece labPiece) {
				lab = OptionalInt.of(labPiece.labNumber(worldSeed));
			} else if (piece instanceof PoolElementStructurePiece building) {
				for (RecordNumber number : TownPlaques.wholePlaqueNumbers(worldSeed, templates, building)) {
					plaques.add(number.digits());
				}
			}
		}
		return new TownFacts(plaques, lab);
	}
}
