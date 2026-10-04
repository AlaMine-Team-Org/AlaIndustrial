package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.AssemblerStatus;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

/**
 * Characterization of the assembler's operation planner (MOD-714, batch 0), taken from the behaviour
 * as it is BEFORE the planner leaves {@code AssemblerBlockEntity}.
 *
 * <p>Each case pins one decision of the scratch-list planning that {@link AssemblerScenarios} does not
 * observe on its own: where a result lands in the output area, that a partial fit refuses the whole
 * operation, where a craft remainder goes when its own warehouse slot is taken, which warehouse slot an
 * ingredient is taken from, that a substituted charge-carrying craft is compared blind to its charge,
 * and that the authoring grid keeps its gaps through a save. The extraction batches that follow must
 * leave every one of them green without touching an expectation.
 *
 * <p>Kept out of {@link AssemblerScenarios} on purpose: that file is already past the size threshold
 * of the coding standard, and these cases share a theme of their own — the planner, not the machine.
 */
public final class AssemblerPlanningScenarios {

	/** The roster entries of this class (MOD-717, ADR-038): both lanes replay them from here. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AssemblerPlanningScenarios::insertIntoMergesBeforeEmpty,
						"assembler_planning_insert_into_merges_before_empty").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::insertIntoAllOrNothing,
						"assembler_planning_insert_into_all_or_nothing").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::insertIntoRespectsComponents,
						"assembler_planning_insert_into_respects_components").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::remainderHomeOccupiedGoesElsewhere,
						"assembler_planning_remainder_home_occupied_goes_elsewhere").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::remainderWarehouseFullGoesToOutput,
						"assembler_planning_remainder_warehouse_full_goes_to_output").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::remainderNowhereRefusesOperation,
						"assembler_planning_remainder_nowhere_refuses_operation").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::findAndTakeLowestSlotFirst,
						"assembler_planning_find_and_take_lowest_slot_first").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::substitutionIgnoresCharge,
						"assembler_planning_substitution_ignores_charge").ticks(40),
				RosterEntry.of(AssemblerPlanningScenarios::patternGridWithGapsSurvivesReload,
						"assembler_planning_pattern_grid_with_gaps_survives_reload").ticks(40));

		private Roster() {}
	}

	private AssemblerPlanningScenarios() {
	}

	/** Assembler under test; the warehouse module sits to its east. */
	private static final BlockPos ASM = new BlockPos(1, 2, 1);
	private static final BlockPos STORE = new BlockPos(2, 2, 1);

	/** A full buffer, so no case is ever accidentally energy-starved. */
	private static final long AMPLE_EU = 12000L;
	/** EU one operation costs — the balance contract from docs/PERFORMANCE.md. */
	private static final long EU_PER_OP = 480L;

	// -- rig ----------------------------------------------------------------------------------------

	private static AssemblerBlockEntity assembler(GameTestHelper helper) {
		AssemblerBlockEntity be = AlaGameTestHelper.place(helper, ASM, ModContent.ASSEMBLER.get(),
				AssemblerBlockEntity.class);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		return be;
	}

	private static StorageModuleBlockEntity warehouse(GameTestHelper helper) {
		helper.setBlock(STORE, ModContent.STORAGE_MODULE.get());
		return helper.getBlockEntity(STORE, StorageModuleBlockEntity.class);
	}

	/** Ticks for at most {@code ops} operations: the start tick, the working ticks, the finish tick. */
	private static int ticksFor(int ops) {
		return ops * (MachineRates.duration(Config.assemblerDuration, Config.globalMachineSpeedMultiplier) + 2);
	}

	/** A recorded blueprint of the nine {@code cells} with no cached result. */
	private static ItemStack blueprint(List<ItemStack> cells) {
		return AssemblyBlueprintItem.record(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
				BlueprintPattern.of(cells));
	}

