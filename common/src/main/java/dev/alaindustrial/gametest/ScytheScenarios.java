package dev.alaindustrial.gametest;

import dev.alaindustrial.item.ScytheItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
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

	// ── helpers ────────────────────────────────────────────────────────────────────────────────────

	private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		// The test mock's gameMode() override hardcodes CREATIVE, which leaves instabuild set → the
		// break path would suppress both drops and durability. Force it off so the mock behaves like a
		// real survival player (durability spent, vanilla loot dropped). prf02 flips it back on.
		player.getAbilities().instabuild = false;
		player.setYRot(0.0f); // face south (+z) so the AOE box direction is deterministic
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
