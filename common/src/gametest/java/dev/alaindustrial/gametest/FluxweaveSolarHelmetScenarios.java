package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.energy.WornSourceDistributor;
import dev.alaindustrial.item.wearable.FluxweaveSolarHelmetItem;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-neutral gametest bodies for the Solar Fluxweave Helmet (MOD-664, suite TC-FLUXSOL-001).
 *
 * <p>The sky-to-EU table itself is pinned at L1 ({@code SolarWearOutputTest}); these prove the wiring that
 * L1 cannot reach: that the world state is read from the wearer's eyes, that the helmet starts itself from
 * empty through the real tick, that only a WORN helmet generates, and the order in which the energy lands
 * (the helmet, then the other armour, then the rest, never more than was made).
 */
public final class FluxweaveSolarHelmetScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluxweaveSolarHelmetScenarios::fun01NoonClearYieldsTheKnob,
						"fluxweave_solar_noon_clear_yields_the_knob").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::fun02EmptyHelmetStartsItselfThroughTheTick,
						"fluxweave_solar_empty_helmet_starts_itself").ticks(60).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::neg01NightYieldsNothing,
						"fluxweave_solar_night_yields_nothing").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::neg02BlockAboveHeadYieldsNothing,
						"fluxweave_solar_block_above_head_yields_nothing").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::neg03RainYieldsNothing,
						"fluxweave_solar_rain_yields_nothing").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::sta01LeavesAboveHalveTheOutput,
						"fluxweave_solar_leaves_above_halve_the_output").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::neg04UnwornHelmetYieldsNothing,
						"fluxweave_solar_unworn_helmet_yields_nothing").ticks(60).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::neg05PlainHelmetYieldsNothing,
						"fluxweave_solar_plain_helmet_yields_nothing").ticks(60).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::ord01HelmetIsFilledFirst,
						"fluxweave_solar_helmet_is_filled_first").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::ord02ArmourBeforeInventorySplitEvenly,
						"fluxweave_solar_armour_before_inventory_split_evenly").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::ord03InventoryGetsTheRestAndEnergyPackIsServed,
						"fluxweave_solar_inventory_gets_the_rest").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::ord04RotationWalksTheSmallBudgetRound,
						"fluxweave_solar_rotation_walks_small_budget_round").ticks(20).sky(),
				RosterEntry.of(FluxweaveSolarHelmetScenarios::eco01FullGearTakesNothing,
						"fluxweave_solar_full_gear_takes_nothing").ticks(20).sky());

		private Roster() {}
	}

	private FluxweaveSolarHelmetScenarios() {
	}

	/** Feet cell of the wearer; the eyes end up in the cell two above it. */
	private static final BlockPos STAND = new BlockPos(2, 2, 2);

	private static ItemStack stack(Supplier<Item> which, long eu) {
		ItemStack stack = new ItemStack(which.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static long capacity(Supplier<Item> which) {
		return ItemEnergy.capacity(new ItemStack(which.get()));
	}

	/** A survival mock in the staging cell wearing the solar helmet at {@code helmetEu}, clear noon. */
	private static ServerPlayer wearer(GameTestHelper helper, long helmetEu) {
		SolarPanelRig.setClearDay(helper);
		return wearerKeepingWeather(helper, helmetEu);
	}

	private static ServerPlayer wearerKeepingWeather(GameTestHelper helper, long helmetEu) {
		ServerPlayer player = survivalPlayer(helper);
		BlockPos abs = helper.absolutePos(STAND);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		player.setItemSlot(EquipmentSlot.HEAD, stack(ModContent.FLUXWEAVE_SOLAR_HELMET, helmetEu));
		return player;
	}

	private static long knob() {
		return ToolConfig.fluxweaveSolarEuPerSecond;
	}

	private static long worn(ServerPlayer player, EquipmentSlot slot) {
		return ItemEnergy.get(player.getItemBySlot(slot));
	}

	private static long solarSecond(ServerPlayer player) {
		return FluxweaveSolarHelmetItem.solarStep(player.getItemBySlot(EquipmentSlot.HEAD), player);
	}

	// ── FUN: the helmet makes energy ─────────────────────────────────────────────────────────────────

	/**
	 * @implements TC-FLUXSOL-001-FUN01 — under a clear noon sky the worn helmet gains exactly the knob in one
	 *     second.
	 */
	public static void fun01NoonClearYieldsTheKnob(GameTestHelper helper) {
		ServerPlayer player = wearer(helper, 1_000);
		long made = solarSecond(player);
		helper.assertTrue(made == knob(), "made " + made + ", expected the knob " + knob());
		helper.assertTrue(worn(player, EquipmentSlot.HEAD) == 1_000 + knob(),
				"helmet holds " + worn(player, EquipmentSlot.HEAD));
		helper.succeed();
	}

	/**
	 * Through the REAL tick, not the step: the base class returns early on an empty helmet, so a panel
	 * hung on the charged branch could never start. The clock cannot be set, so the test polls until the
	 * tick that lands on a whole second has run.
	 *
	 * @implements TC-FLUXSOL-001-FUN02 — a helmet at 0 EU charges itself under the sun with no outside help.
	 */
	public static void fun02EmptyHelmetStartsItselfThroughTheTick(GameTestHelper helper) {
		ServerPlayer player = wearer(helper, 0);
		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		helper.succeedWhen(() -> {
			helmet.getItem().inventoryTick(helmet, helper.getLevel(), player, EquipmentSlot.HEAD);
			helper.assertTrue(ItemEnergy.get(helmet) > 0, "an empty solar helmet stayed empty in the sun");
		});
	}

	// ── NEG: the table's zeros, read from the eyes ───────────────────────────────────────────────────

	/** @implements TC-FLUXSOL-001-NEG01 — at night the helmet makes nothing. */
	public static void neg01NightYieldsNothing(GameTestHelper helper) {
		SolarPanelRig.setNight(helper);
		ServerPlayer player = wearerKeepingWeather(helper, 1_000);
		helper.assertTrue(solarSecond(player) == 0, "night produced EU");
		helper.assertTrue(worn(player, EquipmentSlot.HEAD) == 1_000, "night changed the helmet charge");
		helper.succeed();
	}

	/** @implements TC-FLUXSOL-001-NEG02 — a block over the wearer's head blocks the sky: nothing. */
	public static void neg02BlockAboveHeadYieldsNothing(GameTestHelper helper) {
		SolarPanelRig.setClearDay(helper);
		helper.setBlock(STAND.above(4), Blocks.STONE);
		ServerPlayer player = wearerKeepingWeather(helper, 1_000);
		helper.assertTrue(solarSecond(player) == 0, "a roof still let the sun through");
		helper.succeed();
	}

	/** @implements TC-FLUXSOL-001-NEG03 — rain over the wearer: nothing. */
	public static void neg03RainYieldsNothing(GameTestHelper helper) {
		SolarPanelRig.setClearDay(helper);
		SolarPanelRig.setRaining(helper, false);
		ServerPlayer player = wearerKeepingWeather(helper, 1_000);
		helper.assertTrue(solarSecond(player) == 0, "rain produced EU");
		helper.succeed();
	}

	/** @implements TC-FLUXSOL-001-STA01 — light through leaves is scaled like the ground panel. */
	public static void sta01LeavesAboveHalveTheOutput(GameTestHelper helper) {
		SolarPanelRig.setClearDay(helper);
		helper.setBlock(STAND.above(4), Blocks.OAK_LEAVES);
		ServerPlayer player = wearerKeepingWeather(helper, 1_000);
		long expected = Math.round(knob() * GeneratorConfig.solarTransparentFactor);
		long made = solarSecond(player);
		helper.assertTrue(made == expected && made > 0 && made < knob(),
				"leaves gave " + made + ", expected " + expected + " of " + knob());
		helper.succeed();
	}

	/** @implements TC-FLUXSOL-001-NEG04 — a helmet in the inventory, or in the wrong slot, makes nothing. */
	public static void neg04UnwornHelmetYieldsNothing(GameTestHelper helper) {
		SolarPanelRig.setClearDay(helper);
		ServerPlayer player = wearerKeepingWeather(helper, 0);
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		ItemStack carried = stack(ModContent.FLUXWEAVE_SOLAR_HELMET, 0);
		player.getInventory().setItem(0, carried);
		helper.onEachTick(() -> {
			carried.getItem().inventoryTick(carried, helper.getLevel(), player, null);
			carried.getItem().inventoryTick(carried, helper.getLevel(), player, EquipmentSlot.CHEST);
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(ItemEnergy.get(carried) == 0, "a helmet that is not on the head generated EU");
			helper.succeed();
		});
	}

	/** @implements TC-FLUXSOL-001-NEG05 — the plain Fluxweave helmet is unchanged: it makes nothing. */
	public static void neg05PlainHelmetYieldsNothing(GameTestHelper helper) {
		SolarPanelRig.setClearDay(helper);
		ServerPlayer player = wearerKeepingWeather(helper, 0);
		ItemStack plain = stack(ModContent.FLUXWEAVE_HELMET, 0);
		player.setItemSlot(EquipmentSlot.HEAD, plain);
		helper.onEachTick(() -> plain.getItem().inventoryTick(plain, helper.getLevel(), player, EquipmentSlot.HEAD));
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(ItemEnergy.get(plain) == 0, "the plain helmet generated EU");
			helper.succeed();
		});
	}

	// ── ORD: where the energy goes ───────────────────────────────────────────────────────────────────

	/** @implements TC-FLUXSOL-001-ORD01 — the helmet fills first: the others get nothing while it has room. */
	public static void ord01HelmetIsFilledFirst(GameTestHelper helper) {
		ServerPlayer player = wearer(helper, 100);
		player.setItemSlot(EquipmentSlot.CHEST, stack(ModContent.FLUXWEAVE_CHESTPLATE, 0));
		ItemStack tool = stack(ModContent.ELECTRIC_DRILL, 0);
		player.getInventory().setItem(0, tool);
		solarSecond(player);
		helper.assertTrue(worn(player, EquipmentSlot.HEAD) == 100 + knob(),
				"the helmet did not take it all first");
		helper.assertTrue(worn(player, EquipmentSlot.CHEST) == 0 && ItemEnergy.get(tool) == 0,
				"something else got EU while the helmet had room");
		helper.succeed();
	}

	/**
	 * With the helmet full the three other pieces split the second evenly, and an inventory tool sees none
	 * of it while armour still has room. The sum of everything gained equals what was generated.
	 *
	 * @implements TC-FLUXSOL-001-ORD02 — armour before inventory, even split, delivered == generated.
	 */
	public static void ord02ArmourBeforeInventorySplitEvenly(GameTestHelper helper) {
		ServerPlayer player = wearer(helper, capacity(ModContent.FLUXWEAVE_SOLAR_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, stack(ModContent.FLUXWEAVE_CHESTPLATE, 0));
		player.setItemSlot(EquipmentSlot.LEGS, stack(ModContent.FLUXWEAVE_LEGGINGS, 0));
		player.setItemSlot(EquipmentSlot.FEET, stack(ModContent.FLUXWEAVE_BOOTS, 0));
		ItemStack tool = stack(ModContent.ELECTRIC_DRILL, 0);
		player.getInventory().setItem(0, tool);
		long made = solarSecond(player);
		long chest = worn(player, EquipmentSlot.CHEST);
		long legs = worn(player, EquipmentSlot.LEGS);
		long feet = worn(player, EquipmentSlot.FEET);
		helper.assertTrue(ItemEnergy.get(tool) == 0, "the tool was served while armour had room");
		helper.assertTrue(chest + legs + feet == made, "armour got " + (chest + legs + feet) + " of " + made);
		long lo = Math.min(chest, Math.min(legs, feet));
		long hi = Math.max(chest, Math.max(legs, feet));
		helper.assertTrue(lo >= made / 4 && hi - lo <= 2, "split not even: " + chest + "/" + legs + "/" + feet);
		helper.succeed();
	}

	/**
	 * Helmet full and the worn Energy Pack served in the armour group: what the pack cannot take (it takes
	 * at most its input rate for the second) goes to the carried tools, and the total equals what was made.
	 *
	 * @implements TC-FLUXSOL-001-ORD03 — the Energy Pack is served; delivered == generated.
	 */
	public static void ord03InventoryGetsTheRestAndEnergyPackIsServed(GameTestHelper helper) {
		ServerPlayer player = wearer(helper, capacity(ModContent.FLUXWEAVE_SOLAR_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, stack(ModContent.ENERGY_PACK, 0));
		ItemStack drill = stack(ModContent.ELECTRIC_DRILL, 0);
		ItemStack magnet = stack(ModContent.ELECTROMAGNET, 0);
		player.getInventory().setItem(0, drill);
		player.getInventory().setItem(1, magnet);
		long made = solarSecond(player);
		long pack = worn(player, EquipmentSlot.CHEST);
		long total = pack + ItemEnergy.get(drill) + ItemEnergy.get(magnet);
		helper.assertTrue(pack > 0, "the worn Energy Pack was not served");
		helper.assertTrue(total == made, "delivered " + total + " of " + made);
		helper.succeed();
	}

	/**
	 * A budget smaller than the number of hungry tools must not always land on the first one scanned: the
	 * list start rotates with the second.
	 *
	 * @implements TC-FLUXSOL-001-ORD04 — consecutive rotations serve different tools for a one-EU budget.
	 */
	public static void ord04RotationWalksTheSmallBudgetRound(GameTestHelper helper) {
		ServerPlayer player = wearer(helper, 0);
		ItemStack[] tools = {stack(ModContent.ELECTRIC_DRILL, 0), stack(ModContent.ELECTROMAGNET, 0),
				stack(ModContent.JETPACK, 0)};
		for (int i = 0; i < tools.length; i++) {
			player.getInventory().setItem(i, tools[i]);
		}
		for (int rotation = 0; rotation < tools.length; rotation++) {
			WornSourceDistributor.distributeArmorFirst(player, 1, 20, EquipmentSlot.HEAD, rotation);
		}
		for (int i = 0; i < tools.length; i++) {
			helper.assertTrue(ItemEnergy.get(tools[i]) == 1,
					"tool " + i + " holds " + ItemEnergy.get(tools[i]) + ", expected 1 after a full rotation");
		}
		helper.succeed();
	}

	// ── ECO: nothing is created, nothing is rewritten for no reason ──────────────────────────────────

	/** @implements TC-FLUXSOL-001-ECO01 — fully charged gear takes nothing: no item is served. */
	public static void eco01FullGearTakesNothing(GameTestHelper helper) {
		long full = capacity(ModContent.FLUXWEAVE_SOLAR_HELMET);
		ServerPlayer player = wearer(helper, full);
		player.setItemSlot(EquipmentSlot.CHEST, stack(ModContent.FLUXWEAVE_CHESTPLATE, full));
		player.getInventory().setItem(0, stack(ModContent.ELECTRIC_DRILL, capacity(ModContent.ELECTRIC_DRILL)));
		var result = WornSourceDistributor.distributeArmorFirst(player, knob(), 20, EquipmentSlot.HEAD, 0);
		helper.assertTrue(result.movedEu() == 0 && result.itemsServed() <= 0,
				"full gear still took " + result.movedEu() + " EU / " + result.itemsServed() + " items");
		long made = solarSecond(player);
		helper.assertTrue(made == knob() && worn(player, EquipmentSlot.HEAD) == full,
				"the surplus was not simply lost");
		helper.succeed();
	}
}
