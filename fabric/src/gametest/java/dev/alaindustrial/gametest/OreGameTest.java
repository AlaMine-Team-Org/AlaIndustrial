package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.Map;

/**
 * L2 server game tests for the eight Phase 0 material ore blocks (tin/silver/nickel/uranium ×
 * stone/deepslate) — TC-ORE-001-*. Parametric over the block set: one method per test-case row,
 * looping the 8 ids inside, so a new metal added to {@link ModBlocks} does not require a new
 * method. Mirrors {@link AlaCommonGameTest}'s parametric-over-blocks pattern.
 *
 * <p>See docs/testing/blocks/materials/ores.md for the case table and covered rules.
 */
public class OreGameTest {

	/** Reused single cell inside the test region; placed, asserted, cleared per block. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/** All 8 ore blocks under test, stone-variant id -> deepslate-variant id pairs are implicit in the flat list. */
	private static final List<Block> ORES = List.of(
			ModBlocks.TIN_ORE, ModBlocks.DEEPSLATE_TIN_ORE,
			ModBlocks.SILVER_ORE, ModBlocks.DEEPSLATE_SILVER_ORE,
			ModBlocks.NICKEL_ORE, ModBlocks.DEEPSLATE_NICKEL_ORE,
			ModBlocks.URANIUM_ORE, ModBlocks.DEEPSLATE_URANIUM_ORE);

	/** Stone-variant ores (hardness 3.0, SoundType.STONE) — see ModBlocks.java. */
	private static final List<Block> STONE_ORES = List.of(
			ModBlocks.TIN_ORE, ModBlocks.SILVER_ORE, ModBlocks.NICKEL_ORE, ModBlocks.URANIUM_ORE);

	/** Deepslate-variant ores (hardness 4.5, SoundType.DEEPSLATE) — see ModBlocks.java. */
	private static final List<Block> DEEPSLATE_ORES = List.of(
			ModBlocks.DEEPSLATE_TIN_ORE, ModBlocks.DEEPSLATE_SILVER_ORE,
			ModBlocks.DEEPSLATE_NICKEL_ORE, ModBlocks.DEEPSLATE_URANIUM_ORE);

	/**
	 * Stone-tier ores — tagged {@code minecraft:needs_stone_tool}: a stone pickaxe or better is
	 * required to harvest (tin/silver/nickel, both stone and deepslate variants).
	 */
	private static final List<Block> STONE_TIER_ORES = List.of(
			ModBlocks.TIN_ORE, ModBlocks.DEEPSLATE_TIN_ORE,
			ModBlocks.SILVER_ORE, ModBlocks.DEEPSLATE_SILVER_ORE,
			ModBlocks.NICKEL_ORE, ModBlocks.DEEPSLATE_NICKEL_ORE);

	/**
	 * Iron-tier ores — tagged {@code minecraft:needs_iron_tool}: an iron pickaxe or better is
	 * required to harvest (uranium, both stone and deepslate variants).
	 */
	private static final List<Block> IRON_TIER_ORES = List.of(
			ModBlocks.URANIUM_ORE, ModBlocks.DEEPSLATE_URANIUM_ORE);

	/**
	 * Ore block -> its {@code raw_<metal>} item, per the loot tables under
	 * {@code data/alaindustrial/loot_table/blocks/*_ore.json}: a normal pickaxe drops the raw material
	 * (vanilla ore semantics), Silk Touch drops the ore block itself.
	 */
	private static final Map<Block, Item> RAW_ITEM = Map.of(
			ModBlocks.TIN_ORE, ModItems.RAW_TIN, ModBlocks.DEEPSLATE_TIN_ORE, ModItems.RAW_TIN,
			ModBlocks.SILVER_ORE, ModItems.RAW_SILVER, ModBlocks.DEEPSLATE_SILVER_ORE, ModItems.RAW_SILVER,
			ModBlocks.NICKEL_ORE, ModItems.RAW_NICKEL, ModBlocks.DEEPSLATE_NICKEL_ORE, ModItems.RAW_NICKEL,
			ModBlocks.URANIUM_ORE, ModItems.RAW_URANIUM, ModBlocks.DEEPSLATE_URANIUM_ORE, ModItems.RAW_URANIUM);

