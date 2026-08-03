package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
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
 * L2 server game tests for the ten material ore blocks (tin/silver/nickel/sulfur/uranium ×
 * stone/deepslate) — TC-ORE-001-*. Parametric over the block set: one method per test-case row,
 * looping the 10 ids inside, so a new material added to {@link ModContent} does not require a new
 * method. Mirrors {@code AlaCommonGameTest}'s parametric-over-blocks pattern.
 *
 * <p>See docs/testing/blocks/materials/ores.md for the case table and covered rules.
 *
 * <p>MOD-310: the block sets are built inside methods (not in static fields) because the loader
 * registries behind {@link ModContent} are only bound at runtime — a static initialiser could be
 * triggered by the NeoForge registration method references before the suppliers resolve.
 */
public final class OreScenarios {

	private OreScenarios() {}

	/** Reused single cell inside the test region; placed, asserted, cleared per block. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/** All 10 ore blocks under test, stone/deepslate pairs are implicit in the flat list. */
	private static List<Block> ores() {
		return List.of(
				ModContent.TIN_ORE.get(), ModContent.DEEPSLATE_TIN_ORE.get(),
				ModContent.SILVER_ORE.get(), ModContent.DEEPSLATE_SILVER_ORE.get(),
				ModContent.NICKEL_ORE.get(), ModContent.DEEPSLATE_NICKEL_ORE.get(),
				ModContent.SULFUR_ORE.get(), ModContent.DEEPSLATE_SULFUR_ORE.get(),
				ModContent.URANIUM_ORE.get(), ModContent.DEEPSLATE_URANIUM_ORE.get());
	}

	/** Stone-variant ores (hardness 3.0, SoundType.STONE) — see the BLOCK_PROPS manifest. */
	private static List<Block> stoneOres() {
		return List.of(
				ModContent.TIN_ORE.get(), ModContent.SILVER_ORE.get(), ModContent.NICKEL_ORE.get(),
				ModContent.SULFUR_ORE.get(), ModContent.URANIUM_ORE.get());
	}

	/** Deepslate-variant ores (hardness 4.5, SoundType.DEEPSLATE) — see the BLOCK_PROPS manifest. */
	private static List<Block> deepslateOres() {
		return List.of(
				ModContent.DEEPSLATE_TIN_ORE.get(), ModContent.DEEPSLATE_SILVER_ORE.get(),
				ModContent.DEEPSLATE_NICKEL_ORE.get(), ModContent.DEEPSLATE_SULFUR_ORE.get(),
				ModContent.DEEPSLATE_URANIUM_ORE.get());
	}

	/**
	 * Stone-tier ores — tagged {@code minecraft:needs_stone_tool}: a stone pickaxe or better is
	 * required to harvest (tin/silver/nickel/sulfur, both stone and deepslate variants).
	 */
	private static List<Block> stoneTierOres() {
		return List.of(
				ModContent.TIN_ORE.get(), ModContent.DEEPSLATE_TIN_ORE.get(),
				ModContent.SILVER_ORE.get(), ModContent.DEEPSLATE_SILVER_ORE.get(),
				ModContent.NICKEL_ORE.get(), ModContent.DEEPSLATE_NICKEL_ORE.get(),
				ModContent.SULFUR_ORE.get(), ModContent.DEEPSLATE_SULFUR_ORE.get());
	}

	/**
	 * Iron-tier ores — tagged {@code minecraft:needs_iron_tool}: an iron pickaxe or better is
	 * required to harvest (uranium, both stone and deepslate variants).
	 */
	private static List<Block> ironTierOres() {
		return List.of(
				ModContent.URANIUM_ORE.get(), ModContent.DEEPSLATE_URANIUM_ORE.get());
	}

