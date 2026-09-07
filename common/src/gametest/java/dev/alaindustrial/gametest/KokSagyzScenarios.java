package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

import java.util.Optional;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.KokSagyzBlock;
import dev.alaindustrial.block.KokSagyzRootBlock;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.core.crop.CropMaturity;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for kok sagyz (MOD-537) — the rubber dandelion that grows DOWN.
 * The Fabric {@code @GameTest} suite ({@code KokSagyzGameTest}) and the NeoForge
 * {@code gameTestServer} lane ({@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests}) run
 * the SAME bodies, so the crop behaves identically on both loaders.
 *
 * <p><b>Why every growth step is driven by bone meal, not random ticks.</b> The 8³ rig is a closed
 * box and the flower's {@code randomTick} refuses to grow below light 9 — inside a rig that gate is
 * not reliably passable, and the chance rolls are not reliably exhaustible. Bone meal
 * ({@code performBonemeal}) is deliberately NOT gated by light (verified against
 * {@link KokSagyzBlock#performBonemeal}: no brightness check) and applies exactly one growth step
 * per application with no chance roll, so every scenario here is deterministic on both loaders.
 * The light gate and the chance divisors of {@code randomTick} stay uncovered by this suite — see
 * the report note; what IS covered is the state machine itself (age stages, the two-deep root
 * column, the harvest/regrow cycle) through the same entry point the sprinkler uses.
 *
 * <p><b>Rig layout.</b> Every scenario plants at the same column: deep soil at {@code y = DEEP},
 * upper soil at {@code y = SOIL}, the flower at {@code POS} one above it. A full plant occupies
 * the flower at {@code POS}, the upper root ({@code tip=false}) at {@code SOIL} and the root tip
 * ({@code tip=true}) at {@code DEEP} — the tip grows INTO the deep soil block, which is why the
 * plant demands two blocks of soil up front ({@code canSurvive}).
 */
public final class KokSagyzScenarios {

	/** MOD-584: mixed soil survives sync serialization and the actual player harvest path. */
	public static void mod584SoilPersistsAndRestores(GameTestHelper helper) {
		soilColumn(helper, Blocks.RED_SAND, Blocks.SAND);
		helper.setBlock(POS, flower(3));
		bonemeal(helper, POS);
		bonemeal(helper, POS);
		for (int depth = 1; depth <= 2; depth++) {
			BlockPos pos = helper.absolutePos(POS.below(depth));
			var root = (dev.alaindustrial.block.entity.KokSagyzRootBlockEntity) helper.getLevel().getBlockEntity(pos);
			BlockState expected = (depth == 1 ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
			var copy = new dev.alaindustrial.block.entity.KokSagyzRootBlockEntity(pos, root.getBlockState());
			copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
					net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), root.getUpdateTag(helper.getLevel().registryAccess())));
			if (!root.soil().equals(expected) || !copy.soil().equals(expected)) {
				helper.fail("Root soil was lost at depth " + depth); return;
			}
		}
		ServerPlayer player = survivalPlayer(helper);
		player.gameMode.destroyBlock(helper.absolutePos(POS.below(2)));
		if (!helper.getBlockState(POS.below(2)).is(Blocks.SAND)) {
			helper.fail("Harvest changed sand to another block"); return;
		}
		bonemeal(helper, POS);
		if (!((dev.alaindustrial.block.entity.KokSagyzRootBlockEntity) helper.getLevel()
				.getBlockEntity(helper.absolutePos(POS.below(2)))).soil().is(Blocks.SAND)) {
			helper.fail("Regrowth lost the restored soil"); return;
		}
		helper.succeed();
	}

	/** MOD-584: shallow soil yields one harvestable tip; repeated scythe use cannot duplicate it. */
	public static void mod584ShortRootScythe(GameTestHelper helper) {
		soilColumn(helper, Blocks.DIRT, Blocks.STONE);
		helper.setBlock(POS, flower(3));
		bonemeal(helper, POS);
		var column = dev.alaindustrial.block.KokSagyzRoots.inspect(helper.getLevel(), helper.absolutePos(POS));
		if (column.depth() != 1 || !column.harvestable() || column.growing()) {
			helper.fail("Shallow column did not become a ready short tip"); return;
		}
		ServerPlayer player = survivalPlayer(helper);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.SCYTHE_WOOD.get()));
		useOn(helper, player, POS);
		useOn(helper, player, POS);
		if (countDrops(helper, ModContent.KOK_SAGYZ_ROOT_ITEM.get()) != 1 || !helper.getBlockState(POS.below()).is(Blocks.DIRT)) {
			helper.fail("Short-tip scythe harvest failed or duplicated the yield"); return;
		}
		if (!helper.getBlockState(POS).is(ModContent.KOK_SAGYZ.get())) {
			helper.fail("Short-root harvest destroyed the flower"); return;
		}
		helper.succeed();
	}

	/** MOD-584: an absent upper segment never appears in the inspection snapshot. */
	public static void mod584MissingSegmentAndLegacy(GameTestHelper helper) {
		fullPlant(helper, 3);
		ServerPlayer player = survivalPlayer(helper);
		player.gameMode.destroyBlock(helper.absolutePos(POS.below()));
		var column = dev.alaindustrial.block.KokSagyzRoots.inspect(helper.getLevel(), helper.absolutePos(POS));
		if (column.upper() || !column.lower() || column.depth() != 2) {
			helper.fail("Inspection invented an upper root after harvesting it"); return;
		}
		var legacy = new dev.alaindustrial.block.entity.KokSagyzRootBlockEntity(helper.absolutePos(POS.below(2)), helper.getBlockState(POS.below(2)));
		legacy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag()));
		if (!legacy.soil().is(Blocks.DIRT)) { helper.fail("Legacy root did not default to dirt"); return; }
		helper.succeed();
	}

	/**
	 * MOD-584: coming down on the flower costs one stage; crossing it at ground level costs
	 * nothing, and neither does a sneaking descent. Each leg drives the same public entry the
	 * {@code entityInside} hook uses, with the entity's own before/after height doing the talking.
	 */
	public static void mod584Trampling(GameTestHelper helper) {
		fullPlant(helper, KokSagyzBlock.AGE_MATURE);
		BlockPos abs = helper.absolutePos(POS);
		ServerPlayer player = survivalPlayer(helper);
		double top = abs.getY() + 1.0;

		// 1. Crossing at ground level: the feet never cross the top face, so nothing happens.
		if (stomp(helper, abs, player, top + 0.2, top + 0.1) || age(helper) != KokSagyzBlock.AGE_MATURE) {
			helper.fail("Walking above the flower trampled it"); return;
		}
		if (stomp(helper, abs, player, top - 0.1, top - 0.2) || age(helper) != KokSagyzBlock.AGE_MATURE) {
			helper.fail("Walking through the flower trampled it"); return;
		}

		// 2. Sneaking down through it is deliberately free — the same key opens the inspection.
		player.setShiftKeyDown(true);
		if (stomp(helper, abs, player, top + 0.5, top - 0.3) || age(helper) != KokSagyzBlock.AGE_MATURE) {
			helper.fail("A sneaking descent trampled the flower"); return;
		}
		player.setShiftKeyDown(false);

		// 3. Landing on it: exactly one stage per descent, down to the rosette and no further.
		for (int expected = KokSagyzBlock.AGE_MATURE - 1; expected >= KokSagyzBlock.AGE_ROSETTE; expected--) {
			if (!stomp(helper, abs, player, top + 0.5, top - 0.3) || age(helper) != expected) {
				helper.fail("Landing should have left age " + expected + ", got " + age(helper)); return;
			}
		}
		if (stomp(helper, abs, player, top + 0.5, top - 0.3) || age(helper) != KokSagyzBlock.AGE_ROSETTE) {
			helper.fail("Trampling pushed the plant below the rosette"); return;
		}

		// 4. Nothing underground ever moved.
		if (!helper.getBlockState(POS.below()).is(ModContent.KOK_SAGYZ_ROOT.get())
				|| !helper.getBlockState(POS.below(2)).getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("Trampling the flower disturbed the root column"); return;
		}
		helper.succeed();
	}

	/**
	 * One descent: put the entity where it was at the start of the tick and where it is now, then
	 * drive the same entry {@code entityInside} uses. {@code yOld} is assigned after the move
	 * because positioning the entity rewrites it.
	 */
	private static boolean stomp(GameTestHelper helper, BlockPos abs, ServerPlayer player,
			double from, double to) {
		player.setPos(player.getX(), to, player.getZ());
		player.yOld = from;
		return KokSagyzBlock.trample(helper.getLevel().getBlockState(abs), helper.getLevel(), abs, player);
	}

	private static int age(GameTestHelper helper) {
		return helper.getBlockState(POS).getValue(KokSagyzBlock.AGE);
	}

	/**
	 * MOD-584: the growth ladder rises step by step, and the harvestable tip is priced by being a
	 * tip rather than by how deep it lands. The second claim is the one worth a test: shallow ground
	 * mints its tip on the FIRST underground step, so pricing by step order would quietly make a
	 * one-block plot out-yield a full column.
	 */
	public static void mod584GrowthLadder(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(POS);
		ServerLevel level = helper.getLevel();

		// Flower stages: each one dearer than the last.
		soilColumn(helper, Blocks.DIRT, Blocks.DIRT);
		helper.setBlock(POS, flower(KokSagyzBlock.AGE_ROSETTE));
		int s1 = KokSagyzBlock.growthDivisor(KokSagyzBlock.AGE_ROSETTE);
		int s2 = KokSagyzBlock.growthDivisor(KokSagyzBlock.AGE_BUD);
		int s3 = KokSagyzBlock.growthDivisor(KokSagyzBlock.AGE_FLOWER);
		if (!(s1 < s2 && s2 < s3)) {
			helper.fail("Flower stages are not a rising ladder: " + s1 + ", " + s2 + ", " + s3); return;
		}

		// Ground: sand is the one soil that pays, tilling is worth nothing, and a rooted column
		// keeps the rate of the ground it was sunk into rather than of the root now under it.
		int dirt = KokSagyzBlock.groundPercent(level, abs);
		soilColumn(helper, Blocks.FARMLAND, Blocks.DIRT);
		if (KokSagyzBlock.groundPercent(level, abs) != dirt) {
			helper.fail("Tilling changed the growth rate; it must not"); return;
		}
		soilColumn(helper, Blocks.SAND, Blocks.SAND);
		int sand = KokSagyzBlock.groundPercent(level, abs);
		if (sand >= dirt) {
			helper.fail("Sand should grow it faster: sand=" + sand + ", dirt=" + dirt); return;
		}
		helper.setBlock(POS.below(), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(KokSagyzRootBlock.TIP, false));
		if (helper.getLevel().getBlockEntity(helper.absolutePos(POS.below()))
				instanceof dev.alaindustrial.block.entity.KokSagyzRootBlockEntity root) {
			root.setSoil(Blocks.SAND.defaultBlockState());
		}
		if (KokSagyzBlock.groundPercent(level, abs) != sand) {
			helper.fail("A column rooted in sand lost the sand rate once it took root"); return;
		}
		soilColumn(helper, Blocks.DIRT, Blocks.DIRT);

		// Deep column, no root yet: the next step is the intermediate root, the cheaper one.
		helper.setBlock(POS, flower(KokSagyzBlock.AGE_MATURE));
		if (KokSagyzBlock.nextRootIsTip(level, abs)) {
			helper.fail("Two blocks of soil should grow the intermediate root first"); return;
		}
		int upper = KokSagyzBlock.rootDivisor(level, abs);

		// Same column once the intermediate root stands: now the tip, and it must cost more.
		helper.setBlock(POS.below(), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(KokSagyzRootBlock.TIP, false));
		if (!KokSagyzBlock.nextRootIsTip(level, abs)) {
			helper.fail("A column over soil should grow the tip next"); return;
		}
		int deepTip = KokSagyzBlock.rootDivisor(level, abs);
		if (deepTip <= upper) {
			helper.fail("The tip must cost more than the intermediate root: " + deepTip + " vs " + upper); return;
		}

		// Shallow ground: soil over stone mints the tip immediately — at the TIP price, not the
		// cheap first-step price, or a one-block plot would out-yield the full column.
		soilColumn(helper, Blocks.FARMLAND, Blocks.STONE);
		if (!KokSagyzBlock.nextRootIsTip(level, abs)) {
			helper.fail("Soil over stone should mint the tip straight away"); return;
		}
		int shallowTip = KokSagyzBlock.rootDivisor(level, abs);
		if (shallowTip != deepTip) {
			helper.fail("Shallow ground priced its tip differently: " + shallowTip + " vs " + deepTip); return;
		}
		helper.succeed();
	}

	private KokSagyzScenarios() {
	}

	/** Deep soil layer: after full root growth this holds the root tip. */
	private static final int DEEP = 1;
	/** Upper soil layer: after the first root step this holds the upper root. */
	private static final int SOIL = 2;
	/** The flower itself. */
	private static final BlockPos POS = new BlockPos(2, SOIL + 1, 2);
	/** A second column, clear of {@link #POS}, where the worldgen feature is run. */
	private static final BlockPos FEAT_POS = new BlockPos(6, 2, 6);

	// ── 1. age stages ──────────────────────────────────────────────────────────────────────────────

	/**
	 * A seed planted on farmland (with a second soil block below it, as {@code canSurvive} demands)
	 * walks AGE 0 → 3 through one bone-meal step each, and only the mature plant begins to root.
	 * Mirrors: KokSagyzGameTest.mod537_stagesAdvance
	 */
	public static void mod537StagesAdvance(GameTestHelper helper) {
		soilColumn(helper, Blocks.FARMLAND, Blocks.DIRT);
		helper.setBlock(POS, flower(0));
		for (int expectedAge = 1; expectedAge <= KokSagyzBlock.AGE_MATURE; expectedAge++) {
			bonemeal(helper, POS);
			int age = helper.getBlockState(POS).getValue(KokSagyzBlock.AGE);
			if (age != expectedAge) {
				helper.fail("bone meal step " + expectedAge + " left the plant at age " + age);
				return;
			}
		}
		// Age 3 must not have grown a root on its own — rooting is a separate step, not a side effect
		// of the final age-up (the age branch returns before growRoot in randomTick too).
		if (helper.getBlockState(POS.below()).is(ModContent.KOK_SAGYZ_ROOT.get())) {
			helper.fail("the age-up to mature grew a root — rooting must be its own bone-meal step");
			return;
		}
		helper.succeed();
	}

	// ── 2. the root column ─────────────────────────────────────────────────────────────────────────

	/**
	 * A mature plant grows its root exactly two deep: the first step turns the upper soil into
	 * {@code kok_sagyz_root[tip=false]}, the second turns the deep soil into {@code tip=true}. A
	 * third application on the full column must do nothing (nothing left to reach).
	 * Mirrors: KokSagyzGameTest.mod537_rootGrowsTwoDeep
	 */
	public static void mod537RootGrowsTwoDeep(GameTestHelper helper) {
		soilColumn(helper, Blocks.FARMLAND, Blocks.DIRT);
		helper.setBlock(POS, flower(KokSagyzBlock.AGE_MATURE));

		bonemeal(helper, POS);
		BlockState upper = helper.getBlockState(POS.below());
		if (!upper.is(ModContent.KOK_SAGYZ_ROOT.get()) || upper.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("first root step left " + upper + " below the flower, expected root[tip=false]");
			return;
		}

		bonemeal(helper, POS);
		BlockState tip = helper.getBlockState(POS.below(2));
		if (!tip.is(ModContent.KOK_SAGYZ_ROOT.get()) || !tip.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("second root step left " + tip + " two below the flower, expected root[tip=true]");
			return;
		}

		// The full column is done: bone meal on it must be a no-op (isValidBonemealTarget is false,
		// and growRoot itself must also refuse for the sprinkler's sake).
		if (ModContent.KOK_SAGYZ.get() instanceof KokSagyzBlock block
				&& block.isValidBonemealTarget(helper.getLevel(), helper.absolutePos(POS),
						helper.getBlockState(POS))) {
			helper.fail("bone meal still considers the full column a valid target");
			return;
		}
		helper.succeed();
	}

	// ── 3. harvesting the tip ──────────────────────────────────────────────────────────────────────

	/**
	 * Digging the tip (the player break path, {@code gameMode.destroyBlock}, so
	 * {@code KokSagyzRootBlock.playerDestroy} runs) pays exactly one root item, refills the hole
	 * with dirt and leaves the plant standing. The root entry of the tip's loot table is
	 * unconditional, so the count is exact; the 35 % seed chance is deliberately not asserted.
	 * Mirrors: KokSagyzGameTest.mod537_tipBreakDropsRootAndKeepsFlower
	 */
	public static void mod537TipBreakDropsRootAndKeepsFlower(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		fullPlant(helper, KokSagyzBlock.AGE_MATURE);

		player.gameMode.destroyBlock(helper.absolutePos(POS.below(2)));

		int roots = countDrops(helper, ModContent.KOK_SAGYZ_ROOT_ITEM.get());
		if (roots != 1) {
			helper.fail("digging the tip dropped " + roots + " root items, expected exactly 1");
			return;
		}
		if (!helper.getBlockState(POS.below(2)).is(Blocks.DIRT)) {
			helper.fail("the harvested tip hole is " + helper.getBlockState(POS.below(2))
					+ ", expected dirt (the dig refills the ground)");
			return;
		}
		assertPlantIntact(helper, POS, "tip harvest");
		helper.succeed();
	}

	// ── 4. the tip regrows ─────────────────────────────────────────────────────────────────────────

	/**
	 * The perennial contract: after a tip harvest the same plant regrows its tip (one bone-meal root
	 * step into the refilled dirt) and pays out a second time. Two full harvest cycles must yield
	 * exactly two root items — the plant is a farm, not a one-shot ore.
	 * Mirrors: KokSagyzGameTest.mod537_tipRegrows
	 */
	public static void mod537TipRegrows(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		fullPlant(helper, KokSagyzBlock.AGE_MATURE);

		player.gameMode.destroyBlock(helper.absolutePos(POS.below(2)));
		bonemeal(helper, POS); // the refilled dirt is soil again → one step regrows the tip

		BlockState tip = helper.getBlockState(POS.below(2));
		if (!tip.is(ModContent.KOK_SAGYZ_ROOT.get()) || !tip.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("the tip did not regrow into the refilled hole; found " + tip);
			return;
		}
		assertPlantIntact(helper, POS, "tip regrow");

		player.gameMode.destroyBlock(helper.absolutePos(POS.below(2)));
		int roots = countDrops(helper, ModContent.KOK_SAGYZ_ROOT_ITEM.get());
		if (roots != 2) {
			helper.fail("two harvest cycles dropped " + roots + " root items, expected exactly 2");
			return;
		}
		helper.succeed();
	}

	// ── 5. digging the upper root keeps the flower and regrows ────────────────────────────────────

	/**
	 * Digging the UPPER root ({@code tip=false}) is a harvest, not plant murder (owner round 7):
	 * the hole refills with dirt, no root item is minted — the payoff lives at the tip — and the
	 * flower is left standing on that fresh dirt, so it roots into it again.
	 *
	 * <p>The regrowth is asserted too, and it is the point of the test rather than a flourish: the
	 * new root must come back as the UPPER root, not as a second {@code tip=true} on top of the tip
	 * still sitting two down. {@code growRoot} decides that from what lies two blocks below, and
	 * before round 7 that lookup only knew about soil — which was invisible while digging the upper
	 * root killed the plant outright and no regrowth could ever happen.
	 * Mirrors: KokSagyzGameTest.mod537_upperRootBreakKeepsFlower
	 */
	public static void mod537UpperRootBreakKeepsFlower(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		fullPlant(helper, KokSagyzBlock.AGE_MATURE);

		player.gameMode.destroyBlock(helper.absolutePos(POS.below()));

		BlockState flower = helper.getBlockState(POS);
		if (!flower.is(ModContent.KOK_SAGYZ.get()) || flower.getValue(KokSagyzBlock.AGE) != KokSagyzBlock.AGE_MATURE) {
			helper.fail("digging the upper root took the flower with it: " + flower);
			return;
		}
		if (!helper.getBlockState(POS.below()).is(Blocks.DIRT)) {
			helper.fail("the upper-root hole is " + helper.getBlockState(POS.below()) + ", expected dirt");
			return;
		}
		if (countDrops(helper, ModContent.KOK_SAGYZ_ROOT_ITEM.get()) != 0) {
			helper.fail("digging the upper root paid out a root item — the payoff lives at the tip only");
			return;
		}
		BlockState tip = helper.getBlockState(POS.below(2));
		if (!tip.is(ModContent.KOK_SAGYZ_ROOT.get()) || !tip.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("the tip two down is " + tip + ", expected it to stay untouched");
			return;
		}

		bonemeal(helper, POS);
		BlockState regrown = helper.getBlockState(POS.below());
		if (!regrown.is(ModContent.KOK_SAGYZ_ROOT.get())) {
			helper.fail("the plant did not root into the refilled dirt: " + regrown);
			return;
		}
		if (regrown.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("the regrown upper root came back as a TIP, stacking two tips in one column");
			return;
		}
		helper.succeed();
	}

	// ── 6. breaking the flower pays no root and leaves the column in the ground ───────────────────

	/**
	 * Breaking the flower drops (at a chance) seeds and never the root item — the harvestable part
	 * is underground, so killing the plant above ground must not mint rubber.
	 *
	 * <p>And the column <b>stays</b> (owner round 5): both root blocks are still there afterwards.
	 * Until this round the root block demanded the plant above and folded the whole column into
	 * dirt the moment the flower went — from the player's side the roots vanished out of the ground
	 * they were dug into, which reads as a bug rather than as death. They now outlive the flower;
	 * they simply stop regrowing, because regrowth rides the flower's {@code randomTick}.
	 * Mirrors: KokSagyzGameTest.mod537_flowerBreakGivesNoRoot
	 */
	public static void mod537FlowerBreakGivesNoRoot(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		fullPlant(helper, KokSagyzBlock.AGE_MATURE);

		player.gameMode.destroyBlock(helper.absolutePos(POS));

		if (!helper.getBlockState(POS).isAir()) {
			helper.fail("the flower survived its own break: " + helper.getBlockState(POS));
			return;
		}
		if (countDrops(helper, ModContent.KOK_SAGYZ_ROOT_ITEM.get()) != 0) {
			helper.fail("breaking the FLOWER dropped a root item — rubber must only come from the tip");
			return;
		}
		BlockState upper = helper.getBlockState(POS.below());
		if (!upper.is(ModContent.KOK_SAGYZ_ROOT.get()) || upper.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("the upper root is " + upper
					+ " after the flower broke, expected it to stay in the ground");
			return;
		}
		BlockState tip = helper.getBlockState(POS.below(2));
		if (!tip.is(ModContent.KOK_SAGYZ_ROOT.get()) || !tip.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("the root tip is " + tip
					+ " after the flower broke, expected it to stay in the ground");
			return;
		}
		helper.succeed();
	}

	// ── 7. planting takes one block of any soil ───────────────────────────────────────────────────

	/**
	 * The soil contract (owner round 4): ONE block of any ground is enough, and the root adapts its
	 * depth to what is underneath — a lone block over stone grows the tip straight under the flower.
	 * Every accepted soil is walked, {@code GRASS_BLOCK} first: 26.2 keeps grass, podzol, mycelium
	 * and mud OUT of {@code #minecraft:dirt}, so a predicate written as that tag alone passes a rig
	 * floored with plain dirt and refuses the whole overworld (MOD-537 round 5).
	 * Driven through {@code gameMode.useItemOn} so the real {@code BlockItem.place} →
	 * {@code canSurvive} path runs — asserting {@code canSurvive} directly would not catch a broken
	 * item placement.
	 * Mirrors: KokSagyzGameTest.mod537PlacementAcceptsAnySingleSoil
	 */
	public static void mod537PlacementAcceptsAnySingleSoil(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemStack seeds = new ItemStack(ModContent.KOK_SAGYZ_SEEDS.get(), 8);
		player.setItemInHand(InteractionHand.MAIN_HAND, seeds);

		// A lone block of dirt over stone plants fine now, and bone meal grows the root straight
		// to a TIP under the flower — the adaptive shallow column (one dig, not two).
		helper.setBlock(POS.below(), Blocks.DIRT);
		helper.setBlock(POS.below(2), Blocks.STONE);
		useOn(helper, player, POS.below());
		if (!helper.getBlockState(POS).is(ModContent.KOK_SAGYZ.get())) {
			helper.fail("the seed refused a single block of dirt over stone; found "
					+ helper.getBlockState(POS));
			return;
		}
		bonemeal(helper, POS);
		bonemeal(helper, POS);
		bonemeal(helper, POS);
		bonemeal(helper, POS); // 3 stages of age + one root step
		BlockState below = helper.getBlockState(POS.below());
		if (!(below.is(ModContent.KOK_SAGYZ_ROOT.get()) && below.getValue(KokSagyzRootBlock.TIP))) {
			helper.fail("shallow ground grew " + below + ", expected a root TIP directly under the flower");
			return;
		}

		// The rest of the soil family, one seed each. GRASS_BLOCK leads on purpose: 26.2 keeps it
		// (and podzol/mycelium/mud) OUT of #minecraft:dirt, so a soil predicate written as "#dirt"
		// alone refuses the surface of every plain while a rig floored with plain dirt — the case
		// above — stays green. That is exactly how the round-5 bug reached the owner's world.
		for (Block soil : new Block[] { Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD,
				Blocks.SAND, Blocks.FARMLAND }) {
			helper.setBlock(POS, Blocks.AIR);
			helper.setBlock(POS.below(), soil);
			useOn(helper, player, POS.below());
			BlockState planted = helper.getBlockState(POS);
			if (!planted.is(ModContent.KOK_SAGYZ.get())) {
				helper.fail("the seed refused " + soil.defaultBlockState() + "; found " + planted);
				return;
			}
			if (planted.getValue(KokSagyzBlock.AGE) != KokSagyzBlock.AGE_ROSETTE) {
				helper.fail("planting on " + soil.defaultBlockState() + " gave age "
						+ planted.getValue(KokSagyzBlock.AGE) + ", expected a fresh rosette");
				return;
			}
		}
		if (seeds.getCount() != 1) {
			helper.fail("planting consumed " + (8 - seeds.getCount()) + " seeds, expected exactly 7");
			return;
		}
		helper.succeed();
	}

	// ── 7b. the worldgen entry point plants on real ground ─────────────────────────────────────────

	/**
	 * The wild-flower path on the surface the overworld actually has: the shipped configured
	 * feature ({@code alaindustrial:kok_sagyz}, a bare {@code simple_block}) runs through
	 * {@code SimpleBlockFeature} onto a GRASS block and must leave a mature flower standing.
	 *
	 * <p>This is the check the round-5 bug walked past. {@code SimpleBlockFeature} places only
	 * where {@code canSurvive} holds, so a soil predicate that rejects grass kills every wild
	 * flower at the instant of placement — no exception, no log line, just an empty world — while
	 * a rig floored with plain dirt stays green. It also covers the data file: a wrong id or a
	 * stale block state in the configured-feature JSON fails the lookup here instead of in a
	 * player's world.
	 *
	 * <p>Only the CONFIGURED layer is asserted, on purpose. The placed feature's modifier chain
	 * (rarity, in_square, heightmap) cannot be judged inside an 8³ rig: {@code in_square} throws
	 * the position anywhere in the surrounding 16×16 chunk and {@code heightmap} answers with the
	 * rig's own roof, so a verdict there would be about the rig, not the mod.
	 * Mirrors: KokSagyzGameTest.mod537_worldgenFeaturePlantsOnGrass
	 */
	public static void mod537WorldgenFeaturePlantsOnGrass(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(FEAT_POS.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(FEAT_POS.below(2), Blocks.DIRT);

		var configured = level.registryAccess()
				.lookupOrThrow(Registries.CONFIGURED_FEATURE)
				.getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE,
						Industrialization.id("kok_sagyz")));
		boolean placed = configured.value().place(level, level.getChunkSource().getGenerator(),
				RandomSource.create(1L), helper.absolutePos(FEAT_POS));
		BlockState grown = helper.getBlockState(FEAT_POS);
		if (!placed || !grown.is(ModContent.KOK_SAGYZ.get())) {
			helper.fail("the worldgen feature refused grass: placed=" + placed + " state=" + grown
					+ " — a wild flower dies the moment it is generated if isSoil rejects grass_block");
			return;
		}
		if (grown.getValue(KokSagyzBlock.AGE) != KokSagyzBlock.AGE_MATURE) {
			helper.fail("the wild flower generated at age " + grown.getValue(KokSagyzBlock.AGE)
					+ ", expected a mature plant (age " + KokSagyzBlock.AGE_MATURE + ")");
			return;
		}
		helper.succeed();
	}

	// ── 8. the maceration recipe ──────────────────────────────────────────────────────────────────

	/**
	 * The rubber entry point resolves against the REAL server recipe manager: 2× kok_sagyz_root →
	 * 1× raw_rubber plus 1× inulin as the secondary, 300 EU, kind maceration. Taken out of the
	 * {@code RecipeManager} rather than a hand-built {@code AlaProcessingRecipe} so a broken JSON or
	 * a serializer regression fails here instead of silently shipping a dead recipe.
	 * Mirrors: KokSagyzGameTest.mod537_macerationYieldsRubberAndInulin
	 */
	public static void mod537MacerationYieldsRubberAndInulin(GameTestHelper helper) {
		Optional<RecipeHolder<?>> found = helper.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, Industrialization.id("maceration/kok_sagyz_root")));
		if (found.isEmpty()) {
			helper.fail("the kok_sagyz_root maceration recipe did not load on this loader");
			return;
		}
		if (!(found.get().value() instanceof AlaProcessingRecipe recipe)) {
			helper.fail("the kok_sagyz_root recipe is not an AlaProcessingRecipe: "
					+ found.get().value().getClass());
			return;
		}
		if (recipe.kind() != ModRecipes.MACERATION) {
			helper.fail("the kok_sagyz_root recipe has kind " + recipe.kind()
					+ ", expected maceration (the macerator would never find it otherwise)");
			return;
		}
		if (recipe.inputCount(0) != 2) {
			helper.fail("the recipe consumes " + recipe.inputCount(0) + " roots per operation, expected 2");
			return;
		}
		ItemStack result = recipe.resultStack();
		if (!result.is(ModContent.RAW_RUBBER.get()) || result.getCount() != 1) {
			helper.fail("the recipe's primary result is " + result + ", expected exactly 1 raw_rubber");
			return;
		}
		ItemStack secondary = recipe.secondaryResultStack();
		if (!secondary.is(ModContent.INULIN.get()) || secondary.getCount() != 1) {
			helper.fail("the recipe's secondary result is " + secondary + ", expected exactly 1 inulin");
			return;
		}
		if (recipe.energy() != 300) {
			helper.fail("the recipe costs " + recipe.energy() + " EU, expected 300");
			return;
		}
		helper.succeed();
	}

	// ── 9. the scythe harvests the tip ────────────────────────────────────────────────────────────

	/**
	 * Scythe crop mode (shift + right-click) on a mature kok sagyz digs the TIP, not the flower: the
	 * tip hole becomes dirt, exactly one root item drops and the plant stays standing for the next
	 * cycle. The maturity gate itself is pinned directly too: {@link CropMaturity#isHarvestable}
	 * must be true only at AGE 3 (the flower carries its own {@code age} property and sits in
	 * {@code #alaindustrial:scythe_crops}), so the scythe and the drone never cut a plant whose
	 * root has not started.
	 * Mirrors: KokSagyzGameTest.mod537_scytheHarvestsTip
	 */
	public static void mod537ScytheHarvestsTip(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setShiftKeyDown(true); // crop mode
		fullPlant(helper, 2); // immature: AGE 2, no root yet
		BlockState immature = helper.getBlockState(POS);
		if (CropMaturity.isHarvestable(helper.getLevel(), helper.absolutePos(POS), immature)) {
			helper.fail("an AGE-2 kok sagyz counts as harvestable — the gate must require the mature stage");
			return;
		}

		// The same column grown to maturity with its full root.
		fullPlant(helper, KokSagyzBlock.AGE_MATURE);
		BlockState mature = helper.getBlockState(POS);
		if (!CropMaturity.isHarvestable(helper.getLevel(), helper.absolutePos(POS), mature)) {
			helper.fail("a mature kok sagyz is NOT harvestable — it is missing from the scythe_crops tag"
					+ " or CropMaturity cannot read its AGE property");
			return;
		}

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.SCYTHE_WOOD.get()));
		useOn(helper, player, POS);

		if (!helper.getBlockState(POS.below(2)).is(Blocks.DIRT)) {
			helper.fail("the scythe did not dig the tip: " + helper.getBlockState(POS.below(2)));
			return;
		}
		int roots = countDrops(helper, ModContent.KOK_SAGYZ_ROOT_ITEM.get());
		if (roots != 1) {
			helper.fail("the scythe harvest dropped " + roots + " root items, expected exactly 1");
			return;
		}
		assertPlantIntact(helper, POS, "scythe harvest");
		helper.succeed();
	}

	// ── 11. the drone cuts the flower for seeds and never reaches the root ────────────────────────

	/**
	 * The garden drone's kok-sagyz job (owner round 8): cut the ripe flower, bank whatever its loot
	 * table pays, and leave the roots alone. A drone that hovers over a field has no business digging
	 * two blocks underground — the root is hand work — and the station briefly did exactly that.
	 *
	 * <p>The assertions are the deterministic half on purpose. The flower's seed drop is a 35 % roll,
	 * so "seeds landed in the output" would flake two runs in three; what must hold every time is
	 * that the flower is gone, that no ROOT item was ever minted, and that both root blocks are still
	 * in the ground where the player left them.
	 * Mirrors: KokSagyzGameTest.mod537_droneCutsFlowerNotRoot
	 */
	public static void mod537DroneCutsFlowerNotRoot(GameTestHelper helper) {
		int configuredRange = Config.gardenDroneRange;
		int configuredFlight = Config.gardenDroneFlightTicksPerBlock;
		Config.gardenDroneRange = 1;
		Config.gardenDroneFlightTicksPerBlock = 0;
		try {
			BlockPos stationPos = new BlockPos(1, SOIL, 1);
			helper.setBlock(stationPos, ModContent.GARDEN_DRONE_STATION.get());
			GardenDroneStationBlockEntity station =
					helper.getBlockEntity(stationPos, GardenDroneStationBlockEntity.class);
			station.setItem(GardenDroneStationBlockEntity.DRONE_SLOT,
					new ItemStack(ModContent.GARDEN_DRONE.get()));
			station.setItem(GardenDroneStationBlockEntity.HOE_SLOT, new ItemStack(Items.IRON_HOE));
			station.getEnergyStorage().setAmountUntracked(Config.gardenDroneBuffer);

			// The column, one tile east of the station (same z, so Chebyshev distance 1 at every
			// block of the column): deep dirt, upper soil, mature flower with both roots grown.
			BlockPos flowerPos = new BlockPos(2, SOIL + 1, 1);
			helper.setBlock(flowerPos.below(2), Blocks.DIRT);
			helper.setBlock(flowerPos.below(), Blocks.FARMLAND);
			helper.setBlock(flowerPos, flower(KokSagyzBlock.AGE_MATURE));
			growRootAt(helper, flowerPos);
			growRootAt(helper, flowerPos);

			int ticksPerJob = GardenDroneStationBlockEntity.MIN_FLIGHT_TICKS + 2;
			AlaGameTestHelper.drive(station, helper, ticksPerJob);

			if (helper.getBlockState(flowerPos).is(ModContent.KOK_SAGYZ.get())) {
				helper.fail("the drone left the ripe flower standing: " + helper.getBlockState(flowerPos));
				return;
			}
			if (stationHolds(station, ModContent.KOK_SAGYZ_ROOT_ITEM.get())) {
				helper.fail("the drone banked a ROOT item — it cannot dig two blocks down, that is hand work");
				return;
			}
			BlockState upper = helper.getBlockState(flowerPos.below());
			if (!upper.is(ModContent.KOK_SAGYZ_ROOT.get()) || upper.getValue(KokSagyzRootBlock.TIP)) {
				helper.fail("the upper root is " + upper + ", expected it untouched in the ground");
				return;
			}
			BlockState tip = helper.getBlockState(flowerPos.below(2));
			if (!tip.is(ModContent.KOK_SAGYZ_ROOT.get()) || !tip.getValue(KokSagyzRootBlock.TIP)) {
				helper.fail("the root tip is " + tip + ", expected it untouched in the ground");
				return;
			}
			helper.succeed();
		} finally {
			Config.gardenDroneRange = configuredRange;
			Config.gardenDroneFlightTicksPerBlock = configuredFlight;
		}
	}

	// ── helpers ────────────────────────────────────────────────────────────────────────────────────

	/** The flower's blockstate at a given age (placement checks are the scenario's own business). */
	private static BlockState flower(int age) {
		return ModContent.KOK_SAGYZ.get().defaultBlockState().setValue(KokSagyzBlock.AGE, age);
	}

	/** Two blocks of soil: {@code upper} directly under the flower, plain dirt below it. */
	private static void soilColumn(GameTestHelper helper, net.minecraft.world.level.block.Block upper,
			net.minecraft.world.level.block.Block deep) {
		helper.setBlock(POS.below(), upper);
		helper.setBlock(POS.below(2), deep);
	}

	/**
	 * A full three-block plant on a farmland-over-dirt column: mature flower at {@code POS}, upper
	 * root at {@code SOIL}, root tip at {@code DEEP}. The states are placed directly rather than
	 * grown so every scenario starts from the same known column.
	 */
	private static void fullPlant(GameTestHelper helper, int age) {
		soilColumn(helper, Blocks.FARMLAND, Blocks.DIRT);
		helper.setBlock(POS, flower(age));
		helper.setBlock(POS.below(), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(KokSagyzRootBlock.TIP, false));
		helper.setBlock(POS.below(2), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(KokSagyzRootBlock.TIP, true));
	}

	/**
	 * One bone-meal step on the flower, via the block's real {@code performBonemeal}: deterministic
	 * (no chance roll, no light gate) and the exact path the sprinkler rides, so these scenarios
	 * double as coverage for the sprinkler's growth hook.
	 */
	private static void bonemeal(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		BlockState state = level.getBlockState(abs);
		((KokSagyzBlock) state.getBlock()).performBonemeal(level, RandomSource.create(0L), abs, state);
	}

	/** Grows the root column under the flower at {@code pos} one step (bone meal's mature branch). */
	private static void growRootAt(GameTestHelper helper, BlockPos pos) {
		bonemeal(helper, pos);
	}

	/** The flower and the upper root are still standing after a harvest of the tip. */
	private static void assertPlantIntact(GameTestHelper helper, BlockPos flowerPos, String after) {
		BlockState flowerState = helper.getBlockState(flowerPos);
		if (!flowerState.is(ModContent.KOK_SAGYZ.get())
				|| flowerState.getValue(KokSagyzBlock.AGE) != KokSagyzBlock.AGE_MATURE) {
			helper.fail("the " + after + " destroyed or reset the plant: " + flowerState);
			return;
		}
		BlockState upper = helper.getBlockState(flowerPos.below());
		if (!upper.is(ModContent.KOK_SAGYZ_ROOT.get()) || upper.getValue(KokSagyzRootBlock.TIP)) {
			helper.fail("the " + after + " damaged the upper root: " + upper);
		}
	}

	/** Total count of {@code item} lying around the column (same box the trellis suite uses). */
	private static int countDrops(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(POS)).inflate(2.0);
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	/**
	 * Right-clicks the TOP of {@code pos} with whatever the player holds — the real
	 * {@code gameMode.useItemOn} path, so item placement ({@code BlockItem.place}) runs exactly as
	 * it does for a player. Shift state is whatever the player currently reports (the scythe
	 * scenario sets it before calling).
	 */
	private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
				InteractionHand.MAIN_HAND, hit);
	}

	/** Whether any of the station's output slots holds {@code item} (same check the drone suite uses). */
	private static boolean stationHolds(GardenDroneStationBlockEntity station, Item item) {
		for (int i = 0; i < GardenDroneStationBlockEntity.OUTPUT_SLOT_COUNT; i++) {
			if (station.getItem(GardenDroneStationBlockEntity.OUTPUT_SLOT_START + i).is(item)) {
				return true;
			}
		}
		return false;
	}
}
