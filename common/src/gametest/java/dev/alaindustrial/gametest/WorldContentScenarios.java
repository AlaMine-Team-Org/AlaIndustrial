package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;

/**
 * Loader-neutral world-based gametest bodies (MOD-022) — world-content seams outside the
 * energy-network core: ore pickaxe tier gates (R-BRK-09) and the pump fluid chain. Suite
 * contract and shared helpers: {@link EnergyScenarioSupport}.
 */
public final class WorldContentScenarios {

	private WorldContentScenarios() {}

	// ── scenario 19: pump source → tank → sink (fluid transport) ───────────────────────────────────

	private static final BlockPos PUMP = new BlockPos(2, 2, 1);
	private static final BlockPos PUMP_SOURCE = new BlockPos(3, 2, 1);
	private static final BlockPos PUMP_SINK = new BlockPos(1, 2, 1);

	/**
	 * Pump moves a lava source into an adjacent geothermal generator's tank, which then burns it for EU.
	 * Proves the FluidPort/FluidTank abstraction works end-to-end on the NeoForge lane with a real fluid
	 * source (Capabilities.Fluid.BLOCK resolves, acquire+push commit, geo burns).
	 * Mirrors: FluidGameTest.tcFluidPump_lavaSourceToGeothermal (compressed)
	 */
	public static void pumpSourceToTankToSinkToEu(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(PUMP, ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST)); // face the source
		helper.setBlock(PUMP_SOURCE, Blocks.LAVA);
		// Geo sink on the pump's west face
		helper.setBlock(PUMP_SINK, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, PUMP) instanceof dev.alaindustrial.block.entity.PumpBlockEntity pump) {
			pump.getEnergyStorage().amount = Config.pumpEuPerBucket * 4; // ample supply
			for (int i = 0; i < 40; i++) {
				pump.serverTick(level, helper.absolutePos(PUMP),
						level.getBlockState(helper.absolutePos(PUMP)));
				if (be(helper, PUMP_SINK) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
					geo.serverTick(level, helper.absolutePos(PUMP_SINK),
							level.getBlockState(helper.absolutePos(PUMP_SINK)));
				}
			}
			// The geo either burned the acquired lava to EU or has lava in its tank; either way the
			// pump moved fluid across. Assert the source was consumed OR the geo buffer grew.
			boolean sourceGone = !level.getFluidState(helper.absolutePos(PUMP_SOURCE))
					.isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA);
			long geoEu = be(helper, PUMP_SINK) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity g
					? g.getEnergyStorage().getAmount() : -1;
			if (!sourceGone && geoEu <= 0) {
				helper.fail("pump moved no lava: source still present=" + !sourceGone + " geoEu=" + geoEu);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("pump block entity missing");
	}

	// ── scenario 26: ore drop tier-gate (R-BRK-09, all 4 metals) ───────────────────────────────────

	private static final BlockPos ORE = new BlockPos(1, 2, 1);

	/**
	 * Ore blocks require a minimum pickaxe tier: tin/silver/nickel need stone+, uranium needs iron+.
	 * A wooden pickaxe (below every ore's tier) must NOT be the correct tool for drops on any of them.
	 * Catches a regression that drops the {@code needs_stone_tool}/{@code needs_iron_tool} tags.
	 * Mirrors: OreGameTest.tcOre001Brk02_pickaxeTierGate (wooden-too-low leg)
	 */
	public static void oreWoodenPickaxeNoDrop(GameTestHelper helper) {
		net.minecraft.world.item.ItemStack woodenPick = new net.minecraft.world.item.ItemStack(Items.WOODEN_PICKAXE);
		java.util.function.Supplier<net.minecraft.world.level.block.Block>[] ores = new java.util.function.Supplier[]{
				ModContent.TIN_ORE, ModContent.DEEPSLATE_TIN_ORE,
				ModContent.SILVER_ORE, ModContent.DEEPSLATE_SILVER_ORE,
				ModContent.NICKEL_ORE, ModContent.DEEPSLATE_NICKEL_ORE,
				ModContent.URANIUM_ORE, ModContent.DEEPSLATE_URANIUM_ORE,
		};
		int rel = 1;
		for (java.util.function.Supplier<net.minecraft.world.level.block.Block> ore : ores) {
			BlockPos orePos = new BlockPos(rel, 2, 1);
			helper.setBlock(orePos, ore.get());
			net.minecraft.world.level.block.state.BlockState state =
					helper.getLevel().getBlockState(helper.absolutePos(orePos));
			if (woodenPick.isCorrectToolForDrops(state)) {
				helper.fail("wooden pickaxe is the correct tool for " + ore + " — tier gate (needs_stone/iron_tool) missing");
				return;
			}
			rel++;
		}
		helper.succeed();
	}

	/**
	 * A stone pickaxe IS the correct tool for tin/silver/nickel (needs_stone_tool), but NOT for uranium
	 * (needs_iron_tool). Catches a regression that mis-tags uranium as stone-tier or the others as iron-tier.
	 * Mirrors: OreGameTest.tcOre001Brk02_pickaxeTierGate (stone/iron legs)
	 */
	public static void oreStonePickaxeTierGate(GameTestHelper helper) {
		net.minecraft.world.item.ItemStack stonePick = new net.minecraft.world.item.ItemStack(Items.STONE_PICKAXE);
		net.minecraft.world.item.ItemStack ironPick = new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE);
		// Stone-tier ores: stone pick OK.
		java.util.function.Supplier<net.minecraft.world.level.block.Block>[] stoneOres = new java.util.function.Supplier[]{
				ModContent.TIN_ORE, ModContent.SILVER_ORE, ModContent.NICKEL_ORE,
		};
		int rel = 1;
		for (java.util.function.Supplier<net.minecraft.world.level.block.Block> ore : stoneOres) {
			BlockPos orePos = new BlockPos(rel, 2, 2);
			helper.setBlock(orePos, ore.get());
			net.minecraft.world.level.block.state.BlockState state =
					helper.getLevel().getBlockState(helper.absolutePos(orePos));
			if (!stonePick.isCorrectToolForDrops(state)) {
				helper.fail("stone pickaxe is NOT the correct tool for " + ore + " (needs_stone_tool)");
				return;
			}
			rel++;
		}
		// Uranium: stone pick too low, iron pick OK.
		BlockPos uraniumPos = new BlockPos(1, 2, 3);
		helper.setBlock(uraniumPos, ModContent.URANIUM_ORE.get());
		net.minecraft.world.level.block.state.BlockState uraniumState =
				helper.getLevel().getBlockState(helper.absolutePos(uraniumPos));
		if (stonePick.isCorrectToolForDrops(uraniumState)) {
			helper.fail("stone pickaxe is the correct tool for uranium — must need iron+ (needs_iron_tool)");
			return;
		}
		if (!ironPick.isCorrectToolForDrops(uraniumState)) {
			helper.fail("iron pickaxe is NOT the correct tool for uranium (needs_iron_tool)");
			return;
		}
		helper.succeed();
	}
}