	/** The same blueprint with the result cached on it, exactly as the machine's "Write" button makes it. */
	private static ItemStack writtenBlueprint(GameTestHelper helper, AssemblerBlockEntity be,
			List<ItemStack> cells) {
		ItemStack made = be.solve(helper.getLevel(), cells)
				.map(found -> found.value().assemble(CraftingInput
						.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, cells).input()))
				.orElse(ItemStack.EMPTY);
		return AssemblyBlueprintItem.record(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
				BlueprintPattern.of(cells), made);
	}

	/** Nine empty cells to lay a pattern out in. */
	private static List<ItemStack> emptyGrid() {
		return new ArrayList<>(Collections.nCopies(BlueprintPattern.GRID_SIZE, ItemStack.EMPTY));
	}

	/** Two oak planks in the left column — 4 sticks. */
	private static ItemStack stickBlueprint() {
		List<ItemStack> cells = emptyGrid();
		cells.set(0, new ItemStack(Items.OAK_PLANKS));
		cells.set(3, new ItemStack(Items.OAK_PLANKS));
		return blueprint(cells);
	}

	/** One honey bottle — 3 sugar, and the empty glass bottle comes back as the craft remainder. */
	private static ItemStack sugarBlueprint() {
		List<ItemStack> cells = emptyGrid();
		cells.set(0, new ItemStack(Items.HONEY_BOTTLE));
		return blueprint(cells);
	}

	private static ItemStack outputSlot(AssemblerBlockEntity be, int index) {
		return be.getItem(AssemblerBlockEntity.OUTPUT_SLOT_START + index);
	}

	private static void fillOutput(AssemblerBlockEntity be, int slots, ItemStack filler) {
		for (int i = 0; i < slots; i++) {
			be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_START + i, filler.copy());
		}
	}

	/** Every warehouse slot from {@code from} on holds a full stack of something no recipe here wants. */
	private static void fillWarehouse(StorageModuleBlockEntity store, int from) {
		for (int i = from; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
	}

	private static long spent(AssemblerBlockEntity be) {
		return AMPLE_EU - be.getEnergyStorage().getAmount();
	}

	private static boolean holds(ItemStack stack, Item item, int count) {
		return stack.is(item) && stack.getCount() == count;
	}

	// -- where the result goes ------------------------------------------------------------------------

	/**
	 * A result merges into a partial stack of itself before it takes an empty slot — even an empty slot
	 * with a lower index.
	 */
	public static void insertIntoMergesBeforeEmpty(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2)); // exactly one operation's worth
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_START + 1, new ItemStack(Items.STICK, 4));

		drive(be, helper, ticksFor(3));

		if (!holds(outputSlot(be, 1), Items.STICK, 8)) {
			helper.fail("the 4 new sticks must merge into the partial stack in output slot 1, found "
					+ outputSlot(be, 1));
			return;
		}
		if (!outputSlot(be, 0).isEmpty()) {
			helper.fail("output slot 0 must stay empty while a matching partial stack has room, found "
					+ outputSlot(be, 0));
			return;
		}
		helper.succeed();
	}

	/**
	 * A result that fits only in part is not placed at all: the operation is refused as OUTPUT_FULL,
	 * the output area and the warehouse are left exactly as they were, and no EU is spent.
	 */
	public static void insertIntoAllOrNothing(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		fillOutput(be, AssemblerBlockEntity.OUTPUT_SLOT_COUNT - 1, new ItemStack(Items.COBBLESTONE, 64));
		// Room for two of the four sticks, and nowhere else.
		be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_END - 1, new ItemStack(Items.STICK, 62));

		drive(be, helper, ticksFor(3));

		if (be.getStatus() != AssemblerStatus.OUTPUT_FULL) {
			helper.fail("a result that fits only in part must report OUTPUT_FULL, got " + be.getStatus());
			return;
		}
		if (!holds(outputSlot(be, AssemblerBlockEntity.OUTPUT_SLOT_COUNT - 1), Items.STICK, 62)) {
			helper.fail("the partial stack must not have been topped up by a refused operation, found "
					+ outputSlot(be, AssemblerBlockEntity.OUTPUT_SLOT_COUNT - 1));
			return;
		}
		if (!holds(store.getItem(0), Items.OAK_PLANKS, 2)) {
			helper.fail("a refused operation must not take materials, warehouse slot 0 holds "
					+ store.getItem(0));
			return;
		}
		if (spent(be) != 0) {
			helper.fail("a refused operation must not cost EU, spent " + spent(be));
			return;
		}
		helper.succeed();
	}

	/** A stack of the same item with different components is not merged into: the result takes a new slot. */
	public static void insertIntoRespectsComponents(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		ItemStack named = new ItemStack(Items.STICK, 4);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("planner characterization"));
		be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_START, named.copy());

		drive(be, helper, ticksFor(3));

		if (!ItemStack.matches(outputSlot(be, 0), named)) {
			helper.fail("the named stack in output slot 0 must be left alone, found " + outputSlot(be, 0));
			return;
		}
		ItemStack plain = outputSlot(be, 1);
		if (!holds(plain, Items.STICK, 4) || plain.has(DataComponents.CUSTOM_NAME)) {
			helper.fail("the plain sticks must go into the next empty slot, found " + plain);
			return;
		}
		helper.succeed();
	}

	// -- where a craft remainder goes -----------------------------------------------------------------

	/**
	 * When the remainder's own warehouse slot still holds something else (the rest of the ingredient
	 * stack), the remainder goes elsewhere in the warehouse — into a matching partial stack first.
	 */
	public static void remainderHomeOccupiedGoesElsewhere(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 2));
		store.setItem(5, new ItemStack(Items.GLASS_BOTTLE, 1));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, sugarBlueprint());

		drive(be, helper, ticksFor(1)); // exactly one operation's window

		if (!holds(outputSlot(be, 0), Items.SUGAR, 3)) {
			helper.fail("one honey bottle makes 3 sugar, output slot 0 holds " + outputSlot(be, 0));
			return;
		}
		if (!holds(store.getItem(0), Items.HONEY_BOTTLE, 1)) {
			helper.fail("one honey bottle must stay in its slot, found " + store.getItem(0));
			return;
		}
		if (!holds(store.getItem(5), Items.GLASS_BOTTLE, 2)) {
			helper.fail("with its own slot occupied the empty bottle must merge into the partial stack "
					+ "in slot 5, found " + store.getItem(5) + " there and " + store.getItem(1) + " in slot 1");
			return;
		}
		if (!store.getItem(1).isEmpty()) {
			helper.fail("nothing may land in the first empty warehouse slot while a partial stack has "
					+ "room, found " + store.getItem(1));
			return;
		}
		helper.succeed();
	}

	/** With no room anywhere in the warehouse the remainder goes into the output area, after the result. */
	public static void remainderWarehouseFullGoesToOutput(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 2));
		fillWarehouse(store, 1);
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, sugarBlueprint());

		drive(be, helper, ticksFor(1));

		if (!holds(outputSlot(be, 0), Items.SUGAR, 3)) {
			helper.fail("the result takes the first output slot, found " + outputSlot(be, 0));
			return;
		}
		if (!holds(outputSlot(be, 1), Items.GLASS_BOTTLE, 1)) {
			helper.fail("with the warehouse full the empty bottle must go to the output area, found "
					+ outputSlot(be, 1));
			return;
		}
		if (!holds(store.getItem(0), Items.HONEY_BOTTLE, 1)) {
			helper.fail("one honey bottle must stay in the warehouse, found " + store.getItem(0));
			return;
		}
		if (spent(be) != EU_PER_OP) {
			helper.fail("one operation costs " + EU_PER_OP + " EU, spent " + spent(be));
			return;
		}
		helper.succeed();
	}

	/**
	 * A remainder with nowhere to go — the warehouse full and the output area full once the result is
	 * in — refuses the whole operation as OUTPUT_FULL, and nothing is taken or spent.
	 */
	public static void remainderNowhereRefusesOperation(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 2));
		fillWarehouse(store, 1);
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, sugarBlueprint());
		// One free output slot: the sugar would fit, the bottle would not.
		fillOutput(be, AssemblerBlockEntity.OUTPUT_SLOT_COUNT - 1, new ItemStack(Items.COBBLESTONE, 64));

		drive(be, helper, ticksFor(3));

		if (be.getStatus() != AssemblerStatus.OUTPUT_FULL) {
			helper.fail("a remainder with nowhere to go must report OUTPUT_FULL, got " + be.getStatus());
			return;
		}
		if (!outputSlot(be, AssemblerBlockEntity.OUTPUT_SLOT_COUNT - 1).isEmpty()) {
			helper.fail("the refused operation must not have placed its result, found "
					+ outputSlot(be, AssemblerBlockEntity.OUTPUT_SLOT_COUNT - 1));
			return;
		}
		if (!holds(store.getItem(0), Items.HONEY_BOTTLE, 2)) {
			helper.fail("the refused operation must not take materials, found " + store.getItem(0));
			return;
		}
		if (spent(be) != 0) {
			helper.fail("a refused operation must not cost EU, spent " + spent(be));
			return;
		}
		helper.succeed();
	}

	// -- where an ingredient comes from -------------------------------------------------------------

	/** One ingredient in two warehouse slots: every unit is taken from the lower slot first. */
	public static void findAndTakeLowestSlotFirst(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(2, new ItemStack(Items.OAK_PLANKS, 2));
		store.setItem(4, new ItemStack(Items.OAK_PLANKS, 2));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());

		drive(be, helper, ticksFor(1));

		if (!holds(outputSlot(be, 0), Items.STICK, 4)) {
			helper.fail("one operation makes 4 sticks, output slot 0 holds " + outputSlot(be, 0));
			return;
		}
		if (!store.getItem(2).isEmpty()) {
			helper.fail("both planks must come out of slot 2, the lower one; it still holds " + store.getItem(2));
			return;
		}
		if (!holds(store.getItem(4), Items.OAK_PLANKS, 2)) {
			helper.fail("slot 4 must be untouched while slot 2 can supply, found " + store.getItem(4));
			return;
		}
		helper.succeed();
	}

	// -- substitution and charge ----------------------------------------------------------------------

	/** The stand-in tin of the gametest-only foreign mod, in {@code #c:ingots/tin} on both lanes. */
	private static final Identifier FOREIGN_TIN = Identifier.fromNamespaceAndPath("foreignmod", "tin_ingot");

	/**
	 * A substituted operation of a charge-carrying recipe runs although its result holds charge the
	 * blueprint's cached result does not: the two are compared blind to charge.
	 *
	 * <p>The energy pack is recorded with the mod's own tin and drained batteries, the shape a player's
	 * blueprint is normally written in. The warehouse holds only the foreign tin — a different item from
	 * the same {@code #c:ingots/tin} ingredient, so the cell is substituted — and charged batteries, so
	 * the pack it makes carries their EU. A comparison that saw the charge would refuse the operation.
	 */
	public static void substitutionIgnoresCharge(GameTestHelper helper) {
		Item foreignTin = BuiltInRegistries.ITEM.getOptional(FOREIGN_TIN).orElse(null);
		if (foreignTin == null) {
			helper.fail("the gametest-only foreign mod did not register " + FOREIGN_TIN
					+ " — this case needs a second item in #c:ingots/tin");
			return;
		}
		AssemblerBlockEntity be = assembler(helper);
		StorageModuleBlockEntity store = warehouse(helper);
		List<ItemStack> cells = energyPackGrid(new ItemStack(ModContent.TIN_INGOT.get()));
		ItemStack written = writtenBlueprint(helper, be, cells);
		if (AssemblyBlueprintItem.resultOf(written).isEmpty()
				|| ItemEnergy.get(AssemblyBlueprintItem.resultOf(written)) != 0) {
			helper.fail("the rig needs a blueprint promising an uncharged energy pack, got "
					+ AssemblyBlueprintItem.resultOf(written));
			return;
		}
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, written);
		be.toggleSubstitution(0);
		stockEnergyPack(store, foreignTin);

		drive(be, helper, ticksFor(3));

		ItemStack made = outputSlot(be, 0);
		if (!made.is(ModContent.ENERGY_PACK.get())) {
			helper.fail("the substituted energy pack must be made although it carries charge the blueprint's "
					+ "result does not; output slot 0 holds " + made + ", status " + be.getStatus());
			return;
		}
		if (ItemEnergy.get(made) <= 0) {
			helper.fail("the pack must carry the charged batteries' EU — otherwise the charge-blind branch "
					+ "was never taken; it holds " + ItemEnergy.get(made));
			return;
		}
		for (int i = 0; i < store.getContainerSize(); i++) {
			if (store.getItem(i).is(foreignTin)) {
				helper.fail("the foreign tin must have been consumed as the stand-in, it is still in slot " + i);
				return;
			}
		}
		helper.succeed();
	}

	/** The energy pack layout LEL / BBB / LTL with drained batteries and the given tin. */
	private static List<ItemStack> energyPackGrid(ItemStack tin) {
		List<ItemStack> cells = emptyGrid();
		for (int cell : new int[] {0, 2, 6, 8}) {
			cells.set(cell, new ItemStack(Items.LEATHER));
		}
		cells.set(1, new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get()));
		for (int cell = 3; cell <= 5; cell++) {
			cells.set(cell, new ItemStack(ModContent.BATTERY.get()));
		}
		cells.set(7, tin);
		return cells;
	}

	/** Exactly one energy pack's worth of materials, with charged batteries and the foreign tin. */
	private static void stockEnergyPack(StorageModuleBlockEntity store, Item foreignTin) {
		store.setItem(0, new ItemStack(Items.LEATHER, 4));
		store.setItem(1, new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get()));
		for (int slot = 2; slot <= 4; slot++) {
			ItemStack battery = new ItemStack(ModContent.BATTERY.get());
			ItemEnergy.set(battery, ToolConfig.batteryBuffer);
			store.setItem(slot, battery);
		}
		store.setItem(5, new ItemStack(foreignTin));
	}

	// -- the authoring grid ---------------------------------------------------------------------------

	/**
	 * An authoring grid with empty cells between its filled ones survives save → load → save: the second
	 * save is the same tag, the {@code "Pattern"} list keeps all nine positions, and every cell is back on
	 * its own index.
	 */
	public static void patternGridWithGapsSurvivesReload(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		AssemblerBlockEntity source = SaveFormatTestSupport.fresh(AssemblerBlockEntity::new,
				ModContent.ASSEMBLER.get());
		source.setPatternCell(1, new ItemStack(Items.OAK_PLANKS));
		source.setPatternCell(5, new ItemStack(Items.COBBLESTONE));
		source.setPatternCell(6, new ItemStack(Items.STICK));

		CompoundTag first = source.saveWithoutMetadata(registries);
		AssemblerBlockEntity restored = SaveFormatTestSupport.load(
				SaveFormatTestSupport.fresh(AssemblerBlockEntity::new, ModContent.ASSEMBLER.get()),
				registries, first);
		CompoundTag second = restored.saveWithoutMetadata(registries);

		if (first.getListOrEmpty("Pattern").size() != BlueprintPattern.GRID_SIZE) {
			helper.fail("the \"Pattern\" key must hold all nine cells, empties included; got " + first);
			return;
		}
		if (!first.equals(second)) {
			helper.fail("the re-save differs from the save: first " + first + ", then " + second);
			return;
		}
		for (int i = 0; i < BlueprintPattern.GRID_SIZE; i++) {
			ItemStack before = source.getPatternContainer().getItem(i);
			ItemStack after = restored.getPatternContainer().getItem(i);
			if (!ItemStack.matches(before, after)) {
				helper.fail("pattern cell " + i + " held " + before + " and came back as " + after);
				return;
			}
		}
		helper.succeed();
	}
}