	/**
	 * Ore block -> its {@code raw_<metal>} item, per the loot tables under
	 * {@code data/alaindustrial/loot_table/blocks/*_ore.json}: a normal pickaxe drops the raw material
	 * (vanilla ore semantics), Silk Touch drops the ore block itself.
	 */
	private static Map<Block, Item> rawItems() {
		return Map.of(
				ModContent.TIN_ORE.get(), ModContent.RAW_TIN.get(),
				ModContent.DEEPSLATE_TIN_ORE.get(), ModContent.RAW_TIN.get(),
				ModContent.SILVER_ORE.get(), ModContent.RAW_SILVER.get(),
				ModContent.DEEPSLATE_SILVER_ORE.get(), ModContent.RAW_SILVER.get(),
				ModContent.NICKEL_ORE.get(), ModContent.RAW_NICKEL.get(),
				ModContent.DEEPSLATE_NICKEL_ORE.get(), ModContent.RAW_NICKEL.get(),
				ModContent.SULFUR_ORE.get(), ModContent.RAW_SULFUR.get(),
				ModContent.DEEPSLATE_SULFUR_ORE.get(), ModContent.RAW_SULFUR.get(),
				ModContent.URANIUM_ORE.get(), ModContent.RAW_URANIUM.get(),
				ModContent.DEEPSLATE_URANIUM_ORE.get(), ModContent.RAW_URANIUM.get());
	}

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
	public static void tcOre001Brk01_dropsItselfWithPickaxe(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(PROBE);
		ServerLevel level = helper.getLevel();
		var miner = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		Map<Block, Item> rawItems = rawItems();
		for (Block ore : ores()) {
			helper.setBlock(PROBE, ore);
			List<ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs,
					level.getBlockEntity(abs), miner, pickaxe);
			Item raw = rawItems.get(ore);
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
	public static void tcOre001Brk03_noDropByHandAxeOrShovel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		List<ItemStack> wrongTools = List.of(ItemStack.EMPTY, new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.WOODEN_SHOVEL));
		for (Block ore : ores()) {
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
	 * Stone-tier ores (tin/silver/nickel/sulfur, {@code minecraft:needs_stone_tool}) need a stone pickaxe or
	 * better; the uranium ore ({@code minecraft:needs_iron_tool}) needs an iron pickaxe or better.
	 * Golden pickaxes are wood-tier for gating (vanilla {@code incorrect_for_gold_tool}), so they are
	 * too low for every ore. The gate lives on the item ({@link ItemStack#isCorrectToolForDrops}), not
	 * in {@link Block#getDrops} — the loot table itself never gates on tier — so it is asserted here the
	 * same way as TC-ORE-001-BRK03 / {@code everyBlockNoDropByHand}.
	 *
	 * @implements TC-ORE-001-BRK02
	 * @covers R-BRK-09
	 */
	public static void tcOre001Brk02_pickaxeTierGate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);

		ItemStack wood = new ItemStack(Items.WOODEN_PICKAXE);
		ItemStack gold = new ItemStack(Items.GOLDEN_PICKAXE);
		ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
		ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
		ItemStack diamond = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemStack netherite = new ItemStack(Items.NETHERITE_PICKAXE);

		// Stone-tier ores: wooden and golden pickaxes are too low (no drop); stone/iron/diamond/netherite harvest.
		for (Block ore : stoneTierOres()) {
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
		for (Block ore : ironTierOres()) {
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
	 * {@code minecraft:alternatives} silk_touch branch); Fortune III boosts the {@code raw_<material>}
	 * count via the loot table's {@code minecraft:apply_bonus}/{@code ore_drops} function on a plain
	 * pickaxe — it is not neutral. Both are vanilla ore semantics, matching the loot table JSON.
	 *
	 * @implements TC-ORE-001-BRK04
	 * @covers R-BRK-08
	 */
	public static void tcOre001Brk04_fortuneAndSilkTouchNeutral(GameTestHelper helper) {
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

		Map<Block, Item> rawItems = rawItems();
		for (Block ore : ores()) {
			Item raw = rawItems.get(ore);

			// Silk Touch: the ore block itself, exactly 1x (no bonus branch on the silk_touch entry).
			helper.setBlock(PROBE, ore);
			List<ItemStack> silkDrops = Block.getDrops(level.getBlockState(abs), level, abs,
					level.getBlockEntity(abs), miner, silkTouchPickaxe);
			long silkSelf = silkDrops.stream().filter(s -> s.getItem() == ore.asItem()).mapToLong(ItemStack::getCount).sum();
			if (silkSelf != 1) {
				helper.fail(ore + " Silk Touch expected exactly 1x the ore block, got " + silkSelf + " (TC-ORE-001-BRK04)");
			}
			helper.setBlock(PROBE, Blocks.AIR);

			// Fortune: boosts the raw_<material> count above the plain-pickaxe baseline (apply_bonus/ore_drops).
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
	 * hardness 4.5 via {@link BlockState#getDestroySpeed}, matching the BLOCK_PROPS manifest
	 * ({@code strength(3.0f, 3.0f)} / {@code strength(4.5f, 3.0f)}).
	 *
	 * @implements TC-ORE-001-BRK05
	 * @covers R-BRK-03
	 */
	public static void tcOre001Brk05_hardnessStoneVsDeepslate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		float expectedStone = 3.0f;
		float expectedDeepslate = 4.5f;

		for (Block ore : stoneOres()) {
			helper.setBlock(PROBE, ore);
			float speed = level.getBlockState(abs).getDestroySpeed(level, abs);
			if (speed != expectedStone) {
				helper.fail(ore + " hardness=" + speed + " expected " + expectedStone + " (stone variant, TC-ORE-001-BRK05)");
			}
			helper.setBlock(PROBE, Blocks.AIR);
		}
		for (Block ore : deepslateOres()) {
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
	public static void tcOre001Phy02_fullCubeHitbox(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		for (Block ore : ores()) {
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
	 * TC-ORE-001-PHY04: every ore block is non-flammable — {@code ignitedByLava()} is false, since
	 * none of the 10 ids call {@code .ignitedByLava()} in their BLOCK_PROPS entry. This is the
	 * public API surface available for a flammability assertion; full fire-spread behaviour (open
	 * flame catching, spreading through the block) needs a lit real-world fire simulation and is not
	 * automated here — see skipped note below.
	 *
	 * @implements TC-ORE-001-PHY04
	 * @covers R-PHY-06
	 */
	public static void tcOre001Phy04_nonFlammable(GameTestHelper helper) {
		for (Block ore : ores()) {
			BlockState state = ore.defaultBlockState();
			if (state.ignitedByLava()) {
				helper.fail(ore + " is marked ignitedByLava — expected non-flammable ore block (TC-ORE-001-PHY04)");
			}
		}
		helper.succeed();
	}
}
