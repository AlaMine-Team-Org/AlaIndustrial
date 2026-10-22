package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownBuildingDef;
import dev.alaindustrial.settlement.works.TownWorks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/**
 * The buildings of an ALA town (MOD-857, ADR-050 §4): the pieces of its structure whose template a
 * {@code town_building} definition names ({@link TownBuildingDef}), with where the structure put each and how it
 * turned it. The ground between the buildings and the lab are pieces with no definition and are not buildings.
 *
 * <p>Read twice in a town's life: when the chunk holding its core registers the town ({@link TownCore}), and — for a
 * town registered before towns knew their buildings — when its core first finds the town's structure
 * ({@link #learnOnce}). Never searched in the world: a building's address is its piece.
 */
public final class TownBuildings {

	private TownBuildings() {
	}

	/**
	 * One piece of a town as the structure placed it — what world generation hands the server thread, which resolves
	 * the definitions there.
	 *
	 * @param template the template the piece placed
	 * @param origin   where it put the template's (0, 0, 0)
	 * @param rotation how it turned it
	 */
	public record PieceRef(Identifier template, BlockPos origin, Rotation rotation) {
		public PieceRef {
			origin = origin.immutable();
		}
	}

	/** The single-template pieces of {@code pieces}, in the structure's order. */
	public static List<PieceRef> refs(List<StructurePiece> pieces) {
		List<PieceRef> out = new ArrayList<>();
		for (StructurePiece piece : pieces) {
			if (piece instanceof PoolElementStructurePiece pool
					&& pool.getElement() instanceof SinglePoolElement single) {
				out.add(new PieceRef(single.getTemplateLocation(), pool.getPosition(), pool.getRotation()));
			}
		}
		return out;
	}

	/** The buildings among {@code refs}: the pieces a definition of {@code access}'s {@code town_building} names. */
	public static List<TownBuilding> resolve(List<PieceRef> refs, RegistryAccess access) {
		Map<Identifier, TownBuildingDef> byTemplate = TownBuildingDef.byTemplate(access);
		List<TownBuilding> out = new ArrayList<>();
		for (PieceRef ref : refs) {
			TownBuildingDef def = byTemplate.get(ref.template());
			if (def != null) {
				out.add(TownBuilding.found(ref.template(), def.intact(), def.name(), ref.origin(), ref.rotation()));
			}
		}
		return out;
	}

	/**
	 * A town of an older save (MOD-857): registered before towns knew their buildings, it learns them from the town
	 * structure its core stands in — once per load, like its footprint ({@link TownFootprint#at}). A town no structure
	 * built (no style) is left alone; so is one whose core stands outside every piece of its town.
	 *
	 * <p>Reads the chunks' structure data: call it from the core's tick, never from a block's removal or a chunk
	 * operation.
	 */
	public static void learnOnce(ServerLevel level, SettlementRegistry registry, int settlementId, BlockPos core) {
		TownWorks works = registry.works().town(settlementId);
		if (works.buildingsKnown() || works.structureAsked()) {
			return;
		}
		works.markStructureAsked();
		boolean styled = registry.find(settlementId).map(town -> !town.style().isEmpty()).orElse(false);
		if (!styled) {
			return;
		}
		StructureStart start = level.structureManager().getStructureWithPieceAt(core,
				holder -> holder.value() instanceof AlaTownStructure);
		if (!start.isValid()) {
			return;
		}
		List<TownBuilding> found = resolve(refs(start.getPieces()), level.registryAccess());
		if (!found.isEmpty() && works.learnBuildings(found)) {
			// INFO on purpose (MOD-833): the playtest protocol of the first slice reads these lines from the log.
			Industrialization.LOGGER.info("[ala-towns] settlement {} of an older save learnt its {} buildings",
					settlementId, found.size());
		}
	}
}
