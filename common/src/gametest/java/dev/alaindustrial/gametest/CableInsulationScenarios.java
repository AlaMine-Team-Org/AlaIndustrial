package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.CreativeTabContent;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Loader-neutral MOD-259 acceptance scenarios, invoked by both loader GameTest lanes. */
public final class CableInsulationScenarios {
	private CableInsulationScenarios() {
	}

	private static final int CABLE_COUNT = 50;
	private static final int STEADY_TICKS = 20;
	private static final int BASE_Y = 120;

	private record LineResult(long deliveredPerTick, long midpointAmount, long midpointCapacity,
			CableType midpointType) {
	}

	/** Fifty insulated copper segments retain 8 EU/t, versus 5 EU/t for bare copper. */
	public static void insulatedCopperLosesLessThanBare(GameTestHelper helper) {
		LineResult bare = runLine(helper, ModContent.COPPER_CABLE.get(), -1);
		LineResult insulated = runLine(helper, ModContent.INSULATED_COPPER_CABLE.get(), -1);

		assertLine(helper, "bare copper", bare, CableType.COPPER, 5L, Config.cableBuffer);
		assertLine(helper, "insulated copper", insulated, CableType.INSULATED_COPPER, 8L,
				Config.cableBuffer);
		if (insulated.deliveredPerTick() <= bare.deliveredPerTick()) {
			helper.fail("insulation did not reduce copper loss: bare delivered "
					+ bare.deliveredPerTick() + " EU/t, insulated delivered "
					+ insulated.deliveredPerTick() + " EU/t");
			return;
		}
		helper.succeed();
	}

	/** Fifty insulated tin segments retain 7 EU/t, versus 6 EU/t for bare tin. */
	public static void insulatedTinLosesLessThanBare(GameTestHelper helper) {
		LineResult bare = runLine(helper, ModContent.TIN_CABLE.get(), -1);
		LineResult insulated = runLine(helper, ModContent.INSULATED_TIN_CABLE.get(), -1);

		assertLine(helper, "bare tin", bare, CableType.TIN, 6L, Config.tinCableBuffer);
		assertLine(helper, "insulated tin", insulated, CableType.INSULATED_TIN, 7L,
				Config.tinCableBuffer);
		if (insulated.deliveredPerTick() <= bare.deliveredPerTick()) {
			helper.fail("insulation did not reduce tin loss: bare delivered "
					+ bare.deliveredPerTick() + " EU/t, insulated delivered "
					+ insulated.deliveredPerTick() + " EU/t");
			return;
		}
		helper.succeed();
	}

	/**
	 * Fifty insulated gold segments out-deliver fifty bare gold ones (MOD-268).
	 *
	 * <p>Deliberately comparative rather than pinned to a literal EU/t figure like its tin and copper
	 * siblings: gold is the MV grade, so its absolute throughput over an LV generator/battery fixture
	 * depends on how the network resolves the mixed tier, and a hardcoded number there would pin
	 * fixture behaviour rather than the insulation contract this test exists to guard.
	 */
	public static void insulatedGoldLosesLessThanBare(GameTestHelper helper) {
		LineResult bare = runLine(helper, ModContent.GOLD_CABLE.get(), -1);
		LineResult insulated = runLine(helper, ModContent.INSULATED_GOLD_CABLE.get(), -1);

		if (bare.midpointType() != CableType.GOLD
				|| insulated.midpointType() != CableType.INSULATED_GOLD) {
			helper.fail("gold fixture built the wrong grades: bare=" + bare.midpointType()
					+ ", insulated=" + insulated.midpointType());
			return;
		}
		if (bare.deliveredPerTick() <= 0 || insulated.deliveredPerTick() <= 0) {
			helper.fail("gold line delivered nothing: bare=" + bare.deliveredPerTick()
					+ " EU/t, insulated=" + insulated.deliveredPerTick() + " EU/t");
			return;
		}
		if (insulated.deliveredPerTick() <= bare.deliveredPerTick()) {
			helper.fail("insulation did not reduce gold loss: bare delivered "
					+ bare.deliveredPerTick() + " EU/t, insulated delivered "
					+ insulated.deliveredPerTick() + " EU/t");
			return;
		}
		helper.succeed();
	}

