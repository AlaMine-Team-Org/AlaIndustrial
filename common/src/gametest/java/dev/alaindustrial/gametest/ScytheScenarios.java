package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.tool.ScytheItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for the scythe (MOD-068 / MOD-098). Each scenario is a plain
 * {@code Consumer<GameTestHelper>} using only vanilla {@code GameTestHelper} + loader-neutral content
 * ({@link ModContent}); the Fabric {@code @GameTest} suite ({@code ScytheGameTest}) and the NeoForge
 * {@code gameTestServer} lane ({@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests}) run the
 * SAME bodies, so both loaders exercise identical AOE logic.
 *
 * <p><b>Test rig.</b> The mock server player faces south by default ({@code yRot = 0}), so the AOE box
 * runs from the clicked block along {@code +z} (depth) and {@code ±x} (width). Every scenario lays a
 * dirt platform at {@code y = FLOOR} and plants foliage at {@code y = FLOOR + 1}, then right-clicks the
 * centre plant. {@link #makeSurvivalPlayer} forces SURVIVAL so drops and durability behave like a real
 * player (the raw {@code makeMockServerPlayerInLevel} mock reports CREATIVE, which suppresses both).
 *
 * <p><b>Two modes (MOD-098).</b> Plain right-click runs <b>decor</b> mode (clears
 * {@code scythe_harvestable} foliage, protects crops); shift + right-click runs <b>crop</b> mode
 * (harvests only mature {@code scythe_crops}, ignores decor). {@link #useScythe} / its shift variant
 * set {@code shiftKeyDown} to pick the mode.
 */
public final class ScytheScenarios {

	private ScytheScenarios() {
	}

	private static final int FLOOR = 1;
	private static final BlockPos CLICK = new BlockPos(2, FLOOR + 1, 2);

	// ── FUN01: clears foliage in the area, keeps solid blocks ─────────────────────────────────────

	/**
	 * A wood scythe right-clicked on a grass patch clears every {@code scythe_harvestable} block in its
	 * box while leaving a solid stone block (in the same box) and a grass block outside the box intact.
	 */
	public static void fun01ClearsFoliageKeepsSolids(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		platform(helper);
		// Fill the wood-scythe box (width 3 × depth 2) with short grass at x∈1..3, z∈2..3.
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.SHORT_GRASS);
			}
		}
		// One solid block inside the box must survive; one grass block outside the box must survive.
		helper.setBlock(new BlockPos(3, FLOOR + 1, 3), Blocks.STONE);
		BlockPos outside = new BlockPos(0, FLOOR + 1, 0);
		helper.setBlock(outside, Blocks.SHORT_GRASS);

		InteractionResult result = useScythe(helper, player, ModContent.SCYTHE_WOOD.get());
		if (!result.consumesAction()) {
			helper.fail("scythe use did not consume the action: " + result);
			return;
		}
		// The five grass blocks in the box are gone; the stone and the outside grass remain.
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				BlockPos p = new BlockPos(x, FLOOR + 1, z);
				if (p.equals(new BlockPos(3, FLOOR + 1, 3))) {
					continue;
				}
				if (!helper.getBlockState(p).isAir()) {
					helper.fail("expected grass cleared at " + p + " but found " + helper.getBlockState(p));
					return;
				}
			}
		}
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, FLOOR + 1, 3));
		helper.assertBlockPresent(Blocks.SHORT_GRASS, outside);
		helper.succeed();
	}

	// ── NEG01: shift on decor is crop mode → decor untouched, returns PASS ─────────────────────────

	/**
	 * With shift held the scythe enters crop mode, so decorative foliage (not a crop) is left alone and
	 * the swing returns PASS (no crop target present). Guards the crop-protection side of decor under
	 * crop mode (MOD-098): a farmer shift-clicking over grass clears nothing.
	 */
	public static void neg01ShiftCropModeKeepsDecor(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		platform(helper);
		helper.setBlock(CLICK, Blocks.SHORT_GRASS);
		helper.setBlock(new BlockPos(2, FLOOR + 1, 3), Blocks.SHORT_GRASS);

		InteractionResult result = useScytheShift(helper, player, ModContent.SCYTHE_WOOD.get());
		if (result != InteractionResult.PASS) {
			helper.fail("crop-mode use over plain decor should return PASS, got " + result);
			return;
		}
		helper.assertBlockPresent(Blocks.SHORT_GRASS, CLICK);
		helper.assertBlockPresent(Blocks.SHORT_GRASS, new BlockPos(2, FLOOR + 1, 3));
		helper.succeed();
	}

	// ── PRF01: durability drops by exactly the number of blocks broken ───────────────────────────

	/**
	 * Each broken foliage block costs one durability (MOD-098: flat 1/block in either mode, regardless
	 * of hardness). Uses leaves here; {@link #prf03DurabilityOnInstantBlock} covers the hardness-0 case
	 * (grass) that used to be free.
	 */
	public static void prf01DurabilityPerBlock(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		platform(helper);
		int expected = 0;
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.OAK_LEAVES);
				expected++;
			}
		}
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStack(helper, player);

		int damage = scythe.getDamageValue();
		if (damage != expected) {
			helper.fail("durability spent " + damage + " but broke " + expected + " leaves");
			return;
		}
		helper.succeed();
	}

	// ── PRF03: durability is spent even on hardness-0 (instant) blocks ─────────────────────────────

	/**
	 * The MOD-098 bug fix: short grass (hardness 0) used to be free under vanilla tool rules, so a
	 * scythe never wore clearing it. Now every broken block costs 1, so a box of {@code N} grass blocks
	 * must cost exactly {@code N} durability.
	 */
	public static void prf03DurabilityOnInstantBlock(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		platform(helper);
		int expected = 0;
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.SHORT_GRASS);
				expected++;
			}
		}
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStack(helper, player);

		int damage = scythe.getDamageValue();
		if (damage != expected) {
			helper.fail("durability spent " + damage + " but broke " + expected
					+ " hardness-0 grass blocks (should cost 1 each now)");
			return;
		}
		helper.succeed();
	}

	// ── PRF02: creative / instabuild spends no durability ──────────────────────────────────────────

	/** A creative player clears the area but the tool takes no durability damage. */
	public static void prf02CreativeNoDurability(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		player.setGameMode(GameType.CREATIVE);
		player.getAbilities().instabuild = true; // creative: break path must skip durability + drops
		platform(helper);
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.SHORT_GRASS);
			}
		}
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStack(helper, player);

		if (scythe.getDamageValue() != 0) {
			helper.fail("creative use spent durability: " + scythe.getDamageValue());
			return;
		}
		if (!helper.getBlockState(CLICK).isAir()) {
			helper.fail("creative use should still clear foliage");
			return;
		}
		helper.succeed();
	}

	// ── BVA01: never breaks more than the tier's max-blocks cap ────────────────────────────────────

	/** A dense area larger than the cap breaks exactly {@code maxBlocks} blocks and no more. */
	public static void bva01StopsAtMaxBlocks(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		// Fill the whole stone-tier 3×3×3 box (27 cells) with leaves — leaves need no support, so a solid
		// cube of 27 targets sits in the area, well over the cap of 18.
		int total = 0;
		for (int x = 1; x <= 3; x++) {
			for (int y = FLOOR; y <= FLOOR + 2; y++) {
				for (int z = 2; z <= 4; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LEAVES);
					total++;
				}
			}
		}
		Item scythe = ModContent.SCYTHE_STONE.get();
		int cap = ((ScytheItem) scythe).profile().maxBlocks();
		ItemStack stack = new ItemStack(scythe);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		useScytheStack(helper, player);

		int remaining = 0;
		for (int x = 1; x <= 3; x++) {
			for (int y = FLOOR; y <= FLOOR + 2; y++) {
				for (int z = 2; z <= 4; z++) {
					if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LEAVES)) {
						remaining++;
					}
				}
			}
		}
		int broken = total - remaining;
		if (broken != cap) {
			helper.fail("broke " + broken + " leaves, expected exactly the cap of " + cap);
			return;
		}
		if (stack.getDamageValue() != cap) {
			helper.fail("durability spent " + stack.getDamageValue() + ", expected the cap of " + cap);
			return;
		}
		helper.succeed();
	}

	// ── NEG02: crops and water are not harvested in decor mode ────────────────────────────────────

	/** Wheat and water are excluded from the decor tag and must survive an AOE over them (plain click). */
	public static void neg02KeepsCropsAndWater(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		platform(helper);
		helper.setBlock(CLICK, Blocks.SHORT_GRASS); // a valid target to trigger the AOE
		// Wheat needs farmland support, else a neighbour update (from clearing adjacent grass) pops it
		// on its own and the test can't tell that apart from the scythe breaking it.
		helper.setBlock(new BlockPos(1, FLOOR, 2), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(1, FLOOR + 1, 2), Blocks.WHEAT);
		helper.setBlock(new BlockPos(3, FLOOR + 1, 2), Blocks.WATER);

		useScythe(helper, player, ModContent.SCYTHE_WOOD.get());

		helper.assertBlockPresent(Blocks.WHEAT, new BlockPos(1, FLOOR + 1, 2));
		helper.assertBlockPresent(Blocks.WATER, new BlockPos(3, FLOOR + 1, 2));
		helper.succeed();
	}

	// ── FUN02: crop mode harvests mature crops ─────────────────────────────────────────────────────

	/**
	 * Shift + right-click (crop mode) over a patch of mature wheat harvests every mature wheat block in
	 * the box. Each mature crop also costs 1 durability, same rule as decor mode.
	 */
	public static void fun02CropModeHarvestsMature(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		farmland(helper);
		int mature = 0;
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				// Max-age wheat on farmland support.
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.WHEAT.defaultBlockState()
						.setValue(CropBlock.AGE, CropBlock.MAX_AGE));
				mature++;
			}
		}
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStackShift(helper, player);

		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				BlockPos p = new BlockPos(x, FLOOR + 1, z);
				if (!helper.getBlockState(p).isAir()) {
					helper.fail("expected mature wheat harvested at " + p + " but found " + helper.getBlockState(p));
					return;
				}
			}
		}
		if (scythe.getDamageValue() != mature) {
			helper.fail("crop-mode durability spent " + scythe.getDamageValue() + " but harvested " + mature + " mature wheat");
			return;
		}
		helper.succeed();
	}

	// ── NEG03: crop mode keeps immature crops ──────────────────────────────────────────────────────

	/**
	 * Crop mode never breaks a crop that is not yet mature. A box of age-0 wheat is left entirely
	 * untouched by a shift-click, so young plants keep growing — the AOE-sickle promise.
	 */
	public static void neg03CropModeKeepsImmature(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		farmland(helper);
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.WHEAT.defaultBlockState()
						.setValue(CropBlock.AGE, 0));
			}
		}
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStackShift(helper, player);

		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				BlockPos p = new BlockPos(x, FLOOR + 1, z);
				if (!helper.getBlockState(p).is(Blocks.WHEAT)) {
					helper.fail("immature wheat should survive crop mode at " + p + " but found " + helper.getBlockState(p));
					return;
				}
			}
		}
		if (scythe.getDamageValue() != 0) {
			helper.fail("crop mode spent durability on immature crops: " + scythe.getDamageValue());
			return;
		}
		helper.succeed();
	}

	// ── NEG04: crop mode keeps decorative foliage ──────────────────────────────────────────────────

	/**
	 * Crop mode does not touch decorative foliage: a grass block inside the box survives a shift-click.
	 * Only the two modes' target sets differ; this pins that crop mode is a strict subset (crops), not a
	 * superset.
	 */
	public static void neg04CropModeKeepsFoliage(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		platform(helper);
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.SHORT_GRASS);
			}
		}
		useScytheShift(helper, player, ModContent.SCYTHE_WOOD.get());
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				helper.assertBlockPresent(Blocks.SHORT_GRASS, new BlockPos(x, FLOOR + 1, z));
			}
		}
		helper.succeed();
	}

	// ── FUN03: crop mode harvests the stalk above a sugar-cane base, keeps the base ─────────────────

	/**
	 * Crop mode on a sugar-cane column breaks the block(s) above the base and leaves the base block
	 * alive, mirroring a hand-pick of the top of the cane. Two columns of height 2 (base + one stalk)
	 * fill the box; after a shift-click the top of each column is gone and the base remains, so the
	 * crop can regrow. Guards MOD-098 decision 1 (leave the base growing) and the bug fix that the
	 * naive version broke the base too.
	 */
	public static void fun03CropModeHarvestsCaneStalk(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		sand(helper); // sugar cane can survive on sand
		int stalks = 0;
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				// base at FLOOR+1 (on sand), one stalk block at FLOOR+2
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.SUGAR_CANE);
				helper.setBlock(new BlockPos(x, FLOOR + 2, z), Blocks.SUGAR_CANE);
				stalks++;
			}
		}
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStackShift(helper, player);

		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				BlockPos base = new BlockPos(x, FLOOR + 1, z);
				BlockPos stalk = new BlockPos(x, FLOOR + 2, z);
				if (!helper.getBlockState(base).is(Blocks.SUGAR_CANE)) {
					helper.fail("sugar cane base should survive at " + base + " but found " + helper.getBlockState(base));
					return;
				}
				if (!helper.getBlockState(stalk).isAir()) {
					helper.fail("sugar cane stalk above base should be harvested at " + stalk + " but found " + helper.getBlockState(stalk));
					return;
				}
			}
		}
		if (scythe.getDamageValue() != stalks) {
			helper.fail("crop-mode durability spent " + scythe.getDamageValue() + " but harvested " + stalks + " cane stalks");
			return;
		}
		helper.succeed();
	}

	// ── NEG05: crop mode keeps a lone cactus base (no stalk above it) ──────────────────────────────

	/**
	 * A single cactus block sitting on sand (its base, nothing above it) is never a harvest target:
	 * the stalk rule requires the same block below, and below a lone cactus is sand. Breaking it would
	 * kill the crop. Guards the base-protection side of the cactus/cane rule.
	 */
	public static void neg05CropModeKeepsLoneCactus(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		sand(helper);
		helper.setBlock(CLICK, Blocks.CACTUS); // lone cactus on sand — a base with no stalk
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStackShift(helper, player);

		helper.assertBlockPresent(Blocks.CACTUS, CLICK);
		if (scythe.getDamageValue() != 0) {
			helper.fail("crop mode spent durability on a lone cactus base: " + scythe.getDamageValue());
			return;
		}
		helper.succeed();
	}

	// ── NEG06: crop mode keeps melon/pumpkin stems (even ripe) ─────────────────────────────────────

	/**
	 * Crop mode never harvests a stem, even a fully-grown one. Stems are not a pickable crop — they
	 * spawn a melon/pumpkin on a neighbour and keep growing — so a ripe {@code melon_stem} (age 7) in
	 * the box must survive a shift-click. Guards the explicit {@code StemBlock} guard in
	 * {@code isCropTarget}, which sits before the AGE fallback precisely because a ripe stem carries
	 * AGE at max and would otherwise be caught there (the MOD-098 decision-2 bug).
	 */
	public static void neg06CropModeKeepsStem(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		farmland(helper);
		// Ripe melon stem: farmland support, age forced to max (7) so it is "grown" — and still must
		// NOT break. The max-age case is the one that would slip through to the AGE fallback.
		helper.setBlock(CLICK, Blocks.MELON_STEM.defaultBlockState()
				.setValue(StemBlock.AGE, StemBlock.MAX_AGE));
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStackShift(helper, player);

		if (!helper.getBlockState(CLICK).is(Blocks.MELON_STEM)) {
			helper.fail("ripe melon stem should survive crop mode at " + CLICK + " but found " + helper.getBlockState(CLICK));
			return;
		}
		if (scythe.getDamageValue() != 0) {
			helper.fail("crop mode spent durability on a stem: " + scythe.getDamageValue());
			return;
		}
		helper.succeed();
	}

	// ── FUN06: crop mode harvests torchflower_crop at its real max age (MOD-325) ───────────────────

	/**
	 * Crop mode harvests a mature {@code torchflower_crop} ({@code age = 1}, its actual top value) and
	 * drops exactly its deterministic loot: one {@code torchflower_seeds}, no binomial. Guards MOD-325:
	 * {@code torchflower_crop} is a {@link CropBlock} whose {@code AGE} property (vanilla
	 * {@code AGE_1}, range 0..1) never reaches {@code getMaxAge()} (2) — the next growth step replaces
	 * the block with {@code minecraft:torchflower} instead of incrementing {@code AGE}. Before the fix,
	 * {@code CropMaturity} trusted {@code CropBlock#isMaxAge}, which is unreachable for this crop, so it
	 * was never harvestable by the scythe or the drone. The fix reads the AGE property's own top value
	 * instead, same as every other tagged crop.
	 */
	public static void fun06CropModeHarvestsTorchflowerAtMaxAge(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		farmland(helper);
		helper.setBlock(CLICK, Blocks.TORCHFLOWER_CROP.defaultBlockState().setValue(TorchflowerCropBlock.AGE, 1));
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStackShift(helper, player);

		if (!helper.getBlockState(CLICK).isAir()) {
			helper.fail("mature torchflower_crop should be harvested at " + CLICK + " but found "
					+ helper.getBlockState(CLICK));
			return;
		}
		if (scythe.getDamageValue() != 1) {
			helper.fail("crop mode should spend exactly 1 durability on the torchflower harvest, spent "
					+ scythe.getDamageValue());
			return;
		}
		// The wood tier's bonusSeedChance is 0 (see fun04), so this stays exact: no bonus roll can add a
		// second seed on top of the loot table's deterministic one.
		int seeds = countDrops(helper, Items.TORCHFLOWER_SEEDS);
		if (seeds != 1) {
			helper.fail("torchflower_crop's loot table is deterministic (exactly 1 seed) — expected 1, got "
					+ seeds);
			return;
		}
		helper.succeed();
	}

	// ── NEG08: crop mode keeps immature torchflower_crop ────────────────────────────────────────────

	/**
	 * Crop mode never breaks an immature {@code torchflower_crop} ({@code age = 0}). Pins the other half
	 * of MOD-325: the AGE-property fallback must gate on the real top value (1), not silently harvest
	 * every age once the {@code CropBlock}-specific short-circuit is gone.
	 */
	public static void neg08CropModeKeepsImmatureTorchflower(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		farmland(helper);
		helper.setBlock(CLICK, Blocks.TORCHFLOWER_CROP.defaultBlockState().setValue(TorchflowerCropBlock.AGE, 0));
		ItemStack scythe = new ItemStack(ModContent.SCYTHE_WOOD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		useScytheStackShift(helper, player);

		if (!helper.getBlockState(CLICK).is(Blocks.TORCHFLOWER_CROP)) {
			helper.fail("immature torchflower_crop should survive crop mode at " + CLICK + " but found "
					+ helper.getBlockState(CLICK));
			return;
		}
		if (scythe.getDamageValue() != 0) {
			helper.fail("crop mode spent durability on immature torchflower_crop: " + scythe.getDamageValue());
			return;
		}
		helper.succeed();
	}

	// ── CON02: only the netherite tier is fire-resistant ───────────────────────────────────────────

	/** The netherite scythe carries DAMAGE_RESISTANT (survives lava) like vanilla netherite gear; the
	 * diamond tier does not. Guards the per-tier {@code fireResistant()} against regression. */
	public static void con02NetheriteFireResistant(GameTestHelper helper) {
		boolean netherite = new ItemStack(ModContent.SCYTHE_NETHERITE.get())
				.has(net.minecraft.core.component.DataComponents.DAMAGE_RESISTANT);
		boolean diamond = new ItemStack(ModContent.SCYTHE_DIAMOND.get())
				.has(net.minecraft.core.component.DataComponents.DAMAGE_RESISTANT);
		if (!netherite || diamond) {
			helper.fail("fire resistance wrong: netherite=" + netherite + " diamond=" + diamond
					+ " (expected netherite=true, diamond=false)");
			return;
		}
		helper.succeed();
	}

	// ── MOD-315: bonus seed drop ───────────────────────────────────────────────────────────────────

	/**
	 * A tier with no bonus (wood, {@code bonusSeedChance = 0}) yields only what the loot table gives.
	 *
	 * <h2>Why this is a threshold and not an exact count</h2>
	 * Wheat, carrot, potato and beetroot all roll their seeds from a binomial (0–3 per plant), so an
	 * exact assertion over this shared 3×2 patch is impossible. ({@code torchflower_crop} is the one
	 * deterministic loot table in the game — a single seed, no binomial, and harvestable since the
	 * MOD-325 fix — but it is exercised separately by {@link #fun06CropModeHarvestsTorchflowerAtMaxAge},
	 * which asserts the exact drop count; this patch stays wheat so the sample size here is unchanged.)
	 *
	 * <p>So the mechanic is instead driven to a <b>guaranteed</b> hit in
	 * {@link #fun05CropBonusAlwaysDropsAtFullChance} and sampled over {@link #BONUS_SWINGS} swings.
	 * That makes the two populations separate by about eight standard deviations — the threshold is
	 * not a p-value chosen to look significant, it is a gap wide enough that a false verdict is far
	 * less likely than a hardware fault. The probability arithmetic itself is covered exactly at L1
	 * by {@code ScytheBonusTest}; these scenarios only prove the roll is wired into the harvest on
	 * both loaders.
	 *
	 * <h2>Why this scenario cannot race the other one</h2>
	 * It asserts through a zero-chance tier, whose effective chance is {@code 0 × multiplier} — zero
	 * for every possible value of the global knob. So even if the runner batches this body alongside
	 * the scenario that mutates that shared static, this assertion still holds.
	 */
	public static void fun04CropBonusAbsentOnZeroChanceTier(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		int harvested = harvestRepeatedly(helper, player, ModContent.SCYTHE_WOOD.get());

		int seeds = countDrops(helper, Items.WHEAT_SEEDS);
		if (seeds > BONUS_DECISION_THRESHOLD) {
			helper.fail("wood scythe (0 % bonus) dropped " + seeds + " seeds from " + harvested
					+ " plants — above the no-bonus threshold of " + BONUS_DECISION_THRESHOLD
					+ ", which means a bonus was granted by a tier that ships with none");
			return;
		}
		helper.succeed();
	}

	/**
	 * With the global multiplier raised so the tier's chance clamps to certainty, every harvested crop
	 * yields its loot-table seeds <b>plus</b> one bonus seed — pushing the total far above what the
	 * loot table alone can produce. See {@link #fun04CropBonusAbsentOnZeroChanceTier} for why the
	 * assertion is a threshold and how wide the margin is.
	 *
	 * <p>The multiplier is restored in a {@code finally}: it is a global static, and leaving it raised
	 * would silently change the yield of every scenario that runs after this one.
	 */
	public static void fun05CropBonusAlwaysDropsAtFullChance(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);

		double previous = Config.scytheBonusSeedMultiplier;
		// Large enough that any non-zero tier clamps to 1.0; iron ships at 0.12.
		Config.scytheBonusSeedMultiplier = 1000.0;
		int harvested;
		try {
			harvested = harvestRepeatedly(helper, player, ModContent.SCYTHE_IRON.get());
		} finally {
			Config.scytheBonusSeedMultiplier = previous;
		}

		int seeds = countDrops(helper, Items.WHEAT_SEEDS);
		// A guaranteed bonus contributes exactly one seed per plant on top of the loot roll, so the
		// total cannot fall to the no-bonus population without the bonus path being skipped.
		if (seeds < BONUS_DECISION_THRESHOLD) {
			helper.fail("iron scythe at a guaranteed bonus dropped only " + seeds + " seeds from "
					+ harvested + " plants — below the bonus threshold of " + BONUS_DECISION_THRESHOLD
					+ ", which means the bonus seed was not granted");
			return;
		}
		helper.succeed();
	}

	/**
	 * A harvestable crop that is <b>not</b> a {@link CropBlock} gets no bonus even at a guaranteed
	 * chance. Sugar cane is in {@code #alaindustrial:scythe_crops} and its loot table is a single
	 * deterministic {@code sugar_cane}, so the assertion is exact: six harvested stalks must yield
	 * exactly six canes, never twelve.
	 *
	 * <p>This is the guard that keeps the {@code instanceof CropBlock} filter honest. Without it the
	 * bonus path would call {@code getCloneItemStack} on cane, get the cane item back (its pick-block
	 * result), and quietly double a crop that has no seed at all — the same would then hold for
	 * cactus, berry bushes and {@code pitcher_crop}. Cane stands in for that whole excluded family
	 * because it is the one whose loot table makes an exact count possible.
	 */
	public static void neg07CropBonusSkipsNonCropBlock(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		sand(helper); // sugar cane can survive on sand
		int stalks = 0;
		for (int x = 1; x <= 3; x++) {
			for (int z = 2; z <= 3; z++) {
				// Base at FLOOR+1, one harvestable stalk block above it — only the upper block is taken.
				helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.SUGAR_CANE);
				helper.setBlock(new BlockPos(x, FLOOR + 2, z), Blocks.SUGAR_CANE);
				stalks++;
			}
		}

		double previous = Config.scytheBonusSeedMultiplier;
		Config.scytheBonusSeedMultiplier = 1000.0;
		try {
			useScytheShift(helper, player, ModContent.SCYTHE_IRON.get());
		} finally {
			Config.scytheBonusSeedMultiplier = previous;
		}

		int canes = countDrops(helper, Items.SUGAR_CANE);
		if (canes != stalks) {
			helper.fail("sugar cane is not a CropBlock and must get no bonus: harvested " + stalks
					+ " stalks but " + canes + " canes dropped (expected exactly " + stalks + ")");
			return;
		}
		helper.succeed();
	}

	/** Swings sampled by the bonus scenarios; each one replants and harvests the shared 3×2 patch. */
	private static final int BONUS_SWINGS = 30;

	/**
	 * Seed count separating the "no bonus" population from the "guaranteed bonus" one.
	 *
	 * <p>A ripe wheat plant drops one wheat plus {@code 1 + Binomial(3, 0.5714)} seeds — the
	 * {@code apply_bonus} function <i>adds</i> its roll to the entry's base count of one, so the mean
	 * is ~2.71 seeds per plant (σ ≈ 0.86), not the ~1.71 the binomial alone would suggest. Over
	 * {@link #BONUS_SWINGS} × 6 = 180 plants that is a mean of ~489 seeds (σ ≈ 11.5) with no bonus and
	 * ~669 with a guaranteed one: the bonus adds exactly one seed per plant, so the two distributions
	 * sit 180 apart while each is barely a dozen wide.
	 *
	 * <p>The threshold below is placed midway, ~7.8 standard deviations from either mean, which is why
	 * this reads as a decision boundary rather than a sampling test — crossing it by chance is on the
	 * order of 1e-15. (The empirical baseline of a real run was 487, against the predicted 489.)
	 */
	private static final int BONUS_DECISION_THRESHOLD = 580;

	/**
	 * Replants the shared 3×2 wheat patch and harvests it {@link #BONUS_SWINGS} times, returning the
	 * total number of plants taken.
	 *
	 * <p>Each swing gets a fresh scythe stack (that is what {@code useScytheShift} does), so tool
	 * durability never bounds the sample. Every swing is also verified to have actually cleared the
	 * patch — without that check a scenario where nothing is harvested at all would read as "no
	 * bonus" and pass the baseline assertion for entirely the wrong reason.
	 */
	private static int harvestRepeatedly(GameTestHelper helper, ServerPlayer player, Item scythe) {
		int harvested = 0;
		for (int swing = 0; swing < BONUS_SWINGS; swing++) {
			farmland(helper);
			for (int x = 1; x <= 3; x++) {
				for (int z = 2; z <= 3; z++) {
					helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.WHEAT.defaultBlockState()
							.setValue(CropBlock.AGE, CropBlock.MAX_AGE));
				}
			}
			useScytheShift(helper, player, scythe);
			for (int x = 1; x <= 3; x++) {
				for (int z = 2; z <= 3; z++) {
					BlockPos p = new BlockPos(x, FLOOR + 1, z);
					if (!helper.getBlockState(p).isAir()) {
						helper.fail("swing " + swing + " left an unharvested crop at " + p + ": "
								+ helper.getBlockState(p));
						return harvested;
					}
					harvested++;
				}
			}
		}
		return harvested;
	}

	/**
	 * Total count of {@code item} lying around the rig. The radius mirrors
	 * {@code TrellisScenarios.countDrops}, where both extremes were tried and rejected on the NeoForge
	 * lane: a wider box reaches into the neighbouring gametest and counts its drops (green on Fabric,
	 * whose layout is sparser — a loader-specific false pass), while {@code getBounds()} is too tight
	 * for code-registered structures and returns zero.
	 */
	private static int countDrops(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(CLICK)).inflate(2.0);
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	// ── helpers ────────────────────────────────────────────────────────────────────────────────────

	/** The shared survival mock (durability spent, vanilla loot dropped — prf02 flips instabuild back on),
	 * facing south (+z) so the AOE box direction is deterministic. */
	private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setYRot(0.0f);
		return player;
	}

	/** A 5×5 dirt platform at {@code y = FLOOR} so planted foliage has support. */
	private static void platform(GameTestHelper helper) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.DIRT);
			}
		}
	}

	/** A 5×5 sand platform at {@code y = FLOOR} so cactus / sugar cane can survive (their canSurvive). */
	private static void sand(GameTestHelper helper) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.SAND);
			}
		}
	}

	/** A 5×5 farmland platform at {@code y = FLOOR} so wheat has the support it needs to survive. */
	private static void farmland(GameTestHelper helper) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.FARMLAND);
			}
		}
	}

	private static InteractionResult useScythe(GameTestHelper helper, ServerPlayer player, Item scythe) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scythe));
		return useScytheStack(helper, player);
	}

	/** Crop-mode variant: holds shift so {@code useOn} runs the harvest path. */
	private static InteractionResult useScytheShift(GameTestHelper helper, ServerPlayer player, Item scythe) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scythe));
		return useScytheStackShift(helper, player);
	}

	private static InteractionResult useScytheStack(GameTestHelper helper, ServerPlayer player) {
		return useOn(helper, player, false);
	}

	private static InteractionResult useScytheStackShift(GameTestHelper helper, ServerPlayer player) {
		return useOn(helper, player, true);
	}

	/**
	 * Builds the {@code UseOnContext} for a right-click on {@link #CLICK}. {@code shift} selects decor vs
	 * crop mode — the scythe reads it via {@code context.isSecondaryUseActive()}, which the mock player
	 * reports from {@code setShiftKeyDown}. The hit position is the absolute world position of CLICK
	 * (gametest structure coords are structure-relative; {@code absolutePos} maps them to world space).
	 */
	private static InteractionResult useOn(GameTestHelper helper, ServerPlayer player, boolean shift) {
		player.setShiftKeyDown(shift);
		BlockPos abs = helper.absolutePos(CLICK);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		return player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(
				player, InteractionHand.MAIN_HAND, hit));
	}
}
