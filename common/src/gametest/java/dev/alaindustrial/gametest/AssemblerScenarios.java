package dev.alaindustrial.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.recipe.IngredientSubstitution;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/**
 * Loader-neutral gametest bodies for the Assembler (MOD-275). Wrapped by the Fabric
 * {@code AssemblerGameTest} suite and registered on the NeoForge {@code gameTestServer} lane
 * ({@code NeoForgeGameTests}, {@code assembler_*}), so both loaders run the SAME bodies.
 *
 * <p>Every case here corresponds to a documented failure of a comparable machine in another mod (the
 * task's acceptance criteria list them): a crafter that never stops and buries the store, a
 * non-returnable remainder, a crash on a recipe the datapack removed, a perpetual loop, and EU burnt
 * while idle.
 *
 * <p><b>Numbers are pinned as literals where they are a contract.</b> Writing an assertion in terms of
 * the same {@code Config} field the machine reads makes it agree with any value at all, including a
 * wrong one — so the cost of an operation is checked against 480, not against
 * {@code assemblerEuPerTick * assemblerDuration}, and {@link #con01BalanceNumbers} is what turns a
 * deliberate rebalance into an honest, single failing test instead of a silent drift.
 */
public final class AssemblerScenarios {

	private AssemblerScenarios() {
	}

	/** Assembler under test; the warehouse module sits to its east. */
	private static final BlockPos ASM = new BlockPos(1, 2, 1);
	private static final BlockPos STORE = new BlockPos(2, 2, 1);
	/** A second assembler on the far side of the same module — both draw from one warehouse. */
	private static final BlockPos ASM_B = new BlockPos(3, 2, 1);

	/** A full buffer: 12000 EU, 25 operations' worth, so no test is ever accidentally energy-starved. */
	private static final long AMPLE_EU = 12000L;
	/** EU one operation costs — the balance contract from docs/PERFORMANCE.md, not a derived value. */
	private static final long EU_PER_OP = 480L;

	// ── rig ──────────────────────────────────────────────────────────────────────────────────────

	private static AssemblerBlockEntity assembler(GameTestHelper helper, BlockPos pos) {
		AssemblerBlockEntity be = AlaGameTestHelper.place(helper, pos, ModContent.ASSEMBLER.get(),
				AssemblerBlockEntity.class);
		be.getEnergyStorage().amount = AMPLE_EU;
		return be;
	}

	private static StorageModuleBlockEntity warehouse(GameTestHelper helper) {
		helper.setBlock(STORE, ModContent.STORAGE_MODULE.get());
		return helper.getBlockEntity(STORE, StorageModuleBlockEntity.class);
	}

	/**
	 * Ticks to run at most {@code ops} operations.
	 *
	 * <p>Exact, with no slack: one operation is the tick that starts it, {@code duration} working
	 * ticks and the tick that finishes it. A test that asserts "one operation ran" only means it if
	 * the window cannot hold two — most cases below additionally stock the warehouse for exactly the
	 * number of operations they expect, so they do not depend on the window at all.
	 */
	private static int ticksFor(int ops) {
		return ops * (Config.scaledDuration(Config.assemblerDuration) + 2);
	}

	private static void drive(GameTestHelper helper, AssemblerBlockEntity be, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	/** Builder for a nine-cell pattern; {@link #blueprint()} stamps it onto a recorded blueprint. */
	private static final class Grid {
		private final List<ItemStack> cells =
				new ArrayList<>(Collections.nCopies(BlueprintPattern.GRID_SIZE, ItemStack.EMPTY));

		private Grid at(int cell, Item item) {
			cells.set(cell, new ItemStack(item));
			return this;
		}

		private ItemStack blueprint() {
			return AssemblyBlueprintItem.record(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
					BlueprintPattern.of(cells));
		}

		/**
		 * The same blueprint with the result cached on it, exactly as the machine's "Write" button
		 * produces — which is what substitution validates against, so the substitution cases must not
		 * run against the resultless shortcut above.
		 */
		private ItemStack blueprint(GameTestHelper helper, AssemblerBlockEntity be) {
			ItemStack made = be.solve(helper.getLevel(), cells)
					.map(found -> found.value().assemble(net.minecraft.world.item.crafting.CraftingInput
							.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, cells).input()))
					.orElse(ItemStack.EMPTY);
			return AssemblyBlueprintItem.record(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
					BlueprintPattern.of(cells), made);
		}
	}

	/** Two oak planks stacked in the left column — 4 sticks. Off-centre, so the input trims to 1×2. */
	private static ItemStack stickBlueprint() {
		return new Grid().at(0, Items.OAK_PLANKS).at(3, Items.OAK_PLANKS).blueprint();
	}

	/** How many of {@code item} a container holds in total. */
	private static int count(net.minecraft.world.Container container, Item item) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** How many of {@code item} sit in the assembler's six-slot output area. */
	private static int output(AssemblerBlockEntity be, Item item) {
		int total = 0;
		for (int i = 0; i < AssemblerBlockEntity.OUTPUT_SLOT_COUNT; i++) {
			ItemStack stack = be.getItem(AssemblerBlockEntity.OUTPUT_SLOT_START + i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// ── the craft contract ───────────────────────────────────────────────────────────────────────

	/** The machine's own craft, as shipped: gears in the corners, casing at the heart. */
	private static List<ItemStack> craftGrid() {
		return List.of(
				new ItemStack(ModContent.IRON_GEAR.get()),
				new ItemStack(Items.CRAFTING_TABLE),
				new ItemStack(ModContent.IRON_GEAR.get()),
				new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get()),
				new ItemStack(ModContent.ADVANCED_MACHINE_CASING_ITEM.get()),
				new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get()),
				new ItemStack(ModContent.IRON_GEAR.get()),
				new ItemStack(ModContent.STORAGE_MODULE_ITEM.get()),
				new ItemStack(ModContent.IRON_GEAR.get()));
	}

	/** The same grid with the four corners emptied — the plus shape the craft used to be. */
	private static List<ItemStack> craftGridWithoutCorners() {
		List<ItemStack> grid = new ArrayList<>(craftGrid());
		for (int corner : new int[] {0, 2, 6, 8}) {
			grid.set(corner, ItemStack.EMPTY);
		}
		return grid;
	}

	/**
	 * The assembler's own craft recipe loads, resolves to the assembler, and needs all nine cells.
	 *
	 * <p>This goes through the server's real {@code RecipeManager} (via
	 * {@link AssemblerBlockEntity#solve}), so it is the one check that a typo in an ingredient id
	 * cannot survive: a recipe whose key names an item that does not exist is dropped at load and
	 * never resolves — the silent failure MOD-292 exists to stop. The negative half is what makes
	 * "all nine cells" a contract rather than a restatement of the grid this file builds: the
	 * previous plus-shaped craft is fed in and must NOT resolve.
	 *
	 * @implements TC-ASM-016 — the craft occupies all nine cells and really loads.
	 */
	public static void con02CraftFillsNineCells(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);

		Optional<RecipeHolder<CraftingRecipe>> found = be.solve(helper.getLevel(), craftGrid());
		if (found.isEmpty()) {
			helper.fail("the assembler's own craft recipe did not resolve — an ingredient id in "
					+ "data/alaindustrial/recipe/assembler.json is not a registered item, so the "
					+ "recipe never loaded");
			return;
		}
		Identifier id = found.get().id().identifier();
		if (!id.equals(Industrialization.id("assembler"))) {
			helper.fail("the nine-cell grid resolves to " + id + ", not alaindustrial:assembler");
			return;
		}
		if (!be.solve(helper.getLevel(), craftGridWithoutCorners()).isEmpty()) {
			helper.fail("the plus-shaped grid still resolves — the four corners are not required, "
					+ "so the craft does not occupy all nine cells");
			return;
		}
		helper.succeed();
	}

	// ── the balance contract ─────────────────────────────────────────────────────────────────────

	/**
	 * The three balance numbers the rest of this file is written against.
	 *
	 * <p>Its whole job is to be the ONE test that fails when someone rebalances the machine. Without
	 * it, every "480 EU" assertion below would look like an arbitrary magic number and the next
	 * balance change would leave a scatter of unexplained red.
	 *
	 * @implements TC-ASM-001 — 12 EU/t × 40 ticks = 480 EU per operation.
	 */
	public static void con01BalanceNumbers(GameTestHelper helper) {
		if (Config.assemblerEuPerTick != 12) {
			helper.fail("the assembler draws 12 EU/t (docs/PERFORMANCE.md), got " + Config.assemblerEuPerTick);
			return;
		}
		if (Config.assemblerDuration != 40) {
			helper.fail("one operation is 40 ticks (docs/PERFORMANCE.md), got " + Config.assemblerDuration);
			return;
		}
		if (Config.assemblerBuffer != 12000) {
			helper.fail("the buffer is 12000 EU (docs/PERFORMANCE.md), got " + Config.assemblerBuffer);
			return;
		}
		if ((long) Config.assemblerEuPerTick * Config.assemblerDuration != EU_PER_OP) {
			helper.fail("one operation must cost " + EU_PER_OP + " EU");
			return;
		}
		helper.succeed();
	}