	/**
	 * Fifty insulated electrum segments out-deliver fifty bare electrum ones (MOD-358).
	 *
	 * <p>Comparative for the same reason as its gold sibling, only more so: electrum is HV, two tiers
	 * above the LV fixture that drives this line, so any literal EU/t here would pin how the network
	 * resolves that mismatch rather than the insulation contract.
	 */
	public static void insulatedElectrumLosesLessThanBare(GameTestHelper helper) {
		LineResult bare = runLine(helper, ModContent.ELECTRUM_CABLE.get(), -1);
		LineResult insulated = runLine(helper, ModContent.INSULATED_ELECTRUM_CABLE.get(), -1);

		if (bare.midpointType() != CableType.ELECTRUM
				|| insulated.midpointType() != CableType.INSULATED_ELECTRUM) {
			helper.fail("electrum fixture built the wrong grades: bare=" + bare.midpointType()
					+ ", insulated=" + insulated.midpointType());
			return;
		}
		if (bare.deliveredPerTick() <= 0 || insulated.deliveredPerTick() <= 0) {
			helper.fail("electrum line delivered nothing: bare=" + bare.deliveredPerTick()
					+ " EU/t, insulated=" + insulated.deliveredPerTick() + " EU/t");
			return;
		}
		if (insulated.deliveredPerTick() <= bare.deliveredPerTick()) {
			helper.fail("insulation did not reduce electrum loss: bare delivered "
					+ bare.deliveredPerTick() + " EU/t, insulated delivered "
					+ insulated.deliveredPerTick() + " EU/t");
			return;
		}
		helper.succeed();
	}

	/**
	 * One bare copper segment keeps the bare tariff for an equal-cap mixed network, independent of
	 * whether it sits at the source or sink end.
	 */
	public static void mixedCopperUsesBareLossDeterministically(GameTestHelper helper) {
		LineResult bareAtSource = runLine(helper, ModContent.INSULATED_COPPER_CABLE.get(), 0);
		LineResult bareAtSink = runLine(helper, ModContent.INSULATED_COPPER_CABLE.get(),
				CABLE_COUNT - 1);

		if (bareAtSource.deliveredPerTick() != 5L || bareAtSink.deliveredPerTick() != 5L) {
			helper.fail("mixed copper tariff depends on cable discovery order: bare-at-source="
					+ bareAtSource.deliveredPerTick() + " EU/t, bare-at-sink="
					+ bareAtSink.deliveredPerTick()
					+ " EU/t; both must use bare copper loss and deliver 5 EU/t");
			return;
		}
		helper.succeed();
	}

