package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.network.HallSchematicPayload;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.HallSchematic;
import dev.alaindustrial.settlement.works.TownBuilding;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * L2 suite for the town hall's schematic (MOD-857, owner's round 2, point 7): the server's reply carries exactly the
 * hall's cells still to build — each wanting a block the world does not hold there, bottom layer first — survives the
 * wire unchanged, empties once the hall is whole, and says «nothing to show» for a town with no hall.
 */
public final class TownHallSchematicScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownHallSchematicScenarios::schematicCarriesTheCellsStillToBuild,
						"town_hall_schematic_carries_the_cells_still_to_build").ticks(40, 40));

		private Roster() {}
	}

	private TownHallSchematicScenarios() {}

	private static final Identifier PLAINS_HALL = Industrialization.id("ala_town/plains/town_hall");
	private static final Identifier PLAINS_HALL_INTACT = Industrialization.id("ala_town/plains/intact/town_hall");
	/** The plains town hall's core in its template (docs/structures/ala_town.md). */
	private static final BlockPos HALL_CORE = new BlockPos(6, 1, 5);

	/**
	 * @implements MOD-857-SCHEMATIC — the schematic reply holds every cell of the hall still to build and nothing else,
	 *     bottom layer first, decodes to the same cells, is empty for a whole hall and unavailable for a town with no
	 *     hall.
	 */
	public static void schematicCarriesTheCellsStillToBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		// A cell of the rig grid (TownDroneScenarios#nextOrigin): its chunks are shared with the other
		// halls, and no other case stands a hall or a town there.
		BlockPos origin = TownDroneScenarios.nextOrigin();
		placeTemplate(level, PLAINS_HALL, origin);
		BlockPos core = origin.offset(HALL_CORE);
		int id = registry.place(level.dimension(), core, 0).settlementId();
		registry.works().forget(id);
		Vec3 viewer = Vec3.atCenterOf(core);

		HallSchematicPayload unknown = HallSchematic.reply(level, core, id, viewer);
		helper.assertTrue(!unknown.available() && unknown.cells().isEmpty(),
				"a town that knows no hall must have no schematic, got " + unknown.missing() + " cells");

		registry.works().town(id).learnBuildings(List.of(TownBuilding.found(PLAINS_HALL,
				Optional.of(PLAINS_HALL_INTACT), "settlement.alaindustrial.building.town_hall", origin,
				Rotation.NONE)));
		HallSchematicPayload ruin = HallSchematic.reply(level, core, id, viewer);
		helper.assertTrue(ruin.available() && ruin.missing() > 0 && ruin.cells().size() == ruin.missing(),
				"the ruined hall must send all its missing cells, sent " + ruin.cells().size() + " of "
						+ ruin.missing());
		int lastY = Integer.MIN_VALUE;
		for (HallSchematicPayload.Ghost ghost : ruin.cells()) {
			BlockPos pos = BlockPos.of(ghost.pos());
			helper.assertTrue(!ghost.state().isAir(), "a ghost of air at " + pos);
			helper.assertTrue(!level.getBlockState(pos).is(ghost.state().getBlock()),
					"a ghost over a cell that already holds " + ghost.state() + " at " + pos);
			helper.assertTrue(pos.getY() >= lastY, "the cells are not bottom layer first at " + pos);
			lastY = pos.getY();
		}

		RegistryFriendlyByteBuf buffer = RegistryFriendlyByteBuf.decorator(level.registryAccess())
				.apply(Unpooled.buffer());
		HallSchematicPayload.CODEC.encode(buffer, ruin);
		HallSchematicPayload decoded = HallSchematicPayload.CODEC.decode(buffer);
		helper.assertTrue(decoded.equals(ruin) && buffer.readableBytes() == 0,
				"the schematic changed on the wire: " + decoded.cells().size() + " cells back of "
						+ ruin.cells().size());

		placeTemplate(level, PLAINS_HALL_INTACT, origin);
		HallSchematicPayload whole = HallSchematic.reply(level, core, id, viewer);
		helper.assertTrue(whole.available() && whole.missing() == 0 && whole.cells().isEmpty(),
				"the whole hall must have nothing left to build, has " + whole.missing());
		helper.succeed();
	}

	/** Places template {@code id} at {@code origin} as a town shows it: jigsaws turned, no structure blocks. */
	private static void placeTemplate(ServerLevel level, Identifier id, BlockPos origin) {
		StructureTemplate template = LineStructures.templates(level).getOrCreate(id);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE)
				.addProcessor(JigsawReplacementProcessor.INSTANCE).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
		template.placeInWorld(level, origin, origin, settings, RandomSource.create(857L),
				Block.UPDATE_CLIENTS);
	}
}
