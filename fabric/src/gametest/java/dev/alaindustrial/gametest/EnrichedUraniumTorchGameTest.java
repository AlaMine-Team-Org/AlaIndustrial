package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * MOD-085 targeted L2 tests for the Enriched Uranium Torch — the two feature guarantees the parametric
 * {@link AlaCommonGameTest} carve-outs deliberately do not check: the light level and the wall variant's
 * drop. (Vanilla-behaviour torch invariants — occlusion, placement — are covered by the common suite.)
 */
public class EnrichedUraniumTorchGameTest {

	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/**
	 * Both the standing and wall torch emit light level 14 — identical to the vanilla torch (kept the same
	 * on purpose to avoid behavioural surprises; the "enriched" identity is the green flame + particles +
	 * underwater burning, not extra light). A pure state query — no world interaction beyond the harness.
	 */
	@GameTest
	public void torchesEmitVanillaTorchLight(GameTestHelper helper) {
		int standing = ModContent.ENRICHED_URANIUM_TORCH.get().defaultBlockState().getLightEmission();
		int wall = ModContent.ENRICHED_URANIUM_WALL_TORCH.get().defaultBlockState().getLightEmission();
		if (standing != 14) {
			helper.fail("standing enriched uranium torch emits light " + standing + " (expected 14, like vanilla torch)");
		}
		if (wall != 14) {
			helper.fail("wall enriched uranium torch emits light " + wall + " (expected 14, like vanilla torch)");
		}
		helper.succeed();
	}

	/**
	 * The wall variant has no block item of its own; breaking it drops the STANDING torch item, via the
	 * {@code Properties.overrideLootTable(standing.getLootTable())} set at registration (vanilla wallVariant).
	 */
	@GameTest
	public void wallTorchDropsStandingTorch(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(PROBE);
		var level = helper.getLevel();
		var miner = helper.makeMockPlayer(GameType.SURVIVAL);
		Block wall = ModContent.ENRICHED_URANIUM_WALL_TORCH.get();
		helper.setBlock(PROBE, wall);
		List<ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs,
				level.getBlockEntity(abs), miner, new ItemStack(Items.DIAMOND_PICKAXE));
		helper.setBlock(PROBE, Blocks.AIR);
		var standingItem = ModContent.ENRICHED_URANIUM_TORCH_ITEM.get();
		long n = drops.stream().filter(s -> s.getItem() == standingItem).mapToLong(ItemStack::getCount).sum();
		if (n != 1) {
			helper.fail("wall torch dropped " + n + "× the standing torch item (expected 1)");
		}
		helper.succeed();
	}
}
