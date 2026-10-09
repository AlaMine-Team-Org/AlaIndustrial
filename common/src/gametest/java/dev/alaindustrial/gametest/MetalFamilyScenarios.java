package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationSources;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * MOD-796 — the metal family: storage blocks of the mod's ingots, their stairs, slabs and walls, and the
 * tempered iron fence. What the generated data cannot show by itself is checked here, in the world:
 * <ul>
 *   <li>compacted uranium radiates like the ingots inside it, carried and placed — a block of nine ingots
 *       that counted as one would make compaction the cheapest shield in the game;</li>
 *   <li>a double slab drops two slabs, as vanilla's does;</li>
 *   <li>the fence joins the nether brick fence and not the oak one ({@code FenceBlock.isSameFence}: the
 *       fence is in {@code #fences} but not in {@code #wooden_fences}), and the walls join vanilla walls.</li>
 * </ul>
 *
 * <p>The radiation rig is {@code RadiationVehicleScenarios}': both radii pinned to 3, the source at
 * {@code (1,2,1)}, a cow two blocks away; the exposure is read synchronously, so nothing ticks.
 */
public final class MetalFamilyScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MetalFamilyScenarios::uraniumFormsWeighTheirIngots,
						"metal_uranium_forms_weigh_their_ingots").ticks(20, 40),
				RosterEntry.of(MetalFamilyScenarios::placedUraniumBlockIrradiates,
						"metal_placed_uranium_block_irradiates").ticks(20, 40),
				RosterEntry.of(MetalFamilyScenarios::doubleSlabDropsTwo, "metal_double_slab_drops_two").ticks(20, 40),
				RosterEntry.of(MetalFamilyScenarios::fenceAndWallJoinTheRightNeighbours,
						"metal_fence_and_wall_join_the_right_neighbours").ticks(20, 40));

		private Roster() {}
	}

	private MetalFamilyScenarios() {
	}

	private static final BlockPos RACK = new BlockPos(1, 2, 1);
	private static final BlockPos BYSTANDER = new BlockPos(1, 2, 3);

	private static Block block(String path) {
		return BuiltInRegistries.BLOCK.getValue(Industrialization.id(path));
	}

	private static Item item(String path) {
		return BuiltInRegistries.ITEM.getValue(Industrialization.id(path));
	}

	/** A uranium block is nine ingots, its stairs and wall nine (one block each on the stonecutter), a slab five. */
	public static void uraniumFormsWeighTheirIngots(GameTestHelper helper) {
		int ingot = RadiationSources.strengthOf(new ItemStack(item("uranium_ingot")));
		helper.assertTrue(ingot > 0, "a uranium ingot no longer radiates — the test proves nothing");
		for (String path : List.of("uranium_block", "uranium_stairs", "uranium_wall")) {
			int strength = RadiationSources.strengthOf(new ItemStack(item(path)));
			helper.assertTrue(strength == 9 * ingot,
					path + " radiates " + strength + ", want nine ingots " + 9 * ingot);
		}
		int slab = RadiationSources.strengthOf(new ItemStack(item("uranium_slab")));
		helper.assertTrue(slab == 5 * ingot, "uranium_slab radiates " + slab + ", want five ingots " + 5 * ingot);
		helper.assertTrue(RadiationSources.strengthOf(new ItemStack(item("tin_block"))) == 0,
				"a tin block radiates");
		helper.succeed();
	}

	/** A placed uranium block is a source the dose and the counter both see; a tin block is not. */
	public static void placedUraniumBlockIrradiates(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("radiationSourceRadius", 3);
			o.set("radiationGroundRadius", 3);
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			helper.setBlock(RACK, block("tin_block"));
			int tin = RadiationSources.exposureAt(level, viewer, RadiationConfig.radiationSourceRadius);
			helper.setBlock(RACK, block("uranium_block"));
			int uranium = RadiationSources.exposureAt(level, viewer, RadiationConfig.radiationSourceRadius);
			int counter = RadiationSources.detectedAt(level, viewer, RadiationConfig.radiationSourceRadius,
					RadiationConfig.radiationGroundRadius);
			helper.setBlock(RACK, Blocks.AIR);
			viewer.discard();
			helper.assertTrue(tin == 0, "a placed tin block irradiates: " + tin);
			helper.assertTrue(uranium > 0, "a placed uranium block does not irradiate the cow beside it");
			helper.assertTrue(counter > 0, "the Geiger counter does not hear a placed uranium block");
		}
		helper.succeed();
	}

	/** Breaking a double slab gives two slabs, a single one gives one. */
	public static void doubleSlabDropsTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(RACK);
		for (SlabType type : List.of(SlabType.BOTTOM, SlabType.DOUBLE)) {
			helper.setBlock(RACK, block("tin_slab").defaultBlockState().setValue(SlabBlock.TYPE, type));
			int count = Block.getDrops(level.getBlockState(abs), level, abs, null).stream()
					.mapToInt(ItemStack::getCount).sum();
			int want = type == SlabType.DOUBLE ? 2 : 1;
			helper.assertTrue(count == want, type + " tin slab drops " + count + ", want " + want);
		}
		helper.setBlock(RACK, Blocks.AIR);
		helper.succeed();
	}

	/** The fence joins the nether brick fence and not the oak one; a metal wall joins a vanilla wall. */
	public static void fenceAndWallJoinTheRightNeighbours(GameTestHelper helper) {
		BlockPos fence = RACK;
		helper.setBlock(fence, block("tempered_iron_fence"));
		helper.setBlock(fence.east(), Blocks.NETHER_BRICK_FENCE);
		helper.setBlock(fence.west(), Blocks.OAK_FENCE);
		helper.assertTrue(helper.getBlockState(fence).getValue(FenceBlock.EAST),
				"the tempered iron fence does not join the nether brick fence");
		helper.assertFalse(helper.getBlockState(fence).getValue(FenceBlock.WEST),
				"the tempered iron fence joins the oak fence");
		// The other side of each joint is what the fence's OWN tags decide: isSameFence reads the neighbour's
		// tags, so only the nether brick and oak fences can tell whether ours is in #fences / #wooden_fences.
		helper.assertTrue(helper.getBlockState(fence.east()).getValue(FenceBlock.WEST),
				"the nether brick fence does not join the tempered iron fence (is it in #minecraft:fences?)");
		helper.assertFalse(helper.getBlockState(fence.west()).getValue(FenceBlock.EAST),
				"the oak fence joins the tempered iron fence (is it in #minecraft:wooden_fences?)");
		BlockPos wall = RACK.above(2);
		helper.setBlock(wall, block("tin_wall"));
		helper.setBlock(wall.east(), Blocks.COBBLESTONE_WALL);
		helper.assertTrue(helper.getBlockState(wall).getValue(WallBlock.EAST) != WallSide.NONE,
				"the tin wall does not join the cobblestone wall");
		for (BlockPos pos : List.of(fence, fence.east(), fence.west(), wall, wall.east())) {
			helper.setBlock(pos, Blocks.AIR);
		}
		helper.succeed();
	}
}
