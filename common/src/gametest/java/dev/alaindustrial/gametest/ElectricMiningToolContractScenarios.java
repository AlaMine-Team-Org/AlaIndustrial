package dev.alaindustrial.gametest;

import dev.alaindustrial.item.energy.ItemEnergy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MOD-707 batch 0 — characterization of the mining contract shared by the four electric mining tools and
 * every one of their tiers (drill + diamond + netherite, chainsaw + diamond, shovel + diamond, hoe +
 * diamond): speed exactly {@code 1.0f} one EU below the per-block cost and above hand speed at it; one
 * {@code euPerBlock} spent per block of non-zero hardness and nothing for a zero-hardness block.
 *
 * <p>{@link ElectricToolEnergyScenarios} pins the base tools; this sweep adds the upgraded tiers, which
 * inherit the rule today and must keep it when the rule moves into one base class. It is a separate class
 * because that file differs between the two Minecraft lines. The costs are literals (the default config),
 * and no gametest assigns those knobs. "No drain on the client" cannot be observed here — an L2 gametest
 * has no client level — and stays with review.
 */
public final class ElectricMiningToolContractScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElectricMiningToolContractScenarios::everyTierKeepsTheContract,
								"powered_item_every_mining_tier_keeps_the_contract")
						.fabricId("PoweredItemContractGameTest", "everyTierKeepsTheContract").ticks(20, 40),
				RosterEntry.of(ElectricMiningToolContractScenarios::everyTierRefusesBelowCost,
								"powered_item_every_mining_tier_refuses_below_cost")
						.ticks(40));

		private Roster() {}
	}

	private ElectricMiningToolContractScenarios() {
	}

	private static final BlockPos SUPPORT = new BlockPos(1, 2, 1);
	private static final BlockPos TARGET = new BlockPos(1, 3, 1);

	private record Tool(String path, long euPerBlock, long buffer, Block profile) {
	}

	private static final List<Tool> TOOLS = List.of(
			new Tool("electric_drill", 50L, 10_000L, Blocks.STONE),
			new Tool("electric_drill_diamond_tip", 50L, 10_000L, Blocks.STONE),
			new Tool("electric_drill_netherite_tip", 50L, 15_000L, Blocks.STONE),
			new Tool("electric_chainsaw", 30L, 10_000L, Blocks.OAK_LOG),
			new Tool("electric_chainsaw_diamond_tip", 30L, 10_000L, Blocks.OAK_LOG),
			new Tool("electric_shovel", 20L, 10_000L, Blocks.DIRT),
			new Tool("electric_shovel_diamond_tip", 20L, 10_000L, Blocks.DIRT),
			new Tool("electric_hoe", 50L, 10_000L, Blocks.HAY_BLOCK),
			new Tool("electric_hoe_diamond_tip", 50L, 10_000L, Blocks.HAY_BLOCK));

	public static void everyTierKeepsTheContract(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		List<String> problems = new ArrayList<>();
		for (Tool tool : TOOLS) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("alaindustrial", tool.path()));
			BlockState profile = tool.profile().defaultBlockState();

			float below = item.getDestroySpeed(stack(item, tool.euPerBlock() - 1), profile);
			if (below != 1.0f) {
				problems.add(tool.path() + ": one EU below cost must be exactly 1.0, got " + below);
			}
			float atCost = item.getDestroySpeed(stack(item, tool.euPerBlock()), profile);
			if (!(atCost > 1.0f)) {
				problems.add(tool.path() + ": at cost it must be faster than hand, got " + atCost);
			}

			helper.setBlock(SUPPORT, Blocks.STONE);
			helper.setBlock(TARGET, tool.profile());
			BlockState placed = helper.getBlockState(TARGET);
			ItemStack paid = stack(item, tool.buffer());
			item.mineBlock(paid, level, placed, helper.absolutePos(TARGET), player);
			if (ItemEnergy.get(paid) != tool.buffer() - tool.euPerBlock()) {
				problems.add(tool.path() + ": a hard block must cost " + tool.euPerBlock() + ", left "
						+ ItemEnergy.get(paid));
			}

			helper.setBlock(TARGET, Blocks.TORCH);
			BlockState free = helper.getBlockState(TARGET);
			ItemStack unpaid = stack(item, tool.buffer());
			item.mineBlock(unpaid, level, free, helper.absolutePos(TARGET), player);
			if (ItemEnergy.get(unpaid) != tool.buffer()) {
				problems.add(tool.path() + ": a zero-hardness block must be free, left " + ItemEnergy.get(unpaid));
			}
			helper.setBlock(TARGET, Blocks.AIR);
		}
		if (!problems.isEmpty()) {
			helper.fail("electric mining contract drifted: " + String.join("; ", problems));
		}
		helper.succeed();
	}

	/**
	 * The refusal half of the contract on every tier: a tool that cannot afford a block breaks it for free
	 * and at exactly hand speed — one EU below the per-block cost, and flat at 0 EU. The upgraded tiers had
	 * no such reading before ({@link ElectricToolEnergyScenarios} and {@code ElectricDrillScenarios} pin the
	 * base tools only), and they are exactly the classes that inherit the rule from a base class.
	 *
	 * <p>The fixture is the tool's own profile block on stone, so the block is hard (non-zero hardness —
	 * asserted, or a missing fixture would make "spent nothing" vacuous) and the charged tool would have
	 * paid for it.
	 *
	 * @implements MOD-707-MT01 — on each of the nine electric mining tiers, {@code mineBlock} with
	 * {@code euPerBlock - 1} EU and with 0 EU leaves the charge untouched, and {@code getDestroySpeed} on the
	 * profile block is exactly {@code 1.0f} for both.
	 */
	public static void everyTierRefusesBelowCost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		List<String> problems = new ArrayList<>();
		for (Tool tool : TOOLS) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("alaindustrial", tool.path()));
			helper.setBlock(SUPPORT, Blocks.STONE);
			helper.setBlock(TARGET, tool.profile());
			BlockState placed = helper.getBlockState(TARGET);
			float hardness = placed.getDestroySpeed(level, helper.absolutePos(TARGET));
			if (placed.getBlock() != tool.profile() || hardness <= 0.0f) {
				problems.add(tool.path() + ": fixture error — " + tool.profile() + " must stand with non-zero"
						+ " hardness, got " + placed + " / " + hardness);
			}
			for (long charge : new long[] {tool.euPerBlock() - 1, 0L}) {
				ItemStack stack = stack(item, charge);
				item.mineBlock(stack, level, placed, helper.absolutePos(TARGET), player);
				if (ItemEnergy.get(stack) != charge) {
					problems.add(tool.path() + ": with " + charge + " EU a hard block must be free, charge went "
							+ charge + " → " + ItemEnergy.get(stack));
				}
				float speed = item.getDestroySpeed(stack, placed);
				if (speed != 1.0f) {
					problems.add(tool.path() + ": with " + charge + " EU it must run at exactly 1.0, got " + speed);
				}
			}
			helper.setBlock(TARGET, Blocks.AIR);
		}
		if (!problems.isEmpty()) {
			helper.fail("electric mining refusal drifted: " + String.join("; ", problems));
		}
		helper.succeed();
	}

	private static ItemStack stack(Item item, long eu) {
		ItemStack stack = new ItemStack(item);
		ItemEnergy.set(stack, eu);
		return stack;
	}
}
