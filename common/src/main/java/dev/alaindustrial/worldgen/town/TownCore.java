package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.SettlementStyle;
import dev.alaindustrial.settlement.SettlementTerritory;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The settlement core of a town (MOD-830, MOD-831): the town hall's template holds the real core block, with no
 * block data — a template core carries no town number, so every town placed from it gets a settlement of its own.
 * The core takes its place in {@link SettlementRegistry} by itself on its first tick; what it cannot know is the
 * town's style, which only the structure has. So the chunk that holds the core registers the settlement at the
 * core's cell and gives it the style, on the server thread: the registry is server data, and world generation
 * runs on worker threads.
 *
 * <p>Either order comes to the same settlement: the registry finds a core's settlement by its cell, so whichever of
 * the two — this task or the core's first tick — runs first creates it, and the other one finds it.
 *
 * <p>The same task gives the settlement the footprint of the whole town ({@link TownFootprint}, MOD-848): its territory
 * covers every piece of it, not only the square of its tier around the core (epic decision R59); and the town's
 * buildings ({@link TownBuildings}, MOD-857), which its core's «Construction» tab lists and its drones restore.
 */
final class TownCore {

	private TownCore() {
	}

	/**
	 * Registers the settlement of the core of {@code piece} that falls into {@code chunkBox}, with {@code style}, the
	 * town's {@code footprint} and its buildings among {@code pieces} (MOD-857), resolved on the server thread against
	 * the {@code town_building} definitions ({@link TownBuildings}).
	 */
	static void register(WorldGenLevel level, StructureTemplateManager templates, PoolElementStructurePiece piece,
			BoundingBox chunkBox, ResourceKey<SettlementStyle> style, Optional<SettlementTerritory.Area> footprint,
			List<TownBuildings.PieceRef> pieces) {
		MinecraftServer server = level.getServer();
		if (server == null || !(piece.getElement() instanceof SinglePoolElement single)) {
			return;
		}
		StructureTemplate template = templates.getOrCreate(single.getTemplateLocation());
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
		for (StructureTemplate.StructureBlockInfo core : template.filterBlocks(piece.getPosition(), settings,
				ModContent.SETTLEMENT_CORE.get())) {
			if (chunkBox.isInside(core.pos())) {
				ResourceKey<Level> dimension = level.getLevel().dimension();
				BlockPos pos = core.pos();
				String styleId = style.identifier().toString();
				server.execute(() -> {
					SettlementRegistry registry = SettlementRegistry.get(server);
					int id = registry.place(dimension, pos, 0).settlementId();
					registry.setStyle(id, styleId);
					footprint.ifPresent(area -> registry.setFootprint(id, area));
					registry.works().town(id).learnBuildings(TownBuildings.resolve(pieces, server.registryAccess()));
				});
			}
		}
	}
}
