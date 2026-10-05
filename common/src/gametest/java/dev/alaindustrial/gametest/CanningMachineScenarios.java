package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.GameTestDrive.drivePowered;
import static dev.alaindustrial.gametest.GameTestDrive.driveUnpowered;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.core.food.CanningMath;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral gametest bodies for the Canning Machine (MOD-383, suite TC-CAN-001). Wrapped by the
 * Fabric {@code CanningMachineGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane ({@code NeoForgeGameTests}, {@code canning_machine_*}), so both loaders run the SAME bodies.
 *
 * <p>What this machine does that no other machine here does — and therefore what these bodies exist
 * to pin down:
 *
 * <ul>
 * <li><b>Output from unlike inputs must merge.</b> {@link #fun02RationsFromDifferentFoodsStack} is
 * the whole reason the machine exists: if a ration ever gained per-stack data, rations made from
 * pork and from bread would stop merging and the player would carry exactly as many slots as before.
 * Every other test here would still pass.</li>
 * <li><b>It eats its own output.</b> The ration is food, so without an explicit exclusion the machine
 * happily grinds rations back into fewer rations, burning a tin can each pass
 * ({@link #reg02RationRefusedAsInput}).</li>
 * <li><b>Absorption never waits on the press.</b> A player pre-feeding food before the first can, or
 * power, or output room arrives must see it banked as calories immediately, not sitting untouched in
 * the slot ({@link #fun04AbsorptionNeedsNeitherCanNorPower}, MOD-488).</li>
 * <li><b>Value in strictly exceeds value out.</b> The one property standing between this machine and
 * a food duplicator ({@link #dup01ValueStrictlyDecreases}).</li>
 * </ul>
 */
public final class CanningMachineScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CanningMachineScenarios::fun01FoodBecomesRation, "canning_machine_food_becomes_ration")
						.fabricId("CanningMachineGameTest", "tcCan001Fun01_foodBecomesRation").ticks(400),
				RosterEntry.of(CanningMachineScenarios::fun02RationsFromDifferentFoodsStack,
								"canning_machine_rations_from_different_foods_stack")
						.fabricId("CanningMachineGameTest", "tcCan001Fun02_rationsFromDifferentFoodsStack").ticks(600),
				RosterEntry.of(CanningMachineScenarios::fun03RichFoodYieldsMoreRations,
								"canning_machine_rich_food_yields_more")
						.fabricId("CanningMachineGameTest", "tcCan001Fun03_richFoodYieldsMoreRations").ticks(1200),
				RosterEntry.of(CanningMachineScenarios::fun04AbsorptionNeedsNeitherCanNorPower,
								"canning_machine_absorption_needs_neither_can_nor_power")
						.fabricId("CanningMachineGameTest", "tcCan001Fun04_absorptionNeedsNeitherCanNorPower")
						.ticks(600),
				RosterEntry.of(CanningMachineScenarios::con02NoPowerNoOutput, "canning_machine_no_power_no_output")
						.fabricId("CanningMachineGameTest", "tcCan001Con02_noPowerNoOutput").ticks(600),
				RosterEntry.of(CanningMachineScenarios::con03FullOutputJams, "canning_machine_full_output_jams")
						.fabricId("CanningMachineGameTest", "tcCan001Con03_fullOutputJams").ticks(600),
				RosterEntry.of(CanningMachineScenarios::reg01HazardousFoodRefused,
								"canning_machine_hazardous_food_refused")
						.fabricId("CanningMachineGameTest", "tcCan001Reg01_hazardousFoodRefused").ticks(200),
				RosterEntry.of(CanningMachineScenarios::reg02RationRefusedAsInput,
								"canning_machine_ration_refused_as_input")
						.fabricId("CanningMachineGameTest", "tcCan001Reg02_rationRefusedAsInput").ticks(200),
				RosterEntry.of(CanningMachineScenarios::reg03NonFoodRefused, "canning_machine_non_food_refused")
						.fabricId("CanningMachineGameTest", "tcCan001Reg03_nonFoodRefused").ticks(200),
				RosterEntry.of(CanningMachineScenarios::dup01ValueStrictlyDecreases,
								"canning_machine_value_strictly_decreases")
						.fabricId("CanningMachineGameTest", "tcCan001Dup01_valueStrictlyDecreases").ticks(1200),
				RosterEntry.of(CanningMachineScenarios::per01BufferSurvivesReload,
								"canning_machine_buffer_survives_reload")
						.fabricId("CanningMachineGameTest", "tcCan001Per01_bufferSurvivesReload").ticks(400),
				RosterEntry.of(CanningMachineScenarios::eat01RationFeedsExactly, "canning_machine_ration_feeds_exactly")
						.fabricId("CanningMachineGameTest", "tcCan001Eat01_rationFeedsExactly").ticks(200),
				RosterEntry.of(CanningMachineScenarios::eat02FullPlayerCannotEat,
								"canning_machine_full_player_cannot_eat")
						.fabricId("CanningMachineGameTest", "tcCan001Eat02_fullPlayerCannotEat").ticks(200),
				RosterEntry.of(CanningMachineScenarios::eat03NoSideEffectsCarried,
								"canning_machine_no_side_effects_carried")
						.fabricId("CanningMachineGameTest", "tcCan001Eat03_noSideEffectsCarried").ticks(200));

		private Roster() {}
	}

	private CanningMachineScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/**
	 * Far above one ration's cost, set directly so the tier packet cap is bypassed — and set before EVERY tick
	 * ({@link GameTestDrive#drivePowered}), the way a connected cable keeps the machine fed.
	 */
	private static final long AMPLE_EU = 20_000L;

	/** Ticks for one press, plus slack for the scaled-duration knob. */
	private static int pressTicks() {
		return MachineRates.duration(Config.canningMachineDuration, Config.globalMachineSpeedMultiplier) + 20;
	}

	private static CanningMachineBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.CANNING_MACHINE.get());
		CanningMachineBlockEntity be = helper.getBlockEntity(POS, CanningMachineBlockEntity.class);
		if (be == null) {
			helper.fail("canning machine block entity missing after placement");
		}
		return be;
	}

	private static void load(CanningMachineBlockEntity be, ItemStack food, ItemStack cans) {
		be.setItem(CanningMachineBlockEntity.FOOD_SLOT, food);
		be.setItem(CanningMachineBlockEntity.CAN_SLOT, cans);
	}

	private static ItemStack cans(int count) {
		return new ItemStack(ModContent.EMPTY_CAN.get(), count);
	}

	private static ItemStack out(CanningMachineBlockEntity be) {
		return be.getItem(CanningMachineBlockEntity.OUTPUT_SLOT);
	}

	private static void assertRations(CanningMachineBlockEntity be, GameTestHelper helper, int count,
			String what) {
		ItemStack o = out(be);
		if (!o.is(ModContent.CANNED_RATION.get()) || o.getCount() != count) {
			helper.fail("expected " + count + " ration(s) from " + what + ", got "
					+ (o.isEmpty() ? "empty" : o.getCount() + "x " + o.getItem()));
		}
	}

	// ── FUN: the machine cans food ─────────────────────────────────────────────────────────────────

	/**
	 * Cooked beef plus an empty can becomes a ration, and the can is spent.
	 *
	 * @implements TC-CAN-001-FUN01 — food plus an empty can becomes a ration, spending one can.
	 */
	public static void fun01FoodBecomesRation(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		load(be, new ItemStack(Items.COOKED_BEEF, 8), cans(4));
		drivePowered(be, helper, pressTicks(), AMPLE_EU);
		assertRations(be, helper, 1, "cooked beef");
		if (be.getItem(CanningMachineBlockEntity.CAN_SLOT).getCount() != 3) {
			helper.fail("one press must spend exactly one empty can, can slot now holds "
					+ be.getItem(CanningMachineBlockEntity.CAN_SLOT).getCount());
		}
		helper.succeed();
	}

	/**
	 * Rations made from pork and rations made from bread occupy ONE stack.
	 *
	 * <p>The headline property of the whole feature, and the one a reader is most likely to break by
	 * "improving" the ration with a component that remembers its source. Minecraft merges stacks only
	 * when their components are equal ({@code ItemStack.isSameItemSameComponents}), so any such data
	 * silently un-merges these two and the machine stops saving the player a single slot — while every
	 * other test in this file keeps passing.
	 *
	 * @implements TC-CAN-001-FUN02 — rations from pork and from bread occupy one stack. The reason the
	 * machine exists: give the ration per-stack data and only this case goes red.
	 */
	public static void fun02RationsFromDifferentFoodsStack(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		load(be, new ItemStack(Items.COOKED_PORKCHOP, 4), cans(8));
		drivePowered(be, helper, pressTicks(), AMPLE_EU);
		ItemStack fromPork = out(be).copy();
		if (fromPork.isEmpty()) {
			helper.fail("no ration produced from pork");
		}

		// Clear the output and run the same machine on a completely different food.
		be.setItem(CanningMachineBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
		load(be, new ItemStack(Items.BREAD, 16), cans(8));
		drivePowered(be, helper, pressTicks(), AMPLE_EU);
		ItemStack fromBread = out(be).copy();
		if (fromBread.isEmpty()) {
			helper.fail("no ration produced from bread");
		}

		if (!ItemStack.isSameItemSameComponents(fromPork, fromBread)) {
			helper.fail("rations from pork and from bread do not stack — the machine's whole purpose "
					+ "is defeated. Something gave the ration per-stack data.");
		}
		helper.succeed();
	}

	/**
	 * Rich food buys more rations than poor food for the same item count.
	 *
	 * @implements TC-CAN-001-FUN03 — rich food out-yields poor food for the same item count.
	 */
	public static void fun03RichFoodYieldsMoreRations(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		load(be, new ItemStack(Items.COOKED_PORKCHOP, 8), cans(16));
		drivePowered(be, helper, pressTicks() * 6, AMPLE_EU);
		int rich = out(be).getCount();

		be.setItem(CanningMachineBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
		load(be, new ItemStack(Items.SWEET_BERRIES, 8), cans(16));
		drivePowered(be, helper, pressTicks() * 6, AMPLE_EU);
		int poor = out(be).getCount();

		if (rich <= poor) {
			helper.fail("eight cooked porkchops (" + rich + " rations) must out-yield eight sweet "
					+ "berries (" + poor + ") — the exchange is ignoring food value");
		}
		helper.succeed();
	}

	/**
	 * Food banks into calories even with no can, no power and a full output — only the paid PRESS step
	 * needs those (MOD-488). Before this, a machine with food but no can yet left it sitting in the
	 * slot untouched, so a player who fed it early got no head start once the first can arrived.
	 *
	 * @implements TC-CAN-001-FUN04 — absorption banks calories with no can and no power present.
	 */
	public static void fun04AbsorptionNeedsNeitherCanNorPower(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		load(be, new ItemStack(Items.COOKED_BEEF, 8), ItemStack.EMPTY);
		driveUnpowered(be, helper, pressTicks() * 2);
		if (be.getItem(CanningMachineBlockEntity.FOOD_SLOT).getCount() == 8) {
			helper.fail("food was not absorbed with no can and no power present");
		}
		if (be.foodBuffer() <= 0) {
			helper.fail("calorie buffer stayed at " + be.foodBuffer() + " with no can and no power present");
		}
		if (!out(be).isEmpty()) {
			helper.fail("a can-less, unpowered machine still pressed a ration");
		}
		helper.succeed();
	}

	// ── CON: the machine refuses to run ────────────────────────────────────────────────────────────

	/**
	 * Unpowered: no ration and no progress, even though absorption still banks calories.
	 *
	 * @implements TC-CAN-001-CON02 — unpowered: no ration and no can spent. @covers R-NRG-10
	 */
	public static void con02NoPowerNoOutput(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		load(be, new ItemStack(Items.COOKED_BEEF, 8), cans(4));
		driveUnpowered(be, helper, pressTicks() * 2);
		if (!out(be).isEmpty()) {
			helper.fail("an unpowered machine produced " + out(be).getCount() + " ration(s)");
		}
		if (be.getItem(CanningMachineBlockEntity.CAN_SLOT).getCount() != 4) {
			helper.fail("an unpowered machine still spent a can");
		}
		helper.succeed();
	}

	/**
	 * A full output slot jams the press, but absorption keeps banking calories regardless.
	 *
	 * @implements TC-CAN-001-CON03 — a full output jams the press; absorption still banks calories.
	 */
	public static void con03FullOutputJams(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		load(be, new ItemStack(Items.COOKED_BEEF, 16), cans(8));
		be.setItem(CanningMachineBlockEntity.OUTPUT_SLOT,
				new ItemStack(ModContent.CANNED_RATION.get(), 64));
		drivePowered(be, helper, pressTicks() * 2, AMPLE_EU);
		if (out(be).getCount() != 64) {
			helper.fail("full output slot changed to " + out(be).getCount() + " — overflow or voiding");
		}
		if (be.getItem(CanningMachineBlockEntity.CAN_SLOT).getCount() != 8) {
			helper.fail("a jammed machine still spent cans");
		}
		if (be.getItem(CanningMachineBlockEntity.FOOD_SLOT).getCount() == 16) {
			helper.fail("food was not absorbed while the output sat jammed");
		}
		helper.succeed();
	}

	// ── REG: what the input slot refuses ───────────────────────────────────────────────────────────

	private static void assertRefused(CanningMachineBlockEntity be, GameTestHelper helper, Item item,
			String why) {
		ItemStack stack = new ItemStack(item);
		if (be.canPlaceItem(CanningMachineBlockEntity.FOOD_SLOT, stack)) {
			helper.fail(why + " — but the food slot accepted " + item);
		}
		// The same guard has to hold for hoppers and pipes, which never consult the menu's Slot.
		if (be.canPlaceItemThroughFace(CanningMachineBlockEntity.FOOD_SLOT, stack, Direction.UP)) {
			helper.fail(why + " — accepted through a face by automation: " + item);
		}
	}

	/**
	 * Hazardous food is refused: canning would launder its risk away for free.
	 *
	 * @implements TC-CAN-001-REG01 — hazardous food is refused. @covers R-GUI-02
	 */
	public static void reg01HazardousFoodRefused(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		assertRefused(be, helper, Items.CHICKEN, "raw chicken carries salmonella");
		assertRefused(be, helper, Items.ROTTEN_FLESH, "rotten flesh carries hunger");
		assertRefused(be, helper, Items.POISONOUS_POTATO, "poisonous potato carries poison");
		assertRefused(be, helper, Items.PUFFERFISH, "pufferfish carries poison");
		assertRefused(be, helper, Items.SPIDER_EYE, "spider eye carries poison");
		// Positive control: the guard must not be refusing everything.
		if (!be.canPlaceItem(CanningMachineBlockEntity.FOOD_SLOT, new ItemStack(Items.COOKED_BEEF))) {
			helper.fail("the blacklist is rejecting ordinary food — cooked beef was refused");
		}
		helper.succeed();
	}

	/**
	 * The ration is refused as input.
	 *
	 * <p>It is food, so it passes the property filter that admits everything else. Without the explicit
	 * exclusion an output-to-input hopper loop grinds rations into fewer rations and burns a tin can
	 * every pass — quietly, because nothing errors.
	 *
	 * @implements TC-CAN-001-REG02 — the ration is refused as input, so the machine cannot eat itself.
	 */
	public static void reg02RationRefusedAsInput(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		assertRefused(be, helper, ModContent.CANNED_RATION.get(),
				"the machine must not eat its own output");
		helper.succeed();
	}

	/**
	 * Items that are not food at all are refused, in both slots.
	 *
	 * @implements TC-CAN-001-REG03 — non-food is refused, and each slot keeps its own contract.
	 */
	public static void reg03NonFoodRefused(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		assertRefused(be, helper, Items.COBBLESTONE, "cobblestone is not food");
		assertRefused(be, helper, ModContent.EMPTY_CAN.get(), "an empty can is not food");
		if (be.canPlaceItem(CanningMachineBlockEntity.CAN_SLOT, new ItemStack(Items.COOKED_BEEF))) {
			helper.fail("the can slot accepted food");
		}
		if (be.canPlaceItem(CanningMachineBlockEntity.OUTPUT_SLOT, new ItemStack(Items.COOKED_BEEF))) {
			helper.fail("the output slot accepted an insertion");
		}
		helper.succeed();
	}

	// ── DUP: the anti-duplication invariant ────────────────────────────────────────────────────────

	/**
	 * Food value out is strictly less than food value in.
	 *
	 * <p>Measured on the real machine rather than on {@link CanningMath} alone, so a block entity that
	 * charged the buffer twice, or forgot to subtract on press, is caught even though the arithmetic
	 * class stays correct.
	 *
	 * @implements TC-CAN-001-DUP01 — food value out is strictly below food value in.
	 */
	public static void dup01ValueStrictlyDecreases(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		int perItem = CanningMath.foodValue(8, 12.8f);          // cooked porkchop
		int items = 8;
		load(be, new ItemStack(Items.COOKED_PORKCHOP, items), cans(16));
		drivePowered(be, helper, pressTicks() * 8, AMPLE_EU);

		int consumed = items - be.getItem(CanningMachineBlockEntity.FOOD_SLOT).getCount();
		int valueIn = consumed * perItem;
		int valueOut = out(be).getCount() * CanningMath.RATION_VALUE + be.foodBuffer();
		if (valueOut >= valueIn) {
			helper.fail("canning did not lose value: " + valueIn + " in, " + valueOut
					+ " out (rations + leftover buffer). The machine is a food duplicator.");
		}
		helper.succeed();
	}

	// ── EAT: what the ration does to the player ────────────────────────────────────────────────────

	/**
	 * A ration restores exactly its declared nutrition and saturation, and nothing more.
	 *
	 * @implements TC-CAN-001-EAT01 — a ration restores exactly its declared nutrition and saturation.
	 */
	public static void eat01RationFeedsExactly(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		FoodData food = player.getFoodData();
		food.setFoodLevel(0);
		food.setSaturation(0.0f);

		ItemStack ration = new ItemStack(ModContent.CANNED_RATION.get());
		FoodProperties props = ration.get(DataComponents.FOOD);
		if (props == null) {
			helper.fail("the ration carries no food component at all");
			return;
		}
		food.eat(props);

		if (food.getFoodLevel() != CanningMath.RATION_NUTRITION) {
			helper.fail("a ration must restore " + CanningMath.RATION_NUTRITION + " hunger, restored "
					+ food.getFoodLevel());
		}
		float expected = CanningMath.RATION_NUTRITION * CanningMath.RATION_SATURATION_MODIFIER * 2.0f;
		if (Math.abs(food.getSaturationLevel() - expected) > 0.01f) {
			helper.fail("a ration must restore " + expected + " saturation, restored "
					+ food.getSaturationLevel());
		}
		helper.succeed();
	}

	/**
	 * A full player cannot eat a ration — it behaves like ordinary food.
	 *
	 * <p>Negative control built in: the same check on a golden apple must say yes, so a
	 * {@code canEat} that simply always refused would not pass this.
	 *
	 * @implements TC-CAN-001-EAT02 — a full player cannot eat a ration, unlike a golden apple.
	 */
	public static void eat02FullPlayerCannotEat(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getFoodData().setFoodLevel(20);

		FoodProperties ration = new ItemStack(ModContent.CANNED_RATION.get()).get(DataComponents.FOOD);
		if (ration == null) {
			helper.fail("the ration carries no food component");
			return;
		}
		if (player.canEat(ration.canAlwaysEat())) {
			helper.fail("a full player was allowed to eat a ration — canAlwaysEat leaked back in");
		}
		FoodProperties apple = new ItemStack(Items.GOLDEN_APPLE).get(DataComponents.FOOD);
		if (apple != null && !player.canEat(apple.canAlwaysEat())) {
			helper.fail("negative control failed: a full player cannot eat a golden apple either, "
					+ "so this test proves nothing about the ration");
		}
		helper.succeed();
	}

	/**
	 * The ration hands out no gameplay effect, so a canned golden apple cannot grant regeneration.
	 *
	 * <p>Checked on the component rather than by eating and watching: the effects live in
	 * {@code CONSUMABLE}, and reading them catches the regression at its source — someone copying the
	 * source food's Consumable across — instead of waiting for one specific buff to show up.
	 *
	 * <p>The assertion is "nothing but sound", not "nothing at all", and that distinction was found by
	 * this very test: the ration deliberately carries one {@link PlaySoundConsumeEffect}, the metallic
	 * clink of the emptied tin. A blanket empty-list check called that a failure. What must never
	 * appear is an effect that touches the player — status effects, teleports, cures.
	 *
	 * @implements TC-CAN-001-EAT03 — the ration carries no consume effects; the golden apple does.
	 */
	public static void eat03NoSideEffectsCarried(GameTestHelper helper) {
		ItemStack ration = new ItemStack(ModContent.CANNED_RATION.get());
		Consumable consumable = ration.get(DataComponents.CONSUMABLE);
		if (consumable == null) {
			helper.fail("the ration has no consumable component — it cannot be eaten at all");
			return;
		}
		for (ConsumeEffect effect : consumable.onConsumeEffects()) {
			if (!(effect instanceof PlaySoundConsumeEffect)) {
				helper.fail("the ration carries a gameplay consume effect ("
						+ effect.getClass().getSimpleName() + "); canning must strip everything but "
						+ "our own sound");
			}
		}
		// Positive control: the golden apple really does carry a gameplay effect, so a reader that
		// silently saw nothing anywhere would be caught here rather than passing.
		Consumable apple = new ItemStack(Items.GOLDEN_APPLE).get(DataComponents.CONSUMABLE);
		boolean appleHasGameplayEffect = apple != null && apple.onConsumeEffects().stream()
				.anyMatch(e -> !(e instanceof PlaySoundConsumeEffect));
		if (!appleHasGameplayEffect) {
			helper.fail("positive control failed: the golden apple reads as carrying no gameplay "
					+ "effect, so the assertion above cannot tell stripped from unread");
		}
		helper.succeed();
	}

	/**
	 * Banked calories survive a save/load round trip.
	 *
	 * <p>The buffer is the machine's only hidden state. If it failed to persist, a chunk unload would
	 * silently eat whatever the player had part-processed, and nothing else here would notice: every
	 * other body runs inside one uninterrupted tick loop.
	 *
	 * @implements TC-CAN-001-PER01 — banked calories survive a save/load round trip.
	 */
	public static void per01BufferSurvivesReload(GameTestHelper helper) {
		CanningMachineBlockEntity be = place(helper);
		// One porkchop banks 208 tenths against a 120 threshold, so a press leaves a real remainder.
		load(be, new ItemStack(Items.COOKED_PORKCHOP, 1), cans(1));
		drivePowered(be, helper, pressTicks(), AMPLE_EU);
		int banked = be.foodBuffer();
		if (banked <= 0) {
			helper.fail("expected leftover calories after one press, buffer is " + banked);
			return;
		}

		ServerLevel level = helper.getLevel();
		CompoundTag tag = be.saveCustomOnly(level.registryAccess());
		// Wipe the live value first, so a load that silently does nothing cannot pass by accident.
		be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(),
				new CompoundTag()));
		if (be.foodBuffer() != 0) {
			helper.fail("negative control failed: an empty tag left the buffer at " + be.foodBuffer());
			return;
		}
		be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		if (be.foodBuffer() != banked) {
			helper.fail("calorie buffer lost across save/load: " + banked + " → " + be.foodBuffer());
		}
		helper.succeed();
	}
}