	/** The batch recipes and the public creative/viewer manifests agree on both loaders. */
	public static void recipesAndVisibility(GameTestHelper helper) {
		assertInsulationRecipe(helper, ModContent.COPPER_CABLE_ITEM.get(),
				ModContent.INSULATED_COPPER_CABLE_ITEM.get(), "insulated_copper_cable");
		assertInsulationRecipe(helper, ModContent.TIN_CABLE_ITEM.get(),
				ModContent.INSULATED_TIN_CABLE_ITEM.get(), "insulated_tin_cable");
		assertInsulationRecipe(helper, ModContent.GOLD_CABLE_ITEM.get(),
				ModContent.INSULATED_GOLD_CABLE_ITEM.get(), "insulated_gold_cable");
		assertInsulationRecipe(helper, ModContent.ELECTRUM_CABLE_ITEM.get(),
				ModContent.INSULATED_ELECTRUM_CABLE_ITEM.get(), "insulated_electrum_cable");

		List<Item> visible = new ArrayList<>();
		CreativeTabContent.main(item -> visible.add(item.asItem()));
		assertVisibleExactlyOnce(helper, visible, ModContent.INSULATED_COPPER_CABLE_ITEM.get(),
				"insulated_copper_cable");
		assertVisibleExactlyOnce(helper, visible, ModContent.INSULATED_TIN_CABLE_ITEM.get(),
				"insulated_tin_cable");
		assertVisibleExactlyOnce(helper, visible, ModContent.INSULATED_GOLD_CABLE_ITEM.get(),
				"insulated_gold_cable");
		assertVisibleExactlyOnce(helper, visible, ModContent.INSULATED_ELECTRUM_CABLE_ITEM.get(),
				"insulated_electrum_cable");

		for (Supplier<? extends ItemLike> hidden : RecipeViewerInfo.hiddenFromRecipeViewerItems()) {
			Item item = hidden.get().asItem();
			if (item == ModContent.INSULATED_COPPER_CABLE_ITEM.get()
					|| item == ModContent.INSULATED_TIN_CABLE_ITEM.get()
					|| item == ModContent.INSULATED_GOLD_CABLE_ITEM.get()
					|| item == ModContent.INSULATED_ELECTRUM_CABLE_ITEM.get()) {
				helper.fail("public insulated cable remains hidden from REI/JEI: " + item);
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The MOD-268 batch craft: three bare cables, a rubber row, three bare cables → six insulated.
	 * The inverted layout is asserted NOT to resolve, so a future switch back to a shapeless recipe
	 * (which would accept any arrangement) cannot pass this test silently.
	 */
	private static void assertInsulationRecipe(GameTestHelper helper, ItemLike bare,
			ItemLike expected, String name) {
		assertCraft(helper, insulationGrid(bare, ModContent.RUBBER.get()), expected, 6, name);
		assertNoCraft(helper, insulationGrid(ModContent.RUBBER.get(), bare),
				name + " (rubber and cable rows swapped)");
	}

	/** {@code CCC / RRR / CCC} as a flat 3×3 grid, outer rows first. */
	private static List<ItemStack> insulationGrid(ItemLike outer, ItemLike middle) {
		List<ItemStack> grid = new ArrayList<>(9);
		for (int i = 0; i < 3; i++) {
			grid.add(new ItemStack(outer));
		}
		for (int i = 0; i < 3; i++) {
			grid.add(new ItemStack(middle));
		}
		for (int i = 0; i < 3; i++) {
			grid.add(new ItemStack(outer));
		}
		return grid;
	}

	private static void assertCraft(GameTestHelper helper, List<ItemStack> grid, ItemLike expected,
			int expectedCount, String label) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail(label + " batch recipe did not resolve");
			return;
		}
		ItemStack output = recipe.value().assemble(input);
		if (!output.is(expected.asItem()) || output.getCount() != expectedCount) {
			helper.fail(label + " recipe produced " + output + "; expected exactly " + expectedCount
					+ " " + expected.asItem());
		}
	}

	private static void assertNoCraft(GameTestHelper helper, List<ItemStack> grid, String label) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
				.ifPresent(recipe -> helper.fail(label + " unexpectedly resolved to "
						+ recipe.value().assemble(input)));
	}

	private static void assertVisibleExactlyOnce(GameTestHelper helper, List<Item> visible,
			ItemLike expected, String name) {
		long count = visible.stream().filter(item -> item == expected.asItem()).count();
		if (count != 1L) {
			helper.fail(name + " occurs " + count
					+ " times in CreativeTabContent.main; expected exactly once");
		}
	}

	private static LineResult runLine(GameTestHelper helper, Block cable, int bareIndex) {
		BlockPos generatorPos = new BlockPos(0, BASE_Y, 1);
		List<BlockPos> cables = fiftyCablePath();
		BlockPos boxPos = new BlockPos(3, BASE_Y + 2, 3);

		helper.setBlock(generatorPos, ModContent.GENERATOR.get());
		for (int i = 0; i < cables.size(); i++) {
			helper.setBlock(cables.get(i),
					i == bareIndex ? ModContent.COPPER_CABLE.get() : cable);
		}
		helper.setBlock(boxPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, net.minecraft.core.Direction.WEST));