	// ── EU is spent on work and on nothing else ──────────────────────────────────────────────────

	/**
	 * One operation makes what the recipe says, takes what it needs from the warehouse, and costs
	 * exactly {@value #EU_PER_OP} EU.
	 *
	 * @implements TC-ASM-002 — the happy path, end to end.
	 */
	public static void fun01OneOperationCostsExactlyOneOperation(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		// Exactly one operation's worth of planks, so the assertion holds however long the machine runs.
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());

		drive(helper, be, ticksFor(3));

		if (output(be, Items.STICK) != 4) {
			helper.fail("one operation should put 4 sticks in the output area, got " + output(be, Items.STICK));
			return;
		}
		if (count(store, Items.OAK_PLANKS) != 0) {
			helper.fail("both planks should have been taken out of the warehouse, "
					+ count(store, Items.OAK_PLANKS) + " left");
			return;
		}
		long spent = AMPLE_EU - be.getEnergyStorage().amount;
		if (spent != EU_PER_OP) {
			helper.fail("one operation must cost exactly " + EU_PER_OP + " EU, spent " + spent);
			return;
		}
		helper.succeed();
	}

	/**
	 * With nothing in the warehouse the machine burns no EU at all.
	 *
	 * <p>The failure this guards is the classic one: a machine that "waits" by running its cycle and
	 * discarding the result still drains a base dry overnight, and nothing about it looks broken.
	 *
	 * @implements TC-ASM-003 — no materials, no EU.
	 */
	public static void neg01NoEuWithoutMaterials(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		warehouse(helper);
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());

		drive(helper, be, ticksFor(3));

		if (be.getEnergyStorage().amount != AMPLE_EU) {
			helper.fail("an assembler with no materials must not spend EU, spent "
					+ (AMPLE_EU - be.getEnergyStorage().amount));
			return;
		}
		if (be.getStatus() != AssemblerStatus.NO_MATERIALS) {
			helper.fail("the machine should report NO_MATERIALS, got " + be.getStatus());
			return;
		}
		if (output(be, Items.STICK) != 0) {
			helper.fail("nothing should have been produced out of an empty warehouse");
			return;
		}
		helper.succeed();
	}

	/**
	 * A full output area stops the machine dead — no EU, no consumed materials.
	 *
	 * @implements TC-ASM-004 — a jammed output costs nothing.
	 */
	public static void neg02NoEuWhenOutputFull(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		for (int i = 0; i < AssemblerBlockEntity.OUTPUT_SLOT_COUNT; i++) {
			be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_START + i, new ItemStack(Items.COBBLESTONE, 64));
		}

		drive(helper, be, ticksFor(3));

		if (be.getEnergyStorage().amount != AMPLE_EU) {
			helper.fail("an assembler with a full output area must not spend EU, spent "
					+ (AMPLE_EU - be.getEnergyStorage().amount));
			return;
		}
		if (count(store, Items.OAK_PLANKS) != 64) {
			helper.fail("a jammed assembler must not eat materials, warehouse has "
					+ count(store, Items.OAK_PLANKS) + " planks left of 64");
			return;
		}
		if (be.getStatus() != AssemblerStatus.OUTPUT_FULL) {
			helper.fail("the machine should report OUTPUT_FULL, got " + be.getStatus());
			return;
		}
		helper.succeed();
	}

	/**
	 * The output area is six slots and the machine uses all of them: with the first five occupied by
	 * something else, the result goes into the sixth rather than jamming on the first.
	 *
	 * <p>A one-slot output is the known cause of a stalled auto-crafter; six slots only help if the
	 * insertion actually walks past an occupied one.
	 *
	 * @implements TC-ASM-005 — the whole output area is used before the machine calls itself full.
	 */
	public static void fun02OutputAreaUsesEverySlot(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2)); // exactly one operation's worth
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		for (int i = 0; i < AssemblerBlockEntity.OUTPUT_SLOT_COUNT - 1; i++) {
			be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_START + i, new ItemStack(Items.COBBLESTONE, 64));
		}

		drive(helper, be, ticksFor(3));

		ItemStack last = be.getItem(AssemblerBlockEntity.OUTPUT_SLOT_END - 1);
		if (!last.is(Items.STICK) || last.getCount() != 4) {
			helper.fail("with only the last output slot free, the sticks must go there, found " + last);
			return;
		}
		helper.succeed();
	}

	// ── craft remainders ─────────────────────────────────────────────────────────────────────────

	/**
	 * The Forge Hammer comes back to the exact warehouse slot it was taken from, one point of
	 * durability worse — the mod's own stack-dependent craft remainder (MOD-078), driven through the
	 * assembler.
	 *
	 * <p>Two independent things fail this one, which is why the layout is chosen the way it is:
	 *
	 * <ul>
	 *   <li><b>The remainder path.</b> Reading {@code Item.getCraftingRemainder()} directly instead of
	 *       {@code CraftingRecipe.getRemainingItems} walks straight past both loaders' hooks, and the
	 *       hammer comes back undamaged — or not at all.</li>
	 *   <li><b>The trimming offset.</b> The ingredients sit in cells 4 and 5, so
	 *       {@code CraftingInput} trims to a 2×1 grid at left=1, top=1 and the remainder list has two
	 *       entries, not nine. Using a remainder index as a grid index therefore points at cell 0,
	 *       which no ingredient came from, and the hammer is filed away in the first free warehouse
	 *       slot (1) instead of its own (5). Both slots are asserted.</li>
	 * </ul>
	 *
	 * @implements TC-ASM-006 — a stack-dependent remainder returns to its own warehouse slot.
	 */
	public static void fun03HammerRemainderReturnsToItsOwnSlot(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		ItemStack hammer = new ItemStack(ModContent.FORGE_HAMMER.get());
		hammer.setDamageValue(10);
		store.setItem(0, new ItemStack(Items.IRON_INGOT, 1)); // exactly one operation's worth
		store.setItem(5, hammer);
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START,
				new Grid().at(4, ModContent.FORGE_HAMMER.get()).at(5, Items.IRON_INGOT).blueprint());

		drive(helper, be, ticksFor(3));

		if (output(be, ModContent.IRON_PLATE.get()) != 1) {
			helper.fail("hammering one ingot should make one iron plate, got "
					+ output(be, ModContent.IRON_PLATE.get()));
			return;
		}
		if (count(store, Items.IRON_INGOT) != 0) {
			helper.fail("the ingot should have been consumed, " + count(store, Items.IRON_INGOT) + " left");
			return;
		}
		ItemStack back = store.getItem(5);
		if (!back.is(ModContent.FORGE_HAMMER.get())) {
			helper.fail("the hammer must return to warehouse slot 5, the slot it came from; found " + back
					+ " there and " + store.getItem(1) + " in slot 1");
			return;
		}
		if (back.getDamageValue() != 11) {
			helper.fail("the hammer must come back with exactly one more point of damage (10 -> 11), got "
					+ back.getDamageValue());
			return;
		}
		if (!store.getItem(1).isEmpty()) {
			helper.fail("nothing should have been filed into warehouse slot 1: the remainder belongs in the "
					+ "slot its ingredient came from, found " + store.getItem(1));
			return;
		}
		helper.succeed();
	}

	/**
	 * An ingredient that returns itself does not make the machine run forever.
	 *
	 * <p>The Forge Hammer is exactly that shape: it goes into the grid and comes back to the warehouse
	 * every single cycle, so the only thing that ever stops this recipe is the ingot next to it. A
	 * machine that fed on its own output — or that re-ran a cycle whose materials were gone — would
	 * keep producing here. Two ingots means two plates and no more, however long it is left running.
	 *
	 * @implements TC-ASM-007 — a self-returning ingredient terminates with the consumable.
	 */
	public static void neg03SelfReturningIngredientTerminates(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
		store.setItem(1, new ItemStack(ModContent.FORGE_HAMMER.get()));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START,
				new Grid().at(4, ModContent.FORGE_HAMMER.get()).at(5, Items.IRON_INGOT).blueprint());

		// Room for six operations; only two can ever happen.
		drive(helper, be, ticksFor(6));

		int plates = output(be, ModContent.IRON_PLATE.get());
		if (plates != 2) {
			helper.fail("two ingots must yield exactly two plates however long the machine runs, got " + plates);
			return;
		}
		if (count(store, Items.IRON_INGOT) != 0) {
			helper.fail("both ingots should be gone, " + count(store, Items.IRON_INGOT) + " left");
			return;
		}
		if (count(store, ModContent.FORGE_HAMMER.get()) != 1) {
			helper.fail("the hammer must still be in the warehouse exactly once, found "
					+ count(store, ModContent.FORGE_HAMMER.get()));
			return;
		}
		long spent = AMPLE_EU - be.getEnergyStorage().amount;
		if (spent != 2 * EU_PER_OP) {
			helper.fail("two operations must cost exactly " + (2 * EU_PER_OP) + " EU, spent " + spent
					+ " — a machine still cycling after its materials ran out would spend more");
			return;
		}
		if (be.getStatus() != AssemblerStatus.NO_MATERIALS) {
			helper.fail("with the ingots gone the machine should report NO_MATERIALS, got " + be.getStatus());
			return;
		}
		helper.succeed();
	}

	// ── the blueprint queue ──────────────────────────────────────────────────────────────────────

	/**
	 * A blueprint with no materials is skipped, not waited on: the queue moves past it and runs the one
	 * that can work.
	 *
	 * @implements TC-ASM-008 — a starved blueprint does not block the queue.
	 */
	public static void fun04QueueSkipsStarvedBlueprint(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2)); // exactly one operation's worth
		// Slot 0 wants nine iron ingots (an iron block); the warehouse holds none of them.
		Grid ironBlock = new Grid();
		for (int cell = 0; cell < BlueprintPattern.GRID_SIZE; cell++) {
			ironBlock.at(cell, Items.IRON_INGOT);
		}
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, ironBlock.blueprint());
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + 1, stickBlueprint());

		drive(helper, be, ticksFor(3));

		if (output(be, Items.STICK) != 4) {
			helper.fail("the queue must skip the starved blueprint and run the second one, got "
					+ output(be, Items.STICK) + " sticks");
			return;
		}
		if (output(be, Items.IRON_BLOCK) != 0) {
			helper.fail("the starved blueprint must not have produced anything");
			return;
		}
		helper.succeed();
	}

	/**
	 * Two runnable blueprints both get their turn.
	 *
	 * <p>The reason the queue is a rotation and not a priority list: with unlimited planks, a
	 * top-down machine would run the first blueprint for ever and the second would never see a
	 * single cycle. Both products in the output area is what proves the cursor moves.
	 *
	 * @implements TC-ASM-009 — the queue rotates instead of starving its tail.
	 */
	public static void fun05QueueRotatesBetweenBlueprints(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
		store.setItem(1, new ItemStack(Items.OAK_LOG, 64));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + 1,
				new Grid().at(0, Items.OAK_LOG).blueprint());

		// The one case here that must NOT bound its materials: the whole point is that the first
		// blueprint could keep going and hands over anyway, so the warehouse is deliberately deep and
		// the window is exactly two operations. A top-down machine spends both of them on sticks.
		drive(helper, be, ticksFor(2));

		if (output(be, Items.STICK) != 4) {
			helper.fail("the first blueprint should have run once, got " + output(be, Items.STICK) + " sticks");
			return;
		}
		if (output(be, Items.OAK_PLANKS) != 4) {
			helper.fail("the second blueprint should have had its turn too — with a strict top-down "
					+ "priority it never would; got " + output(be, Items.OAK_PLANKS) + " planks");
			return;
		}
		helper.succeed();
	}

	// ── a blueprint whose recipe is not there ────────────────────────────────────────────────────

	/**
	 * A blueprint whose layout resolves to no recipe at all — the shape a datapack change leaves behind
	 * — stops the machine and says so. It does not throw, and it does not quietly eat the ingredients
	 * it would have used.
	 *
	 * @implements TC-ASM-010 — a recipe that no longer exists stalls the machine safely.
	 */
	public static void neg04MissingRecipeStopsWithoutCrashing(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.DIAMOND, 8));
		// Two diamonds side by side are not a recipe in any pack shipped here.
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START,
				new Grid().at(0, Items.DIAMOND).at(1, Items.DIAMOND).blueprint());

		drive(helper, be, ticksFor(3));

		if (be.getStatus() != AssemblerStatus.NO_RECIPE) {
			helper.fail("a blueprint with no recipe should report NO_RECIPE, got " + be.getStatus());
			return;
		}
		if (count(store, Items.DIAMOND) != 8) {
			helper.fail("nothing may be consumed for a recipe that does not exist, warehouse has "
					+ count(store, Items.DIAMOND) + " diamonds left of 8");
			return;
		}
		if (be.getEnergyStorage().amount != AMPLE_EU) {
			helper.fail("a blueprint with no recipe must not cost EU, spent "
					+ (AMPLE_EU - be.getEnergyStorage().amount));
			return;
		}
		helper.succeed();
	}

	// ── two machines, one warehouse ──────────────────────────────────────────────────────────────

	// ── "what recipe is this?" — the window and the tooltip ──────────────────────────────────────

	/**
	 * A blueprint written by the machine says what it makes, in its tooltip, by name and count.
	 *
	 * <p>The defect this pins down was a usability one, not a logic one: every recorded blueprint read
	 * "recipe recorded" and nothing else, so a queue of three was three identical pieces of paper. The
	 * line has to come out as <i>Blueprint: Stick ×4</i>, which means the result must be resolved and
	 * cached at write time — the client has no way to solve a crafting recipe in 26.2 and cannot work it
	 * out later.
	 *
	 * <p>The fallback is asserted too, and deliberately: a blueprint recorded without a resolved result
	 * must still say <i>something</i> rather than showing an empty or broken line.
	 *
	 * @implements TC-ASM-012 — a recorded blueprint names its product.
	 */
	public static void gui01BlueprintTooltipNamesItsResult(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		be.setItem(AssemblerBlockEntity.BLANK_SLOT,
				new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
		be.setPatternCell(0, new ItemStack(Items.OAK_PLANKS));
		be.setPatternCell(3, new ItemStack(Items.OAK_PLANKS));

		if (!be.writePattern()) {
			helper.fail("two planks in the left column are a recipe — Write should have recorded it");
			return;
		}
		ItemStack blueprint = be.getItem(AssemblerBlockEntity.BLANK_SLOT);
		ItemStack cached = AssemblyBlueprintItem.resultOf(blueprint);
		if (!cached.is(Items.STICK) || cached.getCount() != 4) {
			helper.fail("the blueprint must carry what it makes (4 sticks) so the client can name it "
					+ "without a recipe manager; got " + cached);
			return;
		}
		TranslatableContents line = tooltipLine(blueprint, 0);
		if (line == null || !"tooltip.alaindustrial.assembly_blueprint.result".equals(line.getKey())) {
			helper.fail("the tooltip must name the product, not just say a recipe is recorded; got "
					+ (line == null ? "no translatable line" : line.getKey()));
			return;
		}
		Object[] args = line.getArgs();
		String expected = new ItemStack(Items.STICK).getHoverName().getString();
		if (args.length != 2 || !expected.equals(String.valueOf(
				args[0] instanceof Component name ? name.getString() : args[0]))) {
			helper.fail("the tooltip should name the item (" + expected + "), got "
					+ java.util.Arrays.toString(args));
			return;
		}
		if (!Integer.valueOf(4).equals(args[1])) {
			helper.fail("the tooltip should carry the count 4, got " + args[1]);
			return;
		}
		// A blueprint recorded with no resolved result (the shape of one written before the cache
		// existed) falls back to the generic line rather than showing nothing.
		TranslatableContents fallback = tooltipLine(stickBlueprint(), 0);
		if (fallback == null || !"tooltip.alaindustrial.assembly_blueprint.recorded".equals(fallback.getKey())) {
			helper.fail("a blueprint with no cached result must fall back to the generic line, got "
					+ (fallback == null ? "no translatable line" : fallback.getKey()));
			return;
		}
		helper.succeed();
	}

	/**
	 * The Work tab shows the queued blueprint's layout, and goes on showing it while the player is
	 * laying out a recipe of their own on the Record tab.
	 *
	 * <p>Asserted on {@link AssemblerMenu#blueprintView}, the projection the Work tab renders from,
	 * because that is the whole of the decision: which layout is on screen and what it makes.
	 *
	 * <p><b>This case changed with the tab split (MOD-275).</b> When both jobs shared one screen the
	 * projection had to arbitrate — the player's own grid took the display away from the machine's
	 * blueprint the instant a cell was filled, and the machine's layout vanished out from under them.
	 * With the two on separate tabs there is nothing to arbitrate, so the second half of this test is
	 * now the opposite assertion: filling an authoring cell must NOT disturb the Work tab.
	 *
	 * @implements TC-ASM-013 — the Work tab shows the active blueprint, and authoring does not disturb it.
	 */
	public static void gui02WindowShowsTheActiveBlueprint(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		// Slot 0 empty on purpose: the fallback has to FIND the recorded blueprint, not assume slot 0.
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + 1, recordedStickBlueprint());
		AssemblerMenu menu = new AssemblerMenu(1,
				helper.makeMockPlayer(GameType.SURVIVAL).getInventory(), be, ContainerLevelAccess.NULL);

		if (menu.isRecordedBlueprint(0) || !menu.isRecordedBlueprint(1)) {
			helper.fail("only queue slot 1 holds a recorded blueprint");
			return;
		}
		if (menu.firstRecordedBlueprintSlot() != 1) {
			helper.fail("the first recorded blueprint is in slot 1, got " + menu.firstRecordedBlueprintSlot());
			return;
		}
		AssemblerMenu.BlueprintView idle = menu.blueprintView(-1);
		if (!idle.isPresent() || idle.blueprintSlot() != 1) {
			helper.fail("with nothing running the Work tab must show queue slot 1's layout; got slot "
					+ idle.blueprintSlot());
			return;
		}
		if (!idle.cells().get(0).is(Items.OAK_PLANKS) || !idle.cells().get(3).is(Items.OAK_PLANKS)) {
			helper.fail("the shown layout must be the blueprint's own cells (planks at 0 and 3), got "
					+ idle.cells());
			return;
		}
		if (!idle.cells().get(1).isEmpty()) {
			helper.fail("cell 1 of that layout is empty and must stay empty, got " + idle.cells().get(1));
			return;
		}
		if (!idle.result().is(Items.STICK) || idle.result().getCount() != 4) {
			helper.fail("the preview must show what the blueprint makes (4 sticks), got " + idle.result());
			return;
		}
		// The player authors a recipe of their own on the Record tab. The Work tab is a different job and
		// must not notice: this is the defect the tabs were introduced to fix.
		be.setPatternCell(4, new ItemStack(Items.DIAMOND));
		AssemblerMenu.BlueprintView whileAuthoring = menu.blueprintView(1);
		if (!whileAuthoring.isPresent() || whileAuthoring.blueprintSlot() != 1) {
			helper.fail("authoring a recipe must not take the Work tab away from the machine's blueprint; "
					+ "got slot " + whileAuthoring.blueprintSlot());
			return;
		}
		if (!whileAuthoring.cells().get(0).is(Items.OAK_PLANKS) || !whileAuthoring.cells().get(4).isEmpty()) {
			helper.fail("the Work tab must still show the blueprint's cells, not the authoring grid's; got "
					+ whileAuthoring.cells());
			return;
		}
		// ... and the authoring grid is untouched by any of that — it is the Record tab's own state.
		if (!menu.slots.get(AssemblerMenu.GRID_SLOT_START + 4).getItem().is(Items.DIAMOND)) {
			helper.fail("the authoring cell the player filled must still hold their item");
			return;
		}
		helper.succeed();
	}

	/**
	 * While an operation runs, the machine reports which queue slot it is running from — on the data
	 * channel the window's marker is drawn from.
	 *
	 * <p>Slot 0 is deliberately a blueprint with no materials, so "the machine reports 1" cannot be
	 * satisfied by reporting the first slot, or zero, or anything else convenient.
	 *
	 * @implements TC-ASM-014 — the active blueprint slot reaches the GUI's data channel.
	 */
	public static void gui03ActiveBlueprintSlotIsSynced(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		Grid ironBlock = new Grid();
		for (int cell = 0; cell < BlueprintPattern.GRID_SIZE; cell++) {
			ironBlock.at(cell, Items.IRON_INGOT);
		}
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, ironBlock.blueprint());
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + 1, stickBlueprint());

		if (be.getDataAccess().get(4) != -1) {
			helper.fail("an idle assembler reports no active blueprint, got " + be.getDataAccess().get(4));
			return;
		}
		// A few ticks in: started, nowhere near the 40-tick finish that would clear the slot again.
		drive(helper, be, 5);

		if (be.activeBlueprintSlot() != 1) {
			helper.fail("the machine should be running the blueprint it can actually feed (slot 1), got "
					+ be.activeBlueprintSlot());
			return;
		}
		if (be.getDataAccess().get(4) != 1) {
			helper.fail("channel 4 is what the window marks the working slot from; expected 1, got "
					+ be.getDataAccess().get(4));
			return;
		}
		helper.succeed();
	}

	/**
	 * The blueprint's icon is wired to the cached-result component, and a blank keeps the grid motif.
	 *
	 * <p>Whether the product is actually <em>drawn</em> is a client question, gated by the L3 screenshot
	 * stand — which runs on Fabric only. What is checkable on <b>both</b> loaders, headless, is the one
	 * seam that silently rots: the item definition names the data component in text
	 * ({@code "component": "alaindustrial:blueprint_result"}) while the machine writes it through a Java
	 * constant. Rename the constant and every test in this file still passes, every icon quietly reverts
	 * to the blank sheet, and nobody finds out until someone looks at a blueprint.
	 */
	public static void gui04BlueprintIconIsWiredToTheCachedResult(GameTestHelper helper) {
		JsonObject model = itemDefinitionModel(helper, "assembly_blueprint");
		if (model == null) {
			return;
		}
		String component = model.has("component") ? model.get("component").getAsString() : null;
		if (!"minecraft:condition".equals(asString(model, "type"))
				|| !"minecraft:has_component".equals(asString(model, "property"))
				|| !ModDataComponents.BLUEPRINT_RESULT_ID.toString().equals(component)) {
			helper.fail("items/assembly_blueprint.json must switch on the presence of "
					+ ModDataComponents.BLUEPRINT_RESULT_ID + "; got type=" + asString(model, "type")
					+ " property=" + asString(model, "property") + " component=" + component);
			return;
		}
		JsonObject blank = model.getAsJsonObject("on_false");
		if (!"minecraft:model".equals(asString(blank, "type"))
				|| !Industrialization.id("item/assembly_blueprint").toString()
						.equals(asString(blank, "model"))) {
			helper.fail("a blank blueprint keeps the plain grid model; got " + blank);
			return;
		}
		if (!mentionsType(model.get("on_true"), Industrialization.id("blueprint_result").toString())) {
			helper.fail("a recorded blueprint must compose in the "
					+ Industrialization.id("blueprint_result") + " model type — that is what draws the "
					+ "product into the icon; got " + model.get("on_true"));
			return;
		}
		helper.succeed();
	}

	// -- the two tabs (MOD-275) -------------------------------------------------------------------

	/**
	 * A slot that belongs to the tab the player is <b>not</b> looking at cannot be interacted with from
	 * the server side.
	 *
	 * <p><b>Why this needs a test at all.</b> {@code Slot#isActive()} — the flag a hidden slot is hidden
	 * with — is consulted <em>only</em> on the client: in 26.2 {@code AbstractContainerScreen} calls it
	 * three times (the slot render pass, the per-slot paint, {@code getHoveredSlot}) and
	 * {@code AbstractContainerMenu} does not call it once; there is not a single reference to it anywhere
	 * in {@code minecraft-common.jar}. So hiding a slot stops the client drawing and targeting it and
	 * leaves the server perfectly willing to accept a click on it — a forged or merely desynced client
	 * could move items into a tab it is not showing. {@link AssemblerMenu} closes that by mirroring the
	 * tab and refusing in {@code clicked} and {@code quickMoveStack}.
	 *
	 * <p>Every refusal below is paired with the same action on the tab where the slot <em>is</em> visible,
	 * which must succeed. Without that half the test would still pass if the slot were simply broken.
	 *
	 * @implements TC-ASM-017 — the hidden tab's slots are unreachable from the server.
	 */
	public static void tab01HiddenTabSlotsAreUnreachable(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AssemblerMenu menu = new AssemblerMenu(1, player.getInventory(), be, ContainerLevelAccess.NULL);

		int queue = menu.findSlot(be, AssemblerBlockEntity.BLUEPRINT_SLOT_START).orElse(-1);
		int blank = menu.findSlot(be, AssemblerBlockEntity.BLANK_SLOT).orElse(-1);
		int output = menu.findSlot(be, AssemblerBlockEntity.OUTPUT_SLOT_START).orElse(-1);
		if (queue < 0 || blank < 0 || output < 0) {
			helper.fail("the menu must bind the queue, the blank slot and the output area");
			return;
		}

		// 1. The Record tab is up; the queue belongs to the Work tab and must refuse a placement.
		menu.setActiveTab(AssemblerMenu.TAB_RECORD);
		menu.setCarried(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
		menu.clicked(queue, 0, ContainerInput.PICKUP, player);
		if (!be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START).isEmpty()) {
			helper.fail("a blueprint reached the queue while the queue was on the hidden tab");
			return;
		}
		if (menu.getCarried().isEmpty()) {
			helper.fail("the refused click must leave the item on the cursor, not swallow it");
			return;
		}
		// ... and the very same click lands once the queue is on screen.
		menu.setActiveTab(AssemblerMenu.TAB_WORK);
		menu.clicked(queue, 0, ContainerInput.PICKUP, player);
		if (!be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START)
				.is(ModContent.ASSEMBLY_BLUEPRINT.get())) {
			helper.fail("with the Work tab up the same click must place the blueprint — otherwise the "
					+ "check above proves nothing");
			return;
		}

		// 2. Mirror image: the blank slot belongs to the Record tab.
		menu.setCarried(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
		menu.clicked(blank, 0, ContainerInput.PICKUP, player);
		if (!be.getItem(AssemblerBlockEntity.BLANK_SLOT).isEmpty()) {
			helper.fail("a blueprint reached the blank slot while it was on the hidden tab");
			return;
		}
		menu.setActiveTab(AssemblerMenu.TAB_RECORD);
		menu.clicked(blank, 0, ContainerInput.PICKUP, player);
		if (!be.getItem(AssemblerBlockEntity.BLANK_SLOT).is(ModContent.ASSEMBLY_BLUEPRINT.get())) {
			helper.fail("with the Record tab up the same click must place the blueprint");
			return;
		}

		// 3. Taking OUT of a hidden slot is the same door: the output area is on the Work tab.
		be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_START, new ItemStack(Items.STICK, 4));
		menu.setCarried(ItemStack.EMPTY);
		menu.clicked(output, 0, ContainerInput.PICKUP, player);
		if (!menu.getCarried().isEmpty()) {
			helper.fail("sticks were lifted out of the output area while it was on the hidden tab");
			return;
		}

		// 4. Shift-click routing follows the tab too: a blueprint in the inventory joins the queue on the
		//    Work tab and the blank slot on the Record tab, never the one the player cannot see.
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, ItemStack.EMPTY);
		be.setItem(AssemblerBlockEntity.BLANK_SLOT, ItemStack.EMPTY);
		int invSlot = menu.findSlot(player.getInventory(), 0).orElse(-1);
		if (invSlot < 0) {
			helper.fail("the menu must bind the player's inventory");
			return;
		}
		player.getInventory().setItem(0, new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
		menu.quickMoveStack(player, invSlot);
		if (!be.getItem(AssemblerBlockEntity.BLANK_SLOT).is(ModContent.ASSEMBLY_BLUEPRINT.get())
				|| !be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START).isEmpty()) {
			helper.fail("on the Record tab a shift-clicked blueprint belongs in the blank slot, not the "
					+ "queue the player cannot see");
			return;
		}
		be.setItem(AssemblerBlockEntity.BLANK_SLOT, ItemStack.EMPTY);
		menu.setActiveTab(AssemblerMenu.TAB_WORK);
		player.getInventory().setItem(0, new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
		menu.quickMoveStack(player, invSlot);
		if (!be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START).is(ModContent.ASSEMBLY_BLUEPRINT.get())
				|| !be.getItem(AssemblerBlockEntity.BLANK_SLOT).isEmpty()) {
			helper.fail("on the Work tab a shift-clicked blueprint belongs in the queue, not the blank "
					+ "slot the player cannot see");
			return;
		}
		helper.succeed();
	}

	/**
	 * Switching to the recording tab is a <b>view</b> switch, not a mode switch: the machine goes on
	 * crafting the whole time.
	 *
	 * <p>A global "recording mode" that halted production was considered and rejected, and this is the
	 * assertion that keeps it rejected — the operation must complete while the window is on the Record
	 * tab, and it must complete having spent exactly one operation's worth of EU.
	 *
	 * @implements TC-ASM-018 — recording does not stop production.
	 */
	public static void tab02RecordingDoesNotStopProduction(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2)); // exactly one operation's worth
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		AssemblerMenu menu = new AssemblerMenu(1,
				helper.makeMockPlayer(GameType.SURVIVAL).getInventory(), be, ContainerLevelAccess.NULL);

		menu.setActiveTab(AssemblerMenu.TAB_RECORD);
		// The player lays out a completely different recipe while the machine works.
		be.setPatternCell(0, new ItemStack(Items.COBBLESTONE));
		long before = be.getEnergyStorage().amount;
		drive(helper, be, ticksFor(1));

		if (output(be, Items.STICK) != 4) {
			helper.fail("the machine must keep crafting while the player is on the recording tab; got "
					+ output(be, Items.STICK) + " sticks");
			return;
		}
		long spent = before - be.getEnergyStorage().amount;
		if (spent != EU_PER_OP) {
			helper.fail("one operation costs " + EU_PER_OP + " EU whichever tab is on screen, spent " + spent);
			return;
		}
		if (!be.getPatternContainer().getItem(0).is(Items.COBBLESTONE)) {
			helper.fail("the authoring grid is the Record tab's own state and production must not clear it");
			return;
		}
		helper.succeed();
	}

	/**
	 * "Write" stamps the blank in the Record tab's own slot — and only while that tab is on screen.
	 *
	 * <p>Two things at once, because they are the same decision: the button now takes its blank from a
	 * dedicated slot rather than hunting through the queue (which lives on the other tab, so a Record tab
	 * that needed one there would put both jobs back in one place), and the button channel is gated on
	 * the tab exactly as the slots are.
	 *
	 * @implements TC-ASM-019 — Write consumes the Record tab's blank, and only from the Record tab.
	 */
	public static void tab03WriteBelongsToTheRecordTab(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AssemblerMenu menu = new AssemblerMenu(1, player.getInventory(), be, ContainerLevelAccess.NULL);

		// A blank in the queue is NOT what Write consumes any more: the Record tab has its own slot.
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START,
				new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
		be.setPatternCell(0, new ItemStack(Items.OAK_PLANKS));
		be.setPatternCell(3, new ItemStack(Items.OAK_PLANKS));
		menu.setActiveTab(AssemblerMenu.TAB_RECORD);
		if (be.canWrite()) {
			helper.fail("with the blank slot empty there is nothing to write on, whatever sits in the queue");
			return;
		}
		if (menu.clickMenuButton(player, AssemblerMenu.BUTTON_WRITE)) {
			helper.fail("Write must refuse when the Record tab's blank slot is empty");
			return;
		}

		be.setItem(AssemblerBlockEntity.BLANK_SLOT,
				new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
		if (!be.canWrite()) {
			helper.fail("a resolvable grid and a blank in the slot are the whole precondition for Write");
			return;
		}
		// The Work tab is up: the recording controls are off screen, so the button channel refuses them.
		menu.setActiveTab(AssemblerMenu.TAB_WORK);
		if (menu.clickMenuButton(player, AssemblerMenu.BUTTON_WRITE)) {
			helper.fail("Write fired while the recording tab was not on screen");
			return;
		}
		if (menu.clickMenuButton(player, AssemblerMenu.BUTTON_CELL_BASE)) {
			helper.fail("a ghost cell was written while the recording tab was not on screen");
			return;
		}
		if (AssemblyBlueprintItem.isRecorded(be.getItem(AssemblerBlockEntity.BLANK_SLOT))) {
			helper.fail("nothing may have been recorded from the Work tab");
			return;
		}

		// Back on the Record tab it goes through, in place, and the queue is untouched.
		if (!menu.clickMenuButton(player, AssemblerMenu.BUTTON_TAB_BASE + AssemblerMenu.TAB_RECORD)) {
			helper.fail("the tab button must be accepted on the server");
			return;
		}
		if (!menu.clickMenuButton(player, AssemblerMenu.BUTTON_WRITE)) {
			helper.fail("Write must record from the Record tab");
			return;
		}
		ItemStack written = be.getItem(AssemblerBlockEntity.BLANK_SLOT);
		if (!AssemblyBlueprintItem.isRecorded(written)
				|| !AssemblyBlueprintItem.resultOf(written).is(Items.STICK)) {
			helper.fail("the recorded blueprint replaces the blank in place; got " + written);
			return;
		}
		if (AssemblyBlueprintItem.isRecorded(be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START))) {
			helper.fail("the blank sitting in the queue must be left alone — recording is not queueing");
			return;
		}
		helper.succeed();
	}

	/** The {@code model} object of an item definition, or {@code null} after failing the test. */
	private static JsonObject itemDefinitionModel(GameTestHelper helper, String path) {
		String resource = "/assets/" + Industrialization.MOD_ID + "/items/" + path + ".json";
		try (java.io.InputStream in = AssemblerScenarios.class.getResourceAsStream(resource)) {
			if (in == null) {
				helper.fail("no item definition on the classpath at " + resource);
				return null;
			}
			JsonObject root = JsonParser.parseReader(
					new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
					.getAsJsonObject();
			return root.getAsJsonObject("model");
		} catch (Exception e) {
			helper.fail("could not read " + resource + ": " + e);
			return null;
		}
	}

	private static String asString(JsonObject object, String key) {
		return object != null && object.has(key) ? object.get(key).getAsString() : null;
	}

	/** True if any object anywhere under {@code element} declares {@code "type": type}. */
	private static boolean mentionsType(JsonElement element, String type) {
		if (element instanceof JsonObject object) {
			if (type.equals(asString(object, "type"))) {
				return true;
			}
			for (String key : object.keySet()) {
				if (mentionsType(object.get(key), type)) {
					return true;
				}
			}
			return false;
		}
		if (element instanceof JsonArray array) {
			for (JsonElement child : array) {
				if (mentionsType(child, type)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Tooltip line {@code index} of {@code stack}, or {@code null} when it is not a translatable one. */
	private static TranslatableContents tooltipLine(ItemStack stack, int index) {
		List<Component> lines = new ArrayList<>();
		stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT,
				lines::add, TooltipFlag.NORMAL);
		if (index >= lines.size()) {
			return null;
		}
		return lines.get(index).getContents() instanceof TranslatableContents translatable
				? translatable : null;
	}

	/** The stick blueprint as the machine itself would write it — layout plus the cached result. */
	private static ItemStack recordedStickBlueprint() {
		return AssemblyBlueprintItem.record(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
				AssemblyBlueprintItem.patternOf(stickBlueprint()), new ItemStack(Items.STICK, 4));
	}

	/**
	 * Two assemblers sharing one warehouse settle on the right totals: ten planks make twenty sticks
	 * between them, and the warehouse ends empty.
	 *
	 * <p>What this rules out is the pair of failures that competition for a shared inventory produces —
	 * both machines counting the same plank (more sticks than the materials allow) and both taking it
	 * (fewer). The numbers are exact, so either one is a failure rather than a rounding difference.
	 *
	 * @implements TC-ASM-011 — several assemblers on one cluster conserve items.
	 */
	public static void fun06TwoAssemblersShareOneWarehouse(GameTestHelper helper) {
		AssemblerBlockEntity first = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		AssemblerBlockEntity second = assembler(helper, ASM_B);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 10));
		first.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		second.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());

		int ticks = ticksFor(8);
		for (int i = 0; i < ticks; i++) {
			BlockPos a = first.getBlockPos();
			first.serverTick(helper.getLevel(), a, helper.getLevel().getBlockState(a));
			BlockPos b = second.getBlockPos();
			second.serverTick(helper.getLevel(), b, helper.getLevel().getBlockState(b));
		}

		int planksLeft = count(store, Items.OAK_PLANKS);
		int sticks = output(first, Items.STICK) + output(second, Items.STICK);
		if (planksLeft != 0) {
			helper.fail("ten planks are five whole operations — the warehouse should end empty, "
					+ planksLeft + " left");
			return;
		}
		if (sticks != 20) {
			helper.fail("five operations make twenty sticks between the two machines, got " + sticks
					+ " — more means a plank was counted twice, fewer means one was eaten for nothing");
			return;
		}
		helper.succeed();
	}

	// -- ingredient substitution (MOD-275 tail) ---------------------------------------------------

	/** The stick pattern recorded with OAK planks, plus the cached result the "Write" button stores. */
	private static ItemStack oakStickBlueprint(GameTestHelper helper, AssemblerBlockEntity be) {
		return new Grid().at(0, Items.OAK_PLANKS).at(3, Items.OAK_PLANKS).blueprint(helper, be);
	}

	/**
	 * The stairs pattern recorded with OAK planks. {@code minecraft:oak_stairs} names
	 * {@code minecraft:oak_planks} literally rather than {@code #minecraft:planks} — verified in the
	 * 26.2 data — which is what makes it the negative half of {@link #sub03OnlyThisRecipesIngredient}.
	 */
	private static ItemStack oakStairsBlueprint(GameTestHelper helper, AssemblerBlockEntity be) {
		return new Grid()
				.at(0, Items.OAK_PLANKS)
				.at(3, Items.OAK_PLANKS).at(4, Items.OAK_PLANKS)
				.at(6, Items.OAK_PLANKS).at(7, Items.OAK_PLANKS).at(8, Items.OAK_PLANKS)
				.blueprint(helper, be);
	}

	/**
	 * Substitution is off until the player asks for it, and works the moment they do.
	 *
	 * <p>Both halves run on the SAME blueprint and the SAME warehouse — only the toggle differs — so the
	 * test cannot pass by accident: if substitution were always on, the first half fails; if it never
	 * worked, the second does.
	 *
	 * <p>The stick recipe is written against {@code #minecraft:planks}, so birch is a legitimate stand-in
	 * for the recorded oak. With the toggle off the machine must starve rather than reach for it — and
	 * must burn no EU doing so, since a starved assembler spends nothing (TC-ASM-003).
	 *
	 * @implements TC-ASM-020 — substitution defaults to off and is honoured when switched on.
	 */
	public static void sub01OffByDefaultOnWhenToggled(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.BIRCH_PLANKS, 2)); // one operation's worth, wrong species
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, oakStickBlueprint(helper, be));

		drive(helper, be, ticksFor(3));

		if (output(be, Items.STICK) != 0) {
			helper.fail("substitution is off by default, so birch planks must not satisfy a blueprint "
					+ "recorded with oak — got " + output(be, Items.STICK) + " sticks");
			return;
		}
		if (be.getEnergyStorage().amount != AMPLE_EU) {
			helper.fail("a blueprint it cannot supply must cost no EU at all, spent "
					+ (AMPLE_EU - be.getEnergyStorage().amount));
			return;
		}

		if (!be.toggleSubstitution(0)) {
			helper.fail("the substitution toggle refused a recorded blueprint");
			return;
		}
		drive(helper, be, ticksFor(3));

		if (output(be, Items.STICK) != 4) {
			helper.fail("with substitution on, birch planks are a legal stand-in in a recipe written "
					+ "against #minecraft:planks — expected 4 sticks, got " + output(be, Items.STICK));
			return;
		}
		if (count(store, Items.BIRCH_PLANKS) != 0) {
			helper.fail("both birch planks should have been consumed, "
					+ count(store, Items.BIRCH_PLANKS) + " left");
			return;
		}
		helper.succeed();
	}

	/**
	 * What the player recorded wins whenever the warehouse can supply it.
	 *
	 * <p>Refined Storage does not do this and it is a standing player complaint: a substituting pattern
	 * that eats the wrong species while the recorded one sits in the box.
	 *
	 * <p><b>The species are chosen so that neither wrong answer can pass by accident.</b> The blueprint
	 * records BIRCH; oak sits in the LOWER warehouse slot and is the FIRST entry of
	 * {@code #minecraft:planks} (verified in the 26.2 data). So an implementation that scans the
	 * warehouse first, and one that walks the ingredient's candidates first, both take oak — only
	 * "the recorded item first" takes birch. An earlier draft recorded oak and was green under a
	 * deliberately broken build for exactly that reason.
	 *
	 * @implements TC-ASM-021 — the recorded item is preferred over any stand-in.
	 */
	public static void sub02RecordedItemWins(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		store.setItem(1, new ItemStack(Items.BIRCH_PLANKS, 2));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START,
				new Grid().at(0, Items.BIRCH_PLANKS).at(3, Items.BIRCH_PLANKS).blueprint(helper, be));
		be.toggleSubstitution(0);

		// Exactly one operation's window: with substitution on, a second would eat the oak as well.
		drive(helper, be, ticksFor(1));

		if (output(be, Items.STICK) != 4) {
			helper.fail("one operation should make 4 sticks, got " + output(be, Items.STICK));
			return;
		}
		if (count(store, Items.BIRCH_PLANKS) != 0) {
			helper.fail("the recorded birch planks must be spent first, "
					+ count(store, Items.BIRCH_PLANKS) + " left");
			return;
		}
		if (count(store, Items.OAK_PLANKS) != 2) {
			helper.fail("the oak planks must be untouched while birch is available, "
					+ count(store, Items.OAK_PLANKS) + " left of 2");
			return;
		}
		helper.succeed();
	}

	/** The nine cells of a pattern, as {@link IngredientSubstitution} wants them. */
	private static List<ItemStack> gridOf(ItemStack blueprint) {
		BlueprintPattern recorded = AssemblyBlueprintItem.patternOf(blueprint);
		List<ItemStack> cells = new ArrayList<>(BlueprintPattern.GRID_SIZE);
		for (int i = 0; i < BlueprintPattern.GRID_SIZE; i++) {
			cells.add(recorded.cell(i));
		}
		return cells;
	}

	/** What may stand in for cell {@code cell} of {@code blueprint}, straight from the recipe. */
	private static List<Item> candidatesFor(GameTestHelper helper, AssemblerBlockEntity be,
			ItemStack blueprint, int cell) {
		List<ItemStack> cells = gridOf(blueprint);
		CraftingRecipe recipe = be.solve(helper.getLevel(), cells).orElseThrow().value();
		return IngredientSubstitution.candidatesByCell(recipe,
				net.minecraft.world.item.crafting.CraftingInput.ofPositioned(
						BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, cells),
				cells).get(cell);
	}

	/**
	 * Candidates come from THIS recipe's ingredient, never "from the tag in general".
	 *
	 * <p>{@code oak_stairs} names {@code minecraft:oak_planks} literally while {@code stick} names
	 * {@code #minecraft:planks} (both verified in the 26.2 data). An implementation that looked up the
	 * recorded item's tags instead of the recipe's ingredient would offer birch for an oak-stairs
	 * blueprint — the "which stairs will I get" failure the spec rules out.
	 *
	 * <p><b>The candidate set is asserted directly, and that is not belt-and-braces.</b> Checking only
	 * the machine's behaviour is not enough: a tag-sourced candidate list is caught by the re-assembly
	 * gate ({@link #sub04ValidatedByReassembly}) and the machine simply refuses, so the outcome looks
	 * identical. Verified by building exactly that naive implementation and watching this test stay
	 * green until the assertion below was added. So the test states the two halves of the promise
	 * separately: for the exact-id recipe the set is <em>only</em> oak, for the tag recipe it includes
	 * birch, and neither is the constant the machine reads.
	 *
	 * @implements TC-ASM-022 — an exact-id ingredient admits no stand-in.
	 */
	public static void sub03OnlyThisRecipesIngredient(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);

		List<Item> stairs = candidatesFor(helper, be, oakStairsBlueprint(helper, be), 0);
		if (!stairs.equals(List.of(Items.OAK_PLANKS))) {
			helper.fail("oak_stairs names minecraft:oak_planks literally, so its only candidate is oak — "
					+ "got " + stairs + "; a candidate list drawn from the recorded item's TAGS instead of "
					+ "the recipe's ingredient looks exactly like this");
			return;
		}
		List<Item> sticks = candidatesFor(helper, be, oakStickBlueprint(helper, be), 0);
		if (!sticks.contains(Items.BIRCH_PLANKS)) {
			helper.fail("stick names #minecraft:planks, so birch must be a candidate — got " + sticks);
			return;
		}

		store.setItem(0, new ItemStack(Items.BIRCH_PLANKS, 64));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, oakStairsBlueprint(helper, be));
		be.toggleSubstitution(0);

		drive(helper, be, ticksFor(3));

		if (output(be, Items.BIRCH_STAIRS) != 0 || output(be, Items.OAK_STAIRS) != 0) {
			helper.fail("oak_stairs names minecraft:oak_planks literally, so birch is not a candidate — "
					+ "got " + output(be, Items.BIRCH_STAIRS) + " birch and "
					+ output(be, Items.OAK_STAIRS) + " oak stairs");
			return;
		}
		if (be.getEnergyStorage().amount != AMPLE_EU) {
			helper.fail("nothing ran, so nothing should have been spent; spent "
					+ (AMPLE_EU - be.getEnergyStorage().amount));
			return;
		}

		// Positive control: the very same blueprint runs the moment its own species is in stock.
		store.setItem(1, new ItemStack(Items.OAK_PLANKS, 6));
		drive(helper, be, ticksFor(3));
		if (output(be, Items.OAK_STAIRS) != 4) {
			helper.fail("with oak in stock the blueprint must make 4 oak stairs, got "
					+ output(be, Items.OAK_STAIRS));
			return;
		}
		helper.succeed();
	}

	/**
	 * A stand-in is accepted only if the layout still re-assembles into what the blueprint promised.
	 *
	 * <p>A shapeless recipe has no positions by definition, so every one of its ingredients is offered
	 * as a candidate for every cell — which is exactly as permissive as the recipe is, and would be
	 * wrong without the re-assembly gate. Here the recorded layout is red + yellow dye and the warehouse
	 * holds yellow only: yellow IS a candidate for the red cell, so the naive answer is
	 * "yellow + yellow", which matches nothing. The machine must refuse rather than run.
	 *
	 * @implements TC-ASM-023 — substitution is validated by re-assembling the recipe.
	 */
	public static void sub04ValidatedByReassembly(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.DYE.yellow(), 64));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START,
				new Grid().at(0, Items.DYE.red()).at(1, Items.DYE.yellow()).blueprint(helper, be));
		be.toggleSubstitution(0);

		drive(helper, be, ticksFor(3));

		if (output(be, Items.DYE.orange()) != 0) {
			helper.fail("two yellow dyes are not an orange dye recipe — the substituted layout must be "
					+ "rejected, got " + output(be, Items.DYE.orange()) + " orange dye");
			return;
		}
		if (count(store, Items.DYE.yellow()) != 64) {
			helper.fail("a refused operation must consume nothing, "
					+ count(store, Items.DYE.yellow()) + " yellow dye left of 64");
			return;
		}

		// Positive control: with the recorded red dye present the very same blueprint runs. A generous
		// window, because the refusals above have put the machine to sleep and it costs a few idle ticks
		// to come back; it cannot overrun, since one red dye is exactly one operation's worth and a
		// second operation would have to substitute yellow for red — the very thing just refused.
		store.setItem(1, new ItemStack(Items.DYE.red(), 1));
		drive(helper, be, ticksFor(4));
		if (output(be, Items.DYE.orange()) != 2) {
			helper.fail("red + yellow makes 2 orange dye, got " + output(be, Items.DYE.orange()));
			return;
		}
		helper.succeed();
	}

	/**
	 * The toggle's button id reaches substitution — and nothing else reaches it by accident.
	 *
	 * <p>The other four substitution scenarios call {@code toggleSubstitution} on the block entity
	 * directly, so none of them touches the button channel: with the id range mis-assigned the feature
	 * is unreachable in-game and all four still pass. That is not hypothetical — the tab split and the
	 * substitution tail were written in parallel and both claimed {@code BUTTON_WRITE + 1}, so the merge
	 * had to move one of them. This test is what makes that collision fail loudly: ids
	 * {@code BUTTON_TAB_BASE + tab} must switch tabs and leave the flags alone, and
	 * {@code BUTTON_SUBSTITUTE_BASE + slot} must flip exactly that slot's flag and leave the tab alone.
	 *
	 * @implements TC-ASM-025 — the substitution toggle is reachable over the menu button channel.
	 */
	public static void sub05ButtonChannelRoutesToSubstitution(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AssemblerMenu menu = new AssemblerMenu(1, player.getInventory(), be, ContainerLevelAccess.NULL);
		for (int slot = 0; slot < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; slot++) {
			be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + slot, oakStickBlueprint(helper, be));
		}
		menu.setActiveTab(AssemblerMenu.TAB_RECORD);

		// A tab press must not be mistaken for a substitution press.
		if (!menu.clickMenuButton(player, AssemblerMenu.BUTTON_TAB_BASE + AssemblerMenu.TAB_WORK)) {
			helper.fail("the tab button must be accepted on the server");
			return;
		}
		for (int slot = 0; slot < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; slot++) {
			if (AssemblyBlueprintItem.substitutes(
					be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + slot))) {
				helper.fail("switching tabs flipped substitution on queue slot " + slot
						+ " — the tab and substitution button ids overlap");
				return;
			}
		}
		menu.setActiveTab(AssemblerMenu.TAB_RECORD);

		// Every queue slot must be reachable, and each press must hit only its own blueprint.
		for (int slot = 0; slot < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; slot++) {
			if (!menu.clickMenuButton(player, AssemblerMenu.BUTTON_SUBSTITUTE_BASE + slot)) {
				helper.fail("the substitution button was refused for queue slot " + slot);
				return;
			}
			if (menu.getActiveTab() != AssemblerMenu.TAB_RECORD) {
				helper.fail("pressing substitution for queue slot " + slot + " switched the tab");
				return;
			}
			for (int other = 0; other < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; other++) {
				boolean on = AssemblyBlueprintItem.substitutes(
						be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + other));
				if (on != (other <= slot)) {
					helper.fail("after toggling slots 0.." + slot + ", slot " + other
							+ " reads substitutes=" + on);
					return;
				}
			}
		}

		// The same id toggles back off: it is a toggle, not a latch.
		if (!menu.clickMenuButton(player, AssemblerMenu.BUTTON_SUBSTITUTE_BASE)) {
			helper.fail("the substitution button was refused on the second press");
			return;
		}
		if (AssemblyBlueprintItem.substitutes(be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START))) {
			helper.fail("a second press must switch substitution back off");
			return;
		}
		helper.succeed();
	}

	// -- breaking the machine (MOD-287 tail) ------------------------------------------------------

	/**
	 * Breaking the assembler itself hands back everything it was holding — the blueprints in its queue
	 * and whatever sat in the output area — and leaves the warehouse alone.
	 *
	 * <p>The gap this fills is a plain one: every other break case in the suite breaks a storage
	 * module, so the machine at the head of the warehouse had never been broken by a test at all. Two
	 * distinct failures are ruled out. A blueprint is not craftable back for free — losing the queue on
	 * a mis-swing is the loss the criterion names. And the machine must not spill the warehouse it
	 * merely reads from: the modules keep their own inventories, so a break next door has to leave them
	 * untouched.
	 *
	 * <p>Counts are exact in both directions, which is what makes this a dupe check as well as a loss
	 * check. The assembler drops on break through two independent paths — {@code playerWillDestroy} on
	 * the block and the container spill in {@code BlockEntity#preRemoveSideEffects} — and if both ever
	 * fire for one break the player gets his output area twice. "At least one stick on the ground"
	 * would not notice that; "exactly twelve" does.
	 *
	 * <p>What it does not cover: the block item itself. {@code GameTestHelper#destroyBlock} passes
	 * {@code dropBlock=false}, so the loot table never runs here — the assembler's own drop is the loot
	 * table's business and is covered by the loot-table checks, not by this test.
	 *
	 * @implements TC-ASM-026 — breaking the assembler returns its queue and output, and only those.
	 */
	public static void brk01BreakingTheMachineDropsQueueAndOutput(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 32));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + 1, stickBlueprint());
		be.setItem(AssemblerBlockEntity.OUTPUT_SLOT_START, new ItemStack(Items.STICK, 12));

		helper.destroyBlock(ASM);

		if (!helper.getBlockState(ASM).isAir()) {
			helper.fail("the assembler is still standing after being broken");
			return;
		}
		int blueprints = countDrops(helper, ASM, ModContent.ASSEMBLY_BLUEPRINT.get());
		if (blueprints != 2) {
			helper.fail("both queued blueprints must come back on the ground, found " + blueprints
					+ " — a blueprint carries a pattern the player recorded by hand and cannot be "
					+ "crafted back for free");
			return;
		}
		int sticks = countDrops(helper, ASM, Items.STICK);
		if (sticks != 12) {
			helper.fail("the twelve sticks in the output area must come back exactly once, found "
					+ sticks + " (fewer = lost on break, more = dropped twice)");
			return;
		}
		if (countDrops(helper, ASM, Items.OAK_PLANKS) != 0) {
			helper.fail("breaking the assembler spilled the warehouse next to it — the modules own "
					+ "their inventories and a break at the machine must not touch them");
			return;
		}
		StorageModuleBlockEntity stillThere =
				helper.getBlockEntity(STORE, StorageModuleBlockEntity.class);
		if (stillThere == null || count(stillThere, Items.OAK_PLANKS) != 32) {
			helper.fail("the warehouse module must still hold its 32 planks after the machine beside "
					+ "it was broken, got " + (stillThere == null ? "no module" : count(stillThere, Items.OAK_PLANKS)));
			return;
		}
		helper.succeed();
	}

	/**
	 * Total count of {@code item} lying within two blocks of {@code pos}.
	 *
	 * <p>Radius 2, for the reason spelled out in {@code TrellisScenarios#countDrops}: the NeoForge lane
	 * puts its rigs about six blocks apart, so a wider box counts the neighbouring test's drops and
	 * turns a real failure green, while {@code getBounds()} is empty for the code-registered NeoForge
	 * structures and makes every count zero.
	 */
	private static int countDrops(GameTestHelper helper, BlockPos pos, Item item) {
		AABB box = new AABB(helper.absolutePos(pos)).inflate(2.0);
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	// -- persistence (MOD-275/MOD-287 tail) -------------------------------------------------------

	/**
	 * A saved and reloaded assembler comes back with its queue, its output, its progress and its
	 * round-robin cursor.
	 *
	 * <p>State is produced by running the machine rather than by assignment: two blueprints and enough
	 * planks for part of a run, driven until an operation is in flight. That matters for the cursor,
	 * which only moves when the queue really rotates — setting it by hand would have proved that a
	 * field round-trips, not that the machine's own state does.
	 *
	 * <p>The cursor is the field with no visible symptom: a reload that dropped it would silently
	 * restart the rotation at the first blueprint, and the fairness between queued plans it exists for
	 * would quietly stop working. Nothing else in the suite would notice.
	 *
	 * <p>What this simulates is the NBT round-trip — {@code saveCustomOnly} into a fresh block entity
	 * of the same type, the same path a chunk save and reload takes. It does not restart a server or
	 * unload a chunk; the suite has no way to do either, and the mod's other persistence cases
	 * ({@code PersistenceGameTest}) are built the same way.
	 *
	 * @implements TC-ASM-027 — the assembler's queue, progress and cursor survive a reload.
	 */
	public static void per01AssemblerStateSurvivesReload(GameTestHelper helper) {
		AssemblerBlockEntity be = assembler(helper, ASM);
		StorageModuleBlockEntity store = warehouse(helper);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START, stickBlueprint());
		be.setItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + 1, stickBlueprint());

		// One whole operation, then a few ticks into the next: the cursor has rotated and progress is
		// mid-flight, so neither reads as its freshly-placed default.
		drive(helper, be, ticksFor(1) + 3);
		int cursor0 = be.queueCursor();
		int progress0 = be.getDataAccess().get(2);
		int sticks0 = output(be, Items.STICK);
		int queued0 = 0;
		for (int i = 0; i < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; i++) {
			if (AssemblyBlueprintItem.isRecorded(be.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + i))) {
				queued0++;
			}
		}
		if (cursor0 == 0 || progress0 <= 0 || sticks0 <= 0 || queued0 != 2) {
			helper.fail("the rig failed to produce state worth reloading: cursor=" + cursor0
					+ " progress=" + progress0 + " sticks=" + sticks0 + " queued=" + queued0
					+ " — a round-trip of default values proves nothing");
			return;
		}

		BlockPos abs = helper.absolutePos(ASM);
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = be.saveCustomOnly(registries);
		AssemblerBlockEntity restored =
				new AssemblerBlockEntity(abs, helper.getLevel().getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		int queued1 = 0;
		for (int i = 0; i < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; i++) {
			if (AssemblyBlueprintItem.isRecorded(restored.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + i))) {
				queued1++;
			}
		}
		if (queued1 != queued0) {
			helper.fail("the blueprint queue did not survive the reload: " + queued0 + " -> " + queued1);
			return;
		}
		if (output(restored, Items.STICK) != sticks0) {
			helper.fail("the output area did not survive the reload: " + sticks0 + " -> "
					+ output(restored, Items.STICK));
			return;
		}
		if (restored.getDataAccess().get(2) != progress0) {
			helper.fail("progress did not survive the reload: " + progress0 + " -> "
					+ restored.getDataAccess().get(2) + " — the half-finished operation would restart "
					+ "and the EU already spent on it would be gone");
			return;
		}
		if (restored.queueCursor() != cursor0) {
			helper.fail("the round-robin cursor did not survive the reload: " + cursor0 + " -> "
					+ restored.queueCursor() + " — the rotation would restart at the first blueprint "
					+ "on every reload and the fairness it exists for would silently stop");
			return;
		}
		helper.succeed();
	}
}
