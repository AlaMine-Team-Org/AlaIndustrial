package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.ceramic.QuenchPress;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.AABB;

/**
 * The quench press (MOD-590): a piston fired into a water source splits the carbon briquettes floating
 * there into ceramic plates.
 *
 * <p>These live in a gametest rather than in an L1 unit test because every step is world state — a
 * fluid, a piston, a redstone signal and an item entity — and none of it can be built without a level.
 * That is also why {@code QuenchPress} sits in the pitest exclusion list: what it does is proved here.
 *
 * <p><b>Both assertions run on a delay, deliberately.</b> Checking on the first tick would let the
 * "a dry press pays nothing" case pass before the piston had even begun to extend — a test that cannot
 * fail. {@link #SETTLE_TICKS} is well past the two ticks a piston takes.
 */
public final class CeramicScenarios {

	/** Where the press sits in the rig: the piston faces east into the water block. */
	private static final BlockPos PISTON = new BlockPos(1, 2, 2);
	private static final BlockPos WATER = new BlockPos(2, 2, 2);
	/** The power source goes directly west of the piston, so it is a genuine adjacent signal. */
	private static final BlockPos POWER = new BlockPos(0, 2, 2);
	/** Comfortably past the two ticks a piston needs to extend. */
	private static final int SETTLE_TICKS = 12;

	private CeramicScenarios() {
	}

	/**
	 * The press pays its full yield, and eats the briquette doing it.
	 *
	 * <p>Mutation check: return any other number from {@code pressYield()} and this reddens naming the
	 * count it actually paid. Since MOD-594 the press is the ONLY way to split a briquette, so that
	 * number is not a comparison against an easier path any more — it is the whole yield of ceramic.
	 */
	public static void quenchPressSplitsFloatingBriquettes(GameTestHelper helper) {
		buildPress(helper);
		helper.setBlock(WATER, Blocks.WATER);
		feed(helper, ModContent.CARBON_BRIQUETTE.get(), 1);
		feed(helper, Items.REDSTONE, Config.ceramicPressRedstoneCost);
		fire(helper);

		helper.runAfterDelay(SETTLE_TICKS, () -> {
			int plates = count(helper, ModContent.CERAMIC_PLATE.get());
			if (plates != QuenchPress.pressYield()) {
				helper.fail("the press paid " + plates + " plates, expected " + QuenchPress.pressYield());
			}
			int left = count(helper, ModContent.CARBON_BRIQUETTE.get());
			if (left != 0) {
				helper.fail("the briquette survived the press: " + left + " left");
			}
			helper.succeed();
		});
	}

	/**
	 * A press with no water pays nothing, and does not eat the briquette either.
	 *
	 * <p>The failure this guards is the tempting shortcut — hooking the piston and converting whatever
	 * lies in front of it. A player who drops briquettes near ANY piston would then lose them.
	 *
	 * <p>Mutation check: delete the {@code isQuenchWater} guard in {@code QuenchPress#quench} and this
	 * reddens with "a dry press paid 4 plates".
	 */
	public static void dryPressPaysNothing(GameTestHelper helper) {
		buildPress(helper);
		helper.setBlock(WATER, Blocks.AIR);
		// The redstone IS supplied: without it this test would pass because the press could not pay,
		// not because there is no water, and the missing-water guard it exists for would go unwatched.
		feed(helper, ModContent.CARBON_BRIQUETTE.get(), 1);
		feed(helper, Items.REDSTONE, Config.ceramicPressRedstoneCost);
		fire(helper);

		helper.runAfterDelay(SETTLE_TICKS, () -> {
			int plates = count(helper, ModContent.CERAMIC_PLATE.get());
			if (plates != 0) {
				helper.fail("a dry press paid " + plates + " plates");
			}
			if (count(helper, ModContent.CARBON_BRIQUETTE.get()) == 0) {
				helper.fail("a dry press swallowed the briquette");
			}
			helper.succeed();
		});
	}

	/**
	 * A press with no redstone pays nothing, and keeps the briquette.
	 *
	 * <p>Mutation check: drop the redstone term from {@code QuenchPress#quench} — take the shot count
	 * from the briquettes alone — and this reddens with "a press with no redstone paid 4 plates".
	 */
	public static void pressWithoutRedstonePaysNothing(GameTestHelper helper) {
		buildPress(helper);
		helper.setBlock(WATER, Blocks.WATER);
		feed(helper, ModContent.CARBON_BRIQUETTE.get(), 1);
		fire(helper);

		helper.runAfterDelay(SETTLE_TICKS, () -> {
			int plates = count(helper, ModContent.CERAMIC_PLATE.get());
			if (plates != 0) {
				helper.fail("a press with no redstone paid " + plates + " plates");
			}
			if (count(helper, ModContent.CARBON_BRIQUETTE.get()) == 0) {
				helper.fail("a press with no redstone swallowed the briquette");
			}
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ rig

	/** Drops {@code count} of an item into the press block. */
	private static void feed(GameTestHelper helper, Item item, int count) {
		for (int i = 0; i < count; i++) {
			helper.spawnItem(item, WATER.getX() + 0.5f, WATER.getY() + 0.5f, WATER.getZ() + 0.5f);
		}
	}

	private static void buildPress(GameTestHelper helper) {
		helper.setBlock(PISTON, Blocks.PISTON.defaultBlockState()
				.setValue(DirectionalBlock.FACING, Direction.EAST));
	}

	/** Powers the piston from the block behind it — the same signal a player's lever would give. */
	private static void fire(GameTestHelper helper) {
		helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
	}

	/** Items of this kind lying anywhere in the rig, counted by stack size, not by entity. */
	private static int count(GameTestHelper helper, Item item) {
		AABB bounds = helper.getBounds().inflate(2.0);
		List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds,
				entity -> entity.isAlive() && entity.getItem().is(item));
		return items.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
	}
}
