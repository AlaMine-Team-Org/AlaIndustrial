package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.gametest.compat.UseResults;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MOD-798: what an electric tool PREDICTS (the speed it reports while mining, the torch the drill places, the
 * soil the hoe tills) is decided by the prices of the server, which reach the client as a {@link ServerBalance}
 * snapshot, not by the local file. The three bodies receive a snapshot whose prices differ from the local
 * ones and check the decision follows the snapshot; they are red on code that reads {@link ToolConfig}.
 *
 * <p>Each body receives the snapshot and forgets it in {@code finally}, inside one synchronous call, so no other
 * scenario sees it. The same questions with nothing received (a dedicated server) are every existing tool
 * scenario: they run with no snapshot, where {@link ServerBalance} reads this JVM's own {@link ToolConfig}.
 */
public final class ElectricToolPredictionScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElectricToolPredictionScenarios::mod798MiningSpeedFollowsTheServerPrice,
						"electric_tool_mining_speed_follows_the_server_price"),
				RosterEntry.of(ElectricToolPredictionScenarios::mod798TorchCostFollowsTheServerPrice,
						"electric_tool_torch_cost_follows_the_server_price").ticks(40),
				RosterEntry.of(ElectricToolPredictionScenarios::mod798TillCostFollowsTheServerPrice,
						"electric_tool_till_cost_follows_the_server_price").ticks(40));

		private Roster() {}
	}

	private ElectricToolPredictionScenarios() {}

	/** A mining tool with the knob that prices its block, the local price, and a block it mines. */
	private record Tool(String path, Supplier<? extends Item> item, IntSupplier localPrice, Block profile) {
	}

	private static final List<Tool> TOOLS = List.of(
			new Tool("drill", () -> ModContent.ELECTRIC_DRILL.get(), () -> ToolConfig.electricDrillEuPerBlock,
					Blocks.STONE),
			new Tool("drill_diamond_tip", () -> ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get(),
					() -> ToolConfig.electricDrillEuPerBlock, Blocks.STONE),
			new Tool("drill_netherite_tip", () -> ModContent.ELECTRIC_DRILL_NETHERITE_TIP.get(),
					() -> ToolConfig.electricDrillEuPerBlock, Blocks.STONE),
			new Tool("chainsaw", () -> ModContent.ELECTRIC_CHAINSAW.get(), () -> ToolConfig.electricChainsawEuPerBlock,
					Blocks.OAK_LOG),
			new Tool("chainsaw_diamond_tip", () -> ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get(),
					() -> ToolConfig.electricChainsawEuPerBlock, Blocks.OAK_LOG),
			new Tool("shovel", () -> ModContent.ELECTRIC_SHOVEL.get(), () -> ToolConfig.electricShovelEuPerBlock,
					Blocks.DIRT),
			new Tool("shovel_diamond_tip", () -> ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP.get(),
					() -> ToolConfig.electricShovelEuPerBlock, Blocks.DIRT),
			new Tool("hoe", () -> ModContent.ELECTRIC_HOE.get(), () -> ToolConfig.electricHoeEuPerBlock,
					Blocks.HAY_BLOCK),
			new Tool("hoe_diamond_tip", () -> ModContent.ELECTRIC_HOE_DIAMOND_TIP.get(),
					() -> ToolConfig.electricHoeEuPerBlock, Blocks.HAY_BLOCK));

	private static ItemStack charged(Item item, long eu) {
		ItemStack stack = new ItemStack(item);
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static boolean receive(GameTestHelper helper, Map<String, Object> knobs) {
		ServerBalance.reset();
		if (!ServerBalance.receive(KnobSnapshot.of(knobs).encode())) {
			helper.fail("the client refused a snapshot carrying " + knobs.keySet());
			return false;
		}
		return true;
	}

	/**
	 * Speed: a server that prices a block 100 EU above the local price is not afforded by a tool holding
	 * {@code local + 1}: the client must predict hand speed, exactly 1.0 (otherwise it digs at full speed and
	 * the server, refusing the progress, puts the block back). And a server that prices it at 1 EU is afforded
	 * by a tool holding {@code local - 1}: full speed, not the hand speed the local file would give.
	 *
	 * @implements MOD-798-PRED01 - an electric tool's getDestroySpeed decides "enough charge for a block" by the
	 *     per-block price of the server, on all nine mining tiers
	 */
	public static void mod798MiningSpeedFollowsTheServerPrice(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		try {
			Map<String, Object> dearer = Map.of("electricDrillEuPerBlock", ToolConfig.electricDrillEuPerBlock + 100,
					"electricChainsawEuPerBlock", ToolConfig.electricChainsawEuPerBlock + 100,
					"electricShovelEuPerBlock", ToolConfig.electricShovelEuPerBlock + 100,
					"electricHoeEuPerBlock", ToolConfig.electricHoeEuPerBlock + 100);
			if (!receive(helper, dearer)) {
				return;
			}
			for (Tool tool : TOOLS) {
				Item item = tool.item().get();
				float speed = item.getDestroySpeed(charged(item, tool.localPrice().getAsInt() + 1L),
						tool.profile().defaultBlockState());
				if (speed != 1.0f) {
					problems.add(tool.path() + ": the server prices a block above the charge, the prediction must be"
							+ " hand speed 1.0, got " + speed);
				}
			}
			Map<String, Object> cheaper = Map.of("electricDrillEuPerBlock", 1, "electricChainsawEuPerBlock", 1,
					"electricShovelEuPerBlock", 1, "electricHoeEuPerBlock", 1);
			if (!receive(helper, cheaper)) {
				return;
			}
			for (Tool tool : TOOLS) {
				Item item = tool.item().get();
				float speed = item.getDestroySpeed(charged(item, tool.localPrice().getAsInt() - 1L),
						tool.profile().defaultBlockState());
				if (!(speed > 1.0f)) {
					problems.add(tool.path() + ": the server prices a block at 1 EU, the prediction must be full"
							+ " speed, got " + speed);
				}
			}
		} finally {
			ServerBalance.reset();
		}
		if (!problems.isEmpty()) {
			helper.fail("mining prediction ignores the price of the server: " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	/**
	 * Torch: with the server pricing a torch far above the charge of a drill, the right-click is refused (CONSUME,
	 * no torch placed, charge untouched) although the local price would afford it. With the server pricing it
	 * below the charge the drill places although the local price would refuse.
	 *
	 * @implements MOD-798-PRED02 - the torch gate of the drill uses electricDrillTorchEuCost of the server
	 */
	public static void mod798TorchCostFollowsTheServerPrice(GameTestHelper helper) {
		BlockPos floor = new BlockPos(1, 2, 2);
		BlockPos torchAt = floor.above();
		helper.setBlock(floor, Blocks.STONE);
		ServerPlayer player = survivalPlayer(helper);
		long charge = ToolConfig.electricDrillTorchEuCost + 3L;
		try {
			ItemStack refused = charged(ModContent.ELECTRIC_DRILL.get(), charge);
			player.setItemInHand(InteractionHand.MAIN_HAND, refused);
			player.getInventory().setItem(1, new ItemStack(Items.TORCH, 8));
			if (!receive(helper, Map.of("electricDrillTorchEuCost", ToolConfig.electricDrillTorchEuCost + 100))) {
				return;
			}
			InteractionResult result = useOnDrill(helper, player, floor);
			if (!UseResults.isNoSwingConsume(result)) {
				helper.fail("the torch price of the server is above the charge: the drill must refuse with CONSUME,"
						+ " got " + result);
				return;
			}
			helper.assertBlockPresent(Blocks.AIR, torchAt);
			if (ItemEnergy.get(refused) != charge || player.getInventory().countItem(Items.TORCH) != 8) {
				helper.fail("a refused torch must touch neither the charge nor the torches");
				return;
			}

			player.setItemInHand(InteractionHand.MAIN_HAND, charged(ModContent.ELECTRIC_DRILL.get(), charge));
			if (!receive(helper, Map.of("electricDrillTorchEuCost", 1))) {
				return;
			}
			result = useOnDrill(helper, player, floor);
			if (!UseResults.isSwingSuccess(result)) {
				helper.fail("the torch price of the server is below the charge: the drill must place, got " + result);
				return;
			}
			helper.assertBlockPresent(Blocks.TORCH, torchAt);
		} finally {
			ServerBalance.reset();
		}
		helper.succeed();
	}

	private static InteractionResult useOnDrill(GameTestHelper helper, ServerPlayer player, BlockPos target) {
		BlockPos abs = helper.absolutePos(target);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	/**
	 * Till: the same two directions for {@code electricHoeTillEuCost}, driven through
	 * {@code gameMode.useItemOn} like {@link ElectricHoeScenarios} (NeoForge replaces {@code ItemStack#useOn}).
	 *
	 * @implements MOD-798-PRED03 - the tilling gate of the hoe uses electricHoeTillEuCost of the server
	 */
	public static void mod798TillCostFollowsTheServerPrice(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long charge = ToolConfig.electricHoeTillEuCost + 3L;
		try {
			player.setItemInHand(InteractionHand.MAIN_HAND, charged(ModContent.ELECTRIC_HOE.get(), charge));
			ElectricHoeScenarios.prepareSoil(helper, Blocks.DIRT);
			if (!receive(helper, Map.of("electricHoeTillEuCost", ToolConfig.electricHoeTillEuCost + 100))) {
				return;
			}
			InteractionResult result = ElectricHoeScenarios.useOnSoil(helper, player);
			if (!UseResults.isNoSwingConsume(result)) {
				helper.fail("the till price of the server is above the charge: the hoe must refuse with CONSUME, got "
						+ result);
				return;
			}
			helper.assertBlockPresent(Blocks.DIRT, ElectricHoeScenarios.SOIL);

			player.setItemInHand(InteractionHand.MAIN_HAND, charged(ModContent.ELECTRIC_HOE.get(), charge));
			if (!receive(helper, Map.of("electricHoeTillEuCost", 1))) {
				return;
			}
			result = ElectricHoeScenarios.useOnSoil(helper, player);
			if (!result.consumesAction()) {
				helper.fail("the till price of the server is below the charge: the hoe must till, got " + result);
				return;
			}
			helper.assertBlockPresent(Blocks.FARMLAND, ElectricHoeScenarios.SOIL);
		} finally {
			ServerBalance.reset();
		}
		helper.succeed();
	}
}
