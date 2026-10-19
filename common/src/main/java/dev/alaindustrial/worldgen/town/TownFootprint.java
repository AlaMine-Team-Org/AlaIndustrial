package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.settlement.SettlementTerritory;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The footprint of an ALA town (MOD-848, epic decision R59): the columns every piece of its structure covers — the
 * buildings, the ground between them and the lab under the town hall — which the town's territory must hold
 * ({@code SettlementTerritory}). The footprint is the pieces' own boxes, not the structure start's box: that one is
 * grown by the terrain adjustment's reach, which is ground the town smooths, not ground it stands on.
 */
public final class TownFootprint {

	private TownFootprint() {
	}

	/** The columns {@code pieces} cover, or empty for no piece. */
	public static Optional<SettlementTerritory.Area> of(List<StructurePiece> pieces) {
		SettlementTerritory.Area footprint = null;
		for (StructurePiece piece : pieces) {
			BoundingBox box = piece.getBoundingBox();
			SettlementTerritory.Area area = new SettlementTerritory.Area(box.minX(), box.minZ(), box.maxX(),
					box.maxZ());
			footprint = footprint == null ? area : footprint.envelope(area);
		}
		return Optional.ofNullable(footprint);
	}

	/**
	 * The footprint of the ALA town with a piece at {@code pos} in {@code level}, found through the structure data of
	 * the chunks — the way a town of an older save, registered before towns knew their footprint, learns it from the
	 * town hall its core stands in. Empty where no town stands, or where the core was carried out of every piece of it.
	 *
	 * <p>Reads the structure references of {@code pos}'s chunk and the start of the town, the way vanilla's own
	 * "is this position inside a structure" checks do on the server thread: call it from a tick, never from a block's
	 * removal or a chunk operation.
	 */
	public static Optional<SettlementTerritory.Area> at(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos,
				holder -> holder.value() instanceof AlaTownStructure);
		return start.isValid() ? of(start.getPieces()) : Optional.empty();
	}
}
