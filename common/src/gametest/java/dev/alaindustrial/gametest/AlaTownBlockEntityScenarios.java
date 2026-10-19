package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.worldgen.town.TownBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The town hall's block entities in a chunk still being generated (MOD-846). World generation writes a
 * {@code DUMMY} block-entity placeholder for every cell whose block is an entity block, and a placeholder whose
 * block makes no block entity for its state ends as the warning "Tried to load a block entity … but failed" when
 * the chunk is loaded — every town logged it for the reception capsule's flask (its cells above the lower one).
 *
 * <p>The other town scenarios place the town into the live level, whose chunks are already level chunks and get
 * no placeholders, so they cannot see this. Here each style's town hall is put into a fresh proto chunk the way
 * the world-generation region does it — the block, then the placeholder — and {@link TownBlockEntities} must leave
 * a placeholder only where a block entity will come of it.
 */
public final class AlaTownBlockEntityScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AlaTownBlockEntityScenarios::hallLeavesNoEmptyPlaceholder,
						"ala_town_hall_leaves_no_empty_block_entity_placeholder").ticks(20));

		private Roster() {}
	}

	private AlaTownBlockEntityScenarios() {}

	/** How many cells of a capsule stand over its lower one: the flask, which makes no block entity (MOD-850). */
	private static final int FLASK_CELLS = ReceptionCapsuleBlock.Part.values().length - 1;

	/** Where the hall's template corner goes in the proto chunk: the chunk's own corner, well inside the height. */
	private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

	/** @implements MOD-846-PLACEHOLDER — see the class comment. */
	public static void hallLeavesNoEmptyPlaceholder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String style : List.of("plains", "desert")) {
			StructureTemplate hall = LineStructures.templates(level)
					.getOrCreate(Industrialization.id("ala_town/" + style + "/town_hall"));
			ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, level,
					PalettedContainerFactory.create(level.registryAccess()), null);
			BoundingBox box = new BoundingBox(0, level.getMinY(), 0, 15, level.getMaxY(), 15);
			int flask = 0;
			for (Block kind : List.of(ModContent.RECEPTION_CAPSULE.get(), ModContent.SETTLEMENT_CORE.get())) {
				for (StructureTemplate.StructureBlockInfo info : hall.filterBlocks(ORIGIN, new StructurePlaceSettings(),
						kind)) {
					if (!box.isInside(info.pos())) {
						helper.fail(style + ": the hall's " + info.state() + " at " + info.pos()
								+ " is outside the test chunk");
						return;
					}
					generate(chunk, info.pos(), info.state());
					if (info.state().getBlock() instanceof ReceptionCapsuleBlock
							&& info.state().getValue(ReceptionCapsuleBlock.PART) != ReceptionCapsuleBlock.Part.LOWER) {
						flask++;
					}
				}
			}
			if (flask != FLASK_CELLS) {
				helper.fail(style + ": the town hall holds " + flask + " capsule cells over the lower one, not "
						+ FLASK_CELLS);
				return;
			}
			int dropped = TownBlockEntities.dropEmptyPlaceholders(chunk, box);
			for (BlockPos pos : chunk.getBlockEntitiesPos()) {
				BlockState state = chunk.getBlockState(pos);
				if (!(state.getBlock() instanceof EntityBlock block) || block.newBlockEntity(pos, state) == null) {
					helper.fail(style + ": a block-entity placeholder is left at " + pos + " on " + state
							+ ", which makes no block entity — the game would warn when the chunk loads");
					return;
				}
			}
			if (dropped != flask || chunk.getBlockEntitiesPos().size() != 2) {
				helper.fail(style + ": dropped " + dropped + " placeholders and kept "
						+ chunk.getBlockEntitiesPos().size() + "; want the capsule's flask cells dropped, its lower "
						+ "cell's and the core's kept");
				return;
			}
		}
		helper.succeed();
	}

	/** What the world-generation region does when it sets a block in a chunk that is not a level chunk yet. */
	private static void generate(ProtoChunk chunk, BlockPos pos, BlockState state) {
		chunk.setBlockState(pos, state, 0);
		if (state.hasBlockEntity()) {
			CompoundTag placeholder = new CompoundTag();
			placeholder.putInt("x", pos.getX());
			placeholder.putInt("y", pos.getY());
			placeholder.putInt("z", pos.getZ());
			placeholder.putString("id", "DUMMY");
			chunk.setBlockEntityNbt(placeholder);
		}
	}
}
