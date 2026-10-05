package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

import dev.alaindustrial.block.CableDyeing;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * L2 scenarios for dyed insulated cables (MOD-666): the colour is cosmetic, persistent, and bounded to
 * the grade it was applied to.
 */
public final class CableDyeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CableDyeScenarios::tcDye001Nrg01_dyedLineCarriesEnergy,
								"tc_dye001_nrg01_dyed_line_carries_energy")
						.fabricId("NetworkGameTest", "tcDye001Nrg01_dyedLineCarriesEnergy").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Per01_colourRoundTripsThroughTheItem,
								"tc_dye001_per01_colour_round_trips_through_the_item")
						.fabricId("NetworkGameTest", "tcDye001Per01_colourRoundTripsThroughTheItem").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Per02_brokenDyedCableDropsItsColour,
								"tc_dye001_per02_broken_dyed_cable_drops_its_colour")
						.fabricId("NetworkGameTest", "tcDye001Per02_brokenDyedCableDropsItsColour").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Run01_runStopsAtAnotherGrade,
								"tc_dye001_run01_run_stops_at_another_grade")
						.fabricId("NetworkGameTest", "tcDye001Run01_runStopsAtAnotherGrade").ticks(80));

		private Roster() {}
	}

	private static final BlockPos GENERATOR = new BlockPos(1, 2, 1);
	private static final BlockPos BOX = new BlockPos(6, 2, 1);

	private CableDyeScenarios() {
	}

	private static CableBlockEntity cable(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, CableBlockEntity.class);
	}

	/**
	 * A line of differently dyed segments carries energy to the load: colour never splits a network.
	 *
	 * @implements TC-DYE-001-NRG01 — dyed segments of any colour mix connect and carry energy.
	 */
	public static void tcDye001Nrg01_dyedLineCarriesEnergy(GameTestHelper helper) {
		helper.setBlock(GENERATOR, ModContent.GENERATOR.get());
		DyeColor[] colours = {DyeColor.RED, null, DyeColor.BLUE, DyeColor.RED};
		for (int i = 0; i < colours.length; i++) {
			helper.setBlock(new BlockPos(2 + i, 2, 1), ModContent.INSULATED_COPPER_CABLE.get());
		}
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		for (int i = 0; i < colours.length; i++) {
			BlockPos pos = new BlockPos(2 + i, 2, 1);
			tick(helper, be(helper, pos));
			cable(helper, pos).setColor(colours[i]);
		}
		tick(helper, be(helper, GENERATOR));
		tick(helper, be(helper, BOX));
		if (!(be(helper, GENERATOR) instanceof GeneratorBlockEntity generator)
				|| !(be(helper, BOX) instanceof BatteryBoxBlockEntity box)) {
			helper.fail("rig incomplete");
			return;
		}
		box.getEnergyStorage().setAmountUntracked(0);
		generator.getEnergyStorage().setAmountUntracked(GeneratorConfig.generatorBuffer);
		for (int i = 0; i < 40; i++) {
			NetworkManager.tickAll(helper.getLevel());
		}
		if (box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("a line of dyed cables must carry energy; the box never charged");
			return;
		}
		helper.succeed();
	}

	/**
	 * The colour rides on the item the segment drops and back into the block placed from it; a bare
	 * grade refuses a colour.
	 *
	 * @implements TC-DYE-001-PER01 — colour round-trips through the item component; bare cables stay undyed.
	 */
	public static void tcDye001Per01_colourRoundTripsThroughTheItem(GameTestHelper helper) {
		BlockPos insulated = new BlockPos(1, 2, 1);
		BlockPos bare = new BlockPos(3, 2, 1);
		helper.setBlock(insulated, ModContent.INSULATED_GOLD_CABLE.get());
		helper.setBlock(bare, ModContent.COPPER_CABLE.get());
		CableBlockEntity dyed = cable(helper, insulated);
		if (!dyed.setColor(DyeColor.LIME) || dyed.setColor(DyeColor.LIME)) {
			helper.fail("dyeing must report a change once and a no-op for the same colour");
			return;
		}
		if (cable(helper, bare).setColor(DyeColor.LIME) || cable(helper, bare).color() != null) {
			helper.fail("a bare cable must refuse a colour");
			return;
		}
		DataComponentMap components = dyed.collectComponents();
		if (components.get(ModDataComponents.CABLE_COLOR.get()) != DyeColor.LIME) {
			helper.fail("the dyed segment must hand its colour to the item it drops");
			return;
		}
		// Replace the block and feed the item's components back, as placing the dropped item does.
		helper.setBlock(insulated, Blocks.AIR);
		helper.setBlock(insulated, ModContent.INSULATED_GOLD_CABLE.get());
		CableBlockEntity placed = cable(helper, insulated);
		if (placed.color() != null) {
			helper.fail("a freshly placed cable must start undyed");
			return;
		}
		placed.applyComponents(components, DataComponentPatch.EMPTY);
		if (placed.color() != DyeColor.LIME) {
			helper.fail("placing a dyed item must dye the segment, got " + placed.color());
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking a dyed insulated segment of each metal drops an item that still carries the colour. The
	 * drop is rolled through the block's own loot table ({@link Block#getDrops}), the path breaking the
	 * block takes: the colour reaches the item only through the table's {@code copy_components}
	 * function, so a table whose function the codec silently skipped (MOD-689, the 26.2 line) drops an
	 * undyed cable here.
	 *
	 * @implements TC-DYE-001-PER02 — a broken dyed insulated cable drops a stack with the same colour.
	 */
	public static void tcDye001Per02_brokenDyedCableDropsItsColour(GameTestHelper helper) {
		Block[] grades = {ModContent.INSULATED_COPPER_CABLE.get(), ModContent.INSULATED_TIN_CABLE.get(),
				ModContent.INSULATED_GOLD_CABLE.get(), ModContent.INSULATED_ELECTRUM_CABLE.get()};
		DyeColor[] colours = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE, DyeColor.YELLOW};
		BlockPos[] positions = {new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(5, 2, 1),
				new BlockPos(3, 2, 3)};
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < grades.length; i++) {
			helper.setBlock(positions[i], grades[i]);
			if (!cable(helper, positions[i]).setColor(colours[i])) {
				helper.fail("rig: " + grades[i] + " refused the colour " + colours[i]);
				return;
			}
		}
		for (int i = 0; i < grades.length; i++) {
			BlockPos abs = helper.absolutePos(positions[i]);
			List<ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs, level.getBlockEntity(abs));
			Item item = grades[i].asItem();
			ItemStack dropped = null;
			int count = 0;
			for (ItemStack stack : drops) {
				if (stack.is(item)) {
					dropped = stack;
					count += stack.getCount();
				}
			}
			if (dropped == null || count != 1) {
				helper.fail(grades[i] + " dropped " + count + "x its own item (expected 1), drops " + drops);
				return;
			}
			DyeColor carried = dropped.get(ModDataComponents.CABLE_COLOR.get());
			if (carried != colours[i]) {
				helper.fail("a broken " + colours[i] + " " + grades[i] + " dropped an item coloured " + carried
						+ " — the loot table lost the colour");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * A Shift run follows the drawn connections of one grade, stops at another grade, and skips a
	 * segment that already has the colour.
	 *
	 * @implements TC-DYE-001-RUN01 — the Shift run dyes the connected same-grade run only.
	 */
	public static void tcDye001Run01_runStopsAtAnotherGrade(GameTestHelper helper) {
		Block[] line = {ModContent.INSULATED_COPPER_CABLE.get(), ModContent.INSULATED_COPPER_CABLE.get(),
				ModContent.INSULATED_COPPER_CABLE.get(), ModContent.INSULATED_TIN_CABLE.get(),
				ModContent.INSULATED_COPPER_CABLE.get()};
		for (int i = 0; i < line.length; i++) {
			helper.setBlock(new BlockPos(1 + i, 2, 1), line[i]);
		}
		for (int i = 0; i < line.length; i++) {
			tick(helper, be(helper, new BlockPos(1 + i, 2, 1)));
		}
		cable(helper, new BlockPos(2, 2, 1)).setColor(DyeColor.CYAN);
		List<CableBlockEntity> run = CableDyeing.run(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)),
				CableType.INSULATED_COPPER, DyeColor.CYAN);
		if (run.size() != 2) {
			helper.fail("expected the two undyed copper segments before the tin one, got " + run.size());
			return;
		}
		helper.succeed();
	}
}
