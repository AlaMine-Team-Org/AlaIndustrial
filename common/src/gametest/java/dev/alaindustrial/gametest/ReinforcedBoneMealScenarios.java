package dev.alaindustrial.gametest;

import dev.alaindustrial.block.KokSagyzBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.gametest.compat.StubPlants;
import dev.alaindustrial.item.misc.ReinforcedBonemeal;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reinforced bone meal (MOD-906): growth without the luck of ordinary bone meal, and no meal spent when nothing
 * can grow. Every click goes through the real interaction path ({@code ServerPlayerGameMode.useItemOn}), except
 * the one scenario that proves the item works with no player at all.
 */
public final class ReinforcedBoneMealScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReinforcedBoneMealScenarios::saplingAlwaysBecomesTree,
						"reinforced_bone_meal_sapling_always_tree").ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::saplingWithoutRoomIsPutBack,
						"reinforced_bone_meal_sapling_no_room_put_back").ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::cropsRipenAtOnce,
						"reinforced_bone_meal_crops_ripen_at_once").sky().ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::ripeCropSpendsNothing,
						"reinforced_bone_meal_ripe_crop_spends_nothing").sky().ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::trellisKeepsItsSoilGate,
						"reinforced_bone_meal_trellis_keeps_soil_gate").ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::trellisGrowsOnMoistLitSoil,
						"reinforced_bone_meal_trellis_grows_on_moist_soil").ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::otherCropsRipenAtOnce,
						"reinforced_bone_meal_other_crops_ripen").sky().ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::creativeSpendsNothing,
						"reinforced_bone_meal_creative_spends_nothing").sky().ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::kokSagyzTakesOneStep,
						"reinforced_bone_meal_kok_sagyz_one_step").sky().ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::kokSagyzRefusesMealInTheDark,
						"reinforced_bone_meal_kok_sagyz_dark").ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::foreignPlantRollIsRetried,
						"reinforced_bone_meal_foreign_roll_retried").ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::blacklistedBlockIsLeftAlone,
						"reinforced_bone_meal_blacklist").ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::worksWithoutAPlayer,
						"reinforced_bone_meal_without_player").sky().ticks(20, 40),
				RosterEntry.of(ReinforcedBoneMealScenarios::isInTheCommonFertilizerTag,
						"reinforced_bone_meal_common_fertilizer_tag").ticks(20, 40));

		private Roster() {}
	}

	private ReinforcedBoneMealScenarios() {
	}

	private static final int FLOOR = 1;
	/** The sapling: centre of the 8×8 rig, so a birch's leaves (radius 2) stay inside the force-loaded chunks. */
	private static final BlockPos SAPLING = new BlockPos(4, FLOOR + 1, 4);
	/** Rounds of the sapling series; one round is a whole tree from stage 0 with no luck involved. */
	private static final int SAPLING_ROUNDS = 4;
	private static final int STACK = 16;

	/**
	 * A birch sapling at stage 0 becomes a tree on one click, round after round, and each tree costs one meal.
	 * Ordinary meal never does this in one click: it needs two successes of a 45 % roll.
	 *
	 * @implements MOD-906 — a sapling always becomes a tree, both steps at once.
	 */
	public static void saplingAlwaysBecomesTree(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		for (int round = 0; round < SAPLING_ROUNDS; round++) {
			clearAbove(helper);
			helper.setBlock(SAPLING.below(), Blocks.DIRT);
			helper.setBlock(SAPLING, Blocks.BIRCH_SAPLING);
			click(helper, player, SAPLING);
			if (!helper.getBlockState(SAPLING).is(Blocks.BIRCH_LOG)) {
				helper.fail("round " + round + ": the sapling did not become a tree, the cell holds "
						+ helper.getBlockState(SAPLING));
				return;
			}
		}
		clearAbove(helper);
		expectCount(helper, player, STACK - SAPLING_ROUNDS, "one meal per tree");
	}

	/**
	 * With stone right above it the birch has no room: the sapling is put back at stage 0 (no free half-growth)
	 * and no meal is spent.
	 *
	 * @implements MOD-906 — no room for the tree: the sapling is restored and the meal is kept.
	 */
	public static void saplingWithoutRoomIsPutBack(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		helper.setBlock(SAPLING.below(), Blocks.DIRT);
		helper.setBlock(SAPLING, Blocks.BIRCH_SAPLING);
		helper.setBlock(SAPLING.above(), Blocks.STONE);
		BlockState before = helper.getBlockState(SAPLING);
		click(helper, player, SAPLING);
		BlockState after = helper.getBlockState(SAPLING);
		if (after != before) {
			helper.fail("the sapling was left as " + after + ", expected it back as " + before
					+ " (stage " + before.getValue(SaplingBlock.STAGE) + ")");
			return;
		}
		expectCount(helper, player, STACK, "nothing grew");
	}

	/**
	 * Wheat, carrots, potatoes and beetroots at age 0 are ripe after one click each — ordinary meal adds a
	 * random 2–5 stages.
	 *
	 * @implements MOD-906 — a crop ripens at once.
	 */
	public static void cropsRipenAtOnce(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.WHEAT,
				Blocks.CARROTS};
		for (int i = 0; i < crops.length; i++) {
			BlockPos pos = new BlockPos(1 + i, FLOOR + 1, 2);
			helper.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
			helper.setBlock(pos, crops[i]);
			click(helper, player, pos);
			BlockState state = helper.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
				helper.fail(crops[i] + " at " + pos + " is not ripe after one click: " + state);
				return;
			}
		}
		expectCount(helper, player, STACK - crops.length, "one meal per crop");
	}

	/**
	 * A ripe crop has nothing to grow: the click changes nothing and spends nothing.
	 *
	 * @implements MOD-906 — nothing to grow: the meal is kept.
	 */
	public static void ripeCropSpendsNothing(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		BlockPos pos = new BlockPos(2, FLOOR + 1, 2);
		helper.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(pos, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
		click(helper, player, pos);
		expectCount(helper, player, STACK, "the wheat was already ripe");
	}

	/**
	 * The trellis keeps its water gate: on dry farmland the meal neither grows it nor is spent, however often
	 * the roll is retried.
	 *
	 * @implements MOD-906 — the mod's plants keep their soil and light conditions.
	 */
	public static void trellisKeepsItsSoilGate(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		BlockPos lower = new BlockPos(2, FLOOR + 1, 2);
		helper.setBlock(lower.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 0));
		DoublePlantBlock.placeAt(helper.getLevel(), ModContent.TRELLIS.get().defaultBlockState(),
				helper.absolutePos(lower), Block.UPDATE_ALL);
		int age = 2;
		for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
			helper.setBlock(p, helper.getBlockState(p).setValue(TrellisBlock.AGE, age));
		}
		click(helper, player, lower);
		int after = helper.getBlockState(lower).getValue(TrellisBlock.AGE);
		if (after != age) {
			helper.fail("the trellis grew on dry farmland (age " + age + " -> " + after + ")");
			return;
		}
		expectCount(helper, player, STACK, "the trellis refused");
	}

	/**
	 * The positive control of {@link #trellisKeepsItsSoilGate}: on moist farmland, in the rig's light, the same
	 * trellis takes exactly one stage and one meal — so the refusal there is the soil, not something else.
	 *
	 * @implements MOD-906 — the trellis grows one stage where its own growth would.
	 */
	public static void trellisGrowsOnMoistLitSoil(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		BlockPos lower = new BlockPos(2, FLOOR + 1, 2);
		helper.setBlock(lower.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		DoublePlantBlock.placeAt(helper.getLevel(), ModContent.TRELLIS.get().defaultBlockState(),
				helper.absolutePos(lower), Block.UPDATE_ALL);
		int age = 2;
		for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
			helper.setBlock(p, helper.getBlockState(p).setValue(TrellisBlock.AGE, age));
		}
		if (!TrellisBlock.canGrowAt(helper.getLevel(), helper.absolutePos(lower))) {
			helper.fail("rig problem: the trellis could not grow here even by itself (moisture or light)");
			return;
		}
		click(helper, player, lower);
		int after = helper.getBlockState(lower).getValue(TrellisBlock.AGE);
		if (after != age + 1) {
			helper.fail("on moist lit farmland the trellis went from age " + age + " to " + after + ", expected "
					+ (age + 1));
			return;
		}
		expectCount(helper, player, STACK - 1, "one stage grew");
	}

	/**
	 * The crops that are not {@code CropBlock}s ripen at once too: sweet berries, a melon stem and cocoa.
	 *
	 * @implements MOD-906 — stems, cocoa and sweet berries ripen at once.
	 */
	public static void otherCropsRipenAtOnce(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		BlockPos berries = new BlockPos(1, FLOOR + 1, 2);
		helper.setBlock(berries.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(berries, Blocks.SWEET_BERRY_BUSH);
		BlockPos stem = new BlockPos(3, FLOOR + 1, 2);
		helper.setBlock(stem.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(stem, Blocks.MELON_STEM);
		BlockPos cocoa = new BlockPos(5, FLOOR + 1, 2);
		helper.setBlock(cocoa.north(), Blocks.JUNGLE_LOG);
		helper.setBlock(cocoa, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.NORTH));
		click(helper, player, berries);
		click(helper, player, stem);
		click(helper, player, cocoa);
		int berryAge = helper.getBlockState(berries).getValue(SweetBerryBushBlock.AGE);
		int stemAge = helper.getBlockState(stem).getValue(StemBlock.AGE);
		int cocoaAge = helper.getBlockState(cocoa).getValue(CocoaBlock.AGE);
		if (berryAge != SweetBerryBushBlock.MAX_AGE || stemAge != StemBlock.MAX_AGE
				|| cocoaAge != CocoaBlock.MAX_AGE) {
			helper.fail("not ripe after one click each: sweet berries " + berryAge + "/" + SweetBerryBushBlock.MAX_AGE
					+ ", melon stem " + stemAge + "/" + StemBlock.MAX_AGE + ", cocoa " + cocoaAge + "/"
					+ CocoaBlock.MAX_AGE);
			return;
		}
		expectCount(helper, player, STACK - 3, "one meal per plant");
	}

	/**
	 * A creative player grows the crop and keeps the meal, as with vanilla bone meal.
	 *
	 * @implements MOD-906 — a creative player spends nothing.
	 */
	public static void creativeSpendsNothing(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		player.setGameMode(GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.REINFORCED_BONE_MEAL.get(), STACK));
		BlockPos pos = new BlockPos(2, FLOOR + 1, 2);
		helper.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(pos, Blocks.WHEAT);
		click(helper, player, pos);
		if (!((CropBlock) Blocks.WHEAT).isMaxAge(helper.getBlockState(pos))) {
			helper.fail("in creative the wheat is not ripe: " + helper.getBlockState(pos));
			return;
		}
		expectCount(helper, player, STACK, "creative");
	}

	/**
	 * Kok-sagyz takes the one step it allows from bone meal — a stage, not a jump to mature.
	 *
	 * @implements MOD-906 — the mod's plants get the largest step they allow, no more.
	 */
	public static void kokSagyzTakesOneStep(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		BlockPos flower = new BlockPos(2, FLOOR + 2, 2);
		helper.setBlock(flower.below(), Blocks.FARMLAND);
		helper.setBlock(flower.below(2), Blocks.DIRT);
		helper.setBlock(flower, ModContent.KOK_SAGYZ.get().defaultBlockState().setValue(KokSagyzBlock.AGE, 0));
		if (helper.getLevel().getRawBrightness(helper.absolutePos(flower), 0) < KokSagyzBlock.MIN_LIGHT) {
			helper.fail("rig problem: the flower stands below light " + KokSagyzBlock.MIN_LIGHT);
			return;
		}
		click(helper, player, flower);
		int age = helper.getBlockState(flower).getValue(KokSagyzBlock.AGE);
		if (age != 1) {
			helper.fail("kok-sagyz went from age 0 to " + age + ", expected exactly one stage");
			return;
		}
		expectCount(helper, player, STACK - 1, "one stage grew");
	}

	/**
	 * Kok-sagyz walled in stone, below its light floor: neither reinforced nor ordinary bone meal grows it, and
	 * neither is spent. The lit counterpart is {@link #kokSagyzTakesOneStep}.
	 *
	 * @implements MOD-906 — bone meal on kok-sagyz demands the light its own growth does.
	 */
	public static void kokSagyzRefusesMealInTheDark(GameTestHelper helper) {
		BlockPos flower = new BlockPos(3, FLOOR + 2, 3);
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				for (int y = FLOOR; y <= FLOOR + 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		helper.setBlock(flower.below(2), Blocks.DIRT);
		helper.setBlock(flower.below(), Blocks.FARMLAND);
		helper.setBlock(flower, ModContent.KOK_SAGYZ.get().defaultBlockState().setValue(KokSagyzBlock.AGE, 0));
		helper.runAfterDelay(5, () -> {
			int light = helper.getLevel().getRawBrightness(helper.absolutePos(flower), 0);
			if (light >= KokSagyzBlock.MIN_LIGHT) {
				helper.fail("rig problem: the walled-in flower has light " + light);
				return;
			}
			ServerPlayer reinforced = playerWithMeal(helper);
			click(helper, reinforced, flower);
			ServerPlayer ordinary = AlaGameTestHelper.survivalPlayer(helper);
			ordinary.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, STACK));
			click(helper, ordinary, flower);
			int age = helper.getBlockState(flower).getValue(KokSagyzBlock.AGE);
			if (age != 0) {
				helper.fail("bone meal grew kok-sagyz in the dark (light " + light + "): age 0 -> " + age);
				return;
			}
			if (ordinary.getMainHandItem().getCount() != STACK) {
				helper.fail("ordinary bone meal was spent in the dark: " + ordinary.getMainHandItem().getCount());
				return;
			}
			expectCount(helper, reinforced, STACK, "the dark refused the meal");
		});
	}

	/**
	 * A foreign plant with a 50 % roll is grown on every one of twenty tries — the roll is retried; a foreign
	 * plant that always refuses (a hard condition) is never grown and reports that nothing grew.
	 *
	 * @implements MOD-906 — a foreign plant's roll is retried, its hard condition is not bypassed.
	 */
	public static void foreignPlantRollIsRetried(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(2, FLOOR + 1, 2));
		helper.setBlock(new BlockPos(2, FLOOR + 1, 2), Blocks.STONE);
		BlockState state = level.getBlockState(pos);
		AtomicInteger performed = new AtomicInteger();
		int tries = 20;
		for (int i = 0; i < tries; i++) {
			if (!ReinforcedBonemeal.grow(StubPlants.rolling(0.5, performed), level, pos, state)) {
				helper.fail("try " + i + ": a plant with a 50 % roll was not grown");
				return;
			}
		}
		if (performed.get() != tries) {
			helper.fail("expected exactly one growth step per try (" + tries + "), got " + performed.get());
			return;
		}
		AtomicInteger refused = new AtomicInteger();
		if (ReinforcedBonemeal.grow(StubPlants.rolling(0.0, refused), level, pos, state) || refused.get() != 0) {
			helper.fail("a plant that never passes its roll was reported grown (steps " + refused.get() + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * A block in {@code #alaindustrial:reinforced_bone_meal_blacklist} (the gametest pack puts pink petals
	 * there) is not fertilised and the meal is kept.
	 *
	 * @implements MOD-906 — the blacklist tag refuses a block and keeps the meal.
	 */
	public static void blacklistedBlockIsLeftAlone(GameTestHelper helper) {
		ServerPlayer player = playerWithMeal(helper);
		BlockPos pos = new BlockPos(2, FLOOR + 1, 2);
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(pos, Blocks.PINK_PETALS);
		BlockState before = helper.getBlockState(pos);
		click(helper, player, pos);
		if (helper.getBlockState(pos) != before) {
			helper.fail("a blacklisted block was fertilised: " + helper.getBlockState(pos));
			return;
		}
		expectCount(helper, player, STACK, "the block is blacklisted");
	}

	/**
	 * A use context with no player — what a foreign deployer or machine builds — ripens a crop and spends the
	 * meal from the stack it was given.
	 *
	 * @implements MOD-906 — works through a {@code UseOnContext} without a real player.
	 */
	public static void worksWithoutAPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(2, FLOOR + 1, 2);
		helper.setBlock(rel.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(rel, Blocks.WHEAT);
		BlockPos abs = helper.absolutePos(rel);
		ItemStack stack = new ItemStack(ModContent.REINFORCED_BONE_MEAL.get(), 3);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		stack.useOn(new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack, hit) {
		});
		BlockState state = helper.getBlockState(rel);
		if (!((CropBlock) Blocks.WHEAT).isMaxAge(state)) {
			helper.fail("with no player the wheat is not ripe: " + state);
			return;
		}
		if (stack.getCount() != 2) {
			helper.fail("with no player the stack holds " + stack.getCount() + ", expected 2");
			return;
		}
		helper.succeed();
	}

	/**
	 * The meal is in {@code #c:fertilizers}, the common fertilizer tag of both loaders, so other mods' machines
	 * and recipes take it as fertilizer.
	 *
	 * @implements MOD-906 — the item is in the loaders' common fertilizer tag.
	 */
	public static void isInTheCommonFertilizerTag(GameTestHelper helper) {
		TagKey<Item> fertilizers = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "fertilizers"));
		if (!new ItemStack(ModContent.REINFORCED_BONE_MEAL.get()).is(fertilizers)) {
			helper.fail("reinforced bone meal is not in #c:fertilizers");
			return;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer playerWithMeal(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.REINFORCED_BONE_MEAL.get(), STACK));
		return player;
	}

	/** Right-click {@code pos} from above through {@code ServerPlayerGameMode.useItemOn}. */
	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
				hit);
	}

	private static void expectCount(GameTestHelper helper, ServerPlayer player, int expected, String why) {
		int count = player.getMainHandItem().getCount();
		if (count != expected) {
			helper.fail("the stack holds " + count + " meal, expected " + expected + " (" + why + ")");
			return;
		}
		helper.succeed();
	}

	/** Air over the whole rig above the floor, high enough for a birch's crown. */
	private static void clearAbove(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = FLOOR; y < FLOOR + 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}
}