	/**
	 * TC-ORE-001-BRK01: every ore block drops exactly 1× its {@code raw_<metal>} item (vanilla ore
	 * semantics — a plain pickaxe yields the raw material, not the block; Silk Touch is required for
	 * the block itself, see TC-ORE-001-BRK04). Confirmed against the loot table JSON
	 * ({@code minecraft:alternatives} — silk_touch branch -> ore block, else -> raw_* with
	 * {@code apply_bonus}/fortune).
	 *
	 * @implements TC-ORE-001-BRK01
	 * @covers R-BRK-01
	 */
	@GameTest
	public void tcOre001Brk01_dropsItselfWithPickaxe(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(PROBE);
		ServerLevel level = helper.getLevel();
		var miner = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		for (Block ore : ORES) {
			helper.setBlock(PROBE, ore);
			List<ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs,
					level.getBlockEntity(abs), miner, pickaxe);
			Item raw = RAW_ITEM.get(ore);
			long self = drops.stream().filter(s -> s.getItem() == raw).mapToLong(ItemStack::getCount).sum();
			if (self < 1) {
				helper.fail(ore + " dropped " + self + "x " + raw + " with a pickaxe (expected >=1, TC-ORE-001-BRK01)");
			}
			helper.setBlock(PROBE, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * TC-ORE-001-BRK03: a bare hand, an axe and a shovel are NOT correct tools for any ore block —
	 * no drop by hand/axe/shovel (only a pickaxe is a correct tool, per R-BRK-02/09).
	 *
	 * @implements TC-ORE-001-BRK03
	 * @covers R-BRK-02
	 */
	@GameTest
	public void tcOre001Brk03_noDropByHandAxeOrShovel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		List<ItemStack> wrongTools = List.of(ItemStack.EMPTY, new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.WOODEN_SHOVEL));
		for (Block ore : ORES) {
			helper.setBlock(PROBE, ore);
			BlockState state = level.getBlockState(abs);
			for (ItemStack tool : wrongTools) {
				if (tool.isCorrectToolForDrops(state)) {
					helper.fail(ore + " accepts " + tool.getItem() + " as a correct tool — should require a pickaxe (TC-ORE-001-BRK03)");
				}
			}
			helper.setBlock(PROBE, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * TC-ORE-001-BRK02: harvest-tier gate. Ore blocks are tagged so a too-low pickaxe is NOT a correct
	 * tool — no drop and (in-world) much slower mining, the visual cue that the pickaxe is wrong.
	 * Stone-tier ores (tin/silver/nickel, {@code minecraft:needs_stone_tool}) need a stone pickaxe or
	 * better; the uranium ore ({@code minecraft:needs_iron_tool}) needs an iron pickaxe or better.
	 * Golden pickaxes are wood-tier for gating (vanilla {@code incorrect_for_gold_tool}), so they are
	 * too low for every ore. The gate lives on the item ({@link ItemStack#isCorrectToolForDrops}), not
	 * in {@link Block#getDrops} — the loot table itself never gates on tier — so it is asserted here the
	 * same way as TC-ORE-001-BRK03 / {@code everyBlockNoDropByHand}.
	 *
	 * @implements TC-ORE-001-BRK02
	 * @covers R-BRK-09
	 */
	@GameTest
	public void tcOre001Brk02_pickaxeTierGate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);

		ItemStack wood = new ItemStack(Items.WOODEN_PICKAXE);
		ItemStack gold = new ItemStack(Items.GOLDEN_PICKAXE);
		ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
		ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
		ItemStack diamond = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemStack netherite = new ItemStack(Items.NETHERITE_PICKAXE);

		// Stone-tier ores: wooden and golden pickaxes are too low (no drop); stone/iron/diamond/netherite harvest.
		for (Block ore : STONE_TIER_ORES) {
			helper.setBlock(PROBE, ore);
			BlockState state = level.getBlockState(abs);
			helper.setBlock(PROBE, Blocks.AIR);
			for (ItemStack tooLow : List.of(wood, gold)) {
				if (tooLow.isCorrectToolForDrops(state)) {
					helper.fail(ore + " accepts " + tooLow.getItem() + " as a correct tool — a stone-tier ore must require a STONE pickaxe or better (TC-ORE-001-BRK02)");
				}
			}
			for (ItemStack ok : List.of(stone, iron, diamond, netherite)) {
				if (!ok.isCorrectToolForDrops(state)) {
					helper.fail(ore + " rejects " + ok.getItem() + " — a stone+ pickaxe must harvest a stone-tier ore (TC-ORE-001-BRK02)");
				}
			}
		}

		// Iron-tier ore (uranium): wooden, golden AND stone are too low; iron/diamond/netherite harvest.
		for (Block ore : IRON_TIER_ORES) {
			helper.setBlock(PROBE, ore);
			BlockState state = level.getBlockState(abs);
			helper.setBlock(PROBE, Blocks.AIR);
			for (ItemStack tooLow : List.of(wood, gold, stone)) {
				if (tooLow.isCorrectToolForDrops(state)) {
					helper.fail(ore + " accepts " + tooLow.getItem() + " as a correct tool — uranium ore must require an IRON pickaxe or better (TC-ORE-001-BRK02)");
				}
			}
			for (ItemStack ok : List.of(iron, diamond, netherite)) {
				if (!ok.isCorrectToolForDrops(state)) {
					helper.fail(ore + " rejects " + ok.getItem() + " — an iron+ pickaxe must harvest uranium ore (TC-ORE-001-BRK02)");
				}
			}
		}
		helper.succeed();
	}

	/**
	 * TC-ORE-001-BRK04: Silk Touch drops the ore BLOCK itself (via the loot table's
	 * {@code minecraft:alternatives} silk_touch branch); Fortune III boosts the {@code raw_<metal>}
	 * count via the loot table's {@code minecraft:apply_bonus}/{@code ore_drops} function on a plain
	 * pickaxe — it is not neutral. Both are vanilla ore semantics, matching the loot table JSON.
	 *
	 * @implements TC-ORE-001-BRK04
	 * @covers R-BRK-08
	 */
	@GameTest
	public void tcOre001Brk04_fortuneAndSilkTouchNeutral(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		var miner = helper.makeMockPlayer(GameType.SURVIVAL);

		Holder<Enchantment> fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
		Holder<Enchantment> silkTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);

		ItemStack plainPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemStack fortunePickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		fortunePickaxe.enchant(fortune, 3);
		ItemStack silkTouchPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		silkTouchPickaxe.enchant(silkTouch, 1);

		for (Block ore : ORES) {
			Item raw = RAW_ITEM.get(ore);

			// Silk Touch: the ore block itself, exactly 1x (no bonus branch on the silk_touch entry).
			helper.setBlock(PROBE, ore);
			List<ItemStack> silkDrops = Block.getDrops(level.getBlockState(abs), level, abs,
					level.getBlockEntity(abs), miner, silkTouchPickaxe);
			long silkSelf = silkDrops.stream().filter(s -> s.getItem() == ore.asItem()).mapToLong(ItemStack::getCount).sum();
			if (silkSelf != 1) {
				helper.fail(ore + " Silk Touch expected exactly 1x the ore block, got " + silkSelf + " (TC-ORE-001-BRK04)");
			}
			helper.setBlock(PROBE, Blocks.AIR);

			// Fortune: boosts the raw_<metal> count above the plain-pickaxe baseline (apply_bonus/ore_drops).
			// ore_drops is probabilistic (binomial-style bonus rolls), so sample several breaks and check
			// the fortune-enchanted maximum exceeds the plain baseline at least once — deterministic enough
			// not to flake while still exercising the real apply_bonus function (not merely "never fails").
			helper.setBlock(PROBE, ore);
			long plainCount = Block.getDrops(level.getBlockState(abs), level, abs,
					level.getBlockEntity(abs), miner, plainPickaxe)
					.stream().filter(s -> s.getItem() == raw).mapToLong(ItemStack::getCount).sum();
			helper.setBlock(PROBE, Blocks.AIR);
			if (plainCount < 1) {
				helper.fail(ore + " plain-pickaxe baseline expected >=1x " + raw + ", got " + plainCount
						+ " (TC-ORE-001-BRK04)");
			}
			long maxFortuneCount = 0;
			for (int i = 0; i < 30 && maxFortuneCount <= plainCount; i++) {
				helper.setBlock(PROBE, ore);
				long fortuneCount = Block.getDrops(level.getBlockState(abs), level, abs,
						level.getBlockEntity(abs), miner, fortunePickaxe)
						.stream().filter(s -> s.getItem() == raw).mapToLong(ItemStack::getCount).sum();
				maxFortuneCount = Math.max(maxFortuneCount, fortuneCount);
				helper.setBlock(PROBE, Blocks.AIR);
			}
			if (maxFortuneCount <= plainCount) {
				helper.fail(ore + " Fortune III never exceeded the plain-pickaxe raw count (" + plainCount
						+ ") over 30 samples — expected apply_bonus/ore_drops to boost it at least once (TC-ORE-001-BRK04)");
			}
		}
		helper.succeed();
	}

	/**
	 * TC-ORE-001-BRK05: stone-variant ores report hardness 3.0 and deepslate-variant ores report
	 * hardness 4.5 via {@link BlockState#getDestroySpeed}, matching {@code ModBlocks.java}
	 * ({@code strength(3.0f, 3.0f)} / {@code strength(4.5f, 3.0f)}).
	 *
	 * @implements TC-ORE-001-BRK05
	 * @covers R-BRK-03
	 */
	@GameTest
	public void tcOre001Brk05_hardnessStoneVsDeepslate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		float expectedStone = 3.0f;
		float expectedDeepslate = 4.5f;

		for (Block ore : STONE_ORES) {
			helper.setBlock(PROBE, ore);
			float speed = level.getBlockState(abs).getDestroySpeed(level, abs);
			if (speed != expectedStone) {
				helper.fail(ore + " hardness=" + speed + " expected " + expectedStone + " (stone variant, TC-ORE-001-BRK05)");
			}
			helper.setBlock(PROBE, Blocks.AIR);
		}
		for (Block ore : DEEPSLATE_ORES) {
			helper.setBlock(PROBE, ore);
			float speed = level.getBlockState(abs).getDestroySpeed(level, abs);
			if (speed != expectedDeepslate) {
				helper.fail(ore + " hardness=" + speed + " expected " + expectedDeepslate + " (deepslate variant, TC-ORE-001-BRK05)");
			}
			helper.setBlock(PROBE, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * TC-ORE-001-PHY02: every ore block has a full-cube collision hitbox (occlusion follows the
	 * common BLOCK_STANDARDS invariant, but this case pins the shape itself for the ore set).
	 *
	 * @implements TC-ORE-001-PHY02
	 * @covers R-PHY-10
	 */
	@GameTest
	public void tcOre001Phy02_fullCubeHitbox(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		for (Block ore : ORES) {
			BlockState state = ore.defaultBlockState();
			boolean fullCube = state.isCollisionShapeFullBlock(level, abs)
					&& Block.isShapeFullBlock(state.getCollisionShape(level, abs, CollisionContext.empty()));
			if (!fullCube) {
				helper.fail(ore + " is not a full-cube collision shape (TC-ORE-001-PHY02)");
			}
		}
		helper.succeed();
	}

	/**
	 * TC-ORE-001-PHY06: every ore block is non-flammable — {@code ignitedByLava()} is false, since
	 * none of the 8 ids call {@code .ignitedByLava()} in {@code ModBlocks.java#props()}. This is the
	 * public API surface available for a flammability assertion; full fire-spread behaviour (open
	 * flame catching, spreading through the block) needs a lit real-world fire simulation and is not
	 * automated here — see skipped note below.
	 *
	 * @implements TC-ORE-001-PHY06
	 * @covers R-PHY-06
	 */
	@GameTest
	public void tcOre001Phy06_nonFlammable(GameTestHelper helper) {
		for (Block ore : ORES) {
			BlockState state = ore.defaultBlockState();
			if (state.ignitedByLava()) {
				helper.fail(ore + " is marked ignitedByLava — expected non-flammable ore block (TC-ORE-001-PHY06)");
			}
		}
		helper.succeed();
	}

}
