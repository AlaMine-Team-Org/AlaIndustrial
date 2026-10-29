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
import dev.alaindustrial.item.fluid.ItemFluid;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
						.fabricId("NetworkGameTest", "tcDye001Run01_runStopsAtAnotherGrade").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Wsh01_waterBucketWashesOneSegment,
						"tc_dye001_wsh01_water_bucket_washes_one_segment").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Wsh02_shiftWashesTheRunForOneBucket,
						"tc_dye001_wsh02_shift_washes_the_run_for_one_bucket").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Wsh03_waterOnACleanCableIsAbsorbed,
						"tc_dye001_wsh03_water_on_a_clean_cable_is_absorbed").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Wsh04_creativeKeepsTheBucket,
						"tc_dye001_wsh04_creative_keeps_the_bucket").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Wsh05_waterCapsuleWashesThroughTheFluidApi,
						"tc_dye001_wsh05_water_capsule_washes_through_the_fluid_api").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Wsh06_stackOfContainersSpendsOne,
						"tc_dye001_wsh06_stack_of_containers_spends_one").ticks(80),
				RosterEntry.of(CableDyeScenarios::tcDye001Wsh07_noWashWhereThePlayerMayNotBuild,
						"tc_dye001_wsh07_no_wash_where_the_player_may_not_build").ticks(80));

		private Roster() {}
	}

	private static final BlockPos GENERATOR = new BlockPos(1, 2, 1);
	private static final BlockPos BOX = new BlockPos(6, 2, 1);

	private CableDyeScenarios() {
	}

	static CableBlockEntity cable(GameTestHelper helper, BlockPos pos) {
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

	// ── WSH01–WSH07: washing the dye off with water (MOD-898) ──────────────────────────────────────

	/** A row of {@code grade} at y=2 from x=1, each segment dyed with {@code colours[i]} (null: undyed). */
	static void dyedRow(GameTestHelper helper, Block grade, DyeColor... colours) {
		for (int i = 0; i < colours.length; i++) {
			helper.setBlock(new BlockPos(1 + i, 2, 1), grade);
		}
		for (int i = 0; i < colours.length; i++) {
			BlockPos pos = new BlockPos(1 + i, 2, 1);
			tick(helper, be(helper, pos));
			cable(helper, pos).setColor(colours[i]);
		}
	}

	static ServerPlayer holding(GameTestHelper helper, ItemStack stack) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return player;
	}

	/** A real right-click on the top face of {@code pos}, through the loader's block-use chain. */
	static InteractionResult click(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND),
				InteractionHand.MAIN_HAND, hit);
	}

	private static ItemStack capsuleOf(Fluid fluid) {
		ItemStack stack = new ItemStack(ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(stack, fluid);
		return stack;
	}

	static ItemStack hand(ServerPlayer player) {
		return player.getItemInHand(InteractionHand.MAIN_HAND);
	}

	/**
	 * A water bucket clicked on a dyed segment strips that segment only, and the bucket comes back empty.
	 *
	 * @implements TC-DYE-001-WSH01 — water on a dyed insulated cable washes the clicked segment and empties
	 * the bucket.
	 */
	public static void tcDye001Wsh01_waterBucketWashesOneSegment(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_COPPER_CABLE.get(), DyeColor.RED, DyeColor.RED);
		ServerPlayer player = holding(helper, new ItemStack(Items.WATER_BUCKET));
		click(helper, player, new BlockPos(1, 2, 1));
		if (cable(helper, new BlockPos(1, 2, 1)).color() != null) {
			helper.fail("the clicked segment must be washed back to plain rubber");
			return;
		}
		if (cable(helper, new BlockPos(2, 2, 1)).color() != DyeColor.RED) {
			helper.fail("a plain click must wash the clicked segment only");
			return;
		}
		if (cable(helper, new BlockPos(1, 2, 1)).collectComponents().get(ModDataComponents.CABLE_COLOR.get()) != null) {
			helper.fail("a washed segment must hand an undyed item on");
			return;
		}
		if (!hand(player).is(Items.BUCKET)) {
			helper.fail("washing must spend the water: hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}

	/**
	 * Shift washes every dyed segment of the connected same-grade run, stops at another grade, and costs
	 * one bucket.
	 *
	 * @implements TC-DYE-001-WSH02 — Shift washes the connected same-grade run for one bucket of water.
	 */
	public static void tcDye001Wsh02_shiftWashesTheRunForOneBucket(GameTestHelper helper) {
		Block copper = ModContent.INSULATED_COPPER_CABLE.get();
		Block tin = ModContent.INSULATED_TIN_CABLE.get();
		Block[] line = {copper, copper, copper, tin};
		DyeColor[] colours = {DyeColor.BLUE, null, DyeColor.LIME, DyeColor.BLUE};
		for (int i = 0; i < line.length; i++) {
			helper.setBlock(new BlockPos(1 + i, 2, 1), line[i]);
		}
		for (int i = 0; i < line.length; i++) {
			BlockPos pos = new BlockPos(1 + i, 2, 1);
			tick(helper, be(helper, pos));
			cable(helper, pos).setColor(colours[i]);
		}
		ServerPlayer player = holding(helper, new ItemStack(Items.WATER_BUCKET));
		player.setShiftKeyDown(true);
		click(helper, player, new BlockPos(2, 2, 1));
		for (int i = 0; i < 3; i++) {
			if (cable(helper, new BlockPos(1 + i, 2, 1)).color() != null) {
				helper.fail("the copper segment at x=" + (1 + i) + " must be washed by the Shift run");
				return;
			}
		}
		if (cable(helper, new BlockPos(4, 2, 1)).color() != DyeColor.BLUE) {
			helper.fail("the Shift run must stop at the tin segment");
			return;
		}
		if (!hand(player).is(Items.BUCKET)) {
			helper.fail("a Shift wash costs one bucket of water: hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}

	/**
	 * Water clicked on an undyed insulated cable — plainly and with Shift over a run with no dyed segment —
	 * and on a bare one takes the click, spends nothing and changes nothing.
	 *
	 * <p>A bucket pours from {@code use()}, which the client sends only when the click on the block was not
	 * consumed, so the consumed result is what keeps the water in the bucket: the scenario asserts that,
	 * not the cell above ({@code useItemOn} never pours, with or without the hook).
	 *
	 * @implements TC-DYE-001-WSH03 — water on a clean or bare cable is absorbed: no water poured, none spent.
	 */
	public static void tcDye001Wsh03_waterOnACleanCableIsAbsorbed(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_GOLD_CABLE.get(), null, null, null);
		BlockPos bare = new BlockPos(1, 2, 3);
		helper.setBlock(bare, ModContent.COPPER_CABLE.get());
		BlockPos[] targets = {new BlockPos(1, 2, 1), new BlockPos(2, 2, 1), bare};
		boolean[] shift = {false, true, false};
		for (int i = 0; i < targets.length; i++) {
			ServerPlayer player = holding(helper, new ItemStack(Items.WATER_BUCKET));
			player.setShiftKeyDown(shift[i]);
			InteractionResult result = click(helper, player, targets[i]);
			String what = targets[i] + (shift[i] ? " with Shift" : "");
			if (!result.consumesAction()) {
				helper.fail("water clicked on the cable at " + what + " was not taken (result " + result
						+ "), so the bucket would pour beside it");
				return;
			}
			if (!hand(player).is(Items.WATER_BUCKET)) {
				helper.fail("nothing to wash at " + what + ", yet the water was spent");
				return;
			}
		}
		for (BlockPos pos : targets) {
			if (cable(helper, pos).color() != null) {
				helper.fail("a wash with nothing to wash must not change the cable at " + pos);
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * In creative the bucket stays full.
	 *
	 * @implements TC-DYE-001-WSH04 — a creative wash keeps the water bucket full.
	 */
	public static void tcDye001Wsh04_creativeKeepsTheBucket(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_ELECTRUM_CABLE.get(), DyeColor.PURPLE);
		ServerPlayer player = holding(helper, new ItemStack(Items.WATER_BUCKET));
		player.getAbilities().instabuild = true;
		click(helper, player, new BlockPos(1, 2, 1));
		if (cable(helper, new BlockPos(1, 2, 1)).color() != null) {
			helper.fail("a creative wash must still wash the segment");
			return;
		}
		if (!hand(player).is(Items.WATER_BUCKET)) {
			helper.fail("creative must keep the bucket full: hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}

	/**
	 * A container that is not the vanilla bucket washes through the loader's item fluid API and becomes
	 * what it says it becomes: a water capsule turns into an empty capsule. A capsule of lava is not
	 * water and washes nothing.
	 *
	 * @implements TC-DYE-001-WSH05 — any water container speaking the item fluid API washes; the container
	 * decides what it becomes.
	 */
	public static void tcDye001Wsh05_waterCapsuleWashesThroughTheFluidApi(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_TIN_CABLE.get(), DyeColor.ORANGE, null, DyeColor.ORANGE);
		// Asked of the bridge, not clicked: a lava capsule clicked on the cable would pour lava beside it.
		ServerPlayer lava = holding(helper, capsuleOf(Fluids.LAVA));
		if (ItemFluidBridge.get().takeFromHand(lava, InteractionHand.MAIN_HAND, Fluids.WATER, 1000L, true)) {
			helper.fail("a capsule of lava must not count as water");
			return;
		}
		ServerPlayer player = holding(helper, capsuleOf(Fluids.WATER));
		click(helper, player, new BlockPos(1, 2, 1));
		if (cable(helper, new BlockPos(1, 2, 1)).color() != null) {
			helper.fail("a water capsule must wash the segment through the item fluid API");
			return;
		}
		if (!hand(player).is(ModContent.VACUUM_CAPSULE.get())) {
			helper.fail("the capsule must decide what it becomes (an empty capsule), hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}

	/**
	 * A stack of water containers spends one: the rest stay in hand, the emptied one goes to the inventory.
	 *
	 * @implements TC-DYE-001-WSH06 — a wash with a stack of water containers spends exactly one of them.
	 */
	public static void tcDye001Wsh06_stackOfContainersSpendsOne(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_COPPER_CABLE.get(), DyeColor.GREEN, DyeColor.GREEN);
		ItemStack stack = capsuleOf(Fluids.WATER);
		stack.setCount(4);
		ServerPlayer player = holding(helper, stack);
		player.setShiftKeyDown(true);
		click(helper, player, new BlockPos(1, 2, 1));
		if (cable(helper, new BlockPos(1, 2, 1)).color() != null
				|| cable(helper, new BlockPos(2, 2, 1)).color() != null) {
			helper.fail("a stack of water capsules must wash the run");
			return;
		}
		if (!hand(player).is(ModContent.FILLED_VACUUM_CAPSULE.get()) || hand(player).getCount() != 3) {
			helper.fail("a wash spends one container of the stack, hand holds " + hand(player));
			return;
		}
		int empty = 0;
		for (ItemStack slot : player.getInventory()) {
			if (slot.is(ModContent.VACUUM_CAPSULE.get())) {
				empty += slot.getCount();
			}
		}
		if (empty != 1) {
			helper.fail("the emptied capsule must land in the inventory, found " + empty);
			return;
		}
		helper.succeed();
	}

	/**
	 * Where the player may not build, water washes nothing and is not spent.
	 *
	 * @implements TC-DYE-001-WSH07 — no wash, and no water spent, for a player who may not build.
	 */
	public static void tcDye001Wsh07_noWashWhereThePlayerMayNotBuild(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_TIN_CABLE.get(), DyeColor.WHITE);
		ServerPlayer player = holding(helper, new ItemStack(Items.WATER_BUCKET));
		player.getAbilities().mayBuild = false;
		click(helper, player, new BlockPos(1, 2, 1));
		if (cable(helper, new BlockPos(1, 2, 1)).color() != DyeColor.WHITE) {
			helper.fail("a player who may not build must not wash a cable");
			return;
		}
		if (!hand(player).is(Items.WATER_BUCKET)) {
			helper.fail("a refused wash must not spend the water, hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}
}