		if (be(helper, generatorPos) instanceof GeneratorBlockEntity generator) {
			generator.getEnergyStorage().amount = EnergyTier.LV.maxVoltage() * 500;
			generator.setChanged();
		}
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().amount = 0;
			box.setChanged();
		}

		driveLine(helper, generatorPos, cables, boxPos, 120);
		BlockPos midpoint = cables.get(cables.size() / 2);
		long midpointAmount = -1;
		long midpointCapacity = -1;
		CableType midpointType = null;
		if (be(helper, midpoint) instanceof CableBlockEntity segment) {
			midpointAmount = segment.getEnergyStorage().getAmount();
			midpointCapacity = segment.getEnergyStorage().getCapacity();
			midpointType = segment.cableType();
		}

		long before = be(helper, boxPos) instanceof BatteryBoxBlockEntity box
				? box.getEnergyStorage().getAmount() : -1;
		driveLine(helper, generatorPos, cables, boxPos, STEADY_TICKS);
		long after = be(helper, boxPos) instanceof BatteryBoxBlockEntity box
				? box.getEnergyStorage().getAmount() : -1;
		long gained = after - before;
		if (gained % STEADY_TICKS != 0) {
			helper.fail("line delivery was not steady over " + STEADY_TICKS
					+ " ticks: gained " + gained + " EU");
		}
		long deliveredPerTick = gained / STEADY_TICKS;

		helper.setBlock(generatorPos, Blocks.AIR);
		for (BlockPos pos : cables) {
			helper.setBlock(pos, Blocks.AIR);
		}
		helper.setBlock(boxPos, Blocks.AIR);
		NetworkManager.tickAll(helper.getLevel());

		return new LineResult(deliveredPerTick, midpointAmount, midpointCapacity, midpointType);
	}

	private static void assertLine(GameTestHelper helper, String label, LineResult result,
			CableType expectedType, long expectedDelivery, long expectedCapacity) {
		if (result.deliveredPerTick() != expectedDelivery) {
			helper.fail(label + " delivered " + result.deliveredPerTick() + " EU/t; expected "
					+ expectedDelivery + " over fifty real segments");
			return;
		}
		if (result.midpointType() != expectedType) {
			helper.fail(label + " midpoint reports " + result.midpointType() + "; expected "
					+ expectedType);
			return;
		}
		if (result.midpointAmount() <= 0 || result.midpointAmount() > expectedCapacity
				|| result.midpointCapacity() != expectedCapacity) {
			helper.fail(label + " midpoint buffer is " + result.midpointAmount() + "/"
					+ result.midpointCapacity() + " EU; expected a live segment within 1.."
					+ expectedCapacity + " EU");
		}
	}

	private static void driveLine(GameTestHelper helper, BlockPos generatorPos,
			List<BlockPos> cables, BlockPos boxPos, int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(helper, be(helper, generatorPos));
			for (BlockPos cable : cables) {
				tick(helper, be(helper, cable));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, boxPos));
		}
	}

	private static List<BlockPos> fiftyCablePath() {
		List<BlockPos> path = new ArrayList<>(CABLE_COUNT);
		for (int row = 0; row < 4; row++) {
			int z = 1 + row * 2;
			if ((row & 1) == 0) {
				for (int x = 1; x <= 7; x++) {
					path.add(new BlockPos(x, BASE_Y, z));
				}
				if (row < 3) {
					path.add(new BlockPos(7, BASE_Y, z + 1));
				}
			} else {
				for (int x = 7; x >= 1; x--) {
					path.add(new BlockPos(x, BASE_Y, z));
				}
				if (row < 3) {
					path.add(new BlockPos(1, BASE_Y, z + 1));
				}
			}
		}
		path.add(new BlockPos(1, BASE_Y + 1, 7));
		for (int x = 1; x <= 7; x++) {
			path.add(new BlockPos(x, BASE_Y + 2, 7));
		}
		path.add(new BlockPos(7, BASE_Y + 2, 6));
		for (int x = 7; x >= 1; x--) {
			path.add(new BlockPos(x, BASE_Y + 2, 5));
		}
		path.add(new BlockPos(1, BASE_Y + 2, 4));
		path.add(new BlockPos(1, BASE_Y + 2, 3));
		path.add(new BlockPos(2, BASE_Y + 2, 3));
		if (path.size() != CABLE_COUNT) {
			throw new IllegalStateException("MOD-259 fixture must contain exactly fifty cables");
		}
		return path;
	}
}
