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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for the scythe (MOD-068). Each scenario is a plain
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

	// ── NEG01: shift (secondary use) does not trigger the AOE ──────────────────────────────────────

	/** With shift held, the scythe must not clear anything and must return PASS. */
	public static void neg01ShiftDoesNotAoe(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		platform(helper);
		helper.setBlock(CLICK, Blocks.SHORT_GRASS);
		helper.setBlock(new BlockPos(2, FLOOR + 1, 3), Blocks.SHORT_GRASS);
		player.setShiftKeyDown(true);

		InteractionResult result = useScythe(helper, player, ModContent.SCYTHE_WOOD.get());
		if (result != InteractionResult.PASS) {
			helper.fail("shift-use should return PASS, got " + result);
			return;
		}
		helper.assertBlockPresent(Blocks.SHORT_GRASS, CLICK);
		helper.assertBlockPresent(Blocks.SHORT_GRASS, new BlockPos(2, FLOOR + 1, 3));
		helper.succeed();
	}

	// ── PRF01: durability drops by exactly the number of non-instant blocks broken ─────────────────

	/**
	 * Each broken hardy-foliage block costs one durability. Uses leaves (hardness 0.2): like vanilla,
	 * instant-break foliage (grass/flowers, hardness 0) costs nothing, so durability is charged per
	 * broken leaf. Non-target blocks cost nothing.
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

	// ── NEG02: crops and water are not harvested ───────────────────────────────────────────────────

	/** Wheat and water are excluded from the scythe tag and must survive an AOE over them. */
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

	private static InteractionResult useScythe(GameTestHelper helper, ServerPlayer player, Item scythe) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scythe));
		return useScytheStack(helper, player);
	}

	private static InteractionResult useScytheStack(GameTestHelper helper, ServerPlayer player) {
		BlockPos abs = helper.absolutePos(CLICK);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		return player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(
				player, InteractionHand.MAIN_HAND, hit));
	}
}
